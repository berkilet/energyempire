package com.energytycoon.ui;

import java.util.Locale;

public final class Fmt {
    private Fmt() {}
    public static String money(double v) {
        double a = Math.abs(v);
        String sign = v < 0 ? "-" : "";
        if (a >= 1e9) return String.format(Locale.ROOT, "%s$%.2fB", sign, a / 1e9);
        if (a >= 1e6) return String.format(Locale.ROOT, a >= 1e8 ? "%s$%.0fM" : "%s$%.1fM", sign, a / 1e6);
        if (a >= 1e3) return String.format(Locale.ROOT, "%s$%.0fK", sign, a / 1e3);
        return String.format(Locale.ROOT, "%s$%.0f", sign, a);
    }
    public static String mw(double v) {
        return v >= 1000 ? String.format(Locale.ROOT, "%.2f GW", v / 1000) : String.format(Locale.ROOT, "%.0f MW", v);
    }
    public static String pct(double v) { return String.format(Locale.ROOT, "%.0f%%", v * 100); }
    public static String pop(double v) {
        return v >= 1e6 ? String.format(Locale.ROOT, "%.2fM", v / 1e6) : String.format(Locale.ROOT, "%.0fK", v / 1e3);
    }
    public static String f2(double v) { return String.format(Locale.ROOT, "%.2f", v); }
    public static String f1(double v) { return String.format(Locale.ROOT, "%.1f", v); }
    public static final String[] MONTHS = { "Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara" };
}
