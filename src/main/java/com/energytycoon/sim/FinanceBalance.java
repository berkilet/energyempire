package com.energytycoon.sim;

/** Finans sistemi sabitleri (makro ortam, kredi notu, bankalar, kredi urunleri, tahsilat). Oranlar yillik ondalik. */
public final class FinanceBalance {
    private FinanceBalance() {}

    // ---- 1. Makro: politika faizi
    public static final double POLICY_START = 0.045;
    public static final double TARGET_EASING = 0.025, TARGET_NEUTRAL = 0.045, TARGET_TIGHTENING = 0.075;
    public static final double POLICY_MIN = 0.005, POLICY_MAX = 0.12;
    /** Toplanti araligi (ay) ve adimlar: hedefe uzaklik >= 2 puan 0,75; >= 1 puan 0,50; >= 0,25 puan 0,25. */
    public static final int MEETING_MONTHS = 2;
    /** Aylik rejim degisimi olasiligi. */
    public static final double REGIME_SWITCH_CHANCE = 0.02;
    /** Piyasa beklentisi: tahmin edilen rejimin dogru olma olasiligi. */
    public static final double FORECAST_ACCURACY = 0.70;

    // ---- 1. Kuresel olaylar (aylik tetiklenme olasiligi; zorluktan bagimsiz)
    public static final double CRISIS_CHANCE = 1.0 / 150, INFLATION_CHANCE = 1.0 / 200, COUNTRY_CHANCE = 1.0 / 100, GREEN_WAVE_CHANCE = 1.0 / 120;
    public static final int CRISIS_MIN = 12, CRISIS_MAX = 24, INFLATION_MIN = 18, INFLATION_MAX = 36,
        COUNTRY_MIN = 6, COUNTRY_MAX = 18, GREEN_WAVE_MIN = 24, GREEN_WAVE_MAX = 48;
    public static final double CRISIS_SPREAD = 0.03, CRISIS_EQUITY_DROP = 0.30, COUNTRY_SPREAD = 0.02;
    public static final double INFLATION_MIN_STEP = 0.005;
    /** Enerji krizi: mevcut gaz soku basladiginda politika faizine olaganustu artis (0,5–1 puan). */
    public static final double ENERGY_HIKE_MIN = 0.005, ENERGY_HIKE_MAX = 0.01;
    public static final double GREEN_DISCOUNT = 0.005, GREEN_DISCOUNT_WAVE = 0.010, GREEN_SHARE = 0.60;

    // ---- 2. Kredi notu
    /** Not esikleri: Net borc / FAVOK <= ve FAVOK / faiz >= ; primler. Sira: A, BBB, BB, B, CCC. */
    public static final double[] RATING_ND_EBITDA = { 2.0, 3.5, 5.0, 7.0 };
    public static final double[] RATING_COVER = { 8.0, 4.0, 2.5, 1.5 };
    public static final double[] RATING_PREMIUM = { 0.015, 0.025, 0.040, 0.060, 0.090 };
    public static final int DEFAULT_CAP_YEARS = 2;

    // ---- 3. Bankalar
    public static final int REL_START = 20, REL_MAX = 100;
    public static final int REL_ON_TIME = 1, REL_ON_TIME_CAP = 10, REL_CLOSED = 10, REL_LATE = -15, REL_LATE30 = -40, REL_DEFAULT_OTHERS = -20;
    public static final int REL_BAN_YEARS = 3;
    public static final int TIER1 = 30, TIER2 = 60, TIER3 = 90;
    public static final double TIER2_DISCOUNT = 0.0015, TIER3_DISCOUNT = 0.0030;
    /** Yogunlasma: bir bankanin ticari + isletme kredisi bakiyesi / sirketin kredi limiti. Kiyibank en genis. */
    public static final double CONCENTRATION_KIYI = 0.50, CONCENTRATION_OTHER = 0.35;
    public static final double YESIL_RENEW_DISCOUNT = 0.005, DEMIRTAS_EXTRA = 0.005, DEMIRTAS_CCC = 0.02;
    public static final double COVENANT_ND_EBITDA = 4.5, COVENANT_ND_EBITDA_YESIL = 4.0;

    // ---- 4. Kredi urunleri
    public static final double LOAN_STEP = 50e6;
    public static final int COMMERCIAL_MIN_YEARS = 3, COMMERCIAL_MAX_YEARS = 7, YESIL_MAX_YEARS = 10;
    public static final double FIXED_PREMIUM = 0.01, COMMERCIAL_FEE = 0.01, COMMERCIAL_PREPAY = 0.01;
    /** Ayni nottaki ticari kredi ECA'dan en az bu kadar pahali (ticari prim tabani). */
    public static final double COMMERCIAL_MIN_OVER_ECA = 0.01;
    public static final int FLOAT_RESET_MONTHS = 3;
    public static final double ECA_SHARE = 0.85, ECA_SPREAD = 0.01, ECA_BANK_FEE = 0.01, ECA_PREPAY = 0.02, ECA_DSCR = 1.20, ECA_DSCR_PENALTY = 0.005;
    public static final int ECA_MIN_YEARS = 10, ECA_MAX_YEARS = 15, ECA_NUCLEAR_YEARS = 18, ECA_GRACE_EXTRA_MONTHS = 6, ECA_DSCR_DEFAULT_QUARTERS = 4;
    /** Isletme kredisi limiti (nota gore A..CCC), marj, taahhut komisyonu, kapatma suresi (ay). */
    public static final double[] REVOLVER_LIMIT = { 150e6, 125e6, 100e6, 75e6, 50e6 };
    public static final double REVOLVER_SPREAD = 0.03, REVOLVER_FEE = 0.005;
    public static final int REVOLVER_CLEAR_MONTHS = 12;
    public static final double BASE_CREDIT = 300e6, ASSET_CREDIT = 0.5;

    // ---- 5. Tahsilat, gecikme, temerrut
    public static final double LATE_RATE = 0.02;
    public static final int LATE_TOLERANCE_DAYS = 30, DEFAULT_DAYS = 90, NEGATIVE_CASH_DAYS = 60;
    public static final double OVERDRAFT_RATE = 0.16;
    public static final double COVENANT_RATE_PENALTY = 0.01, COVENANT_DEMAND = 0.25;
    public static final int RESTRUCT_PROTECT_MONTHS = 3, RESTRUCT_EXTEND_YEARS = 3, RESTRUCT_SELL_MONTHS = 6;
    public static final double RESTRUCT_RATE = 0.02;
    /** El konan santral: rakip teklifi yoksa banka defter degerinin bu oranina alir (Kamu varlik fonuna devreder). */
    public static final double SEIZE_FLOOR = 0.60;
    public static final int DUE_WARNING_DAYS = 30;
    public static final double COVENANT_WARNING = 0.90;

    // ---- 6. Rakip YZ
    /** Rakip en az bu kadar aylik taksit karsiligi nakit tutar. */
    public static final int RIVAL_BUFFER_MONTHS = 6;
    /** Faiz bu seviyenin ustunde ve beklenti dususteyse buyuk yatirim ertelenir (olasilik). */
    public static final double RIVAL_DEFER_RATE = 0.065, RIVAL_DEFER_CHANCE = 0.6;
}
