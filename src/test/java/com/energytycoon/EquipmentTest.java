package com.energytycoon;

import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentTest {
    static Plant place(GameState s, String eqId) {
        Equipment e = s.catalog.get(eqId);
        Plant p = s.placeLegacy(s.player, e, s.cities.get(0), 0);
        assertNotNull(p, eqId);
        return p;
    }

    @Test
    void ccgtCheaperThanOcgt() {
        GameState s = GameState.newGame(11, "T");
        Plant oc = place(s, "at-oc"), cc = place(s, "mp-ha");
        assertTrue(s.marginalCost(cc) < s.marginalCost(oc), "CCGT marjinal maliyeti daha dusuk olmali");
        assertTrue(cc.co2PerMWh(s.hour) < oc.co2PerMWh(s.hour));
    }

    @Test
    void coalEfficiencyLowersEmissions() {
        GameState s = GameState.newGame(12, "T");
        Plant sub = place(s, "fk-sub"), usc = place(s, "th-usc");
        assertTrue(usc.co2PerMWh(s.hour) < sub.co2PerMWh(s.hour));
        assertTrue(s.marginalCost(usc) < s.marginalCost(sub));
    }

    @Test
    void ccsCutsCo2() {
        GameState s = GameState.newGame(13, "T");
        Plant p = place(s, "fk-sc");
        double before = p.co2PerMWh(s.hour);
        s.player.cash = 5e9;
        assertNull(s.startUpgrade(p, Upgrade.CCS));
        assertFalse(p.online(), "CCS sirasinda santral kapali");
        for (int i = 0; i < Upgrade.CCS.days * 24 + 2; i++) s.tick();
        assertTrue(p.upgrades.contains(Upgrade.CCS));
        assertTrue(p.co2PerMWh(s.hour) < before * 0.3);
    }

    @Test
    void premiumSolarProducesMoreAndDegradesLess() {
        GameState s = GameState.newGame(14, "T");
        Plant perc = place(s, "sp-perc"), hjt = place(s, "lu-hjt");
        assertTrue(hjt.renewableMult(s.hour) > perc.renewableMult(s.hour));
        long later = s.hour + 10L * Balance.HOURS_PER_YEAR;
        double percLoss = perc.degradation(later), hjtLoss = hjt.degradation(later);
        assertTrue(hjtLoss > percLoss, "HJT daha yavas yaslanmali");
        assertTrue(perc.degradation(later) < perc.degradation(s.hour));
    }

    @Test
    void windClassesMatchSites() {
        GameState s = GameState.newGame(15, "T");
        Equipment low = s.catalog.get("nw-45"), high = s.catalog.get("rs-36");
        // zayif ve guclu ruzgar karesi bul
        int wx = -1, wy = -1, sx = -1, sy = -1; float wmin = 9, smax = 0;
        for (int y = 0; y < s.map.size; y++) for (int x = 0; x < s.map.size; x++) {
            int i = s.map.idx(x, y);
            if (s.map.water[i]) continue;
            if (s.map.wind[i] < wmin) { wmin = s.map.wind[i]; wx = x; wy = y; }
            if (s.map.wind[i] > smax) { smax = s.map.wind[i]; sx = x; sy = y; }
        }
        double lowWeak = s.siteScore(low, wx, wy) / low.capexPerMW, highWeak = s.siteScore(high, wx, wy) / high.capexPerMW;
        assertTrue(lowWeak > highWeak, "zayif sahada dusuk ruzgar turbini daha karli");
        assertTrue(s.siteScore(high, sx, sy) > s.siteScore(high, wx, wy));
    }

    @Test
    void lockedTechCannotBeBuilt() {
        GameState s = GameState.newGame(16, "T");
        Equipment tandem = s.catalog.get("kg-tandem");
        assertNotNull(s.checkEquipment(tandem));
        assertNull(s.placeLegacy(s.player, tandem, s.cities.get(0), 0));
        assertNull(RivalAI.buildNear(s, s.player, tandem, s.cities.get(0)));
    }

    @Test
    void supplierPricesFluctuateWithinBounds() {
        GameState s = GameState.newGame(17, "T");
        for (int i = 0; i < 5 * Balance.HOURS_PER_YEAR; i++) s.tick();
        for (Supplier sp : s.catalog.allSuppliers()) {
            assertTrue(sp.priceIndex >= 0.75 && sp.priceIndex <= 1.4);
        }
    }
}
