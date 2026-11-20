package com.energytycoon.sim;

/** Iletim hatti kademeleri. Sayisal degerler GridBalance'tadir. */
public enum LineTier {
    OG("OG 34,5 kV", "OG"),
    YG("YG 154 kV", "YG"),
    CYG("ÇYG 380 kV", "ÇYG");

    public final String label, shortLabel;
    public final double capacityMW, costPerTile, omPerTileYear, lossPerTile, maxLength, outagePerTileHour;
    public final int baseBuildDays, buildDaysPerTile;

    LineTier(String label, String shortLabel) {
        this.label = label; this.shortLabel = shortLabel;
        int i = ordinal();
        capacityMW = GridBalance.TIER_CAPACITY_MW[i];
        costPerTile = GridBalance.TIER_COST_PER_TILE[i];
        omPerTileYear = GridBalance.TIER_OM_PER_TILE_YEAR[i];
        lossPerTile = GridBalance.TIER_LOSS_PER_TILE[i];
        maxLength = GridBalance.TIER_MAX_LENGTH[i];
        baseBuildDays = GridBalance.TIER_BASE_BUILD_DAYS[i];
        buildDaysPerTile = GridBalance.TIER_BUILD_DAYS_PER_TILE[i];
        outagePerTileHour = GridBalance.TIER_OUTAGE_PER_TILE_HOUR[i];
    }

    /** Kendi kurulumunda insaat suresi (gun). */
    public int buildDays(double lengthTiles) { return (int) Math.round(baseBuildDays + buildDaysPerTile * lengthTiles); }

    public LineTier next() { return values()[(ordinal() + 1) % values().length]; }
    public LineTier up() { return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null; }
}
