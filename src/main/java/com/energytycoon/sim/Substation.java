package com.energytycoon.sim;

/**
 * Trafo merkezi (TM): yaricapi icindeki ayni sirket santrallerini kayipsiz toplayici hatlarla tek bir
 * baglanti noktasinda birlestirir. Ihracat kapasitesi = min(trafo gucu, bagli hatlar).
 * level 1 = TM154, 2 = TM380.
 */
public final class Substation extends GridNode {
    public Company owner;
    public int level = 1;
    public double transformerMW = GridBalance.TM154_MW;
    public double radius = GridBalance.TM_RADIUS;
    public int buildHoursLeft;
    public long builtAtHour;
    public double buildCost;
    /** Ucuncu taraf erisimi acik mi ve bedeli ($/MWh). */
    public boolean openAccess = true;
    public double accessFeePerMWh = GridBalance.ACCESS_FEE_DEFAULT;
    /** Devam eden yukseltme: 0 yok, 1 = +600 MW trafo, 2 = TM154 -> TM380. TM calismaya devam eder. */
    public int pendingUpgrade;
    public int upgradeHoursLeft;
    /** Bu ayki erisim geliri (gosterim icin). */
    public double accessIncomeMonth, lastAccessIncome;

    /** Baglanti noktasi (topoloji kurulunca atanir). */
    public transient ConnectionPoint cp;

    public boolean operational() { return buildHoursLeft <= 0; }

    public String levelLabel() { return level >= 2 ? "TM380" : "TM154"; }

    @Override public String name() { return levelLabel() + " #" + id; }

    public boolean inRange(int px, int py) { return Math.hypot(px - x, py - y) <= radius + 1e-6; }

    public double bookValue(long nowHour) {
        if (!operational()) return buildCost;
        double age = Math.max(0, (nowHour - builtAtHour) / (double) Balance.HOURS_PER_YEAR);
        return buildCost * Math.max(GridBalance.LINE_BOOK_FLOOR, 1 - age / GridBalance.LINE_LIFE_YEARS);
    }

    @Override public double utilization() { return cp == null ? 0 : cp.utilization(); }
}
