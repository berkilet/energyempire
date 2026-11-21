package com.energytycoon.sim;

import java.util.List;

/**
 * Sermaye piyasasi: temel deger, gunluk hisse fiyati, ceyrek beklentisi ve haberler (bolum 1);
 * halka arz, bedelli sermaye artirimi, geri alim, temettu (bolum 2-3). Kamu Uretim disarida.
 */
public final class Capital {
    private final GameState s;

    Capital(GameState s) { this.s = s; }

    public int idx(Company c) { return s.companies.indexOf(c); }

    // ------------------------------------------------------------ 1. degerleme
    /** F/K carpani: politika faizine bagli (dusuk faiz -> yuksek carpan). */
    public double baseMultiple() {
        double m = FinanceBalance.PE_BASE + (FinanceBalance.PE_RATE_REF - s.macro.policyRate) * 100 * FinanceBalance.PE_PER_POINT;
        return Math.max(FinanceBalance.PE_MIN, Math.min(FinanceBalance.PE_MAX, m));
    }

    /** Not ve buyume duzeltmesi (+-%20). */
    public double multipleAdj(Company c) {
        double r = FinanceBalance.PE_RATING_ADJ[s.finance.rating(c).ordinal()];
        double g = growth(c);
        double adj = r + Math.max(-0.1, Math.min(0.1, g * FinanceBalance.PE_GROWTH_K));
        return Math.max(-FinanceBalance.PE_ADJ_MAX, Math.min(FinanceBalance.PE_ADJ_MAX, adj));
    }

    public double multiple(Company c) { return baseMultiple() * (1 + multipleAdj(c)); }

    /** Son 4 ceyregin net kari onceki 4 ceyrege gore buyume orani (8 ceyrek yoksa 0). */
    public double growth(Company c) {
        Equity e = c.eq;
        if (e.qNetCount < 8) return 0;
        double last = 0, prev = 0;
        for (int k = 0; k < 4; k++) { last += qNetAgo(e, k); prev += qNetAgo(e, k + 4); }
        return Math.max(-1, Math.min(1, (last - prev) / Math.max(Math.abs(prev), 20e6)));
    }

    /** k ceyrek onceki net kar (0 = son kapanan ceyrek). */
    public static double qNetAgo(Equity e, int k) { return e.qNet[Math.floorMod(e.qNetCount - 1 - k, 8)]; }

    /** Son 4 ceyregin toplam net kari (yetersiz veri: olan ceyrekler). */
    public static double last4Net(Equity e) {
        double t = 0;
        for (int k = 0; k < Math.min(4, e.qNetCount); k++) t += qNetAgo(e, k);
        return t;
    }

    /** Normallestirilmis yillik net kar: son 12 ayin ortalamasi x 12. */
    public double normalizedProfit(Company c) {
        int n = c.netHistory.size();
        return n == 0 ? 0 : c.trailingNet() / n * 12;
    }

    /** Hisse basina deger: halka acikta piyasa fiyati, kapalida temel deger / hisse. */
    public double perShare(Company t) { return t.eq.listed ? t.eq.price : t.eq.shares > 0 ? t.eq.fundamental / t.eq.shares : 0; }

    /** Sirketin diger sirketlerdeki paylarinin degeri (finansal yatirimlar). */
    public double holdingsValue(Company c) {
        int i = idx(c);
        double v = 0;
        for (Company t : s.companies) if (t != c && t.eq.held(i) > 0) v += t.eq.held(i) * perShare(t);
        return v;
    }

    /** Ozsermaye: nakit + tesisler (defter) + finansal yatirimlar - borc. */
    public double bookEquity(Company c) { return c.cash + s.assetValue(c) - c.debt() + holdingsValue(c); }

    /** Temel deger: ozsermaye + carpan x normallestirilmis kar (zararda kayip LOSS_MULT kat). */
    public double fundamental(Company c) {
        double p = normalizedProfit(c);
        double earn = p >= 0 ? multiple(c) * p : FinanceBalance.LOSS_MULT * p;
        return Math.max(0, bookEquity(c) + earn);
    }

    /** Sirket degeri: halka acikta fiyat x hisse, kapalida temel deger (senaryo hedefleri ve olcek karsilastirmasi). */
    public double value(Company c) { return c.eq.listed ? c.eq.price * c.eq.shares : c.eq.fundamental; }

    /** Piyasa duyarliligi: hisse endeksi / son 24 ayin ortalamasi (ussel, sinirli) + temettu destegi. */
    public double sentiment(Company c) {
        List<Double> h = s.macro.indexHistory;
        double avg = 0; int n = 0;
        for (int i = Math.max(0, h.size() - 24); i < h.size(); i++) { avg += h.get(i); n++; }
        double ratio = n == 0 || avg <= 0 ? 1 : s.macro.equityIndex / (avg / n);
        double sm = Math.pow(ratio, FinanceBalance.SENTIMENT_EXP);
        sm = Math.max(FinanceBalance.SENTIMENT_MIN, Math.min(FinanceBalance.SENTIMENT_MAX, sm));
        int dp = c.eq.dividendPct >= 50 ? 2 : c.eq.dividendPct >= 30 ? 1 : 0;
        return sm * (1 + FinanceBalance.DIVIDEND_SUPPORT[dp]);
    }

    /** Aylik: temel degerler (paylar icin iki tur), kapali sirket fiyati, sirket degeri. */
    void monthly() {
        for (int pass = 0; pass < 2; pass++)
            for (Company c : s.companies) {
                c.eq.fundamental = fundamental(c);
                if (!c.eq.listed) c.eq.price = c.eq.shares > 0 ? c.eq.fundamental / c.eq.shares : 0;
                else if (c.eq.price <= 0) c.eq.price = targetPrice(c);
            }
        for (Company c : s.companies) { c.valuation = value(c); c.stockPrice = c.eq.price; }
    }

    double targetPrice(Company c) {
        return c.eq.shares <= 0 ? 0 : c.eq.fundamental / c.eq.shares * sentiment(c) * c.eq.newsMult;
    }

    /** Gunluk: halka acik sirketlerde fiyat olusur; haber etkisi notre doner. */
    void daily() {
        for (Company c : s.companies) {
            Equity e = c.eq;
            e.newsMult = 1 + (e.newsMult - 1) * (1 - FinanceBalance.NEWS_DECAY);
            if (!e.listed || c.bankrupt) continue;
            e.price = Math.max(0.01, targetPrice(c) * (1 + FinanceBalance.PRICE_NOISE * s.rng.nextGaussian()));
            c.stockPrice = e.price; c.valuation = value(c);
        }
    }

    /** Ceyrek sonu: net kar halkasi; halka acik sirkette beklentiyle (son 4 ceyrek egilimi) kiyas, sapma fiyati sicratir. */
    void quarterly(Company c) {
        Equity e = c.eq;
        double actual = e.qNetAcc;
        e.qNetAcc = 0;
        if (e.listed && e.qNetCount >= 4) {
            double newest = qNetAgo(e, 0), oldest = qNetAgo(e, 3);
            double expected = newest + (newest - oldest) / 3;
            double denom = Math.max(Math.abs(expected), e.fundamental * 0.005 + 1e6);
            double jump = Math.max(-FinanceBalance.SURPRISE_MAX, Math.min(FinanceBalance.SURPRISE_MAX, FinanceBalance.SURPRISE_K * (actual - expected) / denom));
            if (Math.abs(jump) >= 0.02)
                news(c, jump, "çeyrek net kârı beklentinin " + (jump > 0 ? "üstünde" : "altında") + " (" + money(actual) + ", beklenti " + money(expected) + ")");
            else e.newsMult *= 1 + jump;
        }
        e.qNet[e.qNetCount % 8] = actual;
        e.qNetCount++;
    }

    /** Fiyati etkileyen haber (yalnizca halka acik sirketlerde fiyata yansir). */
    public void news(Company c, double pct, String why) {
        if (c.neutral || !c.eq.listed) return;
        c.eq.newsMult *= 1 + pct;
        if (Math.abs(pct) >= 0.03 && (c.player || s.playerHolds(c)))
            s.notify("FİNANS: " + (c.player ? "Hisseniz" : c.name + " hissesi") + " " + (pct > 0 ? "+" : "") + Math.round(pct * 100) + "%: " + why, "openfin");
    }

    static String money(double v) { return Finance.money(v); }
}
