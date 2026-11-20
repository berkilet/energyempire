package com.energytycoon.sim;

/**
 * Sebeke reformu (Faz 1+) denge sabitleri. Mevcut Balance desenindedir; yeni sayisal sabitler burada toplanir.
 */
public final class GridBalance {
    private GridBalance() {}

    // ---- Faz 1: Kamu Uretim (eski filo)
    /** Her sehrin gercek tepe talebinin (bkz. GameState.MAX_DEMAND_MULT) bu kati kadar eski kapasite kurulur. */
    public static final double LEGACY_COVERAGE = 1.05;
    /** Kendi baslangic filosu olan senaryolarda (coal, crisis) kullanilan kapsama. */
    public static final double LEGACY_COVERAGE_SCENARIO = 0.5;
    /** Eski filonun yakit maliyeti carpani (verimsiz, bakimsiz tesis). */
    public static final double LEGACY_FUEL_MULT = 1.25;
    /** Kapanislar bu yillar arasina (dahil) esit dagitilir. */
    public static final int LEGACY_RETIRE_FROM = 2027, LEGACY_RETIRE_TO = 2034;
    /** Oyun basinda eski santrallerin yasi (yil). */
    public static final int LEGACY_AGE_MIN = 20, LEGACY_AGE_MAX = 30;
    /** Kapanistan kac ay once haber yayimlanir. */
    public static final int LEGACY_NOTICE_MONTHS = 6;
    /** Katalogdaki en eski modeller. */
    public static final String LEGACY_COAL_ID = "fk-sub", LEGACY_GAS_ID = "at-oc";
    /** Eski santrallerin sehre uzakligi (kare). */
    public static final double LEGACY_MIN_DIST = 3, LEGACY_MAX_DIST = 9;
    public static final String NEUTRAL_NAME = "Kamu Üretim";
    public static final int NEUTRAL_COLOR = 0x95a5a6;

    // ---- Faz 1: Rakip kurallari
    /** Rakip insaat kararinda santral + hat maliyetinin ustune tutulan nakit rezervi. */
    public static final double RIVAL_BUILD_RESERVE = 40e6;
    /** Rakibin aylik karar vermesi icin gereken asgari nakit (zorluk carpaninda bolunur). */
    public static final double RIVAL_DECISION_CASH = 160e6;
    /** Rakibin bir ayda insaat karari verme olasiligi (zorluk carpaniyla carpilir). */
    public static final double RIVAL_BUILD_CHANCE = 0.55;
}
