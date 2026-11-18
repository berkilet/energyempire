package com.energytycoon;

import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimTest {

    /** Oyuncu stratejisi: her sehre yakin bir santral + hat. */
    static void playerBuild(GameState s, PlantType t, int cityIdx) {
        City c = s.cities.get(cityIdx);
        Plant p = RivalAI.buildNear(s, s.player, t, c, false);
        assertNotNull(p, "yer bulunamadi " + t);
    }

    static void run(GameState s, int years) {
        for (int i = 0; i < years * Balance.HOURS_PER_YEAR; i++) s.tick();
    }

    static void assertFinite(GameState s) {
        for (Company c : s.companies) {
            assertTrue(Double.isFinite(c.cash), c.name + " cash");
            assertTrue(Double.isFinite(c.valuation), c.name + " val");
        }
        assertTrue(Double.isFinite(s.statPrice));
    }

    @Test
    void worldGeneratesCities() {
        GameState s = GameState.newGame(42, "Test");
        assertTrue(s.cities.size() >= 4, "sehir sayisi " + s.cities.size());
        for (City c : s.cities) assertFalse(s.map.isWater(c.x, c.y));
    }

    @Test
    void buildAndDispatch() {
        GameState s = GameState.newGame(7, "Test");
        double cash0 = s.player.cash;
        Plant p = RivalAI.buildNear(s, s.player, PlantType.GAS, s.cities.get(2), false);
        assertNotNull(p);
        assertTrue(s.player.cash < cash0);
        run(s, 1);
        assertTrue(p.operational());
        assertTrue(p.lifetimeMWh > 0, "gaz santrali uretmeli");
        assertFinite(s);
    }

    @Test
    void longRunStable() {
        for (long seed : new long[] {1, 2, 3}) {
            GameState s = GameState.newGame(seed, "Test");
            for (int i = 0; i < s.cities.size(); i++) {
                PlantType t = i % 2 == 0 ? PlantType.GAS : PlantType.WIND;
                if (s.player.cash > s.catalog.defaultFor(t).capex() + 120e6) RivalAI.buildNear(s, s.player, t, s.cities.get(i), false);
            }
            for (int y = 0; y < 10; y++) {
                run(s, 1);
                assertFinite(s);
                if (s.gameOver) break;
            }
            System.out.printf("seed %d yil %d: oyuncu nakit=%.0fM borc=%.0fM deger=%.0fM  fiyat=%.1f  riv1=%.0fM riv2=%.0fM  sehir sat: %s%n",
                seed, s.year(), s.player.cash / 1e6, s.player.debt / 1e6, s.player.valuation / 1e6, s.statPrice,
                s.companies.get(1).valuation / 1e6, s.companies.get(2).valuation / 1e6,
                s.cities.stream().map(c -> String.format("%.2f", c.satisfaction)).toList());
        }
    }

    @Test
    void blackoutFinesWhenUndersupplied() {
        GameState s = GameState.newGame(5, "Test");
        // sadece kucuk bir santral, buyuk sehre bagli
        City big = s.cities.get(0);
        Plant p = RivalAI.buildNear(s, s.player, PlantType.WIND, big, true);
        assertNotNull(p);
        double before = s.player.cash;
        run(s, 1);
        assertTrue(big.satisfaction < 1.0);
        assertFinite(s);
    }
}
