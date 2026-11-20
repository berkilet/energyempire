package com.energytycoon.sim;

import java.util.Random;

/** Basit rakip sirket yapay zekasi. */
public final class RivalAI {
    private RivalAI() {}

    public static void monthly(GameState s, Company c) {
        double rivalMult = s.settings.difficulty.rivalMult;
        if (c.cash < GridBalance.RIVAL_DECISION_CASH / rivalMult) return;
        Random r = s.rng;
        if (r.nextDouble() > GridBalance.RIVAL_BUILD_CHANCE * rivalMult) return;
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
        // Aday sirasi: tercih edilen model, tarza uygun yedek tur, en ucuz model. Her aday santral + hat + rezervle sinanir.
        Equipment first = pick(s, c, t);
        if (first.type == PlantType.NUCLEAR && c.cash < first.capex() * 1.4) first = pick(s, c, PlantType.GAS);
        Equipment second = pick(s, c, c.aiStyle.equals("green") ? PlantType.WIND : PlantType.GAS);
        Equipment third = s.catalog.cheapest(second.type, s.year());
        for (Equipment e : new Equipment[] { first, second, third }) {
            if (e == null || e.capex() + GridBalance.RIVAL_BUILD_RESERVE > c.cash) continue;
            if (buildNear(s, c, e, target, GridBalance.RIVAL_BUILD_RESERVE) != null) return;
        }
    }

    static Equipment pick(GameState s, Company c, PlantType t) {
        return switch (c.aiStyle) {
            case "green" -> s.catalog.newest(t, s.year());
            case "fossil" -> s.catalog.cheapest(t, s.year());
            default -> s.catalog.defaultFor(t).availableYear <= s.year() ? s.catalog.defaultFor(t) : s.catalog.cheapest(t, s.year());
        };
    }

    public static Plant buildNear(GameState s, Company c, PlantType t, City city) {
        return buildNear(s, c, s.catalog.defaultFor(t), city, 0);
    }

    public static Plant buildNear(GameState s, Company c, Equipment e, City city) {
        return buildNear(s, c, e, city, 0);
    }

    /**
     * Sehrin yakinina parasi odenerek santral ve sehre hat kurar. Santral + hat + reserve nakdi yoksa hicbir sey kurmaz.
     * (Bedava kurulum icin GameState.placeLegacy.)
     */
    public static Plant buildNear(GameState s, Company c, Equipment e, City city, double reserve) {
        if (s.checkEquipment(e) != null) return null;
        int[] site = findSite(s, e, city, 4, 16);
        if (site == null) return null;
        int bx = site[0], by = site[1];
        Probe probe = new Probe(bx, by);
        // Kademe: santral gucunu tasiyan en ucuz kademe (oyuncuyla ayni kurallar).
        LineTier tier = GameState.tierFor(e.capacityMW, s.lineLength(probe, city));
        double lineCost = s.map.inBounds(bx, by) ? s.lineCost(probe, city, tier) : 0;
        if (c.cash < e.capex() + lineCost + reserve) return null;
        Plant p = s.buildPlant(c, e, bx, by);
        if (p == null) return null;
        if (s.buildLine(c, p, city, tier) == null) s.buildLine(c, p, city);
        return p;
    }

    /** Hat maliyeti hesabi icin gecici konum dugumu. */
    private static final class Probe extends GridNode {
        Probe(int x, int y) { this.x = x; this.y = y; }
        @Override public String name() { return ""; }
    }

    /** Sehir cevresinde ekipmana uygun en iyi yeri arar (rng ile, deterministik). */
    public static int[] findSite(GameState s, Equipment e, City city, double minDist, double maxDist) {
        Random r = s.rng;
        PlantType t = e.type;
        double bestScore = -1; int bx = -1, by = -1;
        for (int i = 0; i < 120; i++) {
            double ang = r.nextDouble() * Math.PI * 2, d = minDist + r.nextDouble() * (maxDist - minDist);
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
        return bx < 0 ? null : new int[] { bx, by };
    }
}
