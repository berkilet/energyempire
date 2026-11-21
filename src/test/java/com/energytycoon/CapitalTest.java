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

    /** Halka arz kosullarini saglayan sirket: 8 ceyrek, son 4 ceyrek karli, not BB. */
    static void eligible(GameState s, Company c) {
        c.quarters = 8; c.rating = Rating.BB; c.lateNotch = false;
        c.eq.qNetCount = 4;
        for (int k = 0; k < 4; k++) c.eq.qNet[k] = 20e6;
        s.updateValuations();
    }

    @Test
    void ipoRequiresConditionsAndTakesSixMonths() {
        GameState s = game(1);
        Company p = s.player;
        assertNotNull(s.capital.ipoBlocker(p), "8 ceyrek yok");
        days(s, 5);
        eligible(s, p);
        assertNull(s.capital.ipoBlocker(p));
        double[] est = s.capital.ipoEstimate(p, 0.30);
        assertEquals(1 / 1.30, est[0], 1e-9);
        assertEquals(0.30 * FinanceBalance.SHARES, est[3], 1e-3);
        double ps = p.eq.fundamental / p.eq.shares;
        assertNull(s.capital.startIpo(p, 0.30));
        assertNotNull(s.capital.ipoBlocker(p), "hazirlik suruyor");
        double cash0 = p.cash;
        // hazirlik boyunca karli kalsin (kosullar arz gununde yeniden bakilir)
        for (int m = 0; m < 6; m++) { days(s, 30); eligible(s, p); }
        days(s, 2);
        assertTrue(p.eq.listed, "6 ay sonra halka acik");
        assertEquals(13e6, p.eq.shares, 1e-3);
        assertEquals(1 / 1.3, p.eq.founderPct(), 1e-9);
        assertEquals(3e6, p.eq.free(), 1e-3);
        System.out.printf("arz fiyatı %.2f, temel/hisse %.2f, hasılat %.1fM%n", p.eq.ipoPrice, ps, p.eq.ipoProceeds / 1e6);
        assertTrue(p.eq.ipoPrice >= ps * 0.75 && p.eq.ipoPrice <= ps * 1.0, "temel degerin ~%80-95'i");
        assertEquals(3e6 * p.eq.ipoPrice * 0.97, p.eq.ipoProceeds, 1);
        assertTrue(p.cash > cash0 + p.eq.ipoProceeds * 0.5);
    }

    @Test
    void ipoBoundsAndCrisisChoice() {
        GameState s = game(0);
        Company p = s.player;
        days(s, 5);
        eligible(s, p);
        assertEquals(FinanceBalance.IPO_MIN, Capital.clampIpo(0.05));
        assertEquals(FinanceBalance.IPO_MAX, Capital.clampIpo(0.9));
        assertNull(s.capital.startIpo(p, 0.2));
        long ready = p.eq.ipoReadyHour;
        s.macro.events.add(new Macro.Event(Macro.EventType.CRISIS, 12));
        days(s, 2);
        assertTrue(p.eq.ipoCrisisPending, "oyuncu karar verir");
        s.capital.postponeIpo(p);
        assertTrue(p.eq.ipoReadyHour >= ready + 3L * HPM);
        s.capital.cancelIpo(p);
        assertFalse(s.capital.ipoPreparing(p));
        assertFalse(p.eq.listed);
        assertEquals("Halka arz penceresi kapalı (küresel finans krizi)", s.capital.ipoBlocker(p));
    }

    static void listNow(GameState s, Company c) {
        eligible(s, c);
        c.eq.listed = true; c.eq.price = c.eq.fundamental / c.eq.shares; c.eq.shares = 13e6; c.eq.lastIssueHour = s.hour;
    }

    @Test
    void rightsIssueOncePerYearDilutesAndDropsPrice() {
        GameState s = game(1);
        Company p = s.player;
        days(s, 5);
        listNow(s, p);
        assertNotNull(s.capital.issueBlocker(p), "yilda bir");
        p.eq.lastIssueHour = s.hour - Balance.HOURS_PER_YEAR;
        double px = p.eq.price, cash = p.cash, f0 = p.eq.founderPct();
        double[] q = s.capital.issueEstimate(p, 0.10);
        assertTrue(q[2] >= 0.10 - 1e-9 && q[2] <= 0.20 + 1e-9, "iskonto %10-20");
        assertTrue(q[3] >= 0.03 - 1e-9 && q[3] <= 0.08 + 1e-9, "duyuru dususu %3-8");
        assertNull(s.capital.issue(p, 0.10));
        assertEquals(13e6 * 1.1, p.eq.shares, 1e-3);
        assertTrue(p.eq.founderPct() < f0);
        assertTrue(p.eq.price < px);
        assertEquals(cash + q[1], p.cash, 1);
        assertEquals(1.3e6 * px * (1 - q[2]) * 0.97, q[1], 1);
        assertNotNull(s.capital.issueBlocker(p));
        assertNotNull(s.capital.issueBlocker(rival(s)), "kapali sirket yeni hisse basamaz");
    }

    @Test
    void buybackCancelsSharesAndRaisesFounderStake() {
        GameState s = game(0);
        Company p = s.player;
        days(s, 5);
        listNow(s, p);
        p.cash += 2e9;
        double free0 = p.eq.free(), f0 = p.eq.founderPct();
        s.capital.setBuyback(p, 1e12);
        days(s, 1);
        assertEquals(free0 * 0.98, p.eq.free(), free0 * 0.002, "gunluk en fazla serbest dolasimin %2'si");
        days(s, 20);
        assertTrue(p.eq.founderPct() > f0);
        assertEquals(10e6, p.eq.founder, 1e-6, "kurucu hissesi degismez, oran artar");
    }

    @Test
    void dividendsStayWithFounderAndPayOtherHolders() {
        GameState s = game(1);
        Company r = rival(s), p = s.player;
        int pi = s.companies.indexOf(p);
        days(s, 5);
        listNow(s, r);
        r.eq.founder = 8e6; r.eq.add(pi, 2e6); // 13M hisse: kurucu 8, oyuncu 2, serbest 3
        r.cash = 1e9;
        s.capital.setDividend(r, 50);
        r.eq.qNetAcc = 30e6;
        s.capital.quarterly(r);
        double total = 30e6 * 0.5;
        System.out.printf("temettü toplam %.1fM, oyuncuya %.2fM%n", r.eq.lastDividend / 1e6, p.dividendIncome / 1e6);
        assertEquals(total, r.eq.lastDividend, 1);
        assertEquals(total * 2 / 13, p.dividendIncome, 1, "oyuncu payi kadar temettu alir");
        // taahhut ihlali: dagitilamaz
        r.dividendBan = true;
        assertNotNull(s.capital.dividendBlocker(r));
    }
}
