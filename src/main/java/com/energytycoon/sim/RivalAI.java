package com.energytycoon.sim;

import java.util.Random;

/** Basit rakip sirket yapay zekasi. */
public final class RivalAI {
    private RivalAI() {}

    public static void monthly(GameState s, Company c) {
        double reserve = 160e6 / s.settings.difficulty.rivalMult;
        if (c.cash < reserve) return;
        Random r = s.rng;
        if (r.nextDouble() > 0.55 * s.settings.difficulty.rivalMult) return;
        City target = null; double worst = 9;
        for (City ct : s.cities) {
            GameState.Component comp = s.componentOf(ct);
            double cover = comp == null || comp.peakMW <= 0 ? 0 : comp.nominalMW / comp.peakMW;
            double score = cover + r.nextDouble() * 0.5;
            if (score < worst) { worst = score; target = ct; }
        }
        if (target == null) return;
        GameState.Component comp = s.componentOf(target);
        double cover = comp == null || comp.peakMW <= 0 ? 0 : comp.nominalMW / comp.peakMW;
        if (cover > 1.3) return;
        PlantType[] pool = switch (c.aiStyle) {
            case "green" -> new PlantType[] { PlantType.SOLAR, PlantType.WIND, PlantType.WIND, PlantType.HYDRO, PlantType.GAS };
            case "fossil" -> new PlantType[] { PlantType.COAL, PlantType.GAS, PlantType.GAS, PlantType.WIND, PlantType.NUCLEAR };
            default -> new PlantType[] { PlantType.GAS, PlantType.SOLAR, PlantType.WIND, PlantType.GAS, PlantType.HYDRO };
        };
        PlantType t = pool[r.nextInt(pool.length)];
        Equipment e = pick(s, c, t);
        if (e.capex() + 40e6 > c.cash) { e = pick(s, c, c.aiStyle.equals("green") ? PlantType.WIND : PlantType.GAS); }
        if (e.type == PlantType.NUCLEAR && c.cash < e.capex() * 1.4) e = pick(s, c, PlantType.GAS);
        if (e.capex() + 40e6 > c.cash) e = s.catalog.cheapest(e.type, s.year());
        if (e.capex() + 40e6 > c.cash) return;
        buildNear(s, c, e, target, false);
    }

    static Equipment pick(GameState s, Company c, PlantType t) {
        return switch (c.aiStyle) {
            case "green" -> s.catalog.newest(t, s.year());
            case "fossil" -> s.catalog.cheapest(t, s.year());
            default -> s.catalog.defaultFor(t).availableYear <= s.year() ? s.catalog.defaultFor(t) : s.catalog.cheapest(t, s.year());
        };
    }

    public static Plant buildNear(GameState s, Company c, PlantType t, City city, boolean free) {
        return buildNear(s, c, s.catalog.defaultFor(t), city, free);
    }

    public static Plant buildNear(GameState s, Company c, Equipment e, City city, boolean free) {
        Random r = s.rng;
        PlantType t = e.type;
        double bestScore = -1; int bx = -1, by = -1;
        for (int i = 0; i < 120; i++) {
            double ang = r.nextDouble() * Math.PI * 2, d = 4 + r.nextDouble() * 12;
            int x = (int) Math.round(city.x + Math.cos(ang) * d), y = (int) Math.round(city.y + Math.sin(ang) * d);
            if (s.checkPlacement(t, x, y) != null) continue;
            int idx = s.map.idx(x, y);
            double q = switch (t) {
                case SOLAR, WIND, HYDRO -> s.siteScore(e, x, y) * 3;
                case COAL -> 0.5 + s.map.coal[idx];
                case GAS -> 0.5 + s.map.gas[idx];
                default -> 0.6 + s.map.hydro[idx];
            };
            double score = q * 2 - d * 0.05 + r.nextDouble() * 0.2;
            if (score > bestScore) { bestScore = score; bx = x; by = y; }
        }
        if (bx < 0) return null;
        double before = c.cash;
        if (free) c.cash = Math.max(c.cash, e.capex() + 1);
        s.suppressDelays = free;
        Plant p = s.buildPlant(c, e, bx, by);
        s.suppressDelays = false;
        if (p == null) { c.cash = before; return null; }
        s.buildLine(c, p, city);
        if (free) { p.buildHoursLeft = 0; p.builtAtHour = s.hour; p.ageStartHour = s.hour; c.cash = before; }
        return p;
    }
}
