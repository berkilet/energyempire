package com.energytycoon.sim;

/** Santral turleri ve ekonomik/teknik parametreleri. */
public enum PlantType {
    //          etiket              yakit        MW   capex/MW  sabitOM/MW/yil varOM  verim  co2/MWh insaatGun omur  yenilenebilir
    COAL("Kömür Santrali",      Fuel.COAL,    200, 1.80e6, 45e3, 4.0, 0.38, 0.95, 240, 35, false),
    GAS("Doğalgaz Santrali",    Fuel.GAS,     150, 0.90e6, 20e3, 3.0, 0.52, 0.40, 120, 30, false),
    SOLAR("Güneş Santrali",     Fuel.NONE,    100, 0.80e6, 14e3, 0.5, 1.00, 0.00, 90,  25, true),
    WIND("Rüzgar Santrali",     Fuel.NONE,     60, 1.35e6, 32e3, 1.0, 1.00, 0.00, 100, 25, true),
    HYDRO("Hidroelektrik",      Fuel.NONE,    120, 2.60e6, 28e3, 1.0, 1.00, 0.00, 330, 60, true),
    NUCLEAR("Nükleer Santral",  Fuel.URANIUM, 500, 4.40e6, 95e3, 2.0, 0.33, 0.00, 600, 50, false);

    public enum Fuel { NONE, COAL, GAS, URANIUM }

    public final String label;
    public final Fuel fuel;
    public final double capacityMW;
    public final double capexPerMW;
    public final double fixedOmPerMWYear;
    public final double varOm;
    public final double efficiency;
    public final double co2PerMWh;
    public final int buildDays;
    public final int lifeYears;
    public final boolean renewable;

    PlantType(String label, Fuel fuel, double mw, double capexPerMW, double fixedOm, double varOm,
              double eff, double co2, int buildDays, int lifeYears, boolean renewable) {
        this.label = label; this.fuel = fuel; this.capacityMW = mw; this.capexPerMW = capexPerMW;
        this.fixedOmPerMWYear = fixedOm; this.varOm = varOm; this.efficiency = eff; this.co2PerMWh = co2;
        this.buildDays = buildDays; this.lifeYears = lifeYears; this.renewable = renewable;
    }

}
