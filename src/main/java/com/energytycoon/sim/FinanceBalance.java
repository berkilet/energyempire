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

    // ================================================================ Paket 2: sermaye, devralma, rekabet
    // ---- 1. Sahiplik ve fiyat
    public static final double SHARES = 10e6;
    /** F/K carpani: politika faizi PE_RATE_REF iken PE_BASE; her 1 puan dususte +PE_PER_POINT, artista -PE_PER_POINT; sinirlar. */
    public static final double PE_BASE = 12, PE_RATE_REF = 0.045, PE_PER_POINT = 1.5, PE_MIN = 6, PE_MAX = 20;
    /** Not (A..CCC) ve buyume etkisi; toplam en fazla +-PE_ADJ_MAX. */
    public static final double[] PE_RATING_ADJ = { 0.10, 0.05, 0, -0.07, -0.15 };
    public static final double PE_GROWTH_K = 0.25, PE_ADJ_MAX = 0.20;
    /** Zarar eden sirket: yillik zarar bu kat kadar degerden duser. */
    public static final double LOSS_MULT = 2;
    /** Gunluk fiyat gurultusu, piyasa duyarliligi (endeks / 24 ay ortalamasi) ussu ve sinirlari. */
    public static final double PRICE_NOISE = 0.012, SENTIMENT_EXP = 0.6, SENTIMENT_MIN = 0.65, SENTIMENT_MAX = 1.35;
    /** Haber etkisi gunluk bu oranda notre doner; ceyrek surprizi katsayisi ve siniri. */
    public static final double NEWS_DECAY = 0.04, SURPRISE_K = 0.10, SURPRISE_MAX = 0.10;
    public static final double NEWS_RATING_UP = 0.04, NEWS_RATING_DOWN = -0.06, NEWS_OUTAGE = -0.02, NEWS_LATE = -0.05, NEWS_DEFAULT = -0.15;
    /** Buyuk ariza haberi: bu kapasitenin ustundeki santral. */
    public static final double NEWS_OUTAGE_MW = 300;
    /** Temettu politikasinin (0/30/50) duyarliliga destegi. */
    public static final double[] DIVIDEND_SUPPORT = { 0, 0.01, 0.02 };

    // ---- 2. Halka arz
    public static final int IPO_MIN_QUARTERS = 8, IPO_PREP_MONTHS = 6, IPO_POSTPONE_MONTHS = 3;
    /** Arz edilen yeni hisse / mevcut hisse; fiyat temel degerin %80-95'i (talebe gore); maliyet hasilatin %3'u. */
    public static final double IPO_MIN = 0.15, IPO_MAX = 0.49, IPO_PRICE_LOW = 0.80, IPO_PRICE_HIGH = 0.95, ISSUE_COST = 0.03;
    /** Rakip: nakit + kullanilabilir kredi planlanan yatirimin altindaysa arz yapar (oran %25-40). */
    public static final double RIVAL_IPO_MIN = 0.25, RIVAL_IPO_MAX = 0.40, RIVAL_IPO_NEED = 600e6, RIVAL_IPO_NEED_ASSETS = 0.25;
    public static final int RIVAL_DIVIDEND_AFTER_IPO = 30;

    // ---- 3. Sermaye islemleri
    /** Bedelli sermaye artirimi: yeni hisse orani, iskonto, duyuru dususu; yilda en fazla bir. */
    public static final double RIGHTS_MIN = 0.05, RIGHTS_MAX = 0.30, RIGHTS_DISCOUNT_MIN = 0.10, RIGHTS_DISCOUNT_MAX = 0.20;
    public static final double RIGHTS_DROP_MIN = 0.03, RIGHTS_DROP_MAX = 0.08;
    public static final int RIGHTS_PER_YEARS = 1;
    /** Geri alim ve piyasadan alim: gunluk en fazla serbest dolasimin %2'si; her %1'lik alim fiyati ~%1,5 yukseltir. */
    public static final double DAILY_TRADE_MAX = 0.02, PRICE_IMPACT = 1.5;
    public static final int[] DIVIDEND_OPTIONS = { 0, 30, 50 };
    public static final double CONTROL = 0.50;

    // ---- 5. Rekabet otoritesi
    /** Bildirim esigi: birlesik yillik gelir ve hedefin yillik geliri. */
    public static final double NOTIFY_COMBINED_REVENUE = 500e6, NOTIFY_TARGET_REVENUE = 50e6;
    /** Olcek farki muafiyeti: devralan degeri / hedef degeri. */
    public static final double SCALE_EXEMPTION_RATIO = 1.5;
    /** Bolgesel birlesik pay esigi, batan sirket savunmasinda esik, ulusal tekel siniri; gevsek (Konsolidasyon) bolgesel esik. */
    public static final double REGIONAL_LIMIT = 0.60, FAILING_LIMIT = 0.75, NATIONAL_LIMIT = 0.80, RELAXED_REGIONAL = 0.80;
    public static final int PRE_REVIEW_DAYS = 30, FINAL_REVIEW_MONTHS = 4, DIVEST_MONTHS = 12, COMMENT_EXTRA_MONTHS = 1;
    /** Taahhut ihlali cezasi: yillik gelirin orani. */
    public static final double DIVEST_FINE = 0.02;
    /** Rakip, satilacak MW birlesik grubun ilgili pazardaki kapasitesinin bu oranini asmiyorsa taahhudu kabul eder. */
    public static final double AI_ACCEPT_DIVEST = 0.5;
    /** Hakim durum: pazar payi esigi ve fiyat tavani (ortalama uretim maliyeti + %15). */
    public static final double DOMINANCE = 0.50, DOMINANCE_MARKUP = 0.15;
    /** Uretim maliyetine yatirimin geri donusu de girer: santral bedeli x sermaye geri kazanim faktoru (%10 ~ ticari borclanma maliyeti; once %8), santral omru. */
    public static final double DOMINANCE_CAPITAL_RATE = 0.10;
    /** Duzenlenmis tekel: ulusal pay esigi ve izin verilen yillik varlik getirisi. */
    public static final double REGULATED_SHARE = 0.80, REGULATED_RETURN = 0.08;
    /** Konsolidasyon senaryosu: hedef ulusal pay, son tarih ve ara hedefler (simulasyonla ayarlandi). */
    public static final double CONSOLIDATION_TARGET = 0.80;
    public static final int CONSOLIDATION_DEADLINE = 2040;
    public static final int[] CONSOLIDATION_MILESTONE_YEARS = { 2030, 2035 };
    public static final double[] CONSOLIDATION_MILESTONE_SHARES = { 0.35, 0.60 };

    // ---- 4. Pay alimi ve devralma
    /** Kurucunun istedigi prim: kontrolu koruyan satis %15-25, kontrol kaybi >= %36 (once %60), zor durumda %0-10, nakit ihtiyacinda %5-15. */
    public static final double ASK_BASE_MIN = 0.15, ASK_BASE_MAX = 0.25, ASK_CONTROL_MIN = 0.36, ASK_CONTROL_MAX = 0.46;
    public static final double ASK_DISTRESS_MAX = 0.10, ASK_CASH_MIN = 0.05, ASK_CASH_MAX = 0.15;
    /** Teklif istenen fiyatin bu oranindan dusukse dogrudan ret; aradaysa bir kez karsi teklif. */
    public static final double COUNTER_BAND = 0.85;
    /** Bildirim esikleri; esik gecisinde hedef hissesi %5-10 yukselir; hedef YZ savunma modu (gun). */
    public static final double[] DISCLOSURE = { 0.05, 0.10, 0.15, 0.20, 0.25, 0.33, 0.50, 0.67 };
    public static final double DISCLOSE_JUMP_MIN = 0.05, DISCLOSE_JUMP_MAX = 0.10;
    public static final int DEFENSE_DAYS = 90;
    /** Pay oranina gore haklar. */
    public static final double ASSOCIATE = 0.20, BOARD_SEAT = 0.25, SQUEEZE_OUT = 0.90;
    /** Cagri: piyasa fiyatinin en az %20 ustu, 30 gun; zorunlu teklif son 6 ayin en yuksek fiyatindan. */
    public static final double TENDER_MIN_PREMIUM = 0.20;
    public static final int TENDER_DAYS = 30, MANDATORY_LOOKBACK_MONTHS = 6;
    /** Kabul egrisi (prim -> serbest dolasim kabul orani). */
    public static final double[] ACCEPT_PREMIUM = { 0.0, 0.20, 0.35, 0.50, 0.80 };
    public static final double[] ACCEPT_RATE = { 0.05, 0.40, 0.65, 0.85, 0.95 };
    /** Savunma: temettu artisi kabulu dusurur; beyaz sovalye en az bu kadar yuksek teklif verir. */
    public static final double DIVIDEND_DEFENSE = 0.05, KNIGHT_STEP = 0.05;
    /** YZ savunma geri alimi: rezerv ustu nakdin %30'u, en fazla hisselerin %5'i. */
    public static final double AI_BUYBACK_CASH = 0.30, AI_BUYBACK_SHARES = 0.05;
    /** Diger sirket ortaklar teklifi hisse basina deger x bu oranin ustundeyse kabul eder. */
    public static final double HOLDER_ACCEPT = 1.10;
    /** Devralma kredisi: ticari faiz + 1 puan (Lemon Brothers 0,5 puan ucuz), hedef degerinin en fazla %50'si, 7 yil, ceyreklik. */
    public static final double ACQ_SPREAD = 0.01, ACQ_DEMIRTAS_DISCOUNT = 0.005, ACQ_MAX_SHARE = 0.50;
    public static final int ACQ_YEARS = 7;
    /** Zorunlu teklifte pay takasi: alicinin kurucu payi bu oranin altina inmez. */
    public static final double SWAP_FOUNDER_FLOOR = 0.55;
    /** Devralma kredisi geri odemesi: yilda anaparanin %5'i, kalani vade sonunda toplu (satin alma finansmaninda olagan yapi). */
    public static final double ACQ_AMORTIZATION = 0.05;
    /** Bagli ortakligin ana sirkete temettusu (kar orani, %): buyume, temkinli, nakit uret. Halka kapali olsa da odenir. */
    public static final int[] SUB_DIVIDEND = { 50, 60, 80 };
    /** Rakip YZ: aylik deneme olasiligi (stil carpani: fosil 1,5 saldirgan, dengeli 1, yesil 0,6), cagri primi araligi, ilk pay. */
    /** Elinde bagli ortaklik olan alici yeni devralmayi ancak konsolide Net borc / FAVOK bu degerin altindaysa dener (YZ ve olcum botu). */
    public static final double SERIAL_ACQ_LEVERAGE = 2.5;
    /** Grup ici borc faizi: politika faizi + bu marj (piyasa kosullari; azinlik ortaklar korunur). */
    public static final double INTRAGROUP_SPREAD = 0.01;
    /** Acik devralma kredisi olan sirket (YZ) konsolide Net borc / FAVOK bunun ustundeyken yeni santral siparisi vermez. */
    public static final double POST_ACQ_BUILD_LEVERAGE = 3.0;
    public static final double RIVAL_MNA_CHANCE = 0.08, RIVAL_TENDER_PREMIUM_MIN = 0.25, RIVAL_TENDER_PREMIUM_MAX = 0.40, RIVAL_TOEHOLD = 0.05;
}
