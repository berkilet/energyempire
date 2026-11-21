package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Finans paketi 2: pay alimi, devralma, rekabet otoritesi. */
class MnaTest {
    static final int HPM = Balance.HOURS_PER_MONTH;

    static GameState game(int rivals) {
        GameSettings g = SimHarness.reference(1);
        g.rivals = rivals; g.operatorGrowth = false;
        GameState s = GameState.newGame(g);
        s.instantLines = true;
        return s;
    }

    static List<Company> rivals(GameState s) { return s.companies.stream().filter(x -> !x.player && !x.neutral).toList(); }

    static void days(GameState s, int d) { for (int h = 0; h < d * 24; h++) { s.tick(); s.messages.clear(); } }

    /** Sirketi halka acik ve fiyatli yapar; kurucu payi f. */
    static void list(GameState s, Company c, double founderPct, double price) {
        s.updateValuations();
        c.eq.listed = true; c.eq.price = c.eq.fundamental / c.eq.shares; // fiyat = temel deger / hisse (price parametresi yok sayilir)
        c.eq.founder = c.eq.shares * founderPct;
        c.quarters = Math.max(c.quarters, 8); c.rating = Rating.BB;
    }

    /** Ayni sehre bagli bir santral (aninda devrede). */
    static Plant plant(GameState s, Company c, String eq, City city) {
        Equipment e = s.catalog.get(eq);
        c.cash += e.capex() * 2;
        Plant p = RivalAI.buildNear(s, c, e, city);
        assertNotNull(p, "santral yeri");
        p.buildHoursLeft = 0;
        return p;
    }

    @Test
    void founderAskFollowsSituation() {
        GameState s = game(2);
        days(s, 40);
        Company t = rivals(s).get(0);
        t.eq.askSeed = 0.5;
        t.rating = Rating.BBB; t.quarters = 8;
        Mna.Ask keep = s.mna.founderAsk(t, t.eq.shares * 0.2);
        Mna.Ask lose = s.mna.founderAsk(t, t.eq.shares * 0.6);
        System.out.printf("prim: kontrol korunur %.0f%%, kontrol kaybı %.0f%% (%s)%n", keep.premium * 100, lose.premium * 100, lose.reason);
        assertTrue(lose.controlLoss);
        assertTrue(lose.premium >= 0.60);
        assertTrue(keep.premium >= 0.05 && keep.premium <= 0.25, "kontrol korunur: %15-25 (nakit ihtiyacinda %5-15)");
        t.rating = Rating.B;
        Mna.Ask dist = s.mna.founderAsk(t, t.eq.shares * 0.6);
        assertTrue(dist.premium <= 0.10, "zor durumda %0-10");
        assertTrue(dist.reason.contains("zor durumda"));
    }

    @Test
    void negotiationAcceptCounterReject() {
        GameState s = game(2);
        days(s, 40);
        Company t = rivals(s).get(0);
        t.rating = Rating.BBB; t.quarters = 8;
        double n = t.eq.shares * 0.3, ask = s.mna.founderAsk(t, n).perShare;
        assertEquals(Mna.ACCEPT, s.mna.founderReply(t, n, ask, false).kind);
        Mna.Reply counter = s.mna.founderReply(t, n, ask * 0.9, false);
        assertEquals(Mna.COUNTER, counter.kind);
        assertEquals(ask, counter.perShare, 1e-9);
        assertEquals(Mna.REJECT, s.mna.founderReply(t, n, ask * 0.9, true).kind, "son turda istenenin alti ret");
        assertEquals(Mna.REJECT, s.mna.founderReply(t, n, ask * 0.5, false).kind, "cok dusuk teklif dogrudan ret");
        assertNotNull(s.mna.founderOfferBlocker(t, s.player, 1e6), "oyuncunun kurucu payi satilmaz");
        // anlasma: oyuncu %30 alir, iştirak
        s.player.cash = 5e9;
        Mna.Financing f = new Mna.Financing(); f.cash = n * ask;
        assertNull(s.mna.buyFromFounder(s.player, t, n, ask, f));
        assertEquals(0.30, s.mna.stake(s.player, t), 1e-9);
        assertEquals(0.70, t.eq.founderPct(), 1e-9);
    }

    @Test
    void marketBuyDisclosuresAndPriceImpact() {
        GameState s = game(2);
        days(s, 40);
        Company t = rivals(s).get(0), p = s.player;
        list(s, t, 0.6, 50);
        p.cash = 5e9;
        assertNull(s.mna.placeOrder(p, t, t.eq.shares * 0.12));
        double px0 = t.eq.price;
        s.messages.clear();
        int notes = 0;
        for (int d = 0; d < 10; d++) {
            for (int h = 0; h < 24; h++) { s.tick(); for (var m : s.messages) if (m.startsWith("PAY BİLDİRİMİ")) notes++; s.messages.clear(); }
        }
        System.out.printf("pay %.1f%%, fiyat %.2f → %.2f, bildirim %d%n", s.mna.stake(p, t) * 100, px0, t.eq.price, notes);
        assertEquals(0.12, s.mna.stake(p, t), 0.01, "savunma geri alimi hisse sayisini azaltir");
        assertEquals(2, notes, "%5 ve %10 esikleri");
        assertTrue(t.eq.price > px0 * 1.1, "alim ve esik bildirimleri fiyati yukseltir");
        assertTrue(t.eq.defenseUntil > s.hour, "hedef savunma modunda");
    }

    @Test
    void acceptanceCurve() {
        assertEquals(0.40, Mna.acceptance(0.20), 1e-9);
        assertEquals(0.65, Mna.acceptance(0.35), 1e-9);
        assertEquals(0.85, Mna.acceptance(0.50), 1e-9);
        assertTrue(Mna.acceptance(0.10) < 0.40);
    }

    @Test
    void acquisitionLoanPricing() {
        GameState s = game(2);
        days(s, 40);
        Company p = s.player, t = rivals(s).get(0);
        assertEquals(s.finance.acquisitionRate(p, Bank.KIYI) - 0.01 + 0.0, s.macro.policyRate + s.finance.commercialSpread(p, Bank.KIYI, false, false), 1e-9);
        double lemonGap = s.finance.acquisitionRate(p, Bank.DEMIRTAS) - (s.macro.policyRate + s.finance.commercialSpread(p, Bank.DEMIRTAS, false, false));
        assertEquals(0.005, lemonGap, 1e-9, "Lemon Brothers 0,5 puan ucuz (ticari + 1 - 0,5)");
        double max = 0.5 * s.capital.value(t);
        assertNotNull(s.finance.acquisitionRefusal(p, Bank.KIYI, max * 1.2, t), "hedef degerinin en fazla %50'si");
    }

    /** Iki rakip ve oyuncu ayni sehre santral; payi 12 aylik uretimle ayarlanir. */
    static Object[] market(double shareA, double shareB) {
        GameState s = game(2);
        days(s, 3);
        City c = s.cities.get(0);
        Company a = rivals(s).get(0), b = rivals(s).get(1);
        Plant pa = plant(s, a, "at-cc", c), pb = plant(s, b, "at-cc", c);
        s.markTopologyDirty();
        days(s, 1);
        GameState.Component comp = s.componentOf(pa);
        assertSame(comp, s.componentOf(pb), "ayni pazar");
        for (Plant x : s.plants) java.util.Arrays.fill(x.gen12, 0);
        // pazardaki diger tum santraller: kalan pay
        double other = Math.max(0, 1 - shareA - shareB);
        List<Plant> rest = comp.plants.stream().filter(x -> x != pa && x != pb).toList();
        // tum aylara yazilir (aylik devirde bir ay gercek uretimle degisir)
        java.util.Arrays.fill(pa.gen12, shareA * 1e6); java.util.Arrays.fill(pb.gen12, shareB * 1e6);
        for (Plant x : rest) java.util.Arrays.fill(x.gen12, other * 1e6 / Math.max(1, rest.size()));
        // diger pazarlar: ulusal payi dusuk tutmak icin buyuk uretim
        for (Plant x : s.plants) if (!comp.plants.contains(x)) java.util.Arrays.fill(x.gen12, 3e6 / Math.max(1, s.plants.size() - comp.plants.size()));
        if (rest.isEmpty() && other > 0) fail("pazarda baska santral yok");
        for (Company x : List.of(a, b)) java.util.Arrays.fill(x.eq.rev12, 100e6);
        return new Object[] { s, a, b };
    }

    @Test
    void competitionPreviewOutcomes() {
        Object[] m = market(0.30, 0.30);
        GameState s = (GameState) m[0]; Company a = (Company) m[1], b = (Company) m[2];
        a.eq.fundamental = 1e9; b.eq.fundamental = 1e9; a.eq.listed = b.eq.listed = false;
        Competition.Preview p = s.competition.preview(a, b);
        System.out.println("%30+%30: " + p.summary());
        assertEquals(Competition.APPROVE, p.outcome);
        assertEquals(0.60, p.rows.get(0).combined, 1e-6);

        m = market(0.35, 0.30);
        s = (GameState) m[0]; a = (Company) m[1]; b = (Company) m[2];
        a.eq.fundamental = 1e9; b.eq.fundamental = 1e9;
        p = s.competition.preview(a, b);
        System.out.println("%35+%30: " + p.summary());
        assertEquals(Competition.CONDITIONAL, p.outcome);
        assertTrue(p.divestMW > 0);
        // olcek farki muafiyeti: deger orani >= 1,5
        a.eq.fundamental = 1.6e9;
        p = s.competition.preview(a, b);
        assertEquals(Competition.APPROVE, p.outcome);
        assertTrue(p.exempt);
        assertEquals(1.6, p.valueRatio, 1e-9);
        // bildirim esiginin alti: incelemesiz onay
        a.eq.fundamental = 1e9;
        java.util.Arrays.fill(a.eq.rev12, 1e6); java.util.Arrays.fill(b.eq.rev12, 1e6);
        p = s.competition.preview(a, b);
        assertFalse(p.notifiable);
        assertEquals(Competition.APPROVE, p.outcome);
        assertEquals(0, p.reviewDays);
        // batan sirket savunmasi: esik %75
        java.util.Arrays.fill(a.eq.rev12, 100e6); java.util.Arrays.fill(b.eq.rev12, 100e6);
        b.restructUsed = true;
        p = s.competition.preview(a, b);
        assertEquals(Competition.APPROVE, p.outcome, "%65 < %75");
    }

    @Test
    void nationalCapRejectsEvenWithExemption() {
        Object[] m = market(0.45, 0.45);
        GameState s = (GameState) m[0]; Company a = (Company) m[1], b = (Company) m[2];
        for (Plant x : s.plants) { java.util.Arrays.fill(x.gen12, 0); if (x.owner == a || x.owner == b) x.gen12[0] = 1000; else x.gen12[0] = 1; }
        a.eq.fundamental = 10e9; b.eq.fundamental = 1e9;
        Competition.Preview p = s.competition.preview(a, b);
        System.out.println("ulusal: " + p.summary());
        assertEquals(Competition.REJECT, p.outcome);
        assertTrue(p.reason.contains("ulusal"));
    }

    @Test
    void tenderOfferTakeoverWithApproval() {
        Object[] m = market(0.20, 0.20);
        GameState s = (GameState) m[0]; Company a = (Company) m[1], b = (Company) m[2];
        list(s, b, 0.40, 50);
        a.cash = 5e9;
        a.eq.fundamental = 2e9;
        Mna.Financing f = new Mna.Financing(); f.cash = 4e9;
        double px = b.eq.price;
        f.cash = a.cash;
        assertNotNull(s.mna.launchTender(a, b, px * 1.1, f), "en az %20 prim");
        assertNull(s.mna.launchTender(a, b, px * 1.45, f));
        assertNotNull(s.mna.openOffer(b));
        days(s, 31);
        double st = s.mna.groupStake(a, b);
        System.out.printf("çağrı sonrası pay %.1f%%, sonuç: %s, savunma etkisi %.3f, temettü savunması %s%n", st * 100, s.deals.offers.get(0).result, s.deals.offers.get(0).defenseUplift, s.deals.offers.get(0).dividendDefense);
        assertTrue(st > 0.5, "kabul egrisiyle %50'yi gecer");
        DealBook.Review r = s.competition.pendingReview(b);
        assertNotNull(r, "otorite basvurusu");
        assertEquals(-1, b.eq.parent, "onaydan once kontrol yok");
        days(s, 31);
        assertTrue(r.decided);
        assertEquals(s.companies.indexOf(a), b.eq.parent, "onay sonrasi bagli ortaklik");
        assertTrue(s.mna.controls(a, b));
        assertNull(s.mna.setStrategy(a, b, 1));
        assertEquals(1, b.eq.strategy);
    }

    @Test
    void squeezeOutMergesCompanies() {
        Object[] m = market(0.10, 0.10);
        GameState s = (GameState) m[0]; Company a = (Company) m[1], b = (Company) m[2];
        list(s, b, 0.0, 40);
        a.cash = 10e9; a.eq.fundamental = 5e9;
        b.eq.add(s.companies.indexOf(a), b.eq.shares * 0.95);
        b.eq.parent = s.companies.indexOf(a);
        int plantsB = s.totalPlantsOf(b), plantsA = s.totalPlantsOf(a);
        double loansB = b.loans.size();
        s.mna.setStrategy(a, b, 0);
        // esik ustu: birlesme tetiklenir
        b.eq.parent = -1;
        DealBook.Review r = s.competition.file(a, b, true);
        days(s, 32);
        assertTrue(r.decided);
        assertTrue(b.eq.merged, "%90 ustu: ortakliktan cikarma ve birlesme");
        assertEquals(0, s.totalPlantsOf(b));
        assertEquals(plantsA + plantsB, s.totalPlantsOf(a));
        assertTrue(a.loans.size() >= loansB);
    }

    @Test
    void playerCanBeTakenOver() {
        Object[] m = market(0.15, 0.15);
        GameState s = (GameState) m[0]; Company a = (Company) m[1];
        Company p = s.player;
        list(s, p, 0.40, 30);
        a.cash = 10e9; a.eq.fundamental = 5e9;
        Mna.Financing f = new Mna.Financing(); f.cash = 9e9;
        assertNull(s.mna.launchTender(a, p, p.eq.price * 1.6, f));
        days(s, 31);
        assertTrue(s.mna.groupStake(a, p) > 0.5);
        for (int d = 0; d < 40 && !s.gameOver; d++) days(s, 1);
        assertTrue(s.gameOver);
        assertTrue(s.gameOverReason.startsWith("Şirketiniz devralındı"), s.gameOverReason);
    }

    @Test
    void conditionalApprovalCreatesDivestCommitment() {
        Object[] m = market(0.40, 0.30);
        GameState s = (GameState) m[0]; Company a = (Company) m[1], b = (Company) m[2];
        a.eq.fundamental = 1e9; b.eq.fundamental = 1e9;
        b.eq.add(s.companies.indexOf(a), b.eq.shares * 0.51); b.eq.founder = b.eq.shares * 0.49;
        DealBook.Review r = s.competition.file(a, b, true);
        assertEquals(Competition.CONDITIONAL, r.outcome);
        assertTrue(r.finalPhase);
        days(s, 4 * 30 + 2);
        assertTrue(r.decided);
        assertTrue(s.mna.controls(a, b));
        assertEquals(1, s.deals.commitments.size());
        DealBook.Commitment c = s.deals.commitments.get(0);
        System.out.printf("taahhüt: %.0f MW, kalan %.0f MW%n", c.requiredMW, s.competition.commitmentLeft(c));
        assertTrue(c.requiredMW > 0);
        // reddedilen taahhut: ret
        Object[] m2 = market(0.40, 0.30);
        GameState s2 = (GameState) m2[0]; Company a2 = (Company) m2[1], b2 = (Company) m2[2];
        a2.eq.fundamental = 1e9; b2.eq.fundamental = 1e9;
        b2.eq.add(s2.companies.indexOf(a2), b2.eq.shares * 0.51); b2.eq.founder = b2.eq.shares * 0.49;
        DealBook.Review r2 = s2.competition.file(a2, b2, false);
        days(s2, 4 * 30 + 2);
        assertFalse(s2.mna.controls(a2, b2));
        assertTrue(s2.mna.groupStake(a2, b2) <= 0.5 + 1e-6, "ret: pay %50'nin altina indirilir");
        assertEquals(1, s2.deals.rejections);
    }

    @Test
    void dealsSurviveSave() {
        Object[] m = market(0.20, 0.20);
        GameState s = (GameState) m[0]; Company a = (Company) m[1], b = (Company) m[2];
        list(s, b, 0.40, 50);
        a.cash = 5e9; a.eq.fundamental = 2e9;
        Mna.Financing f = new Mna.Financing(); f.cash = 4e9;
        assertNull(s.mna.launchTender(a, b, b.eq.price * 1.4, f));
        GameState back = new Json().fromJson(SaveData.class, new Json().toJson(SaveData.from(s))).restore();
        assertEquals(1, back.deals.offers.size());
        assertEquals(s.deals.offers.get(0).price, back.deals.offers.get(0).price, 1e-9);
        Plant p0 = s.plants.get(0);
        Plant q0 = back.plants.stream().filter(x -> x.id == p0.id).findFirst().orElseThrow();
        assertArrayEquals(p0.gen12, q0.gen12, 1e-9);
    }

    @Test
    void dominanceCapsPriceAtCostPlus15() {
        Object[] m = market(0.70, 0.10);
        GameState s = (GameState) m[0]; Company a = (Company) m[1];
        GameState.Component comp = null;
        for (Plant x : s.plants) if (x.owner == a) { comp = s.componentOf(x); x.lastMonthMWh = 1e5; x.lastMonthCost = 5e6; }
        // ayin sonuna kadar ilerle (aylik hesap)
        int m0 = (int) (s.hour / HPM);
        while ((int) (s.hour / HPM) == m0) { s.tick(); s.messages.clear(); for (Plant x : s.plants) if (x.owner == a) { x.monthMWh = 1e5; x.monthCost = 5e6; } }
        Plant pa = s.plants.stream().filter(x -> x.owner == a).findFirst().orElseThrow();
        System.out.printf("hakim durum tavanı %.1f $/MWh%n", pa.priceCap);
        double mwh = 0, cost = 0;
        for (Plant x : s.componentOf(pa).plants) if (x.owner == a) { mwh += x.lastMonthMWh; cost += x.lastMonthCost + Competition.capitalCostMonthly(x); }
        System.out.printf("beklenen tavan %.2f (yakıt+işletme 50 $/MWh + sermaye maliyeti, +%%15)%n", cost / mwh * 1.15);
        assertEquals(cost / mwh * 1.15, pa.priceCap, 1e-6, "ortalama uretim maliyeti (sermaye dahil) + %15");
        assertTrue(pa.priceCap > 57.5, "sermaye maliyeti tavani yukseltir");
        Company b = (Company) m[2];
        Plant pb = s.plants.stream().filter(x -> x.owner == b).findFirst().orElseThrow();
        assertEquals(0, pb.priceCap, "%10 pay: tavan yok");
    }

    @Test
    void regulatedMonopolyAboveEightyPercent() {
        Object[] m = market(0.45, 0.45);
        GameState s = (GameState) m[0]; Company a = (Company) m[1];
        for (Plant x : s.plants) java.util.Arrays.fill(x.gen12, x.owner == a ? 1e6 : 1);
        days(s, 31);
        assertEquals(s.companies.indexOf(a), s.deals.regulated, "organik %80 ustu: kamu hizmeti modu");
        assertFalse(s.deals.achievements.isEmpty());
        assertNotNull(s.competition.regulatedScore());
    }

    @Test
    void commitmentBreachAuctionsAndFines() {
        Object[] m = market(0.40, 0.30);
        GameState s = (GameState) m[0]; Company a = (Company) m[1], b = (Company) m[2];
        a.eq.fundamental = 1e9; b.eq.fundamental = 1e9;
        b.eq.add(s.companies.indexOf(a), b.eq.shares * 0.51); b.eq.founder = b.eq.shares * 0.49;
        s.competition.file(a, b, true);
        days(s, 4 * 30 + 2);
        DealBook.Commitment c = s.deals.commitments.get(0);
        double fines0 = a.fines + a.lastFines;
        c.deadline = s.hour + 24;
        days(s, 2);
        assertTrue(c.enforced, "sure doldu: otorite ihaleye cikarir");
        assertTrue(s.competition.commitmentLeft(c) <= 0.5, "eksik MW satildi");
        assertTrue(a.fines + a.lastFines > fines0, "yillik gelirin %2'si ceza");
    }

    @Test
    void thirdPartyCommentExtendsReviewOnce() {
        Object[] m = market(0.35, 0.30);
        GameState s = (GameState) m[0]; Company a = (Company) m[1], b = (Company) m[2];
        a.eq.fundamental = 1e9; b.eq.fundamental = 1e9;
        b.eq.add(s.companies.indexOf(a), b.eq.shares * 0.51); b.eq.founder = b.eq.shares * 0.49;
        DealBook.Review r = s.competition.file(a, b, true);
        long end = r.endHour;
        assertNull(s.competition.comment(r));
        assertEquals(end + HPM, r.endHour);
        assertNotNull(s.competition.comment(r), "islem basina bir kez");
    }

    @Test
    void consolidationScenarioRelaxesAuthority() {
        GameSettings g = SimHarness.reference(1);
        g.scenarioId = "consolidation";
        GameState s = GameState.newGame(g);
        assertTrue(s.deals.relaxed);
        assertEquals(0.80, s.competition.regionalLimit(s.player), 1e-9);
        assertEquals(1, s.scenario.objectives.size());
        assertEquals(FinanceBalance.CONSOLIDATION_DEADLINE, s.scenario.deadlineYear);
    }

    @Test
    void acquisitionLoanAmortizesFivePercentWithBalloon() {
        Object[] m = market(0.20, 0.20);
        GameState s = (GameState) m[0]; Company a = (Company) m[1], b = (Company) m[2];
        a.cash = 5e9;
        for (int k = 0; k < 4; k++) { a.qEbitda[k] = 200e6; b.qEbitda[k] = 100e6; }
        a.quarters = 8; b.eq.fundamental = 2e9;
        Loan l = s.finance.signAcquisition(a, Bank.KIYI, 400e6, b);
        assertNotNull(l, s.finance.acquisitionRefusal(a, Bank.KIYI, 400e6, b));
        double interest = 400e6 * l.effectiveRate() / 4;
        assertEquals(interest + 400e6 * 0.05 / 4, l.payment(400e6, l.remainingPeriods()), 1, "ceyreklik: faiz + yillik %5 anapara");
        assertEquals(100e6 * (1 + l.effectiveRate() / 4), l.payment(100e6, 1), 1, "son taksit: kalan bakiye toplu");
        double[] sch = s.finance.schedule12(a);
        double year = 0; for (double v : sch) year += v;
        System.out.printf("devralma kredisi 400M: ilk yıl borç servisi %.1fM (annüite olsaydı ~%.1fM)%n", year / 1e6, Loan.annuity(400e6, l.effectiveRate(), 4, 28) * 4 / 1e6);
        assertTrue(year < Loan.annuity(400e6, l.effectiveRate(), 4, 28) * 4 * 0.6);
    }

    @Test
    void subsidiaryPaysDividendsAndRatingIsConsolidated() {
        Object[] m = market(0.20, 0.20);
        GameState s = (GameState) m[0]; Company a = (Company) m[1], b = (Company) m[2];
        int ai = s.companies.indexOf(a);
        b.eq.add(ai, b.eq.shares * 0.6); b.eq.founder = b.eq.shares * 0.4; b.eq.parent = ai;
        b.cash = 1e9; double a0 = a.cash;
        assertEquals(FinanceBalance.SUB_DIVIDEND[0], s.capital.effectiveDividendPct(b), "halka kapali bagli ortaklik da temettu oder");
        b.eq.qNetAcc = 40e6;
        s.capital.quarterly(b);
        assertEquals(40e6 * 0.5 * 0.6, a.cash - a0, 1, "ana sirkete payi kadar nakit");
        // konsolide not: ana sirketin tek basina borcu yuksek, bagli ortakligin FAVOK'u eklenir
        for (int k = 0; k < 4; k++) { a.qEbitda[k] = 10e6; b.qEbitda[k] = 100e6; a.qInterest[k] = 1e6; b.qInterest[k] = 1e6; }
        assertEquals(s.finance.ebitda4(a) + s.finance.ebitda4(b), s.finance.groupEbitda4(a), 1);
        assertTrue(s.finance.leverage(a) < (a.debt() - a.cash + 1) / s.finance.ebitda4(a) + 1e9);
    }
}
