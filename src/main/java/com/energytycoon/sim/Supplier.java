package com.energytycoon.sim;

/** Kurgusal ekipman tedarikcisi. Itibar teslim gecikmesini ve ariza oranini belirler. */
public final class Supplier {
    public final String id, name, origin;
    public final double reputation;   // 0..1
    public final int warrantyYears;
    /** Fiyat endeksi (1 = liste fiyati), aylik dalgalanir. */
    public double priceIndex = 1.0;

    public Supplier(String id, String name, String origin, double reputation, int warrantyYears) {
        this.id = id; this.name = name; this.origin = origin; this.reputation = reputation; this.warrantyYears = warrantyYears;
    }

    public double outageMult() { return 0.5 + 1.5 * (1 - reputation); }
    public double delayChance() { return (1 - reputation) * 0.8; }

    public String stars() {
        int n = (int) Math.round(reputation * 5);
        return "★".repeat(Math.max(1, n)) + "☆".repeat(Math.max(0, 5 - n));
    }

    public void monthly(java.util.Random r) {
        double lp = Math.log(priceIndex);
        lp += 0.12 * (0 - lp) + 0.025 * r.nextGaussian();
        priceIndex = Math.max(0.75, Math.min(1.4, Math.exp(lp)));
    }
}
