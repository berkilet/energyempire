package com.energytycoon.sim;

import com.energytycoon.world.WorldMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Butun simulasyon durumu ve saatlik tick. LibGDX'ten bagimsiz. */
public final class GameState {
    public static final String[] CITY_NAMES = {
        "Akköy", "Bozova", "Çınarlı", "Demirpark", "Elmalı", "Gülbahçe", "Kuzeyliman", "Yeşilırmak", "Taşkale", "Ovakent",
        "Karasu", "Mercanköy", "Sarıyurt", "Dumanlı"
    };
    static final String[][] RIVALS = {
        { "Anadolu Enerji", "e74c3c", "fossil" },
        { "Yeşil Vadi Güç", "3498db", "green" },
        { "Delta Elektrik", "f39c12", "balanced" },
    };

    public final GameSettings settings;
    public final Scenario scenario;
    public final long seed;
    public final WorldMap map;
    public final Random rng;
    public final Markets markets = new Markets();
    public final EquipmentCatalog catalog = new EquipmentCatalog();

    public final List<Company> companies = new ArrayList<>();
    public Company player;
    public final List<City> cities = new ArrayList<>();
    public final List<Plant> plants = new ArrayList<>();
    public final List<PowerLine> lines = new ArrayList<>();
    public final Map<Integer, GridNode> nodeById = new HashMap<>();
    /** tile -> node id (-1 yok). Sehir 3x3 kaplar. */
    public final int[] occ;
    public int nextId = 1;
    /** Hazir kurulumlarda (senaryo/rakip baslangici) teslim gecikmesi uygulanmaz. */
    boolean suppressDelays;

    public long hour;
    public boolean gameOver, won;
    public String gameOverReason = "";

    // Gozlem istatistikleri (son saat)
    public double statDemand, statServed, statPrice = 40, statPlayerMW, statRivalMW, statCo2Hour;
    public double statRenewShare;
    // Aylik sistem istatistikleri
    public double monthDemandMWh, monthServedMWh, lastServedRatio = 1;
    public final History history = new History();
    public final ArrayDeque<String> messages = new ArrayDeque<>();

    // Topoloji onbellegi
    private boolean topoDirty = true;
    final List<Component> components = new ArrayList<>();

    public static final class Component {
        public final List<City> cities = new ArrayList<>();
        public final List<Plant> plants = new ArrayList<>();
        public double loss;
        public double price = 40;
        public double nominalMW, peakMW;
    }

    public GameState(long seed) { this(defaultSettings(seed)); }

    public GameState(GameSettings settings) {
        this.settings = settings.effective();
        this.scenario = this.settings.scenario();
        this.seed = this.settings.seed;
        this.rng = new Random(seed ^ 0x5DEECE66DL);
        this.map = new WorldMap(this.settings.size, seed, this.settings.preset);
        this.occ = new int[map.size * map.size];
        java.util.Arrays.fill(occ, -1);
    }

    private static GameSettings defaultSettings(long seed) {
        GameSettings g = new GameSettings();
        g.seed = seed;
        return g;
    }

    public static GameState newGame(long seed, String companyName) {
        GameSettings g = defaultSettings(seed);
        g.companyName = companyName;
        return newGame(g);
    }

    public static GameState newGame(GameSettings settings) {
        GameState s = new GameState(settings);
        Difficulty d = s.settings.difficulty;
        s.player = new Company(s.settings.companyName, true, 0x2ecc71, Balance.START_CASH * d.cashMult);
        s.player.rateAdd = d.rateAdd;
        s.companies.add(s.player);
        for (int i = 0; i < s.settings.rivals; i++) {
            Company r = new Company(RIVALS[i][0], false, Integer.parseInt(RIVALS[i][1], 16), 420e6 * d.rivalMult);
            r.aiStyle = RIVALS[i][2];
            s.companies.add(r);
        }
        s.hour = 8;
        s.placeCities();
        s.seedRivals();
        s.scenario.setup.accept(s);
        s.updateValuations();
        s.notify("Hoş geldiniz! Senaryo: " + s.scenario.title);
        return s;
    }

    // ------------------------------------------------------------ olusturma
    private void placeCities() {
        int want = settings.cities;
        int tries = 0;
        List<String> names = new ArrayList<>(List.of(CITY_NAMES));
        java.util.Collections.shuffle(names, rng);
        double spacing = map.size / Math.sqrt(want) * 0.85;
        int margin = Math.max(4, map.size / 12);
        int maxLevel = Math.max(9, settings.preset.relief - 4);
        while (cities.size() < want && tries++ < 8000) {
            int x = margin + rng.nextInt(map.size - 2 * margin), y = margin + rng.nextInt(map.size - 2 * margin);
            int lv = map.level[map.idx(x, y)];
            if (!allLand(x, y, 2) || lv > maxLevel) continue;
            double minD = spacing * (tries < 3000 ? 1.0 : tries < 6000 ? 0.75 : 0.55);
            boolean ok = true;
            for (City c : cities) if (Math.hypot(c.x - x, c.y - y) < minD) { ok = false; break; }
            if (!ok) continue;
            map.flatten(x, y, 1);
            City c = new City();
            c.id = nextId++; c.x = x; c.y = y; c.name = names.get(cities.size() % names.size());
            c.population = 260_000 + rng.nextInt(650_000);
            c.satisfaction = 0.9;
            cities.add(c); nodeById.put(c.id, c);
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) occ[map.idx(x + dx, y + dy)] = c.id;
        }
        topoDirty = true;
    }

    private boolean allLand(int cx, int cy, int r) {
        for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
            if (!map.inBounds(cx + dx, cy + dy) || map.water[map.idx(cx + dx, cy + dy)]) return false;
            if (occ[map.idx(cx + dx, cy + dy)] >= 0) return false;
        }
        return true;
    }

    private void seedRivals() {
        int ci = 0;
        for (Company r : companies) {
            if (r.player || cities.isEmpty()) continue;
            City c = cities.get(ci++ % cities.size());
            double cash = r.cash;
            switch (r.aiStyle) {
                case "fossil" -> RivalAI.buildNear(this, r, PlantType.COAL, c, true);
                case "green" -> { RivalAI.buildNear(this, r, PlantType.WIND, c, true); RivalAI.buildNear(this, r, PlantType.GAS, c, true); }
                default -> { RivalAI.buildNear(this, r, PlantType.GAS, c, true); RivalAI.buildNear(this, r, PlantType.SOLAR, c, true); }
            }
            r.cash = cash;
        }
    }

    // ------------------------------------------------------------ sorgular
    public GridNode nodeAt(int x, int y) {
        if (!map.inBounds(x, y)) return null;
        int id = occ[map.idx(x, y)];
        return id < 0 ? null : nodeById.get(id);
    }
    public GridNode nodeById(int id) { return nodeById.get(id); }

    public long year() { return Balance.START_YEAR + hour / Balance.HOURS_PER_YEAR; }
    public int month() { return (int) ((hour / Balance.HOURS_PER_MONTH) % 12); }
    public int dayOfMonth() { return (int) ((hour / Balance.HOURS_PER_DAY) % Balance.DAYS_PER_MONTH) + 1; }
    public int hourOfDay() { return (int) (hour % 24); }
    public int dayIndex() { return (int) (hour / 24); }

    public double assetValue(Company c) {
        double v = 0;
        for (Plant p : plants) if (p.owner == c) v += p.bookValue(hour);
        for (PowerLine l : lines) if (l.owner == c) v += l.buildCost * 0.8;
        return v;
    }

    public double interestRate(Company c) { return c.interestRate(assetValue(c)); }
    public double creditLimit(Company c) { return c.creditLimit(assetValue(c)); }

    public void notify(String msg) {
        messages.add(msg);
        while (messages.size() > 200) messages.poll();
    }

    public int countPlants(Company c, PlantType t) {
        int n = 0;
        for (Plant p : plants) if (p.owner == c && p.type == t) n++;
        return n;
    }

    public double lastPlayerMarketShare() {
        double tot = 0;
        for (Company c : companies) tot += c.lastMwhGenerated;
        return tot > 0 ? player.lastMwhGenerated / tot : 0;
    }

    // ------------------------------------------------------------ insaat
    /** Null donerse yer uygun, aksi halde sebep. */
    public String checkPlacement(PlantType t, int x, int y) {
        if (!map.inBounds(x, y)) return "Harita dışı";
        if (map.isWater(x, y)) return "Suya inşa edilemez";
        if (occ[map.idx(x, y)] >= 0) return "Alan dolu";
        if (t == PlantType.HYDRO && map.hydro[map.idx(x, y)] < 0.18) return "Hidro için su kenarı gerekli";
        if (t == PlantType.NUCLEAR && map.hydro[map.idx(x, y)] < 0.10) return "Nükleer için soğutma suyu gerekli";
        if (t == PlantType.NUCLEAR) {
            for (City c : cities) if (Math.hypot(c.x - x, c.y - y) < 5) return "Şehre çok yakın (min 5)";
        }
        return null;
    }

    public String checkEquipment(Equipment e) {
        if (e.availableYear > year()) return e.fullName() + " " + e.availableYear + " yılında piyasaya çıkacak";
        return null;
    }

    public Plant buildPlant(Company owner, PlantType t, int x, int y) {
        return buildPlant(owner, catalog.defaultFor(t), x, y);
    }

    public Plant buildPlant(Company owner, Equipment eq, int x, int y) {
        if (checkPlacement(eq.type, x, y) != null || checkEquipment(eq) != null) return null;
        double cost = eq.capex();
        if (owner.cash < cost - 1 && owner.player) return null;
        owner.cash -= cost; owner.capexSpent += cost;
        Plant p = new Plant();
        p.id = nextId++; p.type = eq.type; p.equipment = eq; p.x = x; p.y = y; p.owner = owner;
        p.buildHoursLeft = eq.buildDays() * 24;
        if (!suppressDelays && rng.nextDouble() < eq.supplier.delayChance()) {
            int extra = Math.min(90 * 24, (int) (p.buildHoursLeft * (0.15 + rng.nextDouble() * 0.3)));
            p.buildHoursLeft += extra;
            if (owner.player) notify("TEDARİK: " + eq.supplier.name + " teslimatı " + extra / 24 + " gün gecikecek");
        }
        p.builtAtHour = hour + p.buildHoursLeft;
        p.ageStartHour = p.builtAtHour;
        plants.add(p); nodeById.put(p.id, p); occ[map.idx(x, y)] = p.id;
        topoDirty = true;
        return p;
    }

    /** Null donerse basladi, aksi halde sebep. */
    public String startUpgrade(Plant p, Upgrade u) {
        if (!p.canUpgrade(u)) return "Bu yükseltme şu an yapılamaz";
        double cost = u.cost(p);
        if (p.owner.cash < cost) return "Yetersiz nakit (" + Math.round(cost / 1e6) + "M $ gerekli)";
        p.owner.cash -= cost; p.owner.capexSpent += cost;
        p.pendingUpgrade = u; p.upgradeHoursLeft = u.days * 24;
        return null;
    }

    private void finishUpgrade(Plant p) {
        Upgrade u = p.pendingUpgrade;
        p.upgrades.add(u);
        if (u == Upgrade.REPOWER || u == Upgrade.HYDRO_RETROFIT || u == Upgrade.LIFE_EXT) p.ageStartHour = hour;
        if (u == Upgrade.REPOWER || u == Upgrade.LIFE_EXT) p.builtAtHour = Math.max(p.builtAtHour, hour - Balance.HOURS_PER_YEAR * 5L);
        p.pendingUpgrade = null; p.upgradeHoursLeft = 0;
        if (p.owner.player) notify(p.type.label + ": " + u.label + " tamamlandı");
        topoDirty = true;
    }

    public double lineLength(GridNode a, GridNode b) { return Math.hypot(a.x - b.x, a.y - b.y); }

    public double lineCost(GridNode a, GridNode b) {
        double len = lineLength(a, b);
        int steps = Math.max(1, (int) Math.ceil(len));
        int w = 0;
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.round(a.x + (b.x - a.x) * i / (double) steps);
            int y = (int) Math.round(a.y + (b.y - a.y) * i / (double) steps);
            if (map.inBounds(x, y) && map.isWater(x, y)) w++;
        }
        double wf = w / (double) (steps + 1);
        return len * Balance.LINE_COST_PER_TILE * (1 + (Balance.LINE_WATER_MULT - 1) * wf);
    }

    public String checkLine(GridNode a, GridNode b) {
        if (a == null || b == null) return "Bağlantı noktası seçin";
        if (a == b) return "Aynı nokta";
        if (a instanceof City && b instanceof City) return "Şehirleri doğrudan bağlayamazsınız";
        if (lineLength(a, b) > 45) return "Hat çok uzun (max 45)";
        int dup = 0;
        for (PowerLine l : a.lines) if (l.other(a) == b) dup++;
        if (dup >= 3) return "Bu iki nokta arası en fazla 3 hat";
        return null;
    }

    public PowerLine buildLine(Company owner, GridNode a, GridNode b) {
        if (checkLine(a, b) != null) return null;
        double cost = lineCost(a, b);
        if (owner.player && owner.cash < cost) return null;
        owner.cash -= cost; owner.capexSpent += cost;
        PowerLine l = new PowerLine();
        l.id = nextId++; l.a = a; l.b = b; l.owner = owner; l.buildCost = cost; l.lengthTiles = lineLength(a, b);
        lines.add(l); a.lines.add(l); b.lines.add(l);
        topoDirty = true;
        return l;
    }

    public void demolishPlant(Plant p) {
        if (!plants.contains(p)) return;
        for (PowerLine l : new ArrayList<>(p.lines)) removeLine(l, false);
        p.owner.cash += p.bookValue(hour) * Balance.DEMOLISH_REFUND;
        plants.remove(p); nodeById.remove(p.id); occ[map.idx(p.x, p.y)] = -1;
        topoDirty = true;
    }

    public void removeLine(PowerLine l, boolean refund) {
        if (!lines.remove(l)) return;
        l.a.lines.remove(l); l.b.lines.remove(l);
        if (refund) l.owner.cash += l.buildCost * Balance.DEMOLISH_REFUND;
        topoDirty = true;
    }

    public boolean borrow(Company c) {
        double limit = creditLimit(c);
        if (c.debt + Balance.LOAN_STEP > limit) return false;
        c.debt += Balance.LOAN_STEP; c.cash += Balance.LOAN_STEP;
        return true;
    }

    public boolean repay(Company c) {
        double amt = Math.min(Balance.LOAN_STEP, c.debt);
        if (amt <= 0 || c.cash < amt) return false;
        c.debt -= amt; c.cash -= amt;
        return true;
    }

    // ------------------------------------------------------------ topoloji
    private void rebuildComponents() {
        components.clear();
        List<GridNode> nodes = new ArrayList<>(cities.size() + plants.size());
        nodes.addAll(cities); nodes.addAll(plants);
        Map<GridNode, Component> comp = new HashMap<>();
        for (GridNode start : nodes) {
            if (comp.containsKey(start)) continue;
            Component c = new Component();
            ArrayDeque<GridNode> q = new ArrayDeque<>();
            q.add(start); comp.put(start, c);
            double totLen = 0;
            while (!q.isEmpty()) {
                GridNode n = q.poll();
                if (n instanceof City ct) c.cities.add(ct); else c.plants.add((Plant) n);
                for (PowerLine l : n.lines) {
                    if (l.a == n) totLen += l.lengthTiles;
                    GridNode o = l.other(n);
                    if (!comp.containsKey(o)) { comp.put(o, c); q.add(o); }
                }
            }
            c.loss = Math.min(0.15, Balance.LINE_LOSS_PER_TILE * (totLen / Math.max(1, c.plants.size())));
            components.add(c);
        }
        topoDirty = false;
        refreshComponentStats();
    }

    private void refreshComponentStats() {
        for (Component c : components) {
            c.peakMW = 0; c.nominalMW = 0;
            for (City ct : c.cities) c.peakMW += ct.peakMW();
            for (Plant p : c.plants) if (p.operational()) c.nominalMW += p.capacityMW() * nominalCf(p.type);
        }
    }

    private static double nominalCf(PlantType t) {
        return switch (t) { case SOLAR -> 0.25; case WIND -> 0.33; case HYDRO -> 0.5; default -> 0.9; };
    }

    public List<Component> components() {
        if (topoDirty) rebuildComponents();
        return components;
    }

    public Component componentOf(GridNode n) {
        for (Component c : components()) {
            if (n instanceof City ct ? c.cities.contains(ct) : c.plants.contains(n)) return c;
        }
        return null;
    }

    // ------------------------------------------------------------ yardimcilar
    /** Yakit yatagina yakinlik indirimi. */
    public double fuelMult(Plant p) {
        double dep = p.type.fuel == PlantType.Fuel.COAL ? nearbyDeposit(map.coal, p.x, p.y)
                   : p.type.fuel == PlantType.Fuel.GAS ? nearbyDeposit(map.gas, p.x, p.y) : 0;
        return 1.0 - 0.28 * dep;
    }

    private double nearbyDeposit(float[] layer, int cx, int cy) {
        double best = 0;
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
            int x = cx + dx, y = cy + dy;
            if (map.inBounds(x, y)) best = Math.max(best, layer[map.idx(x, y)] * (1 - 0.2 * Math.max(Math.abs(dx), Math.abs(dy))));
        }
        return Math.min(1, best);
    }

    public double fuelCostPerMWh(Plant p) {
        if (p.type.fuel == PlantType.Fuel.NONE) return 0;
        return markets.fuelPrice(p.type.fuel) * fuelMult(p) * p.fuelPriceMult() / p.efficiency(hour);
    }

    public double marginalCost(Plant p) {
        return fuelCostPerMWh(p) + p.type.varOm + markets.carbonPrice * p.co2PerMWh(hour);
    }

    /** Ekipman ve sahaya bagli anlik kapasite faktoru (0..1+). */
    public double capacityFactorNow(Plant p) {
        int h = hourOfDay(), m = month();
        int i = map.idx(p.x, p.y);
        Equipment e = p.equipment;
        switch (p.type) {
            case SOLAR: {
                double winter = (1 + Math.cos(2 * Math.PI * m / 12.0)) / 2;
                double rise = 6.5 + winter * 1.5, set = 17.5 - winter * 1.5;
                if (h + 0.5 <= rise || h + 0.5 >= set) return 0;
                double sunH = Math.sin(Math.PI * (h + 0.5 - rise) / (set - rise));
                if (p.upgrades.contains(Upgrade.TRACKER)) sunH = Math.pow(sunH, 0.6);
                double season = 0.62 + 0.38 * (1 - winter);
                double heat = 1 - e.paramA * Math.max(0, (map.sun[i] - 0.8) / 0.2) * (0.5 + 0.5 * (1 - winter));
                return Math.min(1.2, sunH * season * markets.cloud * map.sun[i] * 1.15 * heat * p.renewableMult(hour) / 1.07);
            }
            case WIND: {
                double speed = markets.wind * map.wind[i];
                double x = (speed - e.paramA) / Math.max(0.1, e.paramB - e.paramA);
                if (x <= 0) return 0;
                return Math.min(1, Math.pow(Math.min(1, x), 1.2) * p.renewableMult(hour));
            }
            case HYDRO: {
                double s = 1 + 0.4 * Math.sin(2 * Math.PI * (m - 1) / 12.0);
                double match = 1.12 - 0.45 * Math.abs(e.paramA - map.head(p.x, p.y));
                return Math.max(0.08, Math.min(0.98, 0.5 * (0.6 + 0.8 * map.hydro[i]) * s * match * p.renewableMult(hour)));
            }
            default:
                return 1.0;
        }
    }

    /** Arayuz icin: sahanin bu ekipman icin ortalama uretim beklentisi (0..1). */
    public double siteScore(Equipment e, int x, int y) {
        int i = map.idx(x, y);
        switch (e.type) {
            case SOLAR: {
                double heat = 1 - e.paramA * Math.max(0, (map.sun[i] - 0.8) / 0.2) * 0.75;
                return 0.21 * map.sun[i] / 0.85 * heat * e.outputMult;
            }
            case WIND:
                return expectedWindCf(e, map.wind[i]);
            case HYDRO: {
                double match = 1.12 - 0.45 * Math.abs(e.paramA - map.head(x, y));
                return Math.min(0.98, 0.5 * (0.6 + 0.8 * map.hydro[i]) * match);
            }
            default: return 0.9;
        }
    }

    /** Tipik ruzgar dagilimi altinda beklenen kapasite faktoru. */
    public static double expectedWindCf(Equipment e, double siteWind) {
        double sum = 0, norm = 0;
        for (int k = 0; k < 20; k++) {
            double w = 0.03 + k * 0.045;
            double weight = Math.exp(-Math.pow((w - 0.36) / 0.2, 2));
            double xx = (w * siteWind - e.paramA) / Math.max(0.1, e.paramB - e.paramA);
            sum += weight * (xx <= 0 ? 0 : Math.pow(Math.min(1, xx), 1.2));
            norm += weight;
        }
        return sum / norm;
    }

    // ------------------------------------------------------------ tick
    public void tick() {
        if (gameOver) return;
        int hod = hourOfDay();
        markets.hourly(rng);
        if (hod == 0) { markets.daily(rng, (int) (hour / Balance.HOURS_PER_YEAR)); }

        boolean changed = false;
        for (Plant p : plants) {
            if (p.buildHoursLeft > 0) {
                if (--p.buildHoursLeft == 0) {
                    changed = true;
                    if (p.owner.player) notify(p.type.label + " devreye girdi (" + (int) p.capacityMW() + " MW, " + p.equipment.fullName() + ")");
                }
                continue;
            }
            if (p.upgrading() && --p.upgradeHoursLeft <= 0) { finishUpgrade(p); changed = true; }
            if (p.outageHoursLeft > 0) {
                if (--p.outageHoursLeft == 0 && p.owner.player) notify(p.type.label + " arızası giderildi");
            } else if (p.online()) {
                double base = p.type.renewable ? Balance.OUTAGE_CHANCE_PER_HOUR * 0.3 : Balance.OUTAGE_CHANCE_PER_HOUR;
                double age = p.wearYears(hour) / p.type.lifeYears;
                double chance = base * p.equipment.supplier.outageMult() * (1 + age * 0.8) * (p.upgrades.contains(Upgrade.LIFE_EXT) ? 0.6 : 1);
                if (rng.nextDouble() < chance) {
                    p.outageHoursLeft = p.type == PlantType.NUCLEAR ? 200 + rng.nextInt(200) : 24 + rng.nextInt(72);
                    double repair = p.underWarranty(hour) ? 0 : p.equipment.listCapex() * (p.type.renewable ? 0.006 : 0.012);
                    p.owner.cash -= repair; p.owner.omCost += repair; p.monthProfit -= repair;
                    if (p.owner.player) notify("ARIZA: " + p.type.label + " devre dışı. "
                        + (repair > 0 ? "Onarım " + Math.round(repair / 1e6 * 10) / 10.0 + "M $" : "Garanti kapsamında (" + p.equipment.supplier.name + ")"));
                }
            }
        }
        if (changed) topoDirty = true;

        dispatchHour();

        for (PowerLine l : lines) {
            double om = l.omPerHour();
            l.owner.cash -= om; l.owner.omCost += om;
        }
        for (City c : cities) c.satisfaction += 0.04 * ((c.demandMW <= 0 ? 1 : c.servedMW / c.demandMW) - c.satisfaction);

        hour++;
        if (hour % Balance.HOURS_PER_MONTH == 0) monthly();
    }

    private static final double[] HOUR_CURVE = {
        0.62, 0.58, 0.55, 0.54, 0.56, 0.63, 0.76, 0.90, 0.95, 0.93, 0.92, 0.93,
        0.94, 0.93, 0.92, 0.93, 0.97, 1.04, 1.10, 1.07, 1.00, 0.90, 0.78, 0.68 };

    private void dispatchHour() {
        int hod = hourOfDay();
        int day = dayIndex();
        double season = 1.07 + 0.05 * Math.cos(2 * Math.PI * month() / 12.0);
        double weekend = (day % 7 >= 5) ? 0.93 : 1.0;
        double dmult = HOUR_CURVE[hod] * season * weekend * markets.demandNoise;
        for (City c : cities) { c.demandMW = c.peakMW() * dmult; c.servedMW = 0; c.flowMW = 0; }
        for (Plant p : plants) { p.outputMW = 0; p.flowMW = 0; p.availableMW = 0; }

        double totDemand = 0, totServed = 0, priceWeighted = 0, priceDemand = 0, playerMW = 0, rivalMW = 0, renew = 0, co2 = 0;
        double fineMult = settings.difficulty.fineMult;

        for (Component comp : components()) {
            double demand = 0, rawDemand = 0;
            for (City ct : comp.cities) {
                demand += Math.min(ct.demandMW, ct.lineCapacity());
                rawDemand += ct.demandMW;
            }
            totDemand += rawDemand;

            List<Plant> cand = new ArrayList<>(comp.plants.size());
            for (Plant p : comp.plants) {
                if (!p.online()) continue;
                double avail = p.capacityMW() * capacityFactorNow(p);
                avail = Math.min(avail, p.lineCapacity() * (1 - comp.loss));
                p.availableMW = avail;
                if (avail > 0.01) cand.add(p);
            }
            if (cand.isEmpty() || demand <= 0) {
                comp.price = demand > 0 ? Balance.SCARCITY_PRICE : Math.max(Balance.MIN_PRICE, comp.price * 0.98);
                if (demand > 0) fineOwners(comp, demand * fineMult);
                continue;
            }
            final Map<Plant, Double> mc = new HashMap<>(cand.size() * 2);
            for (Plant p : cand) mc.put(p, marginalCost(p) * (p.type.renewable ? 1.0 : 1.04));
            cand.sort(Comparator.<Plant>comparingDouble(pl -> mc.get(pl)).thenComparingInt(pl -> pl.id));

            double gross = demand / (1 - comp.loss);
            double remaining = gross, price = Balance.MIN_PRICE;
            for (Plant p : cand) {
                if (remaining <= 1e-6) break;
                double out = Math.min(p.availableMW, remaining);
                p.outputMW = out;
                remaining -= out;
                price = Math.max(Balance.MIN_PRICE, mc.get(p));
            }
            double produced = gross - Math.max(0, remaining);
            double delivered = produced * (1 - comp.loss);
            double ratio = demand > 0 ? Math.min(1, delivered / demand) : 1;
            boolean shortage = remaining > 1e-3;

            for (City ct : comp.cities) {
                double d = Math.min(ct.demandMW, ct.lineCapacity());
                ct.servedMW = d * ratio; ct.flowMW = ct.servedMW;
            }
            double unserved = rawDemand;
            for (City ct : comp.cities) unserved -= ct.servedMW;
            double unservedFrac = rawDemand > 0 ? Math.max(0, unserved) / rawDemand : 0;
            comp.price = shortage ? Math.min(Balance.SCARCITY_PRICE, Math.max(price, 40) * (1 + 2.5 * unservedFrac)) : price;
            totServed += rawDemand - unserved;
            priceWeighted += comp.price * rawDemand; priceDemand += rawDemand;

            for (Plant p : cand) {
                double out = p.outputMW;
                if (out <= 0) continue;
                p.flowMW = out;
                double rev = out * comp.price;
                double fuelC = out * fuelCostPerMWh(p);
                double var = out * p.type.varOm;
                double pco2 = p.co2PerMWh(hour);
                double carb = out * pco2 * markets.carbonPrice;
                Company o = p.owner;
                o.cash += rev - fuelC - var - carb;
                o.revenue += rev; o.fuelCost += fuelC; o.omCost += var; o.carbonCost += carb;
                o.mwhGenerated += out; o.co2Tons += out * pco2;
                if (p.type.renewable) { o.renewMwh += out; renew += out; }
                p.monthProfit += rev - fuelC - var - carb;
                p.lifetimeMWh += out;
                co2 += out * pco2;
                if (o.player) playerMW += out; else rivalMW += out;
            }
            if (unserved > 1e-3) fineOwners(comp, unserved * fineMult);
        }
        // sabit O&M
        for (Plant p : plants) {
            if (!p.operational()) continue;
            double fx = p.fixedOmPerHour();
            p.owner.cash -= fx; p.owner.omCost += fx; p.monthProfit -= fx;
        }
        statDemand = totDemand; statServed = totServed;
        monthDemandMWh += totDemand; monthServedMWh += totServed;
        statPrice = priceDemand > 0 ? priceWeighted / priceDemand : statPrice;
        statPlayerMW = playerMW; statRivalMW = rivalMW; statCo2Hour = co2;
        double gen = playerMW + rivalMW;
        statRenewShare = gen > 0 ? renew / gen : 0;
        history.push(statPrice, totDemand, gen, playerMW);
    }

    private void fineOwners(Component comp, double unservedMWh) {
        double cap = 0;
        for (Plant p : comp.plants) if (p.operational()) cap += p.capacityMW();
        if (cap <= 0) return;
        double fine = unservedMWh * Balance.BLACKOUT_FINE;
        for (Plant p : comp.plants) {
            if (!p.operational()) continue;
            double share = p.capacityMW() / cap;
            p.owner.cash -= fine * share; p.owner.fines += fine * share;
        }
    }

    // ------------------------------------------------------------ aylik
    private void monthly() {
        refreshComponentStats();
        for (Supplier s : catalog.allSuppliers()) s.monthly(rng);
        lastServedRatio = monthDemandMWh > 0 ? monthServedMWh / monthDemandMWh : 1;
        monthDemandMWh = 0; monthServedMWh = 0;
        for (Company c : companies) {
            double assets = assetValue(c);
            double rate = c.interestRate(assets);
            double intr = c.debt * rate / 12;
            if (c.cash < 0) intr += -c.cash * Balance.OVERDRAFT_RATE / 12;
            c.cash -= intr; c.interest += intr;

            double net = c.monthNetSoFar();
            c.lastRevenue = c.revenue; c.lastFuel = c.fuelCost; c.lastOm = c.omCost; c.lastCarbon = c.carbonCost;
            c.lastInterest = c.interest; c.lastFines = c.fines; c.lastNet = net;
            c.lastMwhGenerated = c.mwhGenerated; c.lastCo2 = c.co2Tons; c.lastRenewMwh = c.renewMwh;
            c.revenue = c.fuelCost = c.omCost = c.carbonCost = c.interest = c.fines = c.capexSpent = 0;
            c.mwhGenerated = 0; c.co2Tons = 0; c.renewMwh = 0;
            c.netHistory.addLast(net);
            while (c.netHistory.size() > 12) c.netHistory.pollFirst();
        }
        for (Plant p : plants) { p.lastMonthProfit = p.monthProfit; p.monthProfit = 0; }

        for (City c : cities) {
            double g = c.satisfaction > 0.97 ? 0.0035 : c.satisfaction > 0.85 ? 0.0008 : c.satisfaction > 0.6 ? -0.002 : -0.006;
            c.population = Math.max(60_000, c.population * (1 + g + 0.0004 * (rng.nextDouble() - 0.3)));
        }
        updateValuations();
        for (Company c : companies) {
            c.valuationHistory.add(c.valuation); c.cashHistory.add(c.cash);
            if (c.valuationHistory.size() > 240) { c.valuationHistory.remove(0); c.cashHistory.remove(0); }
        }
        history.monthly(statPrice);

        for (Company c : companies) {
            if (c.bankrupt) continue;
            if (c.cash < Balance.BANKRUPT_CASH) c.negativeMonths++; else c.negativeMonths = 0;
            if (c.negativeMonths >= 2) {
                c.bankrupt = true;
                if (c.player) {
                    gameOver = true; gameOverReason = "Şirketiniz iflas etti. Nakit tükendi ve borç ödenemedi.";
                } else {
                    notify(c.name + " iflas etti!");
                    for (Plant p : new ArrayList<>(plants)) if (p.owner == c) demolishPlant(p);
                }
            } else if (c.player && c.cash < 0) {
                notify("UYARI: Nakit negatif! Borçlanın veya giderleri azaltın.");
            }
        }
        evaluateScenario();
        for (Company c : companies) if (!c.player && !c.bankrupt) RivalAI.monthly(this, c);
        if ((hour / Balance.HOURS_PER_MONTH) % 12 == 0) notify("Yeni yıl: " + year());
        for (Equipment e : catalog.equipment.values())
            if (e.availableYear == year() && month() == 0) notify("YENİ TEKNOLOJİ: " + e.fullName() + " (" + e.tech + ") piyasada!");
    }

    private void evaluateScenario() {
        if (gameOver || won) return;
        if (scenario.allMet(this) && hour >= Balance.HOURS_PER_MONTH * 2) {
            won = true;
            notify("TEBRİKLER! \"" + scenario.title + "\" senaryosunun tüm hedeflerine ulaştınız. Oyun devam ediyor.");
            return;
        }
        if (scenario.deadlineYear > 0 && year() > scenario.deadlineYear) {
            gameOver = true;
            gameOverReason = "Süre doldu: " + scenario.deadlineYear + " sonuna kadar \"" + scenario.title + "\" hedeflerine ulaşılamadı.";
        }
    }

    public void updateValuations() {
        for (Company c : companies) {
            double assets = assetValue(c);
            double equity = c.cash + assets - c.debt;
            int n = c.netHistory.size();
            double annual = n == 0 ? 0 : c.trailingNet() / n * 12;
            c.valuation = Math.max(0, equity + 4 * Math.max(0, annual));
            double target = c.valuation / Balance.SHARES;
            c.stockPrice = c.stockPrice <= 0 ? target : c.stockPrice * 0.8 + target * 0.2;
        }
    }

    public int totalPlantsOf(Company c) {
        int n = 0;
        for (Plant p : plants) if (p.owner == c) n++;
        return n;
    }
}
