package com.energytycoon;

import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Kurulum ekraninin finansman yollari: oz kaynak, ECA, ticari kredi. */
class PurchaseTest {
    static int[] site(GameState s, Equipment e) { return RivalAI.findSite(s, e, s.cities.get(0), 2, 8); }

    @Test
    void commercialLoanFinancesPlant() {
        GameState s = GameState.newGame(SimHarness.reference(2));
        Company p = s.player;
        Equipment e = s.catalog.get("nw-45");
        int[] xy = site(s, e);
        double cash0 = p.cash, amount = Math.round(e.capex() * 0.7);
        int loans0 = p.loans.size();
        Plant pl = s.finance.buildWithCommercial(p, e, xy[0], xy[1], Bank.KIYI, amount, 7, 6);
        assertNotNull(pl, "ticari krediyle kurulum");
        assertEquals(loans0 + 1, p.loans.size());
        Loan l = p.loans.get(p.loans.size() - 1);
        assertEquals(Loan.Type.COMMERCIAL, l.type);
        assertEquals(amount, l.principal, 1);
        double fee = cash0 + amount - e.capex() - p.cash;
        assertTrue(fee > 0 && fee < amount * 0.02, "pesin cikan: bedel - kredi + masraf");
    }

    @Test
    void refusedLoanBuildsNothing() {
        GameState s = GameState.newGame(SimHarness.reference(2));
        Company p = s.player;
        Equipment coal = s.catalog.get("th-usc");
        int[] xy = site(s, coal);
        int plants = s.plants.size();
        assertNull(s.finance.buildWithCommercial(p, coal, xy[0], xy[1], Bank.YESIL, coal.capex() * 0.7, 7, 6), "Yesil Banka fosile kredi vermez");
        assertEquals(plants, s.plants.size());
        assertNull(s.finance.buildWithCommercial(p, coal, xy[0], xy[1], Bank.KIYI, 50e9, 7, 6), "limit asimi");
        assertEquals(plants, s.plants.size());
    }

    @Test
    void siteEvaluationUsesTile() {
        GameState s = GameState.newGame(SimHarness.reference(2));
        Equipment w = s.catalog.get("nw-45");
        int[] xy = site(s, w);
        RivalStrategy.Evaluation ev = RivalStrategy.evaluate(s, s.player, w, s.cities.get(0), xy[0], xy[1]);
        assertEquals(s.siteScore(w, xy[0], xy[1]), ev.cf1, 1e-9, "kurulum ekrani o karenin verimini kullanir");
        assertTrue(ev.mwh1 > 0);
    }
}
