package com.energytycoon.sim;

/** Tum oyun dengesi sabitleri tek yerde. */
public final class Balance {
    private Balance() {}

    public static final int MAP_SIZE = 64;
    public static final int HOURS_PER_DAY = 24;
    public static final int DAYS_PER_MONTH = 30;
    public static final int MONTHS_PER_YEAR = 12;
    public static final int HOURS_PER_MONTH = HOURS_PER_DAY * DAYS_PER_MONTH;
    public static final int HOURS_PER_YEAR = HOURS_PER_MONTH * MONTHS_PER_YEAR;

    public static final int START_YEAR = 2025;
    public static final double START_CASH = 650e6;
    public static final double BASE_CREDIT = 300e6;
    public static final double LOAN_STEP = 100e6;
    public static final double OVERDRAFT_RATE = 0.16;
    public static final double BANKRUPT_CASH = -150e6;
    public static final double SHARES = 10e6;

    /** Kisi basina tepe talep: MW / kisi. */
    public static final double MW_PER_CAPITA = 1.0 / 1400.0;
    public static final double SCARCITY_PRICE = 160;
    public static final double MIN_PRICE = 1.0;
    public static final double BLACKOUT_FINE = 20; // $/MWh karsilanamayan

    public static final double LINE_COST_PER_TILE = 1.1e6;
    public static final double LINE_WATER_MULT = 1.8;
    public static final double LINE_CAPACITY = 250; // MW
    public static final double LINE_LOSS_PER_TILE = 0.0035;
    public static final double LINE_OM_PER_TILE_YEAR = 15e3;

    public static final double DEMOLISH_REFUND = 0.15;
    public static final double OUTAGE_CHANCE_PER_HOUR = 0.0004;

    public static final double CARBON_START = 12;     // $/ton
    public static final double CARBON_GROWTH_YEAR = 3; // $/ton / yil

    /** Pazar payi hedefli senaryolarin (Yesil Donusum, Komurden Cikis) son yili. Kamu payi paydada oldugu icin genis tutuldu. */
    public static final int SHARE_SCENARIO_DEADLINE = 2040;

    // ---- Acil kapasite ihalesi (UŞİ)
    /** Memnuniyet bu degerin altinda TENDER_LOW_MONTHS ay ust uste kalirsa ihale acilir. */
    public static final double TENDER_SATISFACTION = 0.60;
    public static final int TENDER_LOW_MONTHS = 12, TENDER_COOLDOWN_YEARS = 3;
    public static final double TENDER_ROUND_MW = 50;
    public static final int TENDER_BID_DAYS = 60, TENDER_BUILD_MONTHS = 24, TENDER_CONTRACT_YEARS = 10;
    public static final double TENDER_MAX_DIST = 15;
    /** Teminat: tahmini santral bedelinin orani; bedel referansi kombine cevrim gaz. */
    public static final double TENDER_DEPOSIT = 0.05;
    public static final String TENDER_REFERENCE_EQ = "at-cc";
    public static final double TENDER_MIN_PRICE = 20, TENDER_MAX_PRICE = 300;
    /** Rakip teklifi: 10 yillik sermaye geri kazanim katsayisi ve kar marji (stile gore +/-). */
    public static final double TENDER_CRF = 0.149, TENDER_RIVAL_MARGIN = 0.15;
}
