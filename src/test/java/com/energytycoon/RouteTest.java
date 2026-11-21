package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Faz 6: güzergah maliyeti, şehir içinden geçme yasağı, paralel hat indirimi, kademe yükseltme, hat arızaları. */
class RouteTest {

    static GameState grid() {
        GameSettings g = SimHarness.reference(1);
        g.rivals = 0;
        g.operatorGrowth = false;
        GameState s = GameState.newGame(g);
        s.instantLines = true;
        for (Plant p : new ArrayList<>(s.plants)) s.demolishPlant(p, false);
        for (PowerLine l : new ArrayList<>(s.lines)) s.removeLine(l, false);
        s.player.cash = 50e9;
        s.cities.get(0).population = 2_000_000;
        return s;
    }

    static Plant plant(GameState s, City c, double d) {
        int[] site = GridTest.siteAt(s, PlantType.GAS, c, d);
        assertNotNull(site);
        Plant p = s.buildPlant(s.player, s.catalog.get("at-cc"), site[0], site[1]);
        p.buildHoursLeft = 0; p.outageHoursLeft = 0;
        return p;
    }

    @Test
    void routeMultipliersPerTile() {
        GameState s = grid();
        City c = s.cities.get(0);
        Plant p = plant(s, c, 10);
        // tum guzergahi duz kara yap: carpan 1
        GameState.Route r0 = s.route(p, c);
        for (int pass = 0; pass < 2; pass++) {
            double len = s.lineLength(p, c);
            int steps = (int) Math.ceil(len * 2);
            for (int i = 0; i <= steps; i++) {
                int x = (int) Math.floor(p.x + 0.5 + (c.x - p.x) * i / (double) steps), y = (int) Math.floor(p.y + 0.5 + (c.y - p.y) * i / (double) steps);
                int idx = s.map.idx(x, y);
                s.map.water[idx] = false;
                s.map.level[idx] = pass == 0 ? 3 : GridBalance.ROUTE_HIGH_LEVEL + 2;
            }
            GameState.Route r = s.route(p, c);
            double expect = pass == 0 ? 1 : GridBalance.ROUTE_HIGH_MULT;
            assertEquals(expect, r.mult, 1e-9, "geçiş " + pass);
            assertEquals(s.lineLength(p, c) * LineTier.YG.costPerTile * expect, s.lineCost(p, c, LineTier.YG), 1e-3);
        }
        // yarisi su: carpan 1,5 ile 1,8 arasinda (kalani yuksek arazi)
        double len = s.lineLength(p, c);
        int steps = (int) Math.ceil(len * 2);
        for (int i = steps / 4; i <= steps * 3 / 4; i++) {
            int x = (int) Math.floor(p.x + 0.5 + (c.x - p.x) * i / (double) steps), y = (int) Math.floor(p.y + 0.5 + (c.y - p.y) * i / (double) steps);
            s.map.water[s.map.idx(x, y)] = true;
        }
        GameState.Route r = s.route(p, c);
        System.out.printf("güzergah: su %%%.0f, yüksek %%%.0f → ×%.2f (önceki ×%.2f)%n", r.waterFrac * 100, r.highFrac * 100, r.mult, r0.mult);
        assertTrue(r.waterFrac > 0.3 && r.mult > GridBalance.ROUTE_HIGH_MULT && r.mult < GridBalance.ROUTE_WATER_MULT);
    }

    @Test
    void lineCannotCrossCity() {
        GameState s = grid();
        for (City a : s.cities) for (City b : s.cities) {
            if (a == b) continue;
            double d = s.lineLength(a, b);
            if (d > 35) continue;
            double ux = (b.x - a.x) / d, uy = (b.y - a.y) / d;
            int x = (int) Math.round(b.x + ux * 5), y = (int) Math.round(b.y + uy * 5);
            if (s.checkPlacement(PlantType.GAS, x, y) != null) continue;
            Plant p = s.buildPlant(s.player, s.catalog.get("at-cc"), x, y);
            String why = s.checkLine(s.player, p, a, LineTier.YG);
            System.out.println("şehir geçişi: " + why);
            assertNotNull(why);
            assertTrue(why.startsWith("Hat şehir içinden geçemez"), why);
            assertNull(s.checkLine(s.player, p, b, LineTier.YG), "aradaki şehre bağlanmak serbest");
            return;
        }
        fail("uygun şehir çifti yok");
    }

    @Test
    void parallelLineDiscount() {
        GameState s = grid();
        City c = s.cities.get(0);
        Plant p = plant(s, c, 10);
        double first = s.lineCost(p, c, LineTier.YG);
        PowerLine l1 = s.buildLine(s.player, p, c, LineTier.YG);
        assertEquals(first, l1.buildCost, 1e-6);
        double second = s.lineCost(p, c, LineTier.YG);
        assertEquals(first * (1 - GridBalance.PARALLEL_DISCOUNT), second, 1e-6);
        s.buildLine(s.player, p, c, LineTier.YG);
        s.buildLine(s.player, p, c, LineTier.YG);
        assertNotNull(s.checkLine(s.player, p, c, LineTier.YG), "en fazla 3 hat");
    }

    @Test
    void tierUpgradeTakesLineOffline() {
        GameState s = grid();
        City c = s.cities.get(0);
        Plant p = plant(s, c, 6);
        PowerLine l = s.buildLine(s.player, p, c, LineTier.OG);
        s.markTopologyDirty(); s.tick();
        assertEquals(60, p.outputMW, 1e-6, "OG 60 MW");
        double expect = (s.routeCost(p, c, LineTier.YG) - s.routeCost(p, c, LineTier.OG)) * GridBalance.UPGRADE_COST_FACTOR;
        assertEquals(expect, s.upgradeCost(l), 1e-6);
        double cash = s.player.cash, book = l.buildCost;
        assertNull(s.upgradeLine(s.player, l));
        assertEquals(cash - expect, s.player.cash, 1e-6);
        assertEquals(LineStatus.UPGRADING, l.status);
        int hours = l.buildHoursLeft;
        assertEquals(Math.round(LineTier.YG.buildDays(l.lengthTiles) * GridBalance.UPGRADE_TIME_MULT) * 24, hours);
        s.tick();
        assertEquals(0, p.outputMW, 1e-9, "yükseltmede hat kapalı");
        for (int h = 0; h < hours + 1; h++) s.tick();
        assertEquals(LineStatus.ACTIVE, l.status);
        assertEquals(LineTier.YG, l.tier);
        assertEquals(book + expect, l.buildCost, 1e-6);
        assertEquals(200, p.outputMW, 1e-6, "YG ile santralin tamamı");
        // ÇYG: kucuk santrale olmaz
        assertNotNull(s.checkUpgrade(s.player, l));
    }

    @Test
    void lineOutagesRateDurationAndRepair() {
        GameState s = grid();
        int created = 0;
        for (City c : s.cities) for (double d = 5; d <= 14 && created < 45; d += 3) {
            Plant p = plant(s, c, d);
            for (int k = 0; k < 3; k++) if (s.buildLine(s.player, p, c, LineTier.YG) != null) created++;
        }
        // bir kismini UŞİ'ye devret: onarim ucretsiz olmali
        int transferred = 0;
        for (PowerLine l : new ArrayList<>(s.lines)) if (transferred++ % 3 == 0) s.operator.transfer(l);
        double expectedPerHour = 0;
        for (PowerLine l : s.lines) expectedPerHour += l.tier.outagePerTileHour * l.lengthTiles;
        int events = 0, minDur = Integer.MAX_VALUE, maxDur = 0, hours = 2 * Balance.HOURS_PER_YEAR;
        int[] before = new int[s.lines.size()];
        s.messages.clear();
        for (int h = 0; h < hours; h++) {
            for (int i = 0; i < before.length; i++) before[i] = s.lines.get(i).outageHoursLeft;
            s.tick();
            for (int i = 0; i < before.length; i++) {
                PowerLine l = s.lines.get(i);
                if (before[i] == 0 && l.outageHoursLeft > 0) { events++; minDur = Math.min(minDur, l.outageHoursLeft); maxDur = Math.max(maxDur, l.outageHoursLeft); }
            }
            while (s.messages.size() > 500) s.messages.poll();
        }
        double expected = expectedPerHour * hours;
        System.out.printf("hat arızası: %d hat, 2 yılda %d olay (beklenen ~%.0f), süre %d–%d saat%n", s.lines.size(), events, expected, minDur, maxDur);
        assertTrue(events > expected * 0.5 && events < expected * 1.6, events + " / " + expected);
        assertTrue(minDur >= GridBalance.LINE_OUTAGE_MIN_H && maxDur <= GridBalance.LINE_OUTAGE_MAX_H);
    }

    @Test
    void repairCostChargedToOwnerOnly() {
        GameState s = grid();
        City c = s.cities.get(0);
        Plant p = plant(s, c, 10);
        PowerLine mine = s.buildLine(s.player, p, c, LineTier.YG);
        PowerLine op = s.buildLine(s.player, p, c, LineTier.YG);
        s.operator.transfer(op);
        s.messages.clear();
        boolean sawMine = false, sawOp = false;
        for (int h = 0; h < 40 * Balance.HOURS_PER_YEAR && !(sawMine && sawOp); h++) {
            int bm = mine.outageHoursLeft, bo = op.outageHoursLeft;
            double om = s.player.omCost, cash = s.player.cash;
            s.tick();
            if (bm == 0 && mine.outageHoursLeft > 0 && !sawMine) {
                sawMine = true;
                assertTrue(s.messages.stream().anyMatch(m -> m.startsWith("HAT ARIZASI") && m.contains("onarım " + Math.round(mine.buildCost * GridBalance.LINE_REPAIR_COST / 1e3) + "K")), s.messages.toString());
            }
            if (bo == 0 && op.outageHoursLeft > 0 && !sawOp) {
                sawOp = true;
                assertTrue(s.messages.stream().anyMatch(m -> m.startsWith("HAT ARIZASI") && m.contains("onarım UŞİ'de")), s.messages.toString());
            }
            s.messages.clear();
        }
        assertTrue(sawMine && sawOp, "iki hatta da arıza görülmeli");
    }

    @Test
    void saveKeepsUpgrade() {
        GameState s = grid();
        City c = s.cities.get(0);
        Plant p = plant(s, c, 6);
        PowerLine l = s.buildLine(s.player, p, c, LineTier.OG);
        assertNull(s.upgradeLine(s.player, l));
        Json json = new Json();
        GameState g = json.fromJson(SaveData.class, json.toJson(SaveData.from(s))).restore();
        PowerLine l2 = g.lines.stream().filter(x -> x.id == l.id).findFirst().orElseThrow();
        assertEquals(LineStatus.UPGRADING, l2.status);
        assertEquals(LineTier.YG, l2.upgradeTo);
        assertEquals(l.upgradeCost, l2.upgradeCost, 1e-6);
    }
}
