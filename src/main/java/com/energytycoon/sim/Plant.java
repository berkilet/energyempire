package com.energytycoon.sim;

import java.util.EnumSet;

public final class Plant extends GridNode {
    public PlantType type;
    public Equipment equipment;
    public final EnumSet<Upgrade> upgrades = EnumSet.noneOf(Upgrade.class);
    public Company owner;
    public int buildHoursLeft;
    public int outageHoursLeft;
    public Upgrade pendingUpgrade;
    public int upgradeHoursLeft;
    public long builtAtHour;
    /** Degradasyon/omur sayaci (repowering vb. sifirlar). */
    public long ageStartHour;
    public double outputMW;
    public double availableMW;
    /** Bu aydaki net kar (gelir - degisken - sabit). */
    public double monthProfit, lastMonthProfit;
    public double lifetimeMWh;
    /** Kamu eski filosu: yakit maliyeti GridBalance.LEGACY_FUEL_MULT ile carpilir, retireYear'da kapanir. */
    public boolean legacy;
    /** Kapanis yili/ayi (0 = kapanis yok); ay 0..11. */
    public int retireYear, retireMonth;
    /** Haritada gosterilen ozel ad (ör. "Bozova Termik"); null ise tur adi. */
    public String customName;

    @Override public String name() { return customName != null ? customName : type.label; }

    public boolean operational() { return buildHoursLeft <= 0; }
    public boolean upgrading() { return pendingUpgrade != null && upgradeHoursLeft > 0; }
    public boolean online() { return operational() && outageHoursLeft <= 0 && !(upgrading() && pendingUpgrade.offline); }

    public double ageYears(long nowHour) { return Math.max(0, (nowHour - builtAtHour) / (double) Balance.HOURS_PER_YEAR); }
    public double wearYears(long nowHour) { return Math.max(0, (nowHour - ageStartHour) / (double) Balance.HOURS_PER_YEAR); }
    public boolean underWarranty(long nowHour) { return ageYears(nowHour) < equipment.supplier.warrantyYears; }

    public double capacityMW() {
        double c = equipment.capacityMW;
        if (upgrades.contains(Upgrade.REPOWER)) c *= 1.15;
        return c;
    }

    /** 1'den kucuk: yaslanma kaybi. */
    public double degradation(long nowHour) {
        double d = equipment.degradationPerYear * (upgrades.contains(Upgrade.ROBOT_CLEAN) ? 0.5 : 1.0);
        return Math.max(0.6, 1 - d * wearYears(nowHour));
    }

    /** Termik verim (yakit MWh -> elektrik MWh). */
    public double efficiency(long nowHour) {
        double e = equipment.efficiency;
        if (upgrades.contains(Upgrade.CCS)) e *= 0.91;
        if (upgrades.contains(Upgrade.GAS_CCS)) e *= 0.92;
        return e * (0.6 + 0.4 * degradation(nowHour));
    }

    public double co2PerMWh(long nowHour) {
        if (type.co2PerMWh <= 0) return 0;
        double c = type.co2PerMWh * type.efficiency / efficiency(nowHour);
        if (upgrades.contains(Upgrade.CCS) || upgrades.contains(Upgrade.GAS_CCS)) c *= 0.15;
        if (upgrades.contains(Upgrade.COFIRE)) c *= 0.70;
        if (upgrades.contains(Upgrade.HYDROGEN)) c *= 0.80;
        return c;
    }

    public double fuelPriceMult() {
        double m = 1;
        if (upgrades.contains(Upgrade.COFIRE)) m *= 1.12;
        if (upgrades.contains(Upgrade.HYDROGEN)) m *= 1.15;
        return m;
    }

    /** Yenilenebilir uretim carpani. */
    public double renewableMult(long nowHour) {
        double m = equipment.outputMult * degradation(nowHour);
        if (upgrades.contains(Upgrade.TRACKER)) m *= 1.18;
        if (upgrades.contains(Upgrade.ROBOT_CLEAN)) m *= 1.04;
        if (upgrades.contains(Upgrade.HYDRO_RETROFIT)) m *= 1.10;
        return m;
    }

    public double fixedOmPerHour() {
        double m = 1;
        if (upgrades.contains(Upgrade.CCS) || upgrades.contains(Upgrade.GAS_CCS)) m += 0.35;
        if (upgrades.contains(Upgrade.TRACKER)) m += 0.12;
        return equipment.fixedOmPerHour() * m * (capacityMW() / equipment.capacityMW);
    }

    public double investedCapex() {
        double v = equipment.listCapex();
        for (Upgrade u : upgrades) v += u.cost(this);
        return v;
    }

    public double bookValue(long nowHour) {
        double depr = Math.min(0.75, ageYears(nowHour) / type.lifeYears);
        return investedCapex() * (1 - depr);
    }

    public boolean canUpgrade(Upgrade u) {
        if (u.type != type || upgrades.contains(u) || upgrading() || !operational()) return false;
        if (u == Upgrade.CCS && upgrades.contains(Upgrade.COFIRE)) return true;
        return true;
    }
}
