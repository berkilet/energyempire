package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.List;
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
        // %50: kendi kumesinin/TM'sinin yanina (bos ihracat kapasitesi varsa hat gerekmez)
        if (s.rng.nextDouble() < GridBalance.RIVAL_CLUSTER_CHANCE) {
            Plant p = buildInCluster(s, c, e, city, reserve);
            if (p != null) return p;
        }
        int[] site = findSite(s, e, city, 4, 16);
        if (site == null) return null;
        int bx = site[0], by = site[1];
        Probe probe = new Probe(bx, by);
        // Kademe: santral gucunu tasiyan en ucuz kademe (oyuncuyla ayni kurallar).
        LineTier tier = GameState.tierFor(e.capacityMW, s.lineLength(probe, city));
        double lineCost = s.map.inBounds(bx, by) ? s.lineCost(probe, city, tier) : 0;
        int nLines = e.capacityMW > tier.capacityMW ? 2 : 1;
        lineCost *= nLines;

        // Erisime acik (veya kendi) trafo merkezi: bos kapasitesi varsa ve erisim bedeli hattan ucuzsa oraya baglan.
        double cfGuess = e.type.renewable ? 0.3 : 0.6;
        Substation bestTm = null; int[] tmSite = null; double bestCost = lineCost;
        for (Substation t : s.substations) {
            if (!t.operational() || t.cp == null || (t.owner != c && !t.openAccess)) continue;
            if (Math.hypot(t.x - city.x, t.y - city.y) > 18) continue;
            double headroom = t.cp.exportCapacityMW() - t.cp.installedMW();
            if (headroom < e.capacityMW * 0.8) continue;
            double access = t.owner == c ? 0 : e.capacityMW * cfGuess * Balance.HOURS_PER_YEAR * GridBalance.RIVAL_ACCESS_HORIZON_YEARS * t.accessFeePerMWh;
            if (access >= bestCost) continue;
            int[] st = siteInRadius(s, e, t, site);
            if (st == null) continue;
            bestTm = t; tmSite = st; bestCost = access;
        }
        if (bestTm != null) {
            if (c.cash < e.capex() + reserve) return null;
            Plant p = s.buildPlant(c, e, tmSite[0], tmSite[1]);
            if (p != null && bestTm.owner != c) s.setPreferredSubstation(p, bestTm.id);
            return p;
        }

        // Kendi hatti mi UŞİ mi: hat + santral + yedek karsilanabiliyor ve santral UŞİ tahmininden once bitiyorsa kendi hatti;
        // aksi halde (ucuz) UŞİ basvurusu. Oyuncuyla ayni kurallar ve ucretler.
        double len = s.lineLength(probe, city);
        int[] eta = s.operator.estimate(tier, len);
        boolean ownAffordable = c.cash >= e.capex() + lineCost + reserve;
        boolean plantFirst = e.buildDays() < eta[0] * 30;
        if (ownAffordable && plantFirst) {
            Plant p = s.buildPlant(c, e, bx, by);
            if (p == null) return null;
            if (s.buildLine(c, p, city, tier) == null) s.buildLine(c, p, city);
            else if (nLines > 1) s.buildLine(c, p, city, tier);
            return p;
        }
        double opPay = GridOperator.applicationCost(lineCost / nLines);
        if (c.cash < e.capex() + opPay + reserve) return null;
        Plant p = s.buildPlant(c, e, bx, by);
        if (p == null) return null;
        if (s.operator.apply(c, p, city, tier) == null) s.operator.apply(c, p, city, LineTier.YG);
        return p;
    }

    /** Kendi kumesinin yanina veya kendi TM yaricapina, ihracat kapasitesinde yer varsa hatsiz kurar. */
    private static Plant buildInCluster(GameState s, Company c, Equipment e, City city, double reserve) {
        if (c.cash < e.capex() + reserve) return null;
        int[] normal = findSite(s, e, city, 4, 16);
        double minScore = e.type.renewable && normal != null ? s.siteScore(e, normal[0], normal[1]) * 0.85 : 0;
        int[] best = null; double bs = -1;
        for (ConnectionPoint cp : s.connectionPoints()) {
            Company owner = cp.tm != null ? cp.tm.owner : cp.members.isEmpty() ? null : cp.members.get(0).owner;
            if (owner != c || cp.exportCapacityMW() - cp.installedMW() < e.capacityMW * 0.8) continue;
            List<int[]> cand = new ArrayList<>();
            if (cp.tm != null) {
                int r = (int) Math.ceil(cp.tm.radius);
                for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) if (cp.tm.inRange(cp.tm.x + dx, cp.tm.y + dy)) cand.add(new int[] { cp.tm.x + dx, cp.tm.y + dy });
            } else for (Plant m : cp.members) {
                if (Math.hypot(m.x - city.x, m.y - city.y) > 18) continue;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) cand.add(new int[] { m.x + dx, m.y + dy });
            }
            for (int[] t : cand) {
                if (s.checkPlacement(e.type, t[0], t[1]) != null) continue;
                double sc = e.type.renewable ? s.siteScore(e, t[0], t[1]) : 1;
                if (sc >= minScore && sc > bs) { bs = sc; best = t; }
            }
        }
        return best == null ? null : s.buildPlant(c, e, best[0], best[1]);
    }

    /** TM yaricapinda uygun yer; yenilenebilirde saha puani normal sahanin %85'inden az olmamali. */
    private static int[] siteInRadius(GameState s, Equipment e, Substation t, int[] normal) {
        double minScore = e.type.renewable ? s.siteScore(e, normal[0], normal[1]) * 0.85 : 0;
        int r = (int) Math.ceil(t.radius);
        int[] best = null; double bs = -1;
        for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            int x = t.x + dx, y = t.y + dy;
            if (!t.inRange(x, y) || s.checkPlacement(e.type, x, y) != null) continue;
            double sc = e.type.renewable ? s.siteScore(e, x, y) : 1 - Math.hypot(dx, dy) * 0.01;
            if (sc >= minScore && sc > bs) { bs = sc; best = new int[] { x, y }; }
        }
        return best;
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
