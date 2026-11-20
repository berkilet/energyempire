package com.energytycoon.sim;

/** Iletim hatti. owner == null ise hat sebeke isletmecisine (UŞİ) aittir. */
public final class PowerLine {
    public int id;
    public GridNode a, b;
    public Company owner;
    public LineTier tier = LineTier.YG;
    public LineStatus status = LineStatus.ACTIVE;
    public int buildHoursLeft;
    public long builtAtHour;
    /** Isletmeciye basvurup hattin kurulmasini isteyen sirket (yillik baglanti bedelini oder). */
    public Company requester;
    /** Yillik baglanti bedeli ($/yil), requester'a aylik tahakkuk eder. */
    public double annualCharge;
    public int outageHoursLeft;
    public int saturatedHoursThisYear, saturatedHoursLastYear;
    public double lengthTiles;
    public double buildCost;

    public GridNode other(GridNode n) { return n == a ? b : a; }
    public boolean operator() { return owner == null; }
    public double capacityMW() { return tier.capacityMW; }
    /** Akim tasiyabilir mi: aktif ve arizada degil. */
    public boolean usable() { return status == LineStatus.ACTIVE && outageHoursLeft <= 0; }
    public double utilization() { return Math.max(a.utilization(), b.utilization()); }
    public double omPerHour() { return lengthTiles * tier.omPerTileYear / Balance.HOURS_PER_YEAR; }
    /** Bu hat uzerindeki kayip orani. */
    public double lossFraction() { return lengthTiles * tier.lossPerTile; }
    public String ownerName() { return owner == null ? GridBalance.OPERATOR_NAME : owner.name; }

    public double bookValue(long nowHour) {
        double age = Math.max(0, (nowHour - builtAtHour) / (double) Balance.HOURS_PER_YEAR);
        return buildCost * Math.max(GridBalance.LINE_BOOK_FLOOR, 1 - age / GridBalance.LINE_LIFE_YEARS);
    }
}
