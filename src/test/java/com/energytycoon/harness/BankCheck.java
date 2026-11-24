package com.energytycoon.harness;

import com.energytycoon.sim.*;
import java.util.*;

/**
 * Rakip olcumu (cok tohum): iflas, karsilanan talep, ortalama fiyat, kurulan tur dagilimi, kokene gore fosil yatirim payi,
 * surpriz ve yonelim duyurulari. Calistir: java -cp ... BankCheck [tohum] [yil] [rakip]
 */
public final class BankCheck {
    public static void main(String[] a) {
        int seeds = Integer.parseInt(a[0]), years = Integer.parseInt(a[1]), rivals = Integer.parseInt(a[2]);
        Map<String, int[]> m = new TreeMap<>(); Map<String, List<String>> shares = new TreeMap<>();
        int[] mix = new int[PlantType.values().length]; double served = 0, price = 0; int months = 0; double minServed = 1; int surprises = 0, pivots = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            GameSettings g = SimHarness.reference(seed); g.rivals = rivals; g.cities = 6;
            GameState s = GameState.newGame(g);
            Map<Company, int[]> fb = new HashMap<>();
            Set<Integer> seen = new HashSet<>(); for (Plant p : s.plants) seen.add(p.id);
            for (int h = 0; h < years * Balance.HOURS_PER_YEAR; h++) {
                s.tick();
                for (String msg : s.messages) if (msg.startsWith("STRATEJİ")) { if (msg.contains("değiştirdi")) pivots++; else if (!msg.contains("zarar eden")) surprises++; }
                s.messages.clear();
                if (s.hour % Balance.HOURS_PER_MONTH == 0 && s.year() >= 2028) { served += s.lastServedRatio; minServed = Math.min(minServed, s.lastServedRatio); price += s.history.monthlyPrice.get(s.history.monthlyPrice.size() - 1); months++; }
                if (s.hour % Balance.HOURS_PER_MONTH == 0) for (Plant p : s.plants) if (seen.add(p.id) && !p.owner.player && !p.owner.neutral) {
                    mix[p.type.ordinal()]++; int[] v = fb.computeIfAbsent(p.owner, k -> new int[2]); v[1]++; if (p.type == PlantType.COAL || p.type == PlantType.GAS) v[0]++; }
            }
            for (Company c : s.companies) if (!c.player && !c.neutral) {
                int[] v = m.computeIfAbsent(c.aiStyle, k -> new int[2]); v[0]++; if (c.bankrupt) v[1]++;
                int[] f = fb.getOrDefault(c, new int[2]);
                shares.computeIfAbsent(c.aiStyle, k -> new ArrayList<>()).add((f[1] == 0 ? "-" : Math.round(100.0 * f[0] / f[1]) + "%") + (c.bankrupt ? "(i)" : ""));
            }
        }
        StringBuilder b = new StringBuilder(); m.forEach((k, v) -> b.append(k).append(Arrays.toString(v)).append(' '));
        System.out.println(String.format(Locale.ROOT, "karsilanan ort %.3f min %.3f, ort fiyat %.1f | ", served / months, minServed, price / months)
            + "IFLAS [rakip,iflas]: " + b + " kurulan " + Arrays.toString(PlantType.values()) + " = " + Arrays.toString(mix));
        System.out.println("surpriz duyurusu " + surprises + ", yonelim degisimi " + pivots);
        shares.forEach((k, v) -> System.out.println("  " + k + " fosil(gaz+komur) yatirim payi, oyun oyun: " + v));
    }
}
