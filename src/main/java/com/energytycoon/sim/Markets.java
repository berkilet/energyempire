package com.energytycoon.sim;

import java.util.Random;

/** Yakit fiyatlari, karbon fiyati ve hava durumu. */
public final class Markets {
    // $/MWh (isil girdisi)
    public double coalPrice = Balance.COAL_PRICE_MEAN, gasPrice = Balance.GAS_PRICE_MEAN, uraniumPrice = 4.5;
    public double carbonPrice = Balance.CARBON_START;
    /** Rüzgar (0..1), bulutluluk-gunes carpani (0.15..1). */
    public double wind = 0.36, cloud = 0.75;
    public double demandNoise = 1.0;

    // Ortalama seviyeler (yillik enflasyon/trend yok, sok olaylari var)
    private double coalMean = Balance.COAL_PRICE_MEAN, gasMean = Balance.GAS_PRICE_MEAN;
    private int shockDaysLeft;
    private double shockGas = 1.0;

    /** Senaryo/harita carpanlari (ithal yakit, kalici kriz). */
    public double coalMult = 1, gasMult = 1, uraniumMult = 1, carbonGrowthMult = 1;

    public double fuelPrice(PlantType.Fuel f) {
        return switch (f) { case COAL -> coalPrice * coalMult; case GAS -> gasPrice * gasMult; case URANIUM -> uraniumPrice * uraniumMult; default -> 0; };
    }

    public void hourly(Random r) {
        wind += 0.04 * (0.36 - wind) + 0.05 * r.nextGaussian();
        wind = Math.max(0.02, Math.min(0.95, wind));
        cloud += 0.03 * (0.72 - cloud) + 0.045 * r.nextGaussian();
        cloud = Math.max(0.15, Math.min(1.0, cloud));
    }

    public void daily(Random r, int year) {
        double gmean = gasMean * shockGas;
        coalPrice = revert(coalPrice, coalMean, 0.05, 0.030, r);
        gasPrice = revert(gasPrice, gmean, 0.07, 0.045, r);
        uraniumPrice = revert(uraniumPrice, 4.5, 0.02, 0.008, r);
        demandNoise += 0.2 * (1.0 - demandNoise) + 0.012 * r.nextGaussian();
        if (shockDaysLeft > 0) {
            if (--shockDaysLeft == 0) shockGas = 1.0;
        } else if (r.nextDouble() < 0.0009) { // yilda ~0.3 sok
            shockDaysLeft = 60 + r.nextInt(120);
            shockGas = 1.4 + r.nextDouble() * 0.6;
        }
        carbonPrice = Balance.CARBON_START + Balance.CARBON_GROWTH_YEAR * carbonGrowthMult * year;
    }

    public boolean gasShock() { return shockDaysLeft > 0; }

    private static double revert(double p, double mean, double theta, double sigma, Random r) {
        double lp = Math.log(p);
        lp += theta * (Math.log(mean) - lp) + sigma * r.nextGaussian();
        return Math.max(mean * 0.4, Math.min(mean * 3.2, Math.exp(lp)));
    }
}
