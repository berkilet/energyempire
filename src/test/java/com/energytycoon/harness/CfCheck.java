package com.energytycoon.harness;

import com.energytycoon.sim.*;
import java.util.*;

/** Teknoloji basina gerceklesen kapasite faktoru (uygun sahalarda, bir yil) ve sahaskoru tahmini. */
public final class CfCheck {
    public static void main(String[] a) {
        String[] presets = a.length > 0 ? a[0].split(",") : new String[] { "CONTINENT" };
        for (String pr : presets) {
            GameSettings g = SimHarness.reference(7); g.preset = com.energytycoon.world.MapPreset.valueOf(pr);
            GameState s = GameState.newGame(g);
            Random r = new Random(1);
            Map<PlantType, List<Plant>> probes = new EnumMap<>(PlantType.class);
            for (PlantType t : new PlantType[] { PlantType.SOLAR, PlantType.HYDRO, PlantType.WIND }) {
                List<Plant> l = new ArrayList<>();
                for (int k = 0; k < 4000 && l.size() < 25; k++) {
                    int x = r.nextInt(s.map.size), y = r.nextInt(s.map.size);
                    if (s.checkPlacement(t, x, y) != null) continue;
                    Plant p = new Plant(); p.type = t; p.equipment = s.catalog.defaultFor(t); p.x = x; p.y = y; p.builtAtHour = s.hour; p.ageStartHour = s.hour;
                    l.add(p);
                }
                probes.put(t, l);
            }
            Map<PlantType, double[]> acc = new EnumMap<>(PlantType.class);
            for (int h = 0; h < Balance.HOURS_PER_YEAR; h++) {
                s.tick(); s.messages.clear();
                for (var e : probes.entrySet()) for (Plant p : e.getValue()) acc.computeIfAbsent(e.getKey(), k -> new double[2])[0] += s.capacityFactorNow(p);
            }
            StringBuilder b = new StringBuilder(pr + ": ");
            for (var e : probes.entrySet()) {
                int n = e.getValue().size();
                double est = 0; for (Plant p : e.getValue()) est += s.siteScore(p.equipment, p.x, p.y);
                b.append(String.format(Locale.ROOT, "%s gercek=%.3f sahaskoru=%.3f (n%d)  ", e.getKey(), acc.get(e.getKey())[0] / n / Balance.HOURS_PER_YEAR, est / n, n));
            }
            System.out.println(b + String.format(Locale.ROOT, " ort fiyat %.1f", s.history.monthlyPrice.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        }
    }
}
