package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.List;

/** Kayit icin duz veri modeli (LibGDX Json ile serilestirilir). */
public class SaveData {
    /** Finans (kredi notu, bankalar, krediler). Yoksa (eski kayit) cd.debt Kiyibank ticari kredisine donusturulur. */
    public static class LoanD {
        public int id, linkedPlant, tenorMonths, graceMonths, frequencyMonths, covenantBreaches, daysLate, onTimePayments, dscrLowQuarters;
        public String type, bank, ecaOrigin, status; public boolean fixed, everLate, covenantPenalty, dscrPenalty, restructured, acquisition;
        public double principal, outstanding, rate, spread, accrued, dueAmount, demand, pendingPrincipal, limit;
        public long startHour, nextDueHour, maturityHour, graceEndHour, lastResetHour, drawnSinceHour = -1;
    }
    public static class FinD {
        public String rating = "BB"; public boolean lateNotch, dividendBan, restructUsed, restructOfferPending;
        public int quarters, plantsAtRestruct, lateEvents, defaults, restructures;
        public double qEbitdaAcc, qInterestAcc, qRenewAcc, qGenAcc, fees, lastFees;
        public double[] qEbitda, qInterest, qRenew, qGen; public int[] bankScore, bankBreachQuarters; public long[] bankBanUntil; public boolean[] bankCovenantBreach;
        public long defaultCapUntil, restructProtectUntil, mustSellBy, restructOfferHour, negativeSinceHour = -1;
        public int plantsSold, soldAtRestruct = -1;
        public List<LoanD> loans = new ArrayList<>();

        static FinD from(Company c, GameState s) {
            FinD f = new FinD();
            f.rating = c.rating.name(); f.lateNotch = c.lateNotch; f.dividendBan = c.dividendBan; f.restructUsed = c.restructUsed; f.restructOfferPending = c.restructOfferPending;
            f.quarters = c.quarters; f.plantsAtRestruct = c.plantsAtRestruct; f.lateEvents = c.lateEvents; f.defaults = c.defaults; f.restructures = c.restructures;
            f.qEbitdaAcc = c.qEbitdaAcc; f.qInterestAcc = c.qInterestAcc; f.qRenewAcc = c.qRenewAcc; f.qGenAcc = c.qGenAcc; f.fees = c.fees; f.lastFees = c.lastFees;
            f.qEbitda = c.qEbitda; f.qInterest = c.qInterest; f.qRenew = c.qRenew; f.qGen = c.qGen; f.bankScore = c.bankScore; f.bankBreachQuarters = c.bankBreachQuarters;
            f.bankBanUntil = c.bankBanUntil; f.bankCovenantBreach = c.bankCovenantBreach;
            f.defaultCapUntil = c.defaultCapUntil; f.restructProtectUntil = c.restructProtectUntil; f.mustSellBy = c.mustSellBy; f.restructOfferHour = c.restructOfferHour; f.negativeSinceHour = c.negativeSinceHour; f.plantsSold = c.plantsSold; f.soldAtRestruct = c.soldAtRestruct;
            for (Loan l : c.loans) {
                LoanD d = new LoanD();
                d.id = l.id; d.linkedPlant = l.linkedPlant; d.tenorMonths = l.tenorMonths; d.graceMonths = l.graceMonths; d.frequencyMonths = l.frequencyMonths;
                d.covenantBreaches = l.covenantBreaches; d.daysLate = l.daysLate; d.onTimePayments = l.onTimePayments; d.dscrLowQuarters = l.dscrLowQuarters;
                d.type = l.type.name(); d.bank = l.bank.name(); d.ecaOrigin = l.ecaOrigin; d.status = l.status.name(); d.fixed = l.fixed; d.everLate = l.everLate;
                d.covenantPenalty = l.covenantPenalty; d.dscrPenalty = l.dscrPenalty; d.restructured = l.restructured; d.acquisition = l.acquisition;
                d.principal = l.principal; d.outstanding = l.outstanding; d.rate = l.rate; d.spread = l.spread; d.accrued = l.accrued; d.dueAmount = l.dueAmount;
                d.demand = l.demand; d.pendingPrincipal = l.pendingPrincipal; d.limit = l.limit;
                d.startHour = l.startHour; d.nextDueHour = l.nextDueHour; d.maturityHour = l.maturityHour; d.graceEndHour = l.graceEndHour; d.lastResetHour = l.lastResetHour; d.drawnSinceHour = l.drawnSinceHour;
                f.loans.add(d);
            }
            return f;
        }

        void apply(Company c) {
            c.rating = Rating.valueOf(rating); c.lateNotch = lateNotch; c.dividendBan = dividendBan; c.restructUsed = restructUsed; c.restructOfferPending = restructOfferPending;
            c.quarters = quarters; c.plantsAtRestruct = plantsAtRestruct; c.lateEvents = lateEvents; c.defaults = defaults; c.restructures = restructures;
            c.qEbitdaAcc = qEbitdaAcc; c.qInterestAcc = qInterestAcc; c.qRenewAcc = qRenewAcc; c.qGenAcc = qGenAcc; c.fees = fees; c.lastFees = lastFees;
            if (qEbitda != null && qEbitda.length == 4) { c.qEbitda = qEbitda; c.qInterest = qInterest; c.qRenew = qRenew; c.qGen = qGen; }
            if (bankScore != null && bankScore.length == 3) { c.bankScore = bankScore; c.bankBreachQuarters = bankBreachQuarters; c.bankBanUntil = bankBanUntil; c.bankCovenantBreach = bankCovenantBreach; }
            c.defaultCapUntil = defaultCapUntil; c.restructProtectUntil = restructProtectUntil; c.mustSellBy = mustSellBy; c.restructOfferHour = restructOfferHour; c.negativeSinceHour = negativeSinceHour; c.plantsSold = plantsSold; c.soldAtRestruct = soldAtRestruct;
            if (loans != null) for (LoanD d : loans) {
                Loan l = new Loan();
                l.id = d.id; l.linkedPlant = d.linkedPlant; l.tenorMonths = d.tenorMonths; l.graceMonths = d.graceMonths; l.frequencyMonths = Math.max(1, d.frequencyMonths);
                l.covenantBreaches = d.covenantBreaches; l.daysLate = d.daysLate; l.onTimePayments = d.onTimePayments; l.dscrLowQuarters = d.dscrLowQuarters;
                l.type = Loan.Type.valueOf(d.type); l.bank = Bank.valueOf(d.bank); l.ecaOrigin = d.ecaOrigin; l.status = Loan.Status.valueOf(d.status); l.fixed = d.fixed; l.everLate = d.everLate;
                l.covenantPenalty = d.covenantPenalty; l.dscrPenalty = d.dscrPenalty; l.restructured = d.restructured; l.acquisition = d.acquisition;
                l.principal = d.principal; l.outstanding = d.outstanding; l.rate = d.rate; l.spread = d.spread; l.accrued = d.accrued; l.dueAmount = d.dueAmount;
                l.demand = d.demand; l.pendingPrincipal = d.pendingPrincipal; l.limit = d.limit;
                l.startHour = d.startHour; l.nextDueHour = d.nextDueHour; l.maturityHour = d.maturityHour; l.graceEndHour = d.graceEndHour; l.lastResetHour = d.lastResetHour; l.drawnSinceHour = d.drawnSinceHour;
                c.loans.add(l);
            }
        }
    }

    /** Paket 2: birlesme-satin alma ve rekabet kayitlari (yoksa bos). */
    public DealBook deals;
    public static class CompanyD { public FinD fin; public Equity eq; public String name; public boolean player; public int color; public double cash, debt, valuation, stockPrice;
        public boolean bankrupt; public int negativeMonths;
        public double lastRevenue, lastFuel, lastOm, lastCarbon, lastInterest, lastFines, lastNet, lastMwh, lastCo2;
        public double[] netHistory; public double[] valHist, cashHist;
        public String aiStyle; public double rateAdd, lastRenew;
        /** rakip stratejisi */ public double carbonBelief, learnRate, priorWeight, hurdle, hurdleBase, scarcityView = 1, styleStrength = 1; public double[] techView; public String orientation, pendingOrientation;
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
        /** varliklar */ public double[] dailyMWh, dailyCurt; public int dayIdx, daysTracked; public double yearProfit, lastYearProfit, capacityYear, lastYearCapacity;
        /** rekabet: son 12 ay uretim */ public double[] gen12; public int genIdx; public double monthMWh, monthCost, lastMonthMWh, lastMonthCost, priceCap; }
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
    /** kapasite mekanizmasi */ public double[] peak12, peakAvail12; public double monthPeakAvail, monthPeakMW, capacityPrice, capacityMargin = 0.2;
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
        d.deals = s.deals;
        d.preset = s.settings.preset.name(); d.difficulty = s.settings.difficulty.name(); d.scenario = s.settings.scenarioId;
        d.companyName = s.settings.companyName; d.size = s.settings.size; d.cityCount = s.settings.cities; d.rivals = s.settings.rivals;
        d.rivalsStartWithFleet = s.settings.rivalsStartWithFleet; d.operatorGrowth = s.settings.operatorGrowth; d.legacyFleet = s.settings.legacyFleet;
        d.coalMult = s.markets.coalMult; d.gasMult = s.markets.gasMult; d.uraniumMult = s.markets.uraniumMult;
        d.carbonGrowthMult = s.markets.carbonGrowthMult; d.lastServedRatio = s.lastServedRatio;
        for (Supplier sp : s.catalog.allSuppliers()) d.supplierIndex.put(sp.id, sp.priceIndex);
        d.peak12 = s.peak12; d.peakAvail12 = s.peakAvail12; d.monthPeakAvail = s.monthPeakAvail; d.monthPeakMW = s.monthPeakMW; d.capacityPrice = s.capacityPrice; d.capacityMargin = s.capacityMargin;
        d.coal = s.markets.coalPrice; d.gas = s.markets.gasPrice; d.uranium = s.markets.uraniumPrice; d.carbon = s.markets.carbonPrice;
        d.wind = s.markets.wind; d.cloud = s.markets.cloud; d.demandNoise = s.markets.demandNoise;
        for (Company c : s.companies) {
            CompanyD cd = new CompanyD();
            cd.name = c.name; cd.player = c.player; cd.color = c.colorRgb; cd.cash = c.cash; cd.debt = 0; // eski alan: borc artik kredilerde
            cd.fin = FinD.from(c, s);
            cd.eq = c.eq;
            cd.valuation = c.valuation; cd.stockPrice = c.stockPrice; cd.bankrupt = c.bankrupt; cd.negativeMonths = c.negativeMonths;
            cd.lastRevenue = c.lastRevenue; cd.lastFuel = c.lastFuel; cd.lastOm = c.lastOm; cd.lastCarbon = c.lastCarbon;
            cd.lastInterest = c.lastInterest; cd.lastFines = c.lastFines; cd.lastNet = c.lastNet; cd.lastMwh = c.lastMwhGenerated; cd.lastCo2 = c.lastCo2;
            cd.netHistory = c.netHistory.stream().mapToDouble(Double::doubleValue).toArray();
            cd.valHist = c.valuationHistory.stream().mapToDouble(Double::doubleValue).toArray();
            cd.cashHist = c.cashHistory.stream().mapToDouble(Double::doubleValue).toArray();
            cd.aiStyle = c.aiStyle; cd.lastRenew = c.lastRenewMwh; cd.neutral = c.neutral;
            cd.hurdle = c.hurdle; cd.hurdleBase = c.hurdleBase; cd.scarcityView = c.scarcityView; cd.styleStrength = c.styleStrength; cd.carbonBelief = c.carbonBelief; cd.learnRate = c.learnRate; cd.priorWeight = c.priorWeight; cd.techView = c.techView; cd.orientation = c.orientation; cd.pendingOrientation = c.pendingOrientation;
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
            pd.dailyMWh = p.dailyMWh; pd.dailyCurt = p.dailyCurtailMWh; pd.dayIdx = p.dayIdx; pd.daysTracked = p.daysTracked; pd.yearProfit = p.yearProfit; pd.lastYearProfit = p.lastYearProfit; pd.capacityYear = p.capacityYear; pd.lastYearCapacity = p.lastYearCapacity;
            pd.gen12 = p.gen12; pd.genIdx = p.genIdx; pd.monthMWh = p.monthMWh; pd.monthCost = p.monthCost; pd.lastMonthMWh = p.lastMonthMWh; pd.lastMonthCost = p.lastMonthCost; pd.priceCap = p.priceCap;
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
        java.util.Map<Company, Double> legacyDebt = new java.util.HashMap<>();
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
        if (peak12 != null && peak12.length == 12) s.peak12 = peak12;
        if (peakAvail12 != null && peakAvail12.length == 12) s.peakAvail12 = peakAvail12;
        s.monthPeakAvail = monthPeakAvail;
        s.monthPeakMW = monthPeakMW; s.capacityPrice = capacityPrice; s.capacityMargin = capacityMargin;
        s.markets.coalPrice = coal; s.markets.gasPrice = gas; s.markets.uraniumPrice = uranium; s.markets.carbonPrice = carbon;
        s.markets.wind = wind; s.markets.cloud = cloud; s.markets.demandNoise = demandNoise;
        for (CompanyD cd : companies) {
            Company c = new Company(cd.name, cd.player, cd.color, cd.cash);
            c.valuation = cd.valuation; c.stockPrice = cd.stockPrice; c.bankrupt = cd.bankrupt; c.negativeMonths = cd.negativeMonths;
            if (cd.eq != null) c.eq = cd.eq; // eski kayit: halka kapali, kurucuya %100 ait
            c.lastRevenue = cd.lastRevenue; c.lastFuel = cd.lastFuel; c.lastOm = cd.lastOm; c.lastCarbon = cd.lastCarbon;
            c.lastInterest = cd.lastInterest; c.lastFines = cd.lastFines; c.lastNet = cd.lastNet; c.lastMwhGenerated = cd.lastMwh; c.lastCo2 = cd.lastCo2;
            if (cd.netHistory != null) for (double v : cd.netHistory) c.netHistory.add(v);
            if (cd.valHist != null) for (double v : cd.valHist) c.valuationHistory.add(v);
            if (cd.cashHist != null) for (double v : cd.cashHist) c.cashHistory.add(v);
            if (cd.aiStyle != null) c.aiStyle = cd.aiStyle;
            else if (!c.player) c.aiStyle = c.name.startsWith("Yeşil") ? "green" : "fossil";
            c.neutral = cd.neutral;
            if (cd.techView != null) { c.techView = cd.techView; c.hurdle = cd.hurdle; c.hurdleBase = cd.hurdleBase; c.scarcityView = cd.scarcityView; c.styleStrength = cd.styleStrength; c.carbonBelief = cd.carbonBelief; c.learnRate = cd.learnRate; c.priorWeight = cd.priorWeight; c.orientation = cd.orientation; c.pendingOrientation = cd.pendingOrientation; }
            else if (!c.player && !c.neutral) RivalStrategy.init(c, g.seed);
            if (c.neutral) c.colorRgb = GridBalance.NEUTRAL_COLOR; // eski kayitlarda gri
            if (cd.fin != null) cd.fin.apply(c);
            legacyDebt.put(c, cd.fin == null ? cd.debt : 0.0);

            c.lastRenewMwh = cd.lastRenew;
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
            p.dayIdx = pd.dayIdx; p.daysTracked = pd.daysTracked; p.yearProfit = pd.yearProfit; p.lastYearProfit = pd.lastYearProfit; p.capacityYear = pd.capacityYear; p.lastYearCapacity = pd.lastYearCapacity;
            if (pd.gen12 != null && pd.gen12.length == 12) p.gen12 = pd.gen12;
            p.genIdx = Math.floorMod(pd.genIdx, 12); p.monthMWh = pd.monthMWh; p.monthCost = pd.monthCost; p.lastMonthMWh = pd.lastMonthMWh; p.lastMonthCost = pd.lastMonthCost; p.priceCap = pd.priceCap;
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
        // Eski kayit: tek borc tutari -> Kiyibank'tan 5 yillik, 3 ayda bir taksitli degisken ticari kredi; isletme kredisi limiti.
        for (Company c : s.companies) {
            if (c.neutral) continue;
            double d = legacyDebt.getOrDefault(c, 0.0);
            if (d > 1) s.finance.convertLegacyDebt(c, d);
            if (s.finance.revolver(c) == null && !c.bankrupt) s.finance.openRevolverFree(c, Bank.KIYI);
        }
        // Eski kayit: ECA kredisinde bolge adi (Avrupa, Uzak Dogu...) -> bagli santralin tedarikci ulkesi
        for (Company c : s.companies) for (Loan l : c.loans)
            if (l.type == Loan.Type.ECA && l.ecaOrigin != null && Finance.ecaPremiumRate(l.ecaOrigin) < 0)
                for (Plant p : s.plants) if (p.id == l.linkedPlant && p.equipment != null) { l.ecaOrigin = p.equipment.supplier.origin; break; }
        if (monthlyPrice != null) for (double v : monthlyPrice) s.history.monthlyPrice.add(v);
        s.rng.setSeed(seed ^ hour * 31);
        if (deals != null) s.deals = deals;
        s.updateValuations();
        return s;
    }
}
