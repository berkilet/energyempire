package com.energytycoon;

import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Rakip YZ: kendi trafo merkezini kurma ve dolu hattını yükseltme. */
class RivalGridTest {
    static final int HPY = Balance.HOURS_PER_YEAR;

    static int rivalTmsAfterDecade(long seed, int size, int cities) {
        GameSettings g = SimHarness.reference(seed);
        g.size = size; g.cities = cities;
        GameState s = GameState.newGame(g);
        s.aiPlayer = true;
        for (int h = 0; h < 10 * HPY && !s.gameOver; h++) { s.tick(); s.messages.clear(); }
        System.out.printf("%dx%d tohum %d: 10 yılda rakiplerin kurduğu TM %d, şu an haritada %d%n", size, size, seed,
            s.statRivalTms, s.substations.stream().filter(t -> !t.owner.player).count());
        return s.statRivalTms;
    }

    @Test
    void rivalsBuildSubstations64() { assertTrue(rivalTmsAfterDecade(2, 64, 5) >= 1); }

    @Test
    void rivalsBuildSubstations96() { assertTrue(rivalTmsAfterDecade(1, 96, 12) >= 1); }

    /** Rakibin tek OG hatla bağlı santrali (ikinci yol yok veya 3 OG hat). */
    static Object[] saturatedRivalLine(String eq, PlantType type, int nLines) {
        GameSettings g = SimHarness.reference(1);
        g.rivals = 1; g.operatorGrowth = false;
        GameState s = GameState.newGame(g);
        s.instantLines = true;
        Company r = s.companies.stream().filter(x -> !x.player && !x.neutral).findFirst().orElseThrow();
        r.cash = 20e9;
        City c = s.cities.get(0);
        c.population = 3_000_000;
        int[] site = null;
        for (double d = 6; site == null && d <= 8; d += 0.5) site = GridTest.siteAt(s, type, c, d);
        assertNotNull(site, "saha");
        Plant p = s.buildPlant(r, s.catalog.get(eq), site[0], site[1]);
        p.buildHoursLeft = 0;
        for (int k = 0; k < nLines; k++) assertNotNull(s.buildLine(r, p, c, LineTier.OG));
        return new Object[] { s, r, p };
    }

    @Test
    void rivalUpgradesSaturatedLineInLowSeason() {
        // 600 MW nukleer tek OG hatta (60 MW): paralel hatla eksik kapatilamaz (en fazla 3 hat) -> yukseltme; ikinci yol yok
        Object[] o = saturatedRivalLine("atr-600", PlantType.NUCLEAR, 1);
        GameState s = (GameState) o[0]; Plant p = (Plant) o[2];
        int upgradeMonth = -1;
        for (int h = 0; h < 10 * HPY && s.statRivalUpgrades == 0; h++) {
            s.tick(); s.messages.clear();
            if (s.statRivalUpgrades > 0) upgradeMonth = s.month();
        }
        System.out.printf("rakip hat yükseltmesi (ikinci yol yok): %d, ay %d, kayıt %s%n", s.statRivalUpgrades, upgradeMonth,
            s.statUpgradeLog.stream().map(u -> "ikinciYol=" + u[2] + " süre=" + u[3] / 24 + "g").toList());
        assertTrue(s.statRivalUpgrades >= 1, "10 yılda en az bir yükseltme");
        long[] log = s.statUpgradeLog.get(0);
        assertEquals(0, log[2], "ikinci yol yok");
        assertEquals(GridBalance.LOW_DEMAND_MONTH, upgradeMonth, "ikinci yol yoksa talebin en düşük olduğu ayda");
        assertTrue(log[3] <= GridBalance.RIVAL_MAX_OFFLINE_DAYS * 24L);
        long h = 0;
        while (s.lines.stream().anyMatch(l -> l.status == LineStatus.UPGRADING) && h++ < 200 * 24) { s.tick(); s.messages.clear(); }
        System.out.printf("yükseltme %d günde bitti, santral %.0f MW üretiyor%n", h / 24, p.outputMW);
        assertTrue(h <= GridBalance.RIVAL_MAX_OFFLINE_DAYS * 24, "santral aylarca kesintide kalmaz: " + h / 24 + " gün");
        assertTrue(p.lines.stream().anyMatch(l -> l.tier == LineTier.YG), "OG → YG");
    }

    @Test
    void rivalWithSecondPathUpgradesWithoutWaiting() {
        // 200 MW gaz, 3 OG hat (180 MW): paralel sinir dolu -> yukseltme; diger iki hat ikinci yol
        Object[] o = saturatedRivalLine("at-cc", PlantType.GAS, 3);
        GameState s = (GameState) o[0]; Plant p = (Plant) o[2];
        // gecen yil dolulugu hazir olsun diye bir yil ilerle, sonra ocakta karar verilmeli (dusuk mevsim beklenmez)
        while (s.year() < Balance.START_YEAR + 1) { s.tick(); s.messages.clear(); }
        int before = s.statRivalUpgrades;
        for (int h = 0; h < 2 * Balance.HOURS_PER_MONTH; h++) { s.tick(); s.messages.clear(); }
        System.out.printf("ikinci yolla yükseltme: %d (ay %d)%n", s.statRivalUpgrades - before, s.month());
        assertTrue(s.statRivalUpgrades > before, "ikinci yol varken düşük mevsim beklenmez");
        assertEquals(1, s.statUpgradeLog.get(0)[2]);
        // yukseltme sirasinda diger hatlar calisiyor: santral uretiyor
        s.tick();
        assertTrue(p.outputMW > 0);
    }
}
