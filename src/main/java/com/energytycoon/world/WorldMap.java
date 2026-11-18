package com.energytycoon.world;

import java.util.Arrays;
import java.util.Random;

/** Statik dunya: yukseklik ve kaynak katmanlari. Tile (x,y) -> dunya (x, h, y). */
public final class WorldMap {
    public static final float SEA_TOP = 0.12f;
    public static final float LAND_BASE = 0.30f;
    public static final float LEVEL_H = 0.14f;
    public static final int MAX_LEVEL = 22;
    public static final float RIVER_DEPTH = 0.09f;

    public final int size;
    public final long seed;
    public final MapPreset preset;
    public final int[] level;       // 0..MAX_LEVEL arazi kademesi
    public final boolean[] water;   // deniz, gol veya nehir
    public final boolean[] river;
    public final float[] top;       // tile ust yuzey yuksekligi
    public final float[] wind;      // 0.4..1.4
    public final float[] sun;       // 0.6..1.1
    public final float[] hydro;     // 0..1 (su yakinligi)
    public final float[] coal;      // 0..1 yatak
    public final float[] gas;       // 0..1 yatak

    public WorldMap(int size, long seed) { this(size, seed, MapPreset.CONTINENT); }

    public WorldMap(int size, long seed, MapPreset preset) {
        this.size = size; this.seed = seed; this.preset = preset;
        int n = size * size;
        level = new int[n]; water = new boolean[n]; river = new boolean[n]; top = new float[n];
        wind = new float[n]; sun = new float[n]; hydro = new float[n]; coal = new float[n]; gas = new float[n];
        generate();
    }

    public int idx(int x, int y) { return y * size + x; }
    public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < size && y < size; }
    public boolean isWater(int x, int y) { return water[idx(x, y)]; }
    public float topAt(int x, int y) { return top[idx(x, y)]; }
    public boolean isSea(int i) { return water[i] && top[i] <= SEA_TOP + 0.001f; }
    /** Dusu (hidro): 0 alcak .. 1 yuksek. */
    public double head(int x, int y) { return Math.min(1.0, level[idx(x, y)] / 12.0); }

    private void generate() {
        Noise hn = new Noise(seed), lake = new Noise(seed + 11), wn = new Noise(seed + 23),
              sn = new Noise(seed + 37), cn = new Noise(seed + 51), gn = new Noise(seed + 77), vn = new Noise(seed + 91);
        Random r = new Random(seed * 31 + preset.ordinal());
        double ox = r.nextDouble() * 1000, oy = r.nextDouble() * 1000;
        int side = r.nextInt(4);
        int n = size * size;
        double[] h = new double[n];
        double vPhase = r.nextDouble() * 6.28, vAmp = 0.10 + r.nextDouble() * 0.08;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double nx = x / (double) size, ny = y / (double) size;
                double freq = preset == MapPreset.ARCHIPELAGO ? 0.085 : preset == MapPreset.DESERT_COAST ? 0.04 : 0.055;
                double v = hn.fbm(ox + x * freq, oy + y * freq, 5);
                double edge = Math.min(Math.min(nx, 1 - nx), Math.min(ny, 1 - ny));
                double edgeFall = Math.max(0, 0.14 - edge) / 0.14;
                double sideT = switch (side) { case 0 -> 1 - nx; case 1 -> nx; case 2 -> 1 - ny; default -> ny; };
                double along = (side < 2) ? ny : nx;
                switch (preset) {
                    case CONTINENT, DESERT_COAST -> v -= edgeFall * 0.25 + Math.max(0, sideT - 0.60) * 1.0;
                    case ISLAND -> {
                        double d = Math.hypot(nx - 0.5, ny - 0.5) / 0.5;
                        v += (1 - d) * 0.25 - Math.max(0, d - 0.55) * 0.8;
                    }
                    case ARCHIPELAGO -> v -= edgeFall * 0.35;
                    case HIGHLANDS -> v += (1 - edgeFall) * 0.05 + Math.pow(hn.fbm(ox * 0.3 + x * 0.025, oy * 0.3 + y * 0.025, 2), 2) * 0.5;
                    case RIVER_VALLEY -> {
                        // denize dogru uzanan kivrimli vadi
                        double center = 0.5 + vAmp * Math.sin(sideT * 7 + vPhase);
                        double dv = Math.abs(along - center);
                        v -= Math.exp(-dv * dv / 0.012) * 0.22;
                        v -= edgeFall * 0.2 + Math.max(0, sideT - 0.80) * 1.4;
                        v += Math.max(0, 0.5 - sideT) * 0.2;
                    }
                }
                h[idx(x, y)] = v;
            }
        }
        // deniz seviyesi: hedef kara oranina gore
        double[] sorted = h.clone();
        Arrays.sort(sorted);
        double sea = sorted[(int) Math.min(n - 1, Math.max(0, (1 - preset.landFraction) * n))];
        double maxH = sorted[n - 1];
        double step = Math.max(1e-6, (maxH - sea) / (preset.relief + 0.99));
        boolean landlocked = preset == MapPreset.HIGHLANDS;
        for (int i = 0; i < n; i++) {
            int lv = (int) Math.floor((h[i] - sea) / step);
            if (lv < 0 && !landlocked) {
                water[i] = true; level[i] = 0; top[i] = SEA_TOP;
            } else {
                lv = Math.max(0, Math.min(MAX_LEVEL, lv));
                level[i] = lv; top[i] = LAND_BASE + lv * LEVEL_H;
            }
        }
        // gollar
        if (!preset.desert) {
            for (int y = 1; y < size - 1; y++) for (int x = 1; x < size - 1; x++) {
                int i = idx(x, y);
                if (!water[i] && level[i] >= 3 && level[i] <= 15 && lake.fbm(x * 0.11, y * 0.11, 3) > preset.lakeThreshold) {
                    water[i] = true; top[i] -= 0.10f;
                }
            }
        }
        carveRivers(r);
        // kaynak katmanlari
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = idx(x, y);
                double coastD = coastDistance(x, y, 4);
                double w = 0.55 + 0.55 * wn.fbm(x * 0.08, y * 0.08, 3) + (level[i] / (double) Math.max(8, preset.relief)) * 0.35
                        + (coastD <= 3 ? 0.18 + preset.windBonus : 0);
                wind[i] = (float) Math.max(0.4, Math.min(1.4, w));
                double s = 0.74 + 0.20 * (1 - y / (double) size) + (sn.fbm(x * 0.07, y * 0.07, 2) - 0.5) * 0.12 + preset.sunBonus;
                sun[i] = (float) Math.max(0.6, Math.min(1.1, s));
                hydro[i] = (float) Math.min(1.0, countWater(x, y, 2) / 7.0);
                double c = cn.fbm(x * 0.09, y * 0.09, 3);
                coal[i] = water[i] ? 0 : (float) Math.max(0, Math.min(1, (c - 0.58 / Math.sqrt(preset.coalMult)) / 0.14));
                double g = gn.fbm(x * 0.09 + 40, y * 0.09 + 40, 3);
                gas[i] = water[i] ? 0 : (float) Math.max(0, Math.min(1, (g - 0.60 / Math.sqrt(preset.gasMult)) / 0.14));
            }
        }
    }

    /** Yuksek noktalardan en dik inisle suya/kenara akan nehirler. */
    private void carveRivers(Random r) {
        int made = 0, tries = 0;
        int want = preset.rivers + (size > 64 ? 1 : 0);
        int minLv = Math.max(3, preset.relief / 2);
        while (made < want && tries++ < 400) {
            int x = 3 + r.nextInt(size - 6), y = 3 + r.nextInt(size - 6);
            int i = idx(x, y);
            if (water[i] || level[i] < minLv) continue;
            boolean nearRiver = false;
            for (int dy = -6; dy <= 6 && !nearRiver; dy++) for (int dx = -6; dx <= 6; dx++)
                if (inBounds(x + dx, y + dy) && river[idx(x + dx, y + dy)]) { nearRiver = true; break; }
            if (nearRiver) continue;
            int[] path = new int[size * 4];
            int len = 0;
            boolean[] visited = new boolean[size * size];
            int cx = x, cy = y;
            boolean reached = false;
            for (int step = 0; step < size * 3; step++) {
                int ci = idx(cx, cy);
                visited[ci] = true;
                path[len++] = ci;
                if (len >= path.length) break;
                int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
                int bx = -1, by = -1; double best = Double.MAX_VALUE;
                for (int[] d : dirs) {
                    int nx = cx + d[0], ny = cy + d[1];
                    if (!inBounds(nx, ny)) { reached = true; break; }
                    int ni = idx(nx, ny);
                    if (visited[ni]) continue;
                    if (water[ni] && !river[ni]) { reached = true; break; }
                    double score = level[ni] + r.nextDouble() * 0.9;
                    if (score < best) { best = score; bx = nx; by = ny; }
                }
                if (reached || bx < 0) break;
                int ni = idx(bx, by);
                if (level[ni] > level[ci]) { level[ni] = level[ci]; top[ni] = LAND_BASE + level[ni] * LEVEL_H; }
                cx = bx; cy = by;
            }
            if (!reached || len < 6) continue;
            for (int k = 0; k < len; k++) {
                int pi = path[k];
                water[pi] = true; river[pi] = true;
                top[pi] = LAND_BASE + level[pi] * LEVEL_H - RIVER_DEPTH;
            }
            made++;
        }
    }

    private int countWater(int cx, int cy, int r) {
        int c = 0;
        for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            int x = cx + dx, y = cy + dy;
            if (inBounds(x, y) && water[idx(x, y)]) c++;
        }
        return c;
    }

    private double coastDistance(int cx, int cy, int max) {
        for (int r = 1; r <= max; r++) {
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                int x = cx + dx, y = cy + dy;
                if (inBounds(x, y) && isSea(idx(x, y))) return r;
            }
        }
        return max + 1;
    }

    /** Alani duzlestir (sehir yerlesimi icin). */
    public void flatten(int cx, int cy, int r) {
        int lv = level[idx(cx, cy)];
        for (int y = cy - r; y <= cy + r; y++) for (int x = cx - r; x <= cx + r; x++) {
            if (!inBounds(x, y)) continue;
            int i = idx(x, y);
            level[i] = lv; top[i] = LAND_BASE + lv * LEVEL_H; water[i] = false; river[i] = false;
        }
    }

    /** Kayittan yuklemede tesis karesinin kara olmasini garanti eder. */
    public void ensureLand(int x, int y) {
        int i = idx(x, y);
        if (!water[i]) return;
        water[i] = false; river[i] = false; top[i] = LAND_BASE + level[i] * LEVEL_H;
    }

    public boolean buildable(int x, int y) {
        return inBounds(x, y) && !water[idx(x, y)];
    }
}
