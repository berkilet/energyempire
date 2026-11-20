package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Faz 4: bara kümeleri, trafo merkezi, hat ucu kuralları ve üçüncü taraf erişim bedeli. */
class SubstationTest {

    static GameState grid(double population, int rivals) {
        GameSettings g = SimHarness.reference(1);
        g.rivals = rivals;
        GameState s = GameState.newGame(g);
        for (Plant p : new ArrayList<>(s.plants)) s.demolishPlant(p, false);
        for (PowerLine l : new ArrayList<>(s.lines)) s.removeLine(l, false);
        for (Company c : s.companies) c.cash = 50e9;
        s.cities.get(0).population = population;
        return s;
    }

    /** Sehirden ~d uzakta, n karelik yatay sirada bos kara kareleri (gaz icin uygun). */
    static int[][] row(GameState s, City c, double d, int n) {
        for (double dd = d; dd < d + 10; dd += 0.5)
            for (int x = 0; x + n < s.map.size; x++) for (int y = 0; y < s.map.size; y++) {
                if (Math.abs(Math.hypot(x - c.x, y - c.y) - dd) > 0.5) continue;
                boolean ok = true;
                for (int k = 0; k < n && ok; k++) ok = s.checkPlacement(PlantType.GAS, x + k, y) == null;
                if (!ok) continue;
                int[][] r = new int[n][];
                for (int k = 0; k < n; k++) r[k] = new int[] { x + k, y };
                return r;
            }
        fail("uygun sıra bulunamadı");
        return null;
    }

    static Plant ready(GameState s, Company owner, String eq, int[] at) {
        Plant p = s.buildPlant(owner, s.catalog.get(eq), at[0], at[1]);
        assertNotNull(p);
        p.buildHoursLeft = 0; p.outageHoursLeft = 0;
        return p;
    }

    static void step(GameState s) { s.markTopologyDirty(); s.tick(); }

    @Test
    void threeAdjacentPlantsShareOneLine() {
        GameState s = grid(4_000_000, 0);
        City c = s.cities.get(0);
        int[][] r = row(s, c, 8, 3);
        Plant a = ready(s, s.player, "at-cc", r[0]), b = ready(s, s.player, "at-cc", r[1]), d = ready(s, s.player, "at-cc", r[2]);
        assertNotNull(s.buildLine(s.player, b, c, LineTier.YG));
        step(s);
        assertSame(a.cp, b.cp); assertSame(b.cp, d.cp);
        assertTrue(a.cp.isBara());
        double total = a.outputMW + b.outputMW + d.outputMW;
        System.out.printf("bara: %.1f + %.1f + %.1f = %.1f MW%n", a.outputMW, b.outputMW, d.outputMW, total);
        assertEquals(250, total, 1e-6, "tek YG: küme toplamı 250 MW");
        // liyakat: en ucuz santral tam, sonraki kalan, en pahali kisinti
        List<Plant> byCost = new ArrayList<>(List.of(a, b, d));
        byCost.sort((x, y) -> Double.compare(s.marginalCost(x), s.marginalCost(y)));
        assertEquals(200, byCost.get(0).outputMW, 1e-6);
        assertEquals(50, byCost.get(1).outputMW, 1e-6);
        assertEquals(0, byCost.get(2).outputMW, 1e-6);
        assertEquals("Bağlantı dolu", byCost.get(2).idleReason);
        // Not: sehrin tek YG hatti talebi de 250 MW ile sinirlar; kisinti bu yuzden kucuk gorunebilir.
        assertTrue(byCost.get(2).curtailedMW > 0, "en pahalı santralde kısıntı");
    }

    @Test
    void rivalNeverJoinsPlayersBara() {
        GameState s = grid(4_000_000, 1);
        Company rival = s.companies.stream().filter(x -> !x.player && !x.neutral).findFirst().orElseThrow();
        City c = s.cities.get(0);
        int[][] r = row(s, c, 8, 2);
        Plant mine = ready(s, s.player, "at-cc", r[0]), theirs = ready(s, rival, "at-cc", r[1]);
        step(s);
        assertNotSame(mine.cp, theirs.cp);
    }

    @Test
    void transformerBottleneck() {
        GameState s = grid(4_000_000, 0);
        City c = s.cities.get(0);
        int[][] r = row(s, c, 10, 3);
        Substation tm = s.buildSubstation(s.player, 1, r[1][0], r[1][1] + 2);
        if (tm == null) tm = s.buildSubstation(s.player, 1, r[1][0], r[1][1] - 2);
        assertNotNull(tm, "TM yeri");
        tm.buildHoursLeft = 0;
        Plant a = ready(s, s.player, "at-cc", r[0]), b = ready(s, s.player, "at-cc", r[1]), d = ready(s, s.player, "at-cc", r[2]);
        assertNotNull(s.buildLine(s.player, tm, c, LineTier.YG));
        assertNotNull(s.buildLine(s.player, tm, c, LineTier.YG));
        tm.transformerMW = 300; // hatlar 500 MW, trafo 300 MW
        step(s);
        assertSame(tm.cp, a.cp); assertSame(tm.cp, d.cp);
        double total = a.outputMW + b.outputMW + d.outputMW;
        System.out.printf("TM darboğaz: hatlar %.0f MW, trafo %.0f MW → %.1f MW%n", tm.cp.lineCapacityMW(), tm.transformerMW, total);
        assertEquals(300, total, 1e-6);
        tm.transformerMW = 900;
        step(s);
        assertEquals(500, a.outputMW + b.outputMW + d.outputMW, 1e-6, "trafo büyüyünce hat sınırı");
    }

    @Test
    void rivalPaysAccessFee() {
        GameState s = grid(4_000_000, 1);
        Company rival = s.companies.stream().filter(x -> !x.player && !x.neutral).findFirst().orElseThrow();
        City c = s.cities.get(0);
        int[][] r = row(s, c, 10, 3);
        Substation tm = s.buildSubstation(s.player, 1, r[0][0], r[0][1]);
        assertNotNull(tm);
        tm.buildHoursLeft = 0;
        assertNotNull(s.buildLine(s.player, tm, c, LineTier.YG));
        Plant theirs = ready(s, rival, "at-cc", r[2]);
        step(s);
        assertTrue(s.substationsFor(theirs).contains(tm));
        assertNotSame(tm.cp, theirs.cp, "aktarılmadan katılmaz");
        s.setPreferredSubstation(theirs, tm.id);
        double cashP = s.player.cash, cashR = rival.cash;
        step(s);
        assertSame(tm.cp, theirs.cp);
        assertTrue(theirs.outputMW > 0);
        double expected = theirs.outputMW * (1 - tm.cp.lossFraction) * tm.accessFeePerMWh;
        System.out.printf("erişim: rakip %.1f MW, bedel %.0f $/saat, oyuncu erişim geliri %.0f $%n", theirs.outputMW, expected, s.player.accessIncome);
        assertEquals(expected, s.player.accessIncome, 1e-6);
        assertEquals(expected, rival.accessCost, 1e-6);
        assertTrue(s.player.cash > cashP, "TM sahibinin nakdi artar");
        // kapali erisim: hat cekilemez, aktarim gecersiz
        tm.openAccess = false;
        step(s);
        assertNotSame(tm.cp, theirs.cp);
        assertNotNull(s.checkLine(rival, theirs, tm, LineTier.OG));
    }

    @Test
    void lineEndRulesAndDemolition() {
        GameState s = grid(4_000_000, 0);
        City c = s.cities.get(0);
        int[][] r = row(s, c, 10, 2);
        Plant a = ready(s, s.player, "at-cc", r[0]), b = ready(s, s.player, "at-cc", r[1]);
        assertNotNull(s.checkLine(s.player, a, c, LineTier.CYG), "bara/küçük santralden ÇYG yok");
        assertNotNull(s.checkLine(s.player, a, c, LineTier.OG), "OG 8 kareden uzun olamaz");
        Substation tm = s.buildSubstation(s.player, 1, r[0][0], r[0][1] + 2);
        if (tm == null) tm = s.buildSubstation(s.player, 1, r[0][0], r[0][1] - 2);
        assertNotNull(tm);
        tm.buildHoursLeft = 0;
        assertNotNull(s.checkLine(s.player, tm, c, LineTier.CYG), "TM154'ten ÇYG yok");
        tm.level = 2;
        assertNull(s.checkLine(s.player, tm, c, LineTier.CYG), "TM380'den ÇYG olur");
        assertNotNull(s.buildLine(s.player, tm, c, LineTier.CYG));
        step(s);
        assertSame(tm.cp, a.cp);
        assertTrue(a.outputMW + b.outputMW > 0);
        s.demolishSubstation(tm, true);
        step(s);
        assertNull(a.cp.tm);
        assertSame(a.cp, b.cp, "TM yıkılınca bara kümesine döner");
        assertEquals(0, a.outputMW + b.outputMW, 1e-9, "hat TM ile birlikte kalktı");
        assertTrue(s.lines.isEmpty());
    }

    @Test
    void upgradesKeepWorking() {
        GameState s = grid(4_000_000, 0);
        City c = s.cities.get(0);
        int[][] r = row(s, c, 10, 1);
        Substation tm = s.buildSubstation(s.player, 1, r[0][0], r[0][1]);
        tm.buildHoursLeft = 0;
        step(s);
        assertNull(s.upgradeSubstation(tm, 2));
        assertNotNull(s.upgradeSubstation(tm, 1), "aynı anda tek yükseltme");
        step(s);
        assertNotNull(tm.cp, "yükseltmede TM çalışır");
        for (int h = 0; h < GridBalance.TM380_UPGRADE_DAYS * 24; h++) s.tick();
        assertEquals(2, tm.level);
        assertEquals(GridBalance.TM380_MW, tm.transformerMW, 1e-9);
    }

    @Test
    void saveRoundTripKeepsSubstation() {
        GameState s = grid(4_000_000, 1);
        Company rival = s.companies.stream().filter(x -> !x.player && !x.neutral).findFirst().orElseThrow();
        City c = s.cities.get(0);
        int[][] r = row(s, c, 10, 2);
        Substation tm = s.buildSubstation(s.player, 1, r[0][0], r[0][1]);
        tm.buildHoursLeft = 0; tm.accessFeePerMWh = 0; tm.openAccess = true;
        Plant theirs = ready(s, rival, "at-cc", r[1]);
        s.setPreferredSubstation(theirs, tm.id);
        Json json = new Json();
        String text = json.toJson(SaveData.from(s));
        GameState l = json.fromJson(SaveData.class, text).restore();
        Substation t2 = l.substations.get(0);
        assertEquals(tm.id, t2.id); assertEquals(0, t2.accessFeePerMWh, 1e-12); assertTrue(t2.openAccess);
        assertEquals(tm.id, ((Plant) l.nodeById(theirs.id)).preferredTm);
        assertSame(t2, l.nodeAt(t2.x, t2.y));
    }
}
