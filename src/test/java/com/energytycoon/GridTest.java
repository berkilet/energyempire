package com.energytycoon;

import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Faz 2: hat kademeleri, bağlantı noktası ihracat sınırı ve mesafeye bağlı kayıp. */
class GridTest {

    /** Rakipsiz, tüm santral ve hatları kaldırılmış; city0 büyük talepli. */
    static GameState emptyGrid(double population) {
        GameSettings g = SimHarness.reference(1);
        g.rivals = 0;
        GameState s = GameState.newGame(g);
        for (Plant p : new ArrayList<>(s.plants)) s.demolishPlant(p, false);
        for (PowerLine l : new ArrayList<>(s.lines)) s.removeLine(l, false);
        s.player.cash = 50e9;
        s.cities.get(0).population = population;
        return s;
    }

    static int[] siteAt(GameState s, PlantType t, City c, double d) {
        int[] best = null; double bestErr = 1e9;
        for (int x = 0; x < s.map.size; x++) for (int y = 0; y < s.map.size; y++) {
            double err = Math.abs(Math.hypot(x - c.x, y - c.y) - d);
            if (err < bestErr && err < 1.0 && s.checkPlacement(t, x, y) == null) { best = new int[] { x, y }; bestErr = err; }
        }
        return best;
    }

    static Plant ready(GameState s, String eqId, City c, double d) {
        Equipment e = s.catalog.get(eqId);
        int[] site = siteAt(s, e.type, c, d);
        assertNotNull(site, "uygun saha yok: " + eqId + " " + d);
        Plant p = s.buildPlant(s.player, e, site[0], site[1]);
        assertNotNull(p);
        p.buildHoursLeft = 0;
        p.outageHoursLeft = 0;
        return p;
    }

    private static double outputAfterTick(GameState s, Plant p) {
        s.markTopologyDirty();
        s.tick();
        return p.outputMW;
    }

    @Test
    void nuclearExportLimitedByLineTiers() {
        double[] results = new double[3];
        LineTier[][] setups = { { LineTier.YG }, { LineTier.YG, LineTier.YG }, { LineTier.CYG } };
        for (int k = 0; k < setups.length; k++) {
            GameState s = emptyGrid(4_000_000);
            City c = s.cities.get(0);
            Plant p = ready(s, "atr-600", c, 8);
            for (LineTier t : setups[k]) {
                PowerLine l = s.buildLine(s.player, p, c, t);
                assertNotNull(l, "hat kurulamadı " + t);
                assertEquals(LineStatus.ACTIVE, l.status);
                assertEquals(t, l.tier);
            }
            results[k] = outputAfterTick(s, p);
            System.out.printf("600 MW nükleer, hatlar %s → %.1f MW%n", java.util.Arrays.toString(setups[k]), results[k]);
        }
        assertTrue(results[0] <= 250 + 1e-6, "tek YG ≤ 250: " + results[0]);
        assertEquals(250, results[0], 1.0);
        assertEquals(500, results[1], 1.0, "iki YG = 500");
        assertTrue(results[2] >= 500, "tek ÇYG ≥ 500: " + results[2]);
    }

    @Test
    void distanceLossAndMeritOrder() {
        GameState s = emptyGrid(150_000); // ~107 MW tepe talep: tek santral yeter
        City c = s.cities.get(0);
        // Nükleer: yakıt maliyeti konumdan bağımsız, iki santralin teklifi aynı; fark yalnızca kayıptan.
        Plant near = ready(s, "atr-600", c, 5);
        Plant far = ready(s, "atr-600", c, 20);
        assertEquals(s.marginalCost(near), s.marginalCost(far), 1e-9);
        assertNotNull(s.buildLine(s.player, near, c, LineTier.YG));
        assertNotNull(s.buildLine(s.player, far, c, LineTier.YG));
        s.markTopologyDirty();
        s.tick();
        double lossNear = near.cp.lossFraction, lossFar = far.cp.lossFraction;
        System.out.printf("kayıp: yakın %.4f, uzak %.4f; çıkış yakın %.1f uzak %.1f MW%n", lossNear, lossFar, near.outputMW, far.outputMW);
        assertTrue(lossFar > lossNear * 3, "uzak santral daha çok kayıp");
        assertEquals(near.lines.get(0).lengthTiles * LineTier.YG.lossPerTile, lossNear, 1e-9);
        assertTrue(near.outputMW > 0, "yakın santral liyakatte önde");
        assertEquals(0, far.outputMW, 1e-9, "küçük talepte uzak santral çalışmaz");
        // Brüt çıkış = teslim / (1 - kayıp)
        assertEquals(c.servedMW / (1 - lossNear), near.outputMW, 1e-6);

        // Aynı santral 5 ve 20 karede: aynı teslim için farklı brüt MW
        double grossNear = c.servedMW / (1 - lossNear), grossFar = c.servedMW / (1 - lossFar);
        assertTrue(grossFar > grossNear + 1);
    }

    @Test
    void lossCappedAt15Percent() {
        GameState s = emptyGrid(150_000);
        City c = s.cities.get(0);
        Plant p = ready(s, "at-cc", c, 7);
        assertNotNull(s.buildLine(s.player, p, c, LineTier.OG)); // 7 × 0.008 = %5.6
        s.markTopologyDirty();
        s.tick();
        assertEquals(p.lines.get(0).lengthTiles * LineTier.OG.lossPerTile, p.cp.lossFraction, 1e-9);
        assertTrue(p.cp.lossFraction <= GridBalance.MAX_PATH_LOSS);
        // bağlantısız santral: en fazla kayıp, üretim yok
        Plant iso = ready(s, "at-cc", c, 12);
        s.markTopologyDirty();
        s.tick();
        assertEquals(GridBalance.MAX_PATH_LOSS, iso.cp.lossFraction, 1e-12);
        assertEquals(0, iso.outputMW, 1e-12);
    }

    @Test
    void tierRules() {
        GameState s = emptyGrid(150_000);
        City c = s.cities.get(0);
        Plant p = ready(s, "at-cc", c, 12);
        assertNotNull(s.checkLine(p, c, LineTier.OG), "OG 8 kareden uzun olamaz");
        assertNull(s.checkLine(p, c, LineTier.YG));
        assertTrue(s.lineCost(p, c, LineTier.CYG) > s.lineCost(p, c, LineTier.YG));
        assertTrue(s.lineCost(p, c, LineTier.YG) > s.lineCost(p, c, LineTier.OG));
    }

    /** Faz 1 referansına göre aylık ortalamalar %3 içinde. */
    @Test
    void compatibilityWithFaz1Reference() throws Exception {
        List<String> ref = java.nio.file.Files.readAllLines(java.nio.file.Path.of(
            getClass().getResource("/baseline/faz1_seed1_free_normal_64_5sehir_2rakip.csv").toURI()));
        double rs = 0, rp = 0, rm = 0; int n = 0;
        for (String line : ref.subList(1, ref.size())) {
            String[] f = line.split(",");
            rs += Double.parseDouble(f[3]); rp += Double.parseDouble(f[4]); rm += Double.parseDouble(f[7]); n++;
        }
        List<SimHarness.Month> m = SimHarness.runMonths(GameState.newGame(SimHarness.reference(1)), n);
        double ms = 0, mp = 0, mm = 0;
        for (SimHarness.Month x : m) { ms += x.servedRatio; mp += x.avgPrice; mm += x.totalMwh; }
        System.out.printf("uyum: karşılanan %+.2f%%, fiyat %+.2f%%, üretim %+.2f%%%n",
            (ms / rs - 1) * 100, (mp / rp - 1) * 100, (mm / rm - 1) * 100);
        assertEquals(1, ms / rs, 0.03);
        assertEquals(1, mp / rp, 0.03);
        assertEquals(1, mm / rm, 0.03);
    }
}
