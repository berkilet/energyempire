package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Basit rakip sirket yapay zekasi. */
public final class RivalAI {
    private RivalAI() {}

    public static void monthly(GameState s, Company c) {
        distress(s, c);
        buyListings(s, c);
        tenders(s, c);
        substations(s, c);
        lineUpgrades(s, c);
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

    // ------------------------------------------------------------ varlik satisi
    /** Nakit eksiye dusunce en az kazandiran varliklarindan baslayarak, acigi + yedegi kapatacak kadarini satisa cikarir. */
    static void distress(GameState s, Company c) {
        if (c.cash >= Balance.DISTRESS_CASH) return;
        double need = -c.cash + GridBalance.RIVAL_BUILD_RESERVE, listed = 0;
        for (Listing l : s.sales.open()) if (l.seller == c) listed += l.price;
        if (listed >= need) return;
        List<GridNode> cand = new ArrayList<>();
        for (Plant p : s.plants) if (p.owner == c && s.sales.openListingOf(p) == null) cand.add(p);
        cand.sort(java.util.Comparator.comparingDouble(n -> ((Plant) n).lastMonthProfit));
        for (Substation t : s.substations) if (t.owner == c && s.sales.openListingOf(t) == null) cand.add(t);
        for (GridNode n : cand) {
            if (listed >= need) break;
            if (s.sales.list(c, n) == null) listed += s.sales.openListingOf(n).price;
        }
    }

    /** Ilanlardan alim: santral yillik kari / fiyat >= esik; TM yaricapinda kendi santrali varsa. Ayda en fazla bir alim. */
    static void buyListings(GameState s, Company c) {
        for (Listing l : s.sales.open()) {
            if (l.seller == c || c.cash < l.price + GridBalance.RIVAL_BUILD_RESERVE) continue;
            boolean want = false;
            if (l.asset instanceof Plant p) want = p.operational() && p.lastMonthProfit * 12 / Math.max(1, l.price) >= Balance.RIVAL_BUY_YIELD;
            else if (l.asset instanceof Substation t) for (Plant p : s.plants) if (p.owner == c && t.inRange(p.x, p.y)) { want = true; break; }
            if (want && s.sales.buy(c, l) == null) return;
        }
    }

    // ------------------------------------------------------------ trafo merkezi
    /**
     * TM kurma: (A) 3 kare yaricapta en az 3 kendi santrali (insaattakiler dahil) veya (B) 2 aydan uzun dolu baglanti noktasi.
     * TM bedeli alternatif hat bedeliyle kiyaslanir; nakit + rezerv kurali gecerli. Kume 600 MW'i asarsa TM380'e yukseltir.
     */
    static void substations(GameState s, Company c) {
        for (Substation t : s.substations)
            if (t.owner == c && t.operational() && t.level < 2 && t.pendingUpgrade == 0 && t.cp != null
                && t.cp.installedMW() > GridBalance.RIVAL_TM380_MW && c.cash >= GridBalance.TM380_UPGRADE_COST + GridBalance.RIVAL_BUILD_RESERVE)
                s.upgradeSubstation(t, 2);
        if (c.cash < GridBalance.TM154_COST + GridBalance.RIVAL_BUILD_RESERVE) return;
        s.components();
        List<Plant> free = new ArrayList<>();
        boolean anyFull = false;
        for (Plant p : s.plants) if (p.owner == c && (p.cp == null || p.cp.tm == null)) { free.add(p); anyFull |= p.cpFullMonths >= GridBalance.RIVAL_TM_FULL_MONTHS; }
        if (free.size() < 2 && !anyFull) return;
        int bx = -1, by = -1, bn = 0; boolean bFull = false;
        int r = (int) Math.ceil(GridBalance.TM_RADIUS);
        for (Plant p : free) for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            int x = p.x + dx, y = p.y + dy;
            if (s.checkSubstation(x, y) != null) continue;
            int n = 0; boolean full = false;
            for (Plant q : free) if (Math.hypot(q.x - x, q.y - y) <= GridBalance.TM_RADIUS + 1e-6) { n++; full |= q.cpFullMonths >= GridBalance.RIVAL_TM_FULL_MONTHS; }
            if (n > bn || (n == bn && full && !bFull)) { bn = n; bx = x; by = y; bFull = full; }
        }
        boolean condA = bn >= GridBalance.RIVAL_TM_MIN_PLANTS, condB = bFull && bn >= 1;
        if (bx < 0 || !(condA || condB)) return;
        City city = nearestCity(s, bx, by);
        if (city == null) return;
        Probe at = new Probe(bx, by);
        // Alternatif: (A) yeni santral icin ayri hat, (B) dolu noktaya paralel hat (indirimli). TM: bedel + (B'de) TM'den yeni hat.
        double line = s.lineCost(at, city, LineTier.YG);
        double alt = condA ? line : line * (1 - GridBalance.PARALLEL_DISCOUNT);
        double tmCost = GridBalance.TM154_COST + (condA ? 0 : line);
        if (tmCost > alt || c.cash < tmCost + GridBalance.RIVAL_BUILD_RESERVE) return;
        Substation t = s.buildSubstation(c, 1, bx, by);
        if (t == null) return;
        // Erisim: bolgede kendisinden buyuk bir rakip varsa kapali, yoksa varsayilan bedelle acik.
        double mine = 0;
        java.util.Map<Company, Double> others = new java.util.HashMap<>();
        for (Plant p : s.plants) {
            if (Math.hypot(p.x - bx, p.y - by) > GridBalance.RIVAL_TM_REGION || p.owner.neutral) continue;
            if (p.owner == c) mine += p.capacityMW(); else others.merge(p.owner, p.capacityMW(), Double::sum);
        }
        boolean bigger = false;
        for (double v : others.values()) bigger |= v > mine;
        t.openAccess = !bigger;
        t.accessFeePerMWh = GridBalance.ACCESS_FEE_DEFAULT;
        if (!condA) s.buildLine(c, t, city, LineTier.YG);
        if (!c.player) s.statRivalTms++;
    }

    static City nearestCity(GameState s, int x, int y) {
        City best = null; double bd = Double.MAX_VALUE;
        for (City ct : s.cities) { double d = Math.hypot(ct.x - x, ct.y - y); if (d < bd) { bd = d; best = ct; } }
        return best;
    }

    // ------------------------------------------------------------ hat yukseltme
    /**
     * Gecen yil saatlerinin %25'inden fazlasinda dolu kendi hatlari: yukseltme paralel hattan ucuzsa yukseltir. Yukseltmede hat
     * kapali kalacagi icin: ayni baglanti noktasinin ikinci aktif yolu varsa hemen; yoksa talebin en dusuk oldugu ayda ve kesinti
     * RIVAL_MAX_OFFLINE_DAYS'i asmiyorsa. Aksi halde paralel hat. Her hat yilda bir kez degerlendirilir.
     */
    static void lineUpgrades(GameState s, Company c) {
        long year = s.year();
        for (PowerLine l : new ArrayList<>(s.lines)) {
            if (l.owner != c || l.status != LineStatus.ACTIVE || l.rivalReviewYear == year) continue;
            if (l.saturatedHoursLastYear <= GridBalance.OP_REINFORCE_SATURATION * Balance.HOURS_PER_YEAR) continue;
            boolean second = secondPath(s, l);
            boolean lowSeason = s.month() == GridBalance.LOW_DEMAND_MONTH;
            if (!second && !lowSeason) continue; // dusuk mevsimi bekle
            l.rivalReviewYear = year;
            // Karsilastirma ayni ek kapasite icin: eksik = noktanin kurulu gucu - hat kapasitesi (en az hattin yarisi).
            ConnectionPoint cp = GameState.cpOf(l.a) != null ? GameState.cpOf(l.a) : GameState.cpOf(l.b);
            double need = Math.max(l.capacityMW() * 0.5, cp == null ? 0 : cp.installedMW() - cp.lineCapacityMW());
            double up = s.checkUpgrade(c, l) == null ? s.upgradeCost(l) : Double.MAX_VALUE;
            if (!second && s.upgradeHours(l) > GridBalance.RIVAL_MAX_OFFLINE_DAYS * 24) up = Double.MAX_VALUE;
            int dup = 0;
            for (PowerLine o : l.a.lines) if (o.other(l.a) == l.b) dup++;
            int nPar = (int) Math.ceil(need / l.capacityMW());
            double par = dup + nPar <= 3 && s.checkLine(c, l.a, l.b, l.tier) == null ? nPar * s.lineCost(l.a, l.b, l.tier) : Double.MAX_VALUE;
            if (up <= par && up < Double.MAX_VALUE) {
                if (c.cash < up + GridBalance.RIVAL_BUILD_RESERVE) continue;
                if (s.upgradeLine(c, l) == null) {
                    if (!c.player) s.statRivalUpgrades++;
                    s.statUpgradeLog.add(new long[] { s.hour, l.id, second ? 1 : 0, l.buildHoursLeft });
                }
            } else if (par < Double.MAX_VALUE && c.cash >= par + GridBalance.RIVAL_BUILD_RESERVE) {
                if (s.buildLine(c, l.a, l.b, l.tier) != null && !c.player) s.statRivalParallel++;
            }
        }
    }

    /** Hattin santral/TM tarafindaki baglanti noktasinin bu hat disinda kullanilabilir bir ihracat hatti var mi. */
    static boolean secondPath(GameState s, PowerLine l) {
        s.components();
        ConnectionPoint cp = GameState.cpOf(l.a);
        if (cp == null) cp = GameState.cpOf(l.b);
        if (cp == null) return false;
        for (PowerLine o : cp.exportLines) if (o != l && o.usable()) return true;
        return false;
    }

    // ------------------------------------------------------------ acil kapasite ihaleleri
    /** Kazanilan ihale yukumlulugunu kurar; acik ihalelere tahmini maliyet + marjla teklif verir. */
    static void tenders(GameState s, Company c) {
        for (Tender t : s.tenders.all) {
            if (t.status != Tender.Status.AWARDED || t.winner != c) continue;
            double have = 0;
            for (Plant p : s.plants)
                if (p.owner == c && p.id >= t.plantIdMark && Math.hypot(p.x - t.city.x, p.y - t.city.y) <= Balance.TENDER_MAX_DIST) have += p.capacityMW();
            if (have < t.mw) buildNear(s, c, tenderEquipment(s, c), t.city, 0, 3, Balance.TENDER_MAX_DIST - 1);
        }
        for (Tender t : s.tenders.open()) {
            if (t.bidOf(c) != null) continue;
            Equipment e = tenderEquipment(s, c);
            int units = (int) Math.ceil(t.mw / e.capacityMW);
            double capex = units * e.capex(), dep = s.tenders.depositFor(t);
            if (c.cash < dep + capex + GridBalance.RIVAL_BUILD_RESERVE) continue; // teminat + yatirim karsilanamiyor
            double price = tenderPrice(s, c, e, t, units);
            s.tenders.bid(c, t, price);
        }
    }

    static Equipment tenderEquipment(GameState s, Company c) {
        return pick(s, c, c.aiStyle.equals("green") ? PlantType.WIND : PlantType.GAS);
    }

    /** Teklif: (yillik sermaye geri kazanimi + sabit O&M) / garantili uretim + marjinal maliyet, ustune kar marji. */
    static double tenderPrice(GameState s, Company c, Equipment e, Tender t, int units) {
        Plant probe = new Plant();
        probe.type = e.type; probe.equipment = e; probe.owner = c; probe.x = t.city.x; probe.y = t.city.y;
        probe.builtAtHour = s.hour; probe.ageStartHour = s.hour;
        double cf = e.type.renewable ? (e.type == PlantType.WIND ? 0.33 : e.type == PlantType.SOLAR ? 0.22 : 0.5) : 0.85;
        double mwh = Math.min(t.mw, units * e.capacityMW) * cf * Balance.HOURS_PER_YEAR;
        double annual = units * (e.capex() * Balance.TENDER_CRF + e.fixedOmPerHour() * Balance.HOURS_PER_YEAR);
        double margin = Balance.TENDER_RIVAL_MARGIN + switch (c.aiStyle) { case "fossil" -> 0.05; case "green" -> -0.03; default -> 0; } + s.rng.nextDouble() * 0.05;
        double price = (annual / Math.max(1, mwh) + s.marginalCost(probe)) * (1 + margin);
        return Math.max(Balance.TENDER_MIN_PRICE, Math.min(Balance.TENDER_MAX_PRICE, Math.round(price * 10) / 10.0));
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
        return buildNear(s, c, e, city, reserve, 4, 16);
    }

    public static Plant buildNear(GameState s, Company c, Equipment e, City city, double reserve, double minDist, double maxDist) {
        if (s.checkEquipment(e) != null) return null;
        // %50: kendi kumesinin/TM'sinin yanina (bos ihracat kapasitesi varsa hat gerekmez)
        if (s.rng.nextDouble() < GridBalance.RIVAL_CLUSTER_CHANCE) {
            Plant p = buildInCluster(s, c, e, city, reserve, maxDist);
            if (p != null) return p;
        }
        int[] site = findSite(s, e, city, minDist, maxDist);
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
        // Oyuncunun hat penceresindeki ayni 15 yillik karsilastirma (bekleme kaybi dahil); nakit yetmiyorsa UŞİ.
        Plant est = new Plant();
        est.type = e.type; est.equipment = e; est.owner = c; est.x = bx; est.y = by; est.builtAtHour = s.hour; est.ageStartHour = s.hour;
        GridOperator.Choice ch = s.operator.compare(probe, city, tier, e.buildDays() / 30.0, s.operator.expectedMonthlyMargin(est, tier) * nLines);
        boolean ownAffordable = c.cash >= e.capex() + lineCost + reserve;
        boolean opAffordable = c.cash >= e.capex() + GridOperator.applicationCost(lineCost / nLines) + reserve;
        if (ownAffordable && (ch.ownBetter() || !opAffordable)) {
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
    private static Plant buildInCluster(GameState s, Company c, Equipment e, City city, double reserve, double maxDist) {
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
                if (Math.hypot(t[0] - city.x, t[1] - city.y) > maxDist) continue;
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
