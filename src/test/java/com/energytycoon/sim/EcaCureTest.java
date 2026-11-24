package com.energytycoon.sim;

import com.energytycoon.harness.SimHarness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ECA DSCR ihlali: kademeli uyari, el koyma ihbari ve sure, oyuncunun cozum araclari. */
class EcaCureTest {
    static final int HPM = Balance.HOURS_PER_MONTH;

    static Object[] setup() {
        GameState s = GameState.newGame(SimHarness.reference(2));
        Company p = s.player;
        p.cash = 3e9;
        Equipment e = s.catalog.get("nw-45");
        int[] xy = RivalAI.findSite(s, e, s.cities.get(0), 2, 8);
        Plant pl = s.finance.buildWithEca(p, e, xy[0], xy[1], Bank.KIYI, 6);
        assertNotNull(pl);
        Loan l = p.loans.stream().filter(x -> x.type == Loan.Type.ECA).findFirst().orElseThrow();
        pl.buildHoursLeft = 0;
        l.graceEndHour = s.hour - 4L * HPM;                     // olcum basladi
        double q = Loan.annuity(l.outstanding, l.effectiveRate(), 12 / l.frequencyMonths, l.remainingPeriods()) * 3.0 / l.frequencyMonths;
        pl.qProfitLast = q * 0.6;                               // DSCR ~0,6
        return new Object[] { s, p, pl, l };
    }

    @Test
    void noticeThenStepIn() {
        Object[] o = setup(); GameState s = (GameState) o[0]; Company p = (Company) o[1]; Plant pl = (Plant) o[2]; Loan l = (Loan) o[3];
        for (int k = 0; k < FinanceBalance.ECA_DSCR_DEFAULT_QUARTERS - 1; k++) { s.finance.quarterly(p); assertEquals(0, l.stepInHour, "ihbar oncesi uyari ceyrekleri"); }
        assertTrue(s.messages.stream().anyMatch(m -> m.contains("ECA taahhüt ihlali")), "her ceyrek kademeli uyari");
        s.finance.quarterly(p);
        assertTrue(l.stepInHour > s.hour, "4. ceyrekte el koyma ihbari ve sure");
        assertTrue(s.messages.stream().anyMatch(m -> m.contains("EL KOYMA İHBARI")));
        assertSame(p, pl.owner, "ihbar suresinde santral oyuncuda");
        s.hour = l.stepInHour;
        s.finance.daily(p);
        assertEquals(Loan.Status.DEFAULTED, l.status, "sure dolunca el koyma");
        assertNotSame(p, pl.owner);
    }

    @Test
    void partialCureStopsProcess() {
        Object[] o = setup(); GameState s = (GameState) o[0]; Company p = (Company) o[1]; Plant pl = (Plant) o[2]; Loan l = (Loan) o[3];
        for (int k = 0; k < FinanceBalance.ECA_DSCR_DEFAULT_QUARTERS; k++) s.finance.quarterly(p);
        assertTrue(l.stepInHour > 0);
        double out0 = l.outstanding, red = s.finance.cureReduction(l);
        assertNull(s.finance.curePartial(p, l));
        assertEquals(out0 - red, l.outstanding, 1);
        assertEquals(0, l.stepInHour, "ihbar iptal");
        assertEquals(0, l.dscrLowQuarters);
        // dusen borc servisiyle ayni kar DSCR'yi ~1,20'ye tasir
        s.finance.quarterly(p);
        assertTrue(l.lastDscr >= FinanceBalance.ECA_DSCR - 0.02, "kismi odeme sonrasi DSCR " + l.lastDscr);
        assertSame(p, pl.owner);
    }

    @Test
    void waiverSuspendsCountingAndReserveReturnsOnClose() {
        Object[] o = setup(); GameState s = (GameState) o[0]; Company p = (Company) o[1]; Loan l = (Loan) o[3];
        s.finance.quarterly(p); s.finance.quarterly(p);
        double cash0 = p.cash, dsra = s.finance.dsraAmount(l), fee = s.finance.waiverFee(l);
        assertNull(s.finance.waiver(p, l));
        assertEquals(cash0 - dsra - fee, p.cash, 1);
        for (int k = 0; k < 3; k++) s.finance.quarterly(p);
        assertEquals(0, l.dscrLowQuarters, "feragat suresinde sayilmaz");
        assertNotNull(s.finance.waiver(p, l), "kredi basina bir kez");
        double before = p.cash, cost = s.finance.prepayCost(p, l);
        assertNull(s.finance.prepay(p, l));
        assertEquals(before - cost + l.principal * 0 + dsra, p.cash, 1, "kapanista rezerv iade");
    }
}
