package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Finans paketi 2: sahiplik, degerleme, hisse fiyati, halka arz, sermaye islemleri. */
class CapitalTest {
    static final int HPM = Balance.HOURS_PER_MONTH;

    static GameState game(int rivals) {
        GameSettings g = SimHarness.reference(1);
        g.rivals = rivals; g.operatorGrowth = false;
        return GameState.newGame(g);
    }

    static Company rival(GameState s) { return s.companies.stream().filter(x -> !x.player && !x.neutral).findFirst().orElseThrow(); }

    static void days(GameState s, int d) { for (int h = 0; h < d * 24; h++) { s.tick(); s.messages.clear(); } }

    @Test
    void everyoneStartsPrivateWithFullFounderStake() {
        GameState s = game(2);
        for (Company c : s.companies) {
            assertFalse(c.eq.listed);
            assertEquals(FinanceBalance.SHARES, c.eq.shares);
            assertEquals(1.0, c.eq.founderPct(), 1e-9);
            assertEquals(0, c.eq.free(), 1e-6);
        }
    }

    @Test
    void multipleFollowsPolicyRate() {
        GameState s = game(0);
        s.macro.policyRate = 0.045; assertEquals(12, s.capital.baseMultiple(), 1e-9);
        s.macro.policyRate = 0.025; assertEquals(15, s.capital.baseMultiple(), 1e-9);
        s.macro.policyRate = 0.065; assertEquals(9, s.capital.baseMultiple(), 1e-9);
        s.macro.policyRate = 0.11;  assertEquals(FinanceBalance.PE_MIN, s.capital.baseMultiple(), 1e-9);
        s.macro.policyRate = 0.0;   assertEquals(18.75, s.capital.baseMultiple(), 1e-9);
    }

    @Test
    void privateValueIsFundamentalAndListedPriceMovesDaily() {
        GameState s = game(1);
        days(s, 70);
        Company p = s.player;
        assertEquals(p.eq.fundamental, s.capital.value(p), 1);
        assertEquals(p.eq.fundamental / p.eq.shares, p.eq.price, 1e-6);
        // halka acik: fiyat gunluk olusur, temel deger etrafinda
        p.eq.listed = true;
        double[] prices = new double[30];
        for (int d = 0; d < 30; d++) { days(s, 1); prices[d] = p.eq.price; }
        double min = 1e18, max = 0;
        for (double v : prices) { min = Math.min(min, v); max = Math.max(max, v); }
        System.out.printf("fiyat aralığı %.2f–%.2f, temel/hisse %.2f%n", min, max, p.eq.fundamental / p.eq.shares);
        assertTrue(max > min, "fiyat gunluk degisir");
        assertTrue(Math.abs(prices[29] / (p.eq.fundamental / p.eq.shares) - 1) < 0.5);
        double before = p.eq.newsMult;
        s.capital.news(p, -0.15, "test");
        assertEquals(before * 0.85, p.eq.newsMult, 1e-9);
    }

    @Test
    void scenarioValueUsesMarketPriceWhenListed() {
        GameState s = game(0);
        days(s, 40);
        Company p = s.player;
        p.eq.listed = true; p.eq.price = 123;
        assertEquals(123 * p.eq.shares, s.capital.value(p), 1e-3);
    }

    @Test
    void equitySurvivesSave() {
        GameState s = game(1);
        days(s, 35);
        Company r = rival(s);
        r.eq.listed = true; r.eq.founder = 6e6; r.eq.add(s.companies.indexOf(s.player), 1.5e6); r.eq.dividendPct = 30;
        GameState back = new Json().fromJson(SaveData.class, new Json().toJson(SaveData.from(s))).restore();
        Company r2 = back.companies.get(s.companies.indexOf(r));
        assertTrue(r2.eq.listed);
        assertEquals(6e6, r2.eq.founder);
        assertEquals(1.5e6, r2.eq.held(back.companies.indexOf(back.player)));
        assertEquals(2.5e6, r2.eq.free(), 1e-6);
        assertEquals(30, r2.eq.dividendPct);
    }
}
