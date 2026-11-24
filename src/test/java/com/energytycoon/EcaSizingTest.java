package com.energytycoon;

import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ECA borc kapasitesi: kredi beklenen nakde gore boyutlanir; vade teknolojiye gore. */
class EcaSizingTest {
    @Test
    void loanSizedToCashFlowAndTenorsByTechnology() {
        GameState s = GameState.newGame(SimHarness.reference(2));
        Company p = s.player;
        for (String id : new String[] { "hx-topcon", "nw-45", "mp-ha", "th-usc" }) {
            Equipment e = s.catalog.get(id);
            int[] xy = RivalAI.findSite(s, e, s.cities.get(0), 2, 8);
            Finance.EcaQuote q = s.finance.ecaQuote(p, e, Bank.KIYI, 6, xy[0], xy[1]);
            assertTrue(q.amount <= e.capex() * FinanceBalance.ECA_SHARE + 1, id + ": en fazla %85");
            double annualService = q.installment * 2;
            if (q.amount > 1) assertTrue(q.cfads / annualService >= FinanceBalance.ECA_SIZING_DSCR - 1e-6, id + ": beklenen DSCR >= 1,35 (" + q.cfads / annualService + ")");
            System.out.printf("%s: kredi %%%d, %d yil, beklenen DSCR %.2f%n", id, Math.round(q.amount / e.capex() * 100), q.years, q.amount > 1 ? q.cfads / annualService : 0);
        }
        assertEquals(15, Finance.ecaYears(PlantType.SOLAR));
        assertEquals(15, Finance.ecaYears(PlantType.WIND));
        assertEquals(18, Finance.ecaYears(PlantType.HYDRO));
        assertEquals(10, Finance.ecaYears(PlantType.COAL));
    }
}
