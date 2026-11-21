package com.energytycoon.sim;

/**
 * Sirketin ortaklik tablosu ve hisse verisi (kayitta oldugu gibi serilestirilir; sirketlere liste sirasiyla atif yapar).
 * Serbest dolasim = hisse sayisi - kurucu payi - diger sirketlerin paylari. Geri alinan hisseler iptal edilir (hazine hissesi tutulmaz).
 */
public final class Equity {
    /** Toplam (dolasimdaki) hisse sayisi ve kurucunun hissesi. */
    public double shares = FinanceBalance.SHARES, founder = FinanceBalance.SHARES;
    /** held[j]: companies.get(j) sirketinin bu sirketteki hissesi. */
    public double[] held = new double[0];
    /** Halka acik mi; gunluk hisse fiyati; temel deger (toplam). */
    public boolean listed;
    public double price, fundamental;
    /** Haber/surpriz etkisi (1 = notr), gunluk 1'e doner. */
    public double newsMult = 1;
    /** Ceyrek net kar halkasi (son 8 ceyrek) ve devam eden ceyrek. */
    public double[] qNet = new double[8];
    public int qNetCount;
    public double qNetAcc;
    /** Aylik hisse fiyati gecmisi (son 120 ay; kapalida temel deger / hisse). */
    public double[] priceMonthly = new double[0];

    // ---- halka arz ve sermaye islemleri
    /** Halka arz hazirligi: bitis saati (-1: yok), arz orani (yeni hisse / mevcut), kriz nedeniyle karar bekliyor. */
    public long ipoReadyHour = -1;
    public double ipoFraction;
    public boolean ipoCrisisPending;
    public long ipoHour = -1;
    public double ipoProceeds, ipoPrice;
    public long lastIssueHour = -1_000_000_000L;
    /** Temettu politikasi (yuzde): 0, 30, 50. */
    public int dividendPct;
    public double lastDividend;
    /** Geri alim: kalan butce. */
    public double buybackBudget;

    // ---- kontrol
    /** Bagli ortaklik ise ana sirketin indeksi (-1: degil); strateji: 0 buyume, 1 temkinli, 2 nakit uret. */
    public int parent = -1, strategy;
    /** Birlesip ana sirkete katildi (ortakliktan cikarma). */
    public boolean merged;
    /** Rekabet: son 12 ayin aylik geliri (halka), hakim durum, kamu hizmeti modunda tuketiciye iade. */
    public double[] rev12 = new double[12];
    public int revIdx;
    public boolean dominant;
    public double refunds, lastRefund;
    /** Esik bildirimleri: disc[j] = j sirketinin bu sirkette gectigi son esik indeksi (+1). */
    public int[] disc = new int[0];
    /** Kurucu pazarlik payi tohumu (0..1, aylik yenilenir). */
    public double askSeed = 0.5;
    /** Savunma modu (esik bildirimi veya cagri sonrasi) bitis saati. */
    public long defenseUntil = -1;

    public double held(int j) { return j < held.length ? held[j] : 0; }

    public void add(int j, double n) {
        if (j >= held.length) held = java.util.Arrays.copyOf(held, j + 1);
        held[j] = Math.max(0, held[j] + n);
    }

    public double othersHeld() {
        double t = 0;
        for (double v : held) t += v;
        return t;
    }

    public double free() { return Math.max(0, shares - founder - othersHeld()); }
    public double founderPct() { return shares > 0 ? founder / shares : 0; }
    public double pct(int j) { return shares > 0 ? held(j) / shares : 0; }
    public double freePct() { return shares > 0 ? free() / shares : 0; }
}
