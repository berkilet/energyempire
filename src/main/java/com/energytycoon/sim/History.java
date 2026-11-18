package com.energytycoon.sim;

/** Grafikler icin kayan pencere verileri. */
public final class History {
    public static final int HOURS = 96;
    public final double[] price = new double[HOURS];
    public final double[] demand = new double[HOURS];
    public final double[] supply = new double[HOURS];
    public final double[] playerMW = new double[HOURS];
    public int count;
    public final java.util.ArrayList<Double> monthlyPrice = new java.util.ArrayList<>();

    public void push(double p, double d, double s, double pm) {
        System.arraycopy(price, 1, price, 0, HOURS - 1);
        System.arraycopy(demand, 1, demand, 0, HOURS - 1);
        System.arraycopy(supply, 1, supply, 0, HOURS - 1);
        System.arraycopy(playerMW, 1, playerMW, 0, HOURS - 1);
        price[HOURS - 1] = p; demand[HOURS - 1] = d; supply[HOURS - 1] = s; playerMW[HOURS - 1] = pm;
        count = Math.min(HOURS, count + 1);
    }

    public void monthly(double avgPrice) {
        monthlyPrice.add(avgPrice);
        if (monthlyPrice.size() > 240) monthlyPrice.remove(0);
    }
}
