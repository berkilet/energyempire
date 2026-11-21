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
    public static class CityD { public int id, x, y; public String name; public double pop, sat;
        /** ihale takibi */ public int lowSatMonths; public long lastTenderHour = Long.MIN_VALUE / 4; public double lastServedRatio = 1; }
    public static class BidD { public int company; public double price, deposit; public long hour; }
    public static class TenderD {
        public int id, city, winner = -1, plantIdMark; public double mw, price, deposit, paidRevenue, paidMWh;
        public long opened, close, awarded, commissionDeadline, contractEnd; public String status;
        public List<BidD> bids = new ArrayList<>();
    }
    public static class PlantD { public int id, x, y, owner, buildLeft, outage; public String type; public long builtAt; public double lifetimeMWh, lastMonthProfit, monthProfit;
        public String equipment, pendingUpgrade; public String[] upgrades; public int upgradeLeft; public long ageStart = Long.MIN_VALUE;
        /** v3 */ public boolean legacy; public int retireYear, retireMonth; public String customName;
        /** v3 Faz 4 */ public int preferredTm;
        /** ihale */ public int tenderId;
        /** varliklar */ public double[] dailyMWh, dailyCurt; public int dayIdx, daysTracked; public double yearProfit; }
    /** Trafo merkezi (v3, Faz 4). */
    public static class SubstationD {
        public int id, x, y, owner, level = 1, buildHoursLeft, pendingUpgrade, upgradeHoursLeft;
        public long builtAtHour; public double transformerMW = GridBalance.TM154_MW, buildCost, accessFee = GridBalance.ACCESS_FEE_DEFAULT;
        public boolean openAccess = true;
    }
    /** owner: sirket indeksi, -1 = isletmeci (UŞİ). tier/status null ise YG/ACTIVE (v2 kayitlari). */
    public static class LineD {
        public int id, a, b, owner; public double cost;
        public String tier, status;
        public long builtAtHour; public int buildHoursLeft, requesterId = -1, outageHoursLeft, saturatedHoursThisYear, saturatedHoursLastYear;
        public double annualCharge;
        /** Faz 5 */ public long appliedAtHour; public double contribution; public boolean accelerated, operatorPlan; public int holdHours;
        /** Faz 6 */ public String upgradeTo; public double upgradeCost;
    }

    /** v3: kamu eski filosu (neutral sirket, legacy santral alanlari), rivalsStartWithFleet. v1/v2 okunabilir. */
    public static final int CURRENT_VERSION = 3;
    /**
     * 0 = alan yok (eski kayit). LibGDX Json varsayilan degere esit alanlari yazmadigi icin v1/v2 kayitlarinda
     * bu alan dosyada bulunmaz; v1 ile v2, v2'ye ozgu 'preset' alaniyla ayirt edilir.
     */
    public int version = 0;
    public boolean rivalsStartWithFleet;
    public boolean operatorGrowth = true, legacyFleet = true;
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
    public List<SubstationD> substations = new ArrayList<>();
    public List<TenderD> tenders = new ArrayList<>();
    public List<ListingD> listings = new ArrayList<>();
    /** Makro (finans paketi). Yoksa (eski kayit) varsayilan ortamla baslatilir. */
    public MacroD macro;
    public static class MacroD {
        public double policyRate = FinanceBalance.POLICY_START, equityIndex = 100; public String regime, expectedRegime;
        public String[] future; public String[] eventTypes; public int[] eventMonths; public int lastCrisisYear = -99, lastInflationYear = -99;
        public boolean energyHikeApplied; public double[] rateHistory, indexHistory, expectedPath;
    }
    public static class ListingD { public int id, asset, seller = -1, buyer = -1; public double price; public long listed, expires; public String status; }
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
        d.rivalsStartWithFleet = s.settings.rivalsStartWithFleet; d.operatorGrowth = s.settings.operatorGrowth; d.legacyFleet = s.settings.legacyFleet;
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
            cd.lowSatMonths = c.lowSatMonths; cd.lastTenderHour = c.lastTenderHour; cd.lastServedRatio = c.lastServedRatio;
            d.cities.add(cd);
        }
        for (Plant p : s.plants) {
            PlantD pd = new PlantD(); pd.id = p.id; pd.x = p.x; pd.y = p.y; pd.type = p.type.name();
            pd.owner = s.companies.indexOf(p.owner); pd.buildLeft = p.buildHoursLeft; pd.outage = p.outageHoursLeft;
            pd.builtAt = p.builtAtHour; pd.lifetimeMWh = p.lifetimeMWh; pd.lastMonthProfit = p.lastMonthProfit; pd.monthProfit = p.monthProfit;
            pd.legacy = p.legacy; pd.retireYear = p.retireYear; pd.retireMonth = p.retireMonth; pd.customName = p.customName; pd.preferredTm = p.preferredTm; pd.tenderId = p.tenderId;
            pd.dailyMWh = p.dailyMWh; pd.dailyCurt = p.dailyCurtailMWh; pd.dayIdx = p.dayIdx; pd.daysTracked = p.daysTracked; pd.yearProfit = p.yearProfit;
            pd.equipment = p.equipment.id; pd.ageStart = p.ageStartHour; pd.upgradeLeft = p.upgradeHoursLeft;
            pd.pendingUpgrade = p.pendingUpgrade == null ? null : p.pendingUpgrade.name();
            pd.upgrades = p.upgrades.stream().map(Enum::name).toArray(String[]::new);
            d.plants.add(pd);
        }
        for (Substation t : s.substations) {
            SubstationD td = new SubstationD(); td.id = t.id; td.x = t.x; td.y = t.y; td.owner = t.owner == null ? -1 : s.companies.indexOf(t.owner);
            td.level = t.level; td.buildHoursLeft = t.buildHoursLeft; td.pendingUpgrade = t.pendingUpgrade; td.upgradeHoursLeft = t.upgradeHoursLeft;
            td.builtAtHour = t.builtAtHour; td.transformerMW = t.transformerMW; td.buildCost = t.buildCost; td.accessFee = t.accessFeePerMWh;
            td.openAccess = t.openAccess;
            d.substations.add(td);
        }
        for (Tender t : s.tenders.all) {
            TenderD td = new TenderD(); td.id = t.id; td.city = t.city.id; td.mw = t.mw; td.opened = t.openedHour; td.close = t.closeHour;
            td.status = t.status.name(); td.winner = t.winner == null ? -1 : s.companies.indexOf(t.winner); td.price = t.price; td.deposit = t.deposit;
            td.awarded = t.awardedHour; td.commissionDeadline = t.commissionDeadline; td.contractEnd = t.contractEndHour; td.plantIdMark = t.plantIdMark;
            td.paidRevenue = t.paidRevenue; td.paidMWh = t.paidMWh;
            for (Tender.Bid b : t.bids) { BidD bd = new BidD(); bd.company = s.companies.indexOf(b.company); bd.price = b.price; bd.deposit = b.deposit; bd.hour = b.hour; td.bids.add(bd); }
            d.tenders.add(td);
        }
        MacroD md = new MacroD(); Macro mc = s.macro;
        md.policyRate = mc.policyRate; md.equityIndex = mc.equityIndex; md.regime = mc.regime.name(); md.expectedRegime = mc.expectedRegime.name();
        md.future = mc.future.stream().map(Enum::name).toArray(String[]::new);
        md.eventTypes = mc.events.stream().map(e -> e.type.name()).toArray(String[]::new);
        md.eventMonths = mc.events.stream().mapToInt(e -> e.monthsLeft).toArray();
        md.lastCrisisYear = mc.lastCrisisYear; md.lastInflationYear = mc.lastInflationYear; md.energyHikeApplied = mc.energyHikeApplied;
        md.rateHistory = mc.rateHistory.stream().mapToDouble(Double::doubleValue).toArray();
        md.indexHistory = mc.indexHistory.stream().mapToDouble(Double::doubleValue).toArray();
        md.expectedPath = mc.expectedPath.clone();
        d.macro = md;
        for (Listing l : s.sales.all) {
            ListingD ld = new ListingD(); ld.id = l.id; ld.asset = l.asset.id; ld.seller = s.companies.indexOf(l.seller);
            ld.buyer = l.buyer == null ? -1 : s.companies.indexOf(l.buyer); ld.price = l.price; ld.listed = l.listedHour; ld.expires = l.expiresHour; ld.status = l.status.name();
            d.listings.add(ld);
        }
        for (PowerLine l : s.lines) {
            LineD ld = new LineD(); ld.id = l.id; ld.a = l.a.id; ld.b = l.b.id; ld.owner = l.owner == null ? -1 : s.companies.indexOf(l.owner); ld.cost = l.buildCost;
            ld.tier = l.tier.name(); ld.status = l.status.name(); ld.buildHoursLeft = l.buildHoursLeft; ld.builtAtHour = l.builtAtHour;
            ld.requesterId = l.requester == null ? -1 : s.companies.indexOf(l.requester); ld.annualCharge = l.annualCharge;
            ld.outageHoursLeft = l.outageHoursLeft; ld.saturatedHoursThisYear = l.saturatedHoursThisYear; ld.saturatedHoursLastYear = l.saturatedHoursLastYear;
            ld.appliedAtHour = l.appliedAtHour; ld.contribution = l.contribution; ld.accelerated = l.accelerated; ld.operatorPlan = l.operatorPlan; ld.holdHours = l.holdHours;
            ld.upgradeTo = l.upgradeTo == null ? null : l.upgradeTo.name(); ld.upgradeCost = l.upgradeCost;
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
        g.operatorGrowth = operatorGrowth; g.legacyFleet = legacyFleet;
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
            if (c.neutral) c.colorRgb = GridBalance.NEUTRAL_COLOR; // eski kayitlarda gri

            c.rateAdd = cd.rateAdd; c.lastRenewMwh = cd.lastRenew;
            s.companies.add(c);
            if (c.player) s.player = c;
            if (c.neutral) s.neutral = c;
        }
        for (CityD cd : cities) {
            City c = new City(); c.id = cd.id; c.x = cd.x; c.y = cd.y; c.name = cd.name; c.population = cd.pop; c.satisfaction = cd.sat;
            c.lowSatMonths = cd.lowSatMonths; c.lastTenderHour = cd.lastTenderHour; c.lastServedRatio = cd.lastServedRatio;
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
            p.legacy = pd.legacy; p.retireYear = pd.retireYear; p.retireMonth = pd.retireMonth; p.customName = pd.customName; p.preferredTm = pd.preferredTm; p.tenderId = pd.tenderId;
            if (pd.dailyMWh != null && pd.dailyMWh.length == 30) p.dailyMWh = pd.dailyMWh;
            if (pd.dailyCurt != null && pd.dailyCurt.length == 30) p.dailyCurtailMWh = pd.dailyCurt;
            p.dayIdx = pd.dayIdx; p.daysTracked = pd.daysTracked; p.yearProfit = pd.yearProfit;
            s.map.ensureLand(p.x, p.y);
            s.plants.add(p); s.nodeById.put(p.id, p); s.occ[s.map.idx(p.x, p.y)] = p.id;
        }
        if (substations != null) for (SubstationD td : substations) {
            Substation t = new Substation(); t.id = td.id; t.x = td.x; t.y = td.y; t.owner = td.owner < 0 ? null : s.companies.get(td.owner);
            t.level = td.level; t.buildHoursLeft = td.buildHoursLeft; t.pendingUpgrade = td.pendingUpgrade; t.upgradeHoursLeft = td.upgradeHoursLeft;
            t.builtAtHour = td.builtAtHour; t.transformerMW = td.transformerMW; t.buildCost = td.buildCost; t.accessFeePerMWh = td.accessFee;
            t.openAccess = td.openAccess;
            s.map.ensureLand(t.x, t.y);
            s.substations.add(t); s.nodeById.put(t.id, t); s.occ[s.map.idx(t.x, t.y)] = t.id;
        }
        for (LineD ld : lines) {
            GridNode a = s.nodeById.get(ld.a), b = s.nodeById.get(ld.b);
            if (a == null || b == null) continue;
            PowerLine l = new PowerLine(); l.id = ld.id; l.a = a; l.b = b; l.owner = ld.owner < 0 ? null : s.companies.get(ld.owner); l.buildCost = ld.cost;
            if (l.owner != null && l.owner.neutral) l.owner = null; // kamu hatlari isletmeciye (UŞİ) aittir
            l.tier = ld.tier == null ? LineTier.YG : LineTier.valueOf(ld.tier);
            l.status = ld.status == null ? LineStatus.ACTIVE : LineStatus.valueOf(ld.status);
            l.buildHoursLeft = ld.buildHoursLeft; l.builtAtHour = ld.builtAtHour; l.annualCharge = ld.annualCharge;
            l.requester = ld.requesterId < 0 ? null : s.companies.get(ld.requesterId);
            l.outageHoursLeft = ld.outageHoursLeft; l.saturatedHoursThisYear = ld.saturatedHoursThisYear; l.saturatedHoursLastYear = ld.saturatedHoursLastYear;
            l.appliedAtHour = ld.appliedAtHour; l.contribution = ld.contribution; l.accelerated = ld.accelerated; l.operatorPlan = ld.operatorPlan; l.holdHours = ld.holdHours;
            l.upgradeTo = ld.upgradeTo == null ? null : LineTier.valueOf(ld.upgradeTo); l.upgradeCost = ld.upgradeCost;
            l.lengthTiles = s.lineLength(a, b);
            s.lines.add(l); a.lines.add(l); b.lines.add(l);
        }
        if (tenders != null) for (TenderD td : tenders) {
            if (!(s.nodeById.get(td.city) instanceof City city)) continue;
            Tender t = new Tender(); t.id = td.id; t.city = city; t.mw = td.mw; t.openedHour = td.opened; t.closeHour = td.close;
            t.status = Tender.Status.valueOf(td.status); t.winner = td.winner < 0 ? null : s.companies.get(td.winner); t.price = td.price; t.deposit = td.deposit;
            t.awardedHour = td.awarded; t.commissionDeadline = td.commissionDeadline; t.contractEndHour = td.contractEnd; t.plantIdMark = td.plantIdMark;
            t.paidRevenue = td.paidRevenue; t.paidMWh = td.paidMWh;
            if (td.bids != null) for (BidD bd : td.bids) { Tender.Bid b = new Tender.Bid(); b.company = s.companies.get(bd.company); b.price = bd.price; b.deposit = bd.deposit; b.hour = bd.hour; t.bids.add(b); }
            s.tenders.add(t);
        }
        if (listings != null) for (ListingD ld : listings) {
            GridNode n = s.nodeById.get(ld.asset);
            if (n == null || ld.seller < 0) continue;
            Listing l = new Listing(); l.id = ld.id; l.asset = n; l.seller = s.companies.get(ld.seller);
            l.buyer = ld.buyer < 0 ? null : s.companies.get(ld.buyer); l.price = ld.price; l.listedHour = ld.listed; l.expiresHour = ld.expires;
            l.status = Listing.Status.valueOf(ld.status);
            s.sales.add(l);
        }
        if (macro != null && macro.regime != null) {
            Macro mc = s.macro;
            mc.policyRate = macro.policyRate; mc.equityIndex = macro.equityIndex; mc.regime = Macro.Regime.valueOf(macro.regime);
            if (macro.expectedRegime != null) mc.expectedRegime = Macro.Regime.valueOf(macro.expectedRegime);
            if (macro.future != null) for (String r : macro.future) mc.future.add(Macro.Regime.valueOf(r));
            while (mc.future.size() < 12) mc.future.add(mc.regime);
            if (macro.eventTypes != null) for (int i = 0; i < macro.eventTypes.length; i++) {
                Macro.Event e = new Macro.Event(); e.type = Macro.EventType.valueOf(macro.eventTypes[i]); e.monthsLeft = macro.eventMonths[i]; mc.events.add(e);
            }
            mc.lastCrisisYear = macro.lastCrisisYear; mc.lastInflationYear = macro.lastInflationYear; mc.energyHikeApplied = macro.energyHikeApplied;
            if (macro.rateHistory != null) for (double v : macro.rateHistory) mc.rateHistory.add(v);
            if (macro.indexHistory != null) for (double v : macro.indexHistory) mc.indexHistory.add(v);
            if (macro.expectedPath != null && macro.expectedPath.length == 12) mc.expectedPath = macro.expectedPath;
        } else s.macro.init(); // eski kayit: varsayilan makro ortam
        if (monthlyPrice != null) for (double v : monthlyPrice) s.history.monthlyPrice.add(v);
        s.rng.setSeed(seed ^ hour * 31);
        s.updateValuations();
        return s;
    }
}
