package com.energytycoon.world;

/** Tohumlu deger gurultusu (value noise) ve fBm. */
public final class Noise {
    private final long seed;
    public Noise(long seed) { this.seed = seed; }

    private double hash(int x, int y) {
        long h = x * 374761393L + y * 668265263L + seed * 1442695040888963407L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= (h >>> 16);
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private double smooth(double t) { return t * t * (3 - 2 * t); }

    public double value(double x, double y) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = smooth(x - x0), fy = smooth(y - y0);
        double a = hash(x0, y0), b = hash(x0 + 1, y0), c = hash(x0, y0 + 1), d = hash(x0 + 1, y0 + 1);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    public double fbm(double x, double y, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += value(x * f, y * f) * amp;
            norm += amp; amp *= 0.5; f *= 2;
        }
        return sum / norm;
    }
}
