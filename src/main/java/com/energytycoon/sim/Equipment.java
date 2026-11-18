package com.energytycoon.sim;

/** Bir santral modeli (tedarikci + teknoloji). Tum ekonomik/teknik degerler buradan turetilir. */
public final class Equipment {
    public final String id;
    public final PlantType type;
    public final Supplier supplier;
    public final String model;
    public final String tech;
    public final double capacityMW;
    public final double capexPerMW;
    /** Termik verim (yenilenebilirde 1). */
    public final double efficiency;
    /** Yenilenebilir uretim carpani (modul verimi, bifasiyal kazanc...). */
    public final double outputMult;
    /** Gunes: sicak sahada kayip katsayisi. Ruzgar: cut-in. Hidro: tasarim dusu (0 alcak .. 1 yuksek). */
    public final double paramA;
    /** Ruzgar: anma (rated) hiz. */
    public final double paramB;
    public final double degradationPerYear;
    public final double buildMult;
    public final double fixedOmMult;
    public final int availableYear;
    public final String note;
    /** Gorsel: ruzgarda kule yuksekligi, gunes panel renk tonu vb. */
    public final float visA, visB;

    Equipment(String id, PlantType type, Supplier s, String model, String tech, double mw, double capexPerMW,
              double eff, double outMult, double a, double b, double deg, double buildMult, double omMult,
              int year, float visA, float visB, String note) {
        this.id = id; this.type = type; this.supplier = s; this.model = model; this.tech = tech;
        this.capacityMW = mw; this.capexPerMW = capexPerMW; this.efficiency = eff; this.outputMult = outMult;
        this.paramA = a; this.paramB = b; this.degradationPerYear = deg; this.buildMult = buildMult;
        this.fixedOmMult = omMult; this.availableYear = year; this.visA = visA; this.visB = visB; this.note = note;
    }

    public double listCapex() { return capacityMW * capexPerMW; }
    public double capex() { return listCapex() * supplier.priceIndex; }
    public int buildDays() { return (int) Math.round(type.buildDays * buildMult); }
    /** Fosil yakitta emisyon verimle ters orantili. */
    public double co2PerMWh() {
        return type.co2PerMWh <= 0 ? 0 : type.co2PerMWh * type.efficiency / efficiency;
    }
    public double fixedOmPerHour() { return type.fixedOmPerMWYear * fixedOmMult * capacityMW / Balance.HOURS_PER_YEAR; }
    public String fullName() { return supplier.name + " " + model; }
}
