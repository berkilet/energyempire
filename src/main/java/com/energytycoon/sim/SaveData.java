package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.List;

/** Kayit icin duz veri modeli (LibGDX Json ile serilestirilir). */
public class SaveData {
    public static class CompanyD { public String name; public boolean player; public int color; public double cash, debt, valuation, stockPrice;
        public boolean bankrupt; public int negativeMonths;
        public double lastRevenue, lastFuel, lastOm, lastCarbon, lastInterest, lastFines, lastNet, lastMwh, lastCo2;
        public double[] netHistory; public double[] valHist, cashHist;
        public String aiStyle; public double rateAdd, lastRenew;
        /** v3 */ public boolean neutral; }
    public static class CityD { public int id, x, y; public String name; public double pop, sat; }
    public static class PlantD { public int id, x, y, owner, buildLeft, outage; public String type; public long builtAt; public double lifetimeMWh, lastMonthProfit, monthProfit;
        public String equipment, pendingUpgrade; public String[] upgrades; public int upgradeLeft; public long ageStart = Long.MIN_VALUE;
        /** v3 */ public boolean legacy; public int retireYear, retireMonth; public String customName; }
    public static class LineD { public int id, a, b, owner; public double cost; }

    /** v3: kamu eski filosu (neutral sirket, legacy santral alanlari), rivalsStartWithFleet. v1/v2 okunabilir. */
    public static final int CURRENT_VERSION = 3;
    /**
     * 0 = alan yok (eski kayit). LibGDX Json varsayilan degere esit alanlari yazmadigi icin v1/v2 kayitlarinda
     * bu alan dosyada bulunmaz; v1 ile v2, v2'ye ozgu 'preset' alaniyla ayirt edilir.
     */
    public int version = 0;
    public boolean rivalsStartWithFleet;
    // v2
    public String preset, difficulty, scenario, companyName;
    public int size, cityCount, rivals;
    public double coalMult = 1, gasMult = 1, uraniumMult = 1, carbonGrowthMult = 1, lastServedRatio = 1;
    public java.util.HashMap<String, Double> supplierIndex = new java.util.HashMap<>();
    public long seed, hour;
    public int nextId;
    public boolean won;
    public double coal, gas, uranium, carbon, wind, cloud, demandNoise;
    public List<CompanyD> companies = new ArrayList<>();
    public List<CityD> cities = new ArrayList<>();
    public List<PlantD> plants = new ArrayList<>();
    public List<LineD> lines = new ArrayList<>();
    public double[] monthlyPrice;

    public SaveData() {}

    /** Dosyadaki surum; alan yoksa v2 alanlarina bakarak 1 ya da 2. */
    public int effectiveVersion() {
        if (version > 0) return version;
        return preset != null ? 2 : 1;
    }

    public static SaveData from(GameState s) {
        SaveData d = new SaveData();
        d.version = CURRENT_VERSION;
        d.seed = s.seed; d.hour = s.hour; d.nextId = s.nextId; d.won = s.won;
        d.preset = s.settings.preset.name(); d.difficulty = s.settings.difficulty.name(); d.scenario = s.settings.scenarioId;
        d.companyName = s.settings.companyName; d.size = s.settings.size; d.cityCount = s.settings.cities; d.rivals = s.settings.rivals;
        d.rivalsStartWithFleet = s.settings.rivalsStartWithFleet;
        d.coalMult = s.markets.coalMult; d.gasMult = s.markets.gasMult; d.uraniumMult = s.markets.uraniumMult;
        d.carbonGrowthMult = s.markets.carbonGrowthMult; d.lastServedRatio = s.lastServedRatio;
        for (Supplier sp : s.catalog.allSuppliers()) d.supplierIndex.put(sp.id, sp.priceIndex);
        d.coal = s.markets.coalPrice; d.gas = s.markets.gasPrice; d.uranium = s.markets.uraniumPrice; d.carbon = s.markets.carbonPrice;
        d.wind = s.markets.wind; d.cloud = s.markets.cloud; d.demandNoise = s.markets.demandNoise;
        for (Company c : s.companies) {
            CompanyD cd = new CompanyD();
            cd.name = c.name; cd.player = c.player; cd.color = c.colorRgb; cd.cash = c.cash; cd.debt = c.debt;
            cd.valuation = c.valuation; cd.stockPrice = c.stockPrice; cd.bankrupt = c.bankrupt; cd.negativeMonths = c.negativeMonths;
            cd.lastRevenue = c.lastRevenue; cd.lastFuel = c.lastFuel; cd.lastOm = c.lastOm; cd.lastCarbon = c.lastCarbon;
            cd.lastInterest = c.lastInterest; cd.lastFines = c.lastFines; cd.lastNet = c.lastNet; cd.lastMwh = c.lastMwhGenerated; cd.lastCo2 = c.lastCo2;
            cd.netHistory = c.netHistory.stream().mapToDouble(Double::doubleValue).toArray();
            cd.valHist = c.valuationHistory.stream().mapToDouble(Double::doubleValue).toArray();
            cd.cashHist = c.cashHistory.stream().mapToDouble(Double::doubleValue).toArray();
            cd.aiStyle = c.aiStyle; cd.rateAdd = c.rateAdd; cd.lastRenew = c.lastRenewMwh; cd.neutral = c.neutral;
            d.companies.add(cd);
        }
        for (City c : s.cities) {
            CityD cd = new CityD(); cd.id = c.id; cd.x = c.x; cd.y = c.y; cd.name = c.name; cd.pop = c.population; cd.sat = c.satisfaction;
            d.cities.add(cd);
        }
        for (Plant p : s.plants) {
            PlantD pd = new PlantD(); pd.id = p.id; pd.x = p.x; pd.y = p.y; pd.type = p.type.name();
            pd.owner = s.companies.indexOf(p.owner); pd.buildLeft = p.buildHoursLeft; pd.outage = p.outageHoursLeft;
            pd.builtAt = p.builtAtHour; pd.lifetimeMWh = p.lifetimeMWh; pd.lastMonthProfit = p.lastMonthProfit; pd.monthProfit = p.monthProfit;
            pd.legacy = p.legacy; pd.retireYear = p.retireYear; pd.retireMonth = p.retireMonth; pd.customName = p.customName;
            pd.equipment = p.equipment.id; pd.ageStart = p.ageStartHour; pd.upgradeLeft = p.upgradeHoursLeft;
            pd.pendingUpgrade = p.pendingUpgrade == null ? null : p.pendingUpgrade.name();
            pd.upgrades = p.upgrades.stream().map(Enum::name).toArray(String[]::new);
            d.plants.add(pd);
        }
        for (PowerLine l : s.lines) {
            LineD ld = new LineD(); ld.id = l.id; ld.a = l.a.id; ld.b = l.b.id; ld.owner = s.companies.indexOf(l.owner); ld.cost = l.buildCost;
            d.lines.add(ld);
        }
        d.monthlyPrice = s.history.monthlyPrice.stream().mapToDouble(Double::doubleValue).toArray();
        return d;
    }

    public GameState restore() {
        GameSettings g = new GameSettings();
        g.seed = seed;
        if (preset != null) g.preset = com.energytycoon.world.MapPreset.valueOf(preset);
        if (difficulty != null) g.difficulty = Difficulty.valueOf(difficulty);
        if (scenario != null) g.scenarioId = scenario;
        if (companyName != null) g.companyName = companyName;
        if (size > 0) g.size = size;
        if (cityCount > 0) g.cities = cityCount;
        int fileVersion = effectiveVersion();
        g.rivals = fileVersion >= 2 ? rivals : 2;
        // v1/v2 kayitlarinda rakipler bedava filoyla baslamisti; ayar bu oyunu tarif etsin.
        g.rivalsStartWithFleet = fileVersion >= 3 ? rivalsStartWithFleet : true;
        GameState s = new GameState(g);
        s.hour = hour; s.nextId = nextId; s.won = won;
        s.markets.coalMult = coalMult; s.markets.gasMult = gasMult; s.markets.uraniumMult = uraniumMult;
        s.markets.carbonGrowthMult = carbonGrowthMult; s.lastServedRatio = lastServedRatio;
        if (supplierIndex != null) for (Supplier sp : s.catalog.allSuppliers()) {
            Double v = supplierIndex.get(sp.id);
            if (v != null) sp.priceIndex = v;
        }
        s.markets.coalPrice = coal; s.markets.gasPrice = gas; s.markets.uraniumPrice = uranium; s.markets.carbonPrice = carbon;
        s.markets.wind = wind; s.markets.cloud = cloud; s.markets.demandNoise = demandNoise;
        for (CompanyD cd : companies) {
            Company c = new Company(cd.name, cd.player, cd.color, cd.cash);
            c.debt = cd.debt; c.valuation = cd.valuation; c.stockPrice = cd.stockPrice; c.bankrupt = cd.bankrupt; c.negativeMonths = cd.negativeMonths;
            c.lastRevenue = cd.lastRevenue; c.lastFuel = cd.lastFuel; c.lastOm = cd.lastOm; c.lastCarbon = cd.lastCarbon;
            c.lastInterest = cd.lastInterest; c.lastFines = cd.lastFines; c.lastNet = cd.lastNet; c.lastMwhGenerated = cd.lastMwh; c.lastCo2 = cd.lastCo2;
            if (cd.netHistory != null) for (double v : cd.netHistory) c.netHistory.add(v);
            if (cd.valHist != null) for (double v : cd.valHist) c.valuationHistory.add(v);
            if (cd.cashHist != null) for (double v : cd.cashHist) c.cashHistory.add(v);
            if (cd.aiStyle != null) c.aiStyle = cd.aiStyle;
            else if (!c.player) c.aiStyle = c.name.startsWith("Yeşil") ? "green" : "fossil";
            c.neutral = cd.neutral;
            c.rateAdd = cd.rateAdd; c.lastRenewMwh = cd.lastRenew;
            s.companies.add(c);
            if (c.player) s.player = c;
            if (c.neutral) s.neutral = c;
        }
        for (CityD cd : cities) {
            City c = new City(); c.id = cd.id; c.x = cd.x; c.y = cd.y; c.name = cd.name; c.population = cd.pop; c.satisfaction = cd.sat;
            s.map.flatten(c.x, c.y, 1);
            s.cities.add(c); s.nodeById.put(c.id, c);
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) s.occ[s.map.idx(c.x + dx, c.y + dy)] = c.id;
        }
        for (PlantD pd : plants) {
            Plant p = new Plant(); p.id = pd.id; p.x = pd.x; p.y = pd.y; p.type = PlantType.valueOf(pd.type);
            p.owner = s.companies.get(pd.owner); p.buildHoursLeft = pd.buildLeft; p.outageHoursLeft = pd.outage;
            p.builtAtHour = pd.builtAt; p.lifetimeMWh = pd.lifetimeMWh; p.lastMonthProfit = pd.lastMonthProfit; p.monthProfit = pd.monthProfit;
            Equipment eq = pd.equipment == null ? null : s.catalog.get(pd.equipment);
            p.equipment = eq != null ? eq : s.catalog.defaultFor(p.type);
            p.ageStartHour = pd.ageStart == Long.MIN_VALUE ? p.builtAtHour : pd.ageStart;
            if (pd.upgrades != null) for (String u : pd.upgrades) p.upgrades.add(Upgrade.valueOf(u));
            if (pd.pendingUpgrade != null) { p.pendingUpgrade = Upgrade.valueOf(pd.pendingUpgrade); p.upgradeHoursLeft = pd.upgradeLeft; }
            p.legacy = pd.legacy; p.retireYear = pd.retireYear; p.retireMonth = pd.retireMonth; p.customName = pd.customName;
            s.map.ensureLand(p.x, p.y);
            s.plants.add(p); s.nodeById.put(p.id, p); s.occ[s.map.idx(p.x, p.y)] = p.id;
        }
        for (LineD ld : lines) {
            GridNode a = s.nodeById.get(ld.a), b = s.nodeById.get(ld.b);
            if (a == null || b == null) continue;
            PowerLine l = new PowerLine(); l.id = ld.id; l.a = a; l.b = b; l.owner = s.companies.get(ld.owner); l.buildCost = ld.cost;
            l.lengthTiles = s.lineLength(a, b);
            s.lines.add(l); a.lines.add(l); b.lines.add(l);
        }
        if (monthlyPrice != null) for (double v : monthlyPrice) s.history.monthlyPrice.add(v);
        s.rng.setSeed(seed ^ hour * 31);
        s.updateValuations();
        return s;
    }
}
