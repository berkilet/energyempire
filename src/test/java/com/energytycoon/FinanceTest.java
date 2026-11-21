package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Finans paketi 1: annüite, kredi notu, bankalar, ECA, tahsilat, gecikme, temerrüt, yapılandırma, kayıt dönüşümü. */
class FinanceTest {
    static final int HPM = Balance.HOURS_PER_MONTH;

    static GameState game(int rivals) {
        GameSettings g = SimHarness.reference(1);
        g.rivals = rivals; g.operatorGrowth = false;
        return GameState.newGame(g);
    }

    static Company rival(GameState s) { return s.companies.stream().filter(x -> !x.player && !x.neutral).findFirst().orElseThrow(); }

    static void days(GameState s, int d) { for (int h = 0; h < d * 24; h++) { s.tick(); s.messages.clear(); } }

    @Test
    void annuityExamples() {
        double t = Loan.annuity(100e6, 0.07, 4, 20);
        System.out.printf("ticari: taksit %.2fM, toplam %.1fM%n", t / 1e6, t * 20 / 1e6);
        assertEquals(5.97e6, t, 0.006e6);
        assertEquals(119.4e6, t * 20, 0.06e6);
        double e = Loan.annuity(85e6, 0.055, 2, 24);
        System.out.printf("ECA: taksit %.2fM, toplam %.1fM%n", e / 1e6, e * 24 / 1e6);
        assertEquals(4.89e6, e, 0.006e6);
        assertEquals(117.2e6, e * 24, 0.1e6);
    }

    @Test
    void ratingComputation() {
        assertEquals(Rating.A, Rating.compute(100, 150, 10));        // 1,5x ve 10x
        assertEquals(Rating.BBB, Rating.compute(100, 300, 20));      // 3,0x ve 5x
        assertEquals(Rating.BB, Rating.compute(100, 150, 35));       // kaldirac A, karsilama 2,86 -> BB (kotu olan)
        assertEquals(Rating.B, Rating.compute(100, 650, 30));        // 6,5x
        assertEquals(Rating.CCC, Rating.compute(100, 800, 10));      // 8x
        assertEquals(Rating.CCC, Rating.compute(0, 10, 1));          // FAVOK <= 0
        assertEquals(Rating.A, Rating.compute(100, -50, 0));         // net nakit, faiz yok
        assertEquals(Rating.CCC, Rating.compute(100, 100, 80));      // karsilama 1,25
    }

    @Test
    void ecaAlwaysCheaperThanCommercial() {
        GameState s = game(0);
        Company c = s.player;
        for (Rating r : Rating.values()) for (int score : new int[] { 0, 20, 95 }) for (Bank b : Bank.values()) {
            c.rating = r; c.bankScore[b.ordinal()] = score;
            double com = s.macro.policyRate + s.finance.commercialSpread(c, b, true, false);
            double eca = s.macro.policyRate + FinanceBalance.ECA_SPREAD;
            assertTrue(com >= eca + FinanceBalance.COMMERCIAL_MIN_OVER_ECA - 1e-12, r + " " + b + " " + score + ": ticari " + com + " ECA " + eca);
        }
    }

    @Test
    void newCompanyIsBB() {
        GameState s = game(0);
        assertEquals(Rating.BB, s.finance.rating(s.player));
        assertNotNull(s.finance.revolver(s.player), "işletme kredisi limiti açık");
    }

    @Test
    void relationshipTransitions() {
        GameState s = game(0);
        Company c = s.player;
        c.cash = 1e9;
        Loan l = s.finance.signCommercial(c, Bank.KIYI, 100e6, 3, 1, false, null);
        assertNotNull(l);
        int start = c.bankScore[0];
        days(s, 31 * 12 + 2);
        System.out.printf("ilişki: başlangıç %d, 12 aylık ödeme sonrası %d (en fazla +10)%n", start, c.bankScore[0]);
        assertEquals(start + FinanceBalance.REL_ON_TIME_CAP, c.bankScore[0]);
        // erken kapama: gecikmesiz kredi kapaninca +10
        assertNull(s.finance.prepay(c, l));
        assertEquals(start + FinanceBalance.REL_ON_TIME_CAP + FinanceBalance.REL_CLOSED, c.bankScore[0]);
        // gecikme: -15, 30 gunu asinca toplam -40
        // (santrali olmayan sirketin notu 4 ceyrek sonra CCC: yalnizca Demirtas kredi verir)
        Loan l2 = s.finance.signCommercial(c, Bank.DEMIRTAS, 100e6, 3, 1, false, null);
        int y0 = c.bankScore[2];
        c.cash = -1e6; s.finance.revolver(c).outstanding = s.finance.revolver(c).limit; // nakit ve limit yok
        days(s, 31);
        assertEquals(Loan.Status.LATE, l2.status);
        assertEquals(y0 + FinanceBalance.REL_LATE, c.bankScore[2]);
        days(s, 31);
        assertEquals(Math.max(0, y0 + FinanceBalance.REL_LATE30), c.bankScore[2], "puan 0'ın altına inmez");
    }

    @Test
    void lateToDefaultToRestructuringToBankruptcyForRival() {
        GameState s = game(1);
        Company r = rival(s);
        r.cash = 1e9;
        Loan l = s.finance.signCommercial(r, Bank.KIYI, 100e6, 5, 1, false, null);
        r.cash = -1; Loan rv = s.finance.revolver(r); rv.outstanding = rv.limit;
        long t0 = s.hour;
        int guard = 0;
        while (l.status != Loan.Status.LATE && guard++ < 60 * 24) { s.tick(); s.messages.clear(); r.cash = Math.min(r.cash, -1); }
        assertEquals(Loan.Status.LATE, l.status);
        assertEquals(Rating.BB.down(), s.finance.rating(r), "gecikmede not bir kademe düşer");
        for (int d = 0; d < 95 && !r.restructUsed; d++) { days(s, 1); r.cash = Math.min(r.cash, -1); }
        System.out.printf("gecikmeden yapılandırmaya %d gün; not %s; kredi %s%n", (s.hour - t0) / 24, s.finance.rating(r), l.status);
        assertTrue(r.restructUsed, "rakip yapılandırmayı kabul eder");
        assertEquals(Loan.Status.RESTRUCTURED, l.status);
        assertEquals(Rating.CCC, s.finance.rating(r));
        assertFalse(r.bankrupt);
        // ikinci temerrut: iflas
        for (int d = 0; d < 400 && !r.bankrupt; d++) { days(s, 1); r.cash = Math.min(r.cash, -1); }
        assertTrue(r.bankrupt, "yapılandırma sonrası yeniden temerrüt iflas");
    }

    @Test
    void playerRestructuringOfferAndAccept() {
        GameState s = game(0);
        Company c = s.player;
        c.cash = 1e9;
        Loan l = s.finance.signCommercial(c, Bank.KIYI, 100e6, 5, 1, false, null);
        long matBefore = l.maturityHour;
        c.cash = -1; Loan rv = s.finance.revolver(c); rv.outstanding = rv.limit;
        for (int d = 0; d < 130 && !c.restructOfferPending; d++) { days(s, 1); c.cash = Math.min(c.cash, -1); }
        assertTrue(c.restructOfferPending, "oyuncuya yapılandırma teklifi");
        assertFalse(c.bankrupt);
        s.finance.acceptRestructuring(c);
        assertEquals(Loan.Status.RESTRUCTURED, l.status);
        assertEquals(matBefore + 3L * Balance.HOURS_PER_YEAR, l.maturityHour, 2.0 * HPM);
        assertTrue(c.mustSellBy > s.hour);
        assertEquals(Rating.CCC, s.finance.rating(c));
    }

    @Test
    void ecaDefaultSeizesPlantOnly() {
        GameState s = game(1);
        Company c = s.player;
        c.cash = 5e9;
        Equipment e = s.catalog.get("at-cc"); // Avrupa: ECA uygun
        assertTrue(Finance.ecaEligible(e));
        int[] site = GridTest.siteAt(s, PlantType.GAS, s.cities.get(0), 8);
        Plant p = s.buildPlant(c, e, site[0], site[1]);
        Finance.EcaQuote q = s.finance.ecaQuote(c, e, Bank.KIYI, 6);
        double cash0 = c.cash;
        Loan eca = s.finance.signEca(c, p, Bank.KIYI, 6);
        assertNotNull(eca);
        assertEquals(cash0 + q.amount - q.premium - q.bankFee, c.cash, 1);
        Loan com = s.finance.signCommercial(c, Bank.DEMIRTAS, 50e6, 5, 3, false, null);
        assertNotNull(com);
        // ilk ECA vadesinde (odemesiz donem: yalnizca faiz) nakit yok
        days(s, 170);
        // nakit sifir, isletme kredisi dolu (eksi nakit kurali devreye girmesin); diger kredilerin taksitleri test disi
        c.cash = 0; Loan rv = s.finance.revolver(c); rv.outstanding = rv.limit;
        for (int d = 0; d < 160 && eca.status != Loan.Status.DEFAULTED; d++) {
            days(s, 1); c.cash = Math.min(c.cash, 0);
            com.dueAmount = 0; com.pendingPrincipal = 0; com.status = Loan.Status.ACTIVE; rv.dueAmount = 0; rv.status = Loan.Status.ACTIVE; c.negativeSinceHour = -1;
        }
        System.out.printf("ECA temerrüt: santral sahibi %s, ECA %s (gecikme %d gün, vade %.1fM, koruma %b), ticari %s%n", p.owner.name, eca.status, eca.daysLate, eca.dueAmount / 1e6, c.restructProtectUntil > s.hour, com.status);
        assertEquals(Loan.Status.DEFAULTED, eca.status);
        assertNotSame(c, p.owner, "bağlı santrale el konuldu");
        assertNotEquals(Loan.Status.DEFAULTED, com.status, "diğer krediler etkilenmez");
        assertEquals(0, c.bankScore[Bank.KIYI.ordinal()]);
        assertTrue(c.bankBanUntil[Bank.KIYI.ordinal()] > s.hour);
    }

    @Test
    void concentrationAndTotalLimit() {
        GameState s = game(0);
        Company c = s.player;
        double lim = s.finance.creditLimit(c);
        double kiyiMax = Bank.KIYI.concentration() * lim - s.finance.bankExposure(c, Bank.KIYI);
        int steps = (int) Math.floor(kiyiMax / FinanceBalance.LOAN_STEP);
        for (int i = 0; i < steps; i++) assertNotNull(s.finance.signCommercial(c, Bank.KIYI, FinanceBalance.LOAN_STEP, 5, 3, false, null), "adım " + i);
        String why = s.finance.refusal(c, Bank.KIYI, Loan.Type.COMMERCIAL, FinanceBalance.LOAN_STEP, null);
        System.out.println("yoğunlaşma: " + why);
        assertNotNull(why);
        assertTrue(why.startsWith("Yoğunlaşma"));
        assertNull(s.finance.refusal(c, Bank.DEMIRTAS, Loan.Type.COMMERCIAL, FinanceBalance.LOAN_STEP, null), "başka bankaya gidebilir");
        assertTrue(s.finance.limitUsage(c) <= lim + 1);
    }

    @Test
    void yesilRefusesFossilAndCccOnlyDemirtas() {
        GameState s = game(0);
        Company c = s.player;
        Plant gas = new Plant(); gas.type = PlantType.GAS;
        assertNotNull(s.finance.refusal(c, Bank.YESIL, Loan.Type.COMMERCIAL, 50e6, gas));
        c.rating = Rating.CCC;
        assertNotNull(s.finance.refusal(c, Bank.KIYI, Loan.Type.COMMERCIAL, 50e6, null));
        assertNull(s.finance.refusal(c, Bank.DEMIRTAS, Loan.Type.COMMERCIAL, 50e6, null));
    }

    @Test
    void legacySaveDebtConvertsToLoan() {
        GameState s = game(0);
        Json json = new Json();
        SaveData d = SaveData.from(s);
        SaveData.CompanyD pd = d.companies.get(0);
        pd.fin = null; pd.debt = 200e6; // eski kayit
        d.macro = null;
        GameState g = json.fromJson(SaveData.class, json.toJson(d)).restore();
        Loan l = g.player.loans.stream().filter(x -> x.type == Loan.Type.COMMERCIAL).findFirst().orElseThrow();
        assertEquals(200e6, l.outstanding, 1);
        assertEquals(Bank.KIYI, l.bank);
        assertEquals(3, l.frequencyMonths);
        assertEquals(60, l.tenorMonths);
        assertFalse(l.fixed);
        assertNotNull(g.finance.revolver(g.player));
        for (int h = 0; h < HPM; h++) g.tick();
        assertTrue(g.player.debt() > 0);
    }
}
