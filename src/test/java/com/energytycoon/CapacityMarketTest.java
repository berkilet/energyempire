package com.energytycoon;

import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Kapasite mekanizmasi: yedek marjina gore egimli fiyat, guvenilir kapasite katsayisiyla odeme. */
class CapacityMarketTest {
    static Plant ofType(GameState s, PlantType t) {
        for (Plant p : s.plants) if (p.type == t && p.operational() && p.owner != null) return p;
        return null;
    }

    @Test
    void tightSystemPaysFirmCapacity() {
        GameState s = GameState.newGame(SimHarness.reference(1));
        for (int h = 0; h < 40 * 24; h++) { s.tick(); s.messages.clear(); }
        // sikisik sistem: en yogun saatte kullanilabilir guc talebin %95'i (marj -%5)
        java.util.Arrays.fill(s.peak12, 2000); java.util.Arrays.fill(s.peakAvail12, 1900);
        s.monthPeakMW = 2000; s.monthPeakAvail = 1900;
        Plant coal = ofType(s, PlantType.COAL);
        assertNotNull(coal, "referans oyunda calisan komur santrali var");
        double before = coal.owner.capacityIncome;
        s.capacityMarket();
        assertEquals(GridBalance.CAPACITY_CONE * GridBalance.CAPACITY_MAX_MULT, s.capacityPrice, 1e-6, "marj hedefin cok altinda: tavan fiyat");
        double expect = coal.capacityMW() * GridBalance.derate(PlantType.COAL) * s.capacityPrice / 12;
        assertEquals(expect, coal.capacityIncomeMonth, 1e-6);
        assertTrue(coal.owner.capacityIncome >= before + expect - 1e-6);
    }

    @Test
    void surplusPaysNothingAndDerateRanks() {
        GameState s = GameState.newGame(SimHarness.reference(1));
        java.util.Arrays.fill(s.peak12, 1000); java.util.Arrays.fill(s.peakAvail12, 1400);
        s.monthPeakMW = 1000; s.monthPeakAvail = 1400;
        s.capacityMarket();
        assertEquals(0, s.capacityPrice, 1e-9, "%40 yedek: kapasite fiyati sifir");
        assertTrue(GridBalance.derate(PlantType.NUCLEAR) > GridBalance.derate(PlantType.HYDRO));
        assertTrue(GridBalance.derate(PlantType.COAL) > 5 * GridBalance.derate(PlantType.WIND));
        assertTrue(GridBalance.derate(PlantType.WIND) > GridBalance.derate(PlantType.SOLAR));
    }
}
