package com.energytycoon.harness;

import com.energytycoon.sim.*;

import java.util.*;

/**
 * Rakip stratejisi olcumu: proje getiri endeksleri ve cok tohumlu uzun kosu (rakip basina yil yil kurulan tur,
 * yonelim, karbon beklentisi, iflas). Calistir: gradle testClasses; java -cp ... StrategyCheck [tohum] [yil] [rakip]
 */
public final class StrategyCheck {
    public static void main(String[] a) {
        int seeds = a.length > 0 ? Integer.parseInt(a[0]) : 4, years = a.length > 1 ? Integer.parseInt(a[1]) : 15, rivals = a.length > 2 ? Integer.parseInt(a[2]) : 3;
        int bankrupt = 0, total = 0;
        Map<String, int[]> byStyleBank = new TreeMap<>();
        for (long seed = 1; seed <= seeds; seed++) {
            GameSettings g = SimHarness.reference(seed);
            g.rivals = rivals; g.cities = 6;
            GameState s = GameState.newGame(g);
            Map<Company, Map<Integer, StringBuilder>> log = new LinkedHashMap<>();
            Map<Company, StringBuilder> trail = new HashMap<>();
            Set<Integer> seen = new HashSet<>();
            for (Plant p : s.plants) seen.add(p.id);
            for (int h = 0; h < years * Balance.HOURS_PER_YEAR; h++) {
                s.tick();
                for (String m : s.messages) if (m.startsWith("STRATEJİ")) System.out.println("  [" + s.year() + "] " + m);
                s.messages.clear();
                if (s.hour % (Balance.HOURS_PER_MONTH) == 0) for (Plant p : s.plants) if (seen.add(p.id) && !p.owner.player && !p.owner.neutral)
                    log.computeIfAbsent(p.owner, k -> new TreeMap<>()).computeIfAbsent((int) (s.year() - 2025) / 5, k -> new StringBuilder()).append(p.type.name().charAt(0));
                if (s.hour % Balance.HOURS_PER_YEAR == 0) for (Company c : s.companies) if (!c.player && !c.neutral && !c.bankrupt) {
                    long n = s.plants.stream().filter(p -> p.owner == c).count();
                    trail.computeIfAbsent(c, k -> new StringBuilder()).append(String.format(Locale.ROOT, " %d:n%d nakit%.0f borc%.0f lev%.1f not%s;", s.year(), n, c.cash / 1e6, c.debt() / 1e6, s.finance.leverage(c), s.finance.rating(c)));
                }
                if (seed <= 2 && s.hour % (Balance.HOURS_PER_YEAR * 3) == 0) {
                    Company c = s.companies.get(1);
                    StringBuilder sb = new StringBuilder("  endeks " + s.year() + " (inan=" + String.format(Locale.ROOT, "%.2f", c.carbonBelief) + "): ");
                    for (PlantType t : PlantType.values()) {
                        double bi = -9;
                        for (Equipment e : s.catalog.available(t, s.year())) bi = Math.max(bi, RivalStrategy.projectIndex(s, c, e, s.cities.get(0)));
                        sb.append(t.name()).append(String.format(Locale.ROOT, "=%.2f ", bi));
                    }
                    double[] g12 = new double[PlantType.values().length], cp = new double[PlantType.values().length]; int[] n = new int[g12.length];
                    for (Plant p : s.plants) if (p.operational() && s.hour - p.builtAtHour > Balance.HOURS_PER_YEAR * 1.5) { g12[p.type.ordinal()] += p.gen12(); cp[p.type.ordinal()] += p.capacityMW() * Balance.HOURS_PER_YEAR; n[p.type.ordinal()]++; }
                    for (PlantType t : PlantType.values()) if (cp[t.ordinal()] > 0) sb.append(String.format(Locale.ROOT, " cf_%s=%.2f(n%d)", t.name(), g12[t.ordinal()] / cp[t.ordinal()], n[t.ordinal()]));
                    System.out.println(sb + String.format(Locale.ROOT, " kapFiyat=%.0fk marj=%.2f", s.capacityPrice / 1e3, s.capacityMargin) + " fiyat=" + String.format(Locale.ROOT, "%.1f", s.statPrice) + " karbon=" + String.format(Locale.ROOT, "%.0f", s.markets.carbonPrice));
                }
            }
            System.out.println("tohum " + seed + ":");
            for (Company c : s.companies) {
                if (c.player || c.neutral) continue;
                total++;
                if (c.bankrupt) bankrupt++;
                byStyleBank.computeIfAbsent(c.aiStyle, k -> new int[2])[0]++;
                if (c.bankrupt) byStyleBank.get(c.aiStyle)[1]++;
                System.out.printf(Locale.ROOT, "  %-18s %-8s %-24s inan=%.2f %s  kurulan(5 yillik): %s%n", c.name, c.aiStyle, c.orientation, c.carbonBelief,
                    c.bankrupt ? "IFLAS" + trail.get(c) : String.format(Locale.ROOT, "nakit=%.0fM", c.cash / 1e6), log.getOrDefault(c, Map.of()));
            }
        }
        System.out.println("iflas " + bankrupt + "/" + total + "  tarz basina [rakip, iflas]: " + toStr(byStyleBank));
    }

    static String toStr(Map<String, int[]> m) { StringBuilder b = new StringBuilder(); m.forEach((k, v) -> b.append(k).append(Arrays.toString(v)).append(' ')); return b.toString(); }
}
