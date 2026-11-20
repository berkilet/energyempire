package com.energytycoon.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class Company {
    public String name;
    public boolean player;
    /** Tarafsiz kamu sirketi (eski filo): YZ calistirmaz, iflas etmez, siralamalara girmez. */
    public boolean neutral;
    /** Rakip YZ tarzi: fossil, green, balanced. */
    public String aiStyle = "balanced";
    public int colorRgb;
    public double cash;
    public double debt;
    public boolean bankrupt;
    public int negativeMonths;

    // Aylik P&L (devam eden ay)
    public double revenue, fuelCost, omCost, carbonCost, interest, fines, capexSpent;
    // Gecen ay
    public double lastRevenue, lastFuel, lastOm, lastCarbon, lastInterest, lastFines, lastNet;
    public double mwhGenerated, lastMwhGenerated;
    public double co2Tons, lastCo2;
    public double renewMwh, lastRenewMwh;

    public double lastRenewShare() { return lastMwhGenerated > 0 ? lastRenewMwh / lastMwhGenerated : 0; }
    /** Son 12 ayin net kari. */
    public final ArrayDeque<Double> netHistory = new ArrayDeque<>();
    public final List<Double> valuationHistory = new ArrayList<>();
    public final List<Double> cashHistory = new ArrayList<>();
    public double valuation;
    public double stockPrice;

    public Company(String name, boolean player, int colorRgb, double cash) {
        this.name = name; this.player = player; this.colorRgb = colorRgb; this.cash = cash;
    }

    public double trailingNet() {
        double s = 0;
        for (double d : netHistory) s += d;
        return s;
    }

    public double monthNetSoFar() {
        return revenue - fuelCost - omCost - carbonCost - interest - fines;
    }

    public double rateAdd;

    public double interestRate(double assets) {
        double lev = debt / Math.max(1e6, assets + Math.max(0, cash));
        return Math.max(0.02, Math.min(0.22, 0.05 + rateAdd + 0.08 * lev));
    }

    public double creditLimit(double assets) {
        return Balance.BASE_CREDIT + 0.5 * assets;
    }
}
