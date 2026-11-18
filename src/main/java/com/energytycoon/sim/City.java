package com.energytycoon.sim;

public final class City extends GridNode {
    public String name;
    public double population;
    /** 0..1 son donemlerde karsilanan talep orani (EMA). */
    public double satisfaction = 1.0;
    public double demandMW;
    public double servedMW;

    @Override public String name() { return name; }

    public double peakMW() { return population * Balance.MW_PER_CAPITA; }
    /** Gorsel boyut kademesi 1..6 */
    public int tier() { return Math.max(1, Math.min(6, (int) (population / 180_000) + 1)); }
}
