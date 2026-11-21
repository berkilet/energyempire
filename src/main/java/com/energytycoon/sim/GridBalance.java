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
    public static final int NEUTRAL_COLOR = 0xa9b8e8; // soluk mavi-gri; arazi/su ve rakip renklerinden ayrik (koyu lacivert kenarla)
    /** Kamu rozeti ve onizleme isaretinin koyu kenar rengi. */
    public static final int NEUTRAL_OUTLINE = 0x1f2d4d;

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

    // ---- Faz 4: kume (bara) ve trafo merkezi
    /** Bara: ayni sirket santralleri arasi en fazla Chebyshev mesafesi. */
    public static final int BARA_DISTANCE = 1;
    public static final double TM_RADIUS = 3;
    public static final double TM154_COST = 9e6;
    public static final int TM154_DAYS = 45;
    public static final double TM154_MW = 600;
    public static final double TM_PLUS_COST = 6e6;
    public static final int TM_PLUS_DAYS = 30;
    public static final double TM_PLUS_MW = 600;
    public static final double TM380_COST = 25e6;
    public static final int TM380_DAYS = 90;
    public static final double TM380_MW = 1500;
    public static final double TM380_UPGRADE_COST = 18e6;
    public static final int TM380_UPGRADE_DAYS = 60;
    /** Ucuncu taraf erisim bedeli ($/MWh, teslim edilen). */
    public static final double ACCESS_FEE_DEFAULT = 3;
    public static final double ACCESS_FEE_MIN = 0, ACCESS_FEE_MAX = 10;
    /** ÇYG hattinin santral ucu icin en kucuk santral gucu. */
    public static final double CYG_MIN_PLANT_MW = 400;
    /** Rakip YZ erisim bedelini hat maliyetiyle kiyaslarken kullandigi ufuk (yil). */
    public static final double RIVAL_ACCESS_HORIZON_YEARS = 5;

    // ---- Faz 5: hat insaati ve Ulusal Sebeke Isletmesi (UŞİ)
    /** Kendi hattinda gecikme olasiligi ve gecikme payi (insaat suresinin orani). */
    public static final double LINE_DELAY_CHANCE = 0.10, LINE_DELAY_MIN = 0.10, LINE_DELAY_MAX = 0.30;
    public static final double OP_APPLICATION_FEE = 0.5e6;
    /** Basvuruda pesin katki: hat maliyetinin orani. */
    public static final double OP_CONTRIBUTION = 0.15;
    /** Yillik baglanti bedeli: hat maliyetinin orani (aylik tahakkuk). */
    public static final double OP_ANNUAL_CHARGE = 0.06;
    public static final int OP_PLANNING_MONTHS = 6;
    /** Ayni anda insaat slotu: OP_BASE_SLOTS + sehir sayisi / OP_CITIES_PER_SLOT. */
    // Denge olcumu (10 yil, oyuncu botu): baglantilarin %94-95i kendi hattindan, kuyrukta bekleme 0 ay -> formul 2 + sehir/4 iken 1 + sehir/6 yapildi.
    public static final int OP_BASE_SLOTS = 1, OP_CITIES_PER_SLOT = 6;
    /** UŞİ insaati, kendi insaat suresinin bu kati. */
    public static final double OP_BUILD_MULT = 1.5;
    /** Planlama/kuyrukta aylik kayma olasiligi (+1 ay). */
    public static final double OP_SLIP_CHANCE = 0.08;
    /** Tahmin araligi: en erken + bu kadar ay. */
    public static final int OP_ETA_SPREAD_MONTHS = 3;
    /** Hizlandirma: hat maliyetinin orani; planlama en fazla bu kadar aya iner ve kuyruk basina gecer. */
    public static final double OP_ACCELERATE = 0.35;
    public static final int OP_ACCELERATE_PLANNING_MONTHS = 2;
    /** Planlamada iptal: katkinin iade orani. */
    public static final double OP_CANCEL_REFUND = 0.5;
    /** Kendi aktif hattini UŞİ'ye devir bedeli: defter degerinin orani. */
    public static final double OP_TRANSFER_BOOK = 0.6;
    /** UŞİ kendi plani: yilda en fazla proje, sure araligi (ay), guclendirme esigi (gecen yil doluluk saat orani). */
    public static final int OP_PLAN_MAX_PER_YEAR = 2, OP_PLAN_MIN_MONTHS = 18, OP_PLAN_MAX_MONTHS = 30;
    public static final double OP_REINFORCE_SATURATION = 0.25;
    /** Rakip YZ: yeni santrali kendi kumesine/TM'sine yerlestirmeyi deneme olasiligi. */
    public static final double RIVAL_CLUSTER_CHANCE = 0.5;

    // ---- Faz 6: guzergah, paralel hat, kademe yukseltme, hat arizasi
    /** Guzergah karesi carpani: su ve yuksek arazi (kademe >= ROUTE_HIGH_LEVEL). */
    public static final double ROUTE_WATER_MULT = 1.8, ROUTE_HIGH_MULT = 1.5;
    public static final int ROUTE_HIGH_LEVEL = 11;
    /** Ayni iki uc arasinda ikinci/ucuncu hat bu oranda ucuz. */
    public static final double PARALLEL_DISCOUNT = 0.40;
    /** Kademe yukseltme: (yeni - eski guzergah maliyeti) x (1 + prim); sure = yeni kademenin insaat suresi x carpan, hat kapali. */
    public static final double UPGRADE_PREMIUM = 0.20, UPGRADE_TIME_MULT = 0.6;
    /** Hat arizasi: saatlik olasilik = kademe.outagePerTileHour x uzunluk; sure (saat); onarim hat maliyetinin orani (UŞİ ucretsiz). */
    public static final int LINE_OUTAGE_MIN_H = 12, LINE_OUTAGE_MAX_H = 48;
    public static final double LINE_REPAIR_COST = 0.005;

    /** Komurden Cikis senaryosunda kamu komur santrallerinin en gec kapanis yili (kapanislar LEGACY_RETIRE_FROM..bu yila yayilir). */
    public static final int LEGACY_COAL_EXIT_RETIRE_TO = 2032;

    // ---- Rakip YZ: trafo merkezi ve hat yukseltme
    /** TM degerlendirmesi: yaricapta en az bu kadar kendi santrali (insaattakiler dahil) veya bu kadar ay dolu baglanti noktasi. */
    public static final int RIVAL_TM_MIN_PLANTS = 3, RIVAL_TM_FULL_MONTHS = 2;
    /** Baglanti noktasi bir ayda bu oranin ustunde saat dolu (%99+) ise "dolu ay" sayilir. */
    public static final double CP_FULL_MONTH_SHARE = 0.5;
    /** Kume bu kurulu gucu gecerse TM380'e yukseltme degerlendirilir. */
    public static final double RIVAL_TM380_MW = 600;
    /** Rakibin kendi bolgesindeki buyukluk karsilastirma yaricapi (erisim politikasi). */
    public static final double RIVAL_TM_REGION = 15;
    /** Talebin yillik en dusuk oldugu ay (0 = Ocak; mevsim egrisi Temmuz'da en dusuk). */
    public static final int LOW_DEMAND_MONTH = 6;
    /** Ikinci yol yokken rakibin kabul ettigi en uzun yukseltme kesintisi (gun); uzunsa paralel hat. */
    public static final int RIVAL_MAX_OFFLINE_DAYS = 60;
}
