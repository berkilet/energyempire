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

    // ---- Faz 2: Hat kademeleri (Bolum 9). Sira: OG 34,5 kV, YG 154 kV, CYG 380 kV
    public static final double[] TIER_CAPACITY_MW      = { 60, 250, 1000 };
    public static final double[] TIER_COST_PER_TILE    = { 0.35e6, 1.1e6, 2.8e6 };
    public static final double[] TIER_OM_PER_TILE_YEAR = { 6_000, 15_000, 30_000 };
    public static final double[] TIER_LOSS_PER_TILE    = { 0.008, 0.0035, 0.0012 };
    public static final double[] TIER_MAX_LENGTH       = { 8, 45, 60 };
    public static final int[]    TIER_BASE_BUILD_DAYS  = { 15, 30, 60 };
    public static final int[]    TIER_BUILD_DAYS_PER_TILE = { 1, 3, 5 };
    public static final double[] TIER_OUTAGE_PER_TILE_HOUR = { 3e-6, 2e-6, 1.5e-6 };
    /** Bir baglanti noktasinin en yakin sehre kaybi en fazla bu kadar olur. */
    public static final double MAX_PATH_LOSS = 0.15;
    /** Doluluk bu oranin ustundeyse hat o saat "dolu" sayilir. */
    public static final double SATURATION_THRESHOLD = 0.99;
    /** Hat defter degeri: dogrusal amortisman suresi (yil) ve alt sinir. */
    public static final double LINE_LIFE_YEARS = 40, LINE_BOOK_FLOOR = 0.25;
    /** Isletmeci (owner == null) hatlarinin gosterilen sahibi. */
    public static final String OPERATOR_NAME = "Ulusal Şebeke İşletmesi (UŞİ)";
    public static final String OPERATOR_SHORT = "UŞİ";
}
