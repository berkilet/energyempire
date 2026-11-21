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
                if (c.bankrupt) { c.eq.fundamental = 0; c.eq.price = 0; continue; } // iflas/birlesme: hisse degersiz
                c.eq.fundamental = fundamental(c);
                if (!c.eq.listed) c.eq.price = c.eq.shares > 0 ? c.eq.fundamental / c.eq.shares : 0;
                else if (c.eq.price <= 0) c.eq.price = targetPrice(c);
            }
        for (Company c : s.companies) {
            c.valuation = value(c); c.stockPrice = c.eq.price;
            // aylik hisse fiyati gecmisi (grafik): son 120 ay
            double[] h = c.eq.priceMonthly;
            double[] n = java.util.Arrays.copyOfRange(h, Math.max(0, h.length - 119), h.length + 1);
            n[n.length - 1] = c.eq.price;
            c.eq.priceMonthly = n;
        }
    }

    double targetPrice(Company c) {
        return c.eq.shares <= 0 ? 0 : c.eq.fundamental / c.eq.shares * sentiment(c) * c.eq.newsMult;
    }

    /** Gunluk: halka acik sirketlerde fiyat olusur; haber etkisi notre doner. */
    void daily() {
        for (Company c : s.companies) {
            Equity e = c.eq;
            e.newsMult = 1 + (e.newsMult - 1) * (1 - FinanceBalance.NEWS_DECAY);
            if (c.bankrupt || c.neutral) continue;
            dailyOps(c);
            if (!e.listed) continue;
            e.price = Math.max(0.01, targetPrice(c) * (1 + FinanceBalance.PRICE_NOISE * s.rng.nextGaussian()));
            c.stockPrice = e.price; c.valuation = value(c);
        }
    }

    /** Ceyrek sonu: net kar halkasi; halka acik sirkette beklentiyle (son 4 ceyrek egilimi) kiyas, sapma fiyati sicratir. */
    public void quarterly(Company c) {
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
        dividend(c, actual);
    }

    /** Fiyati etkileyen haber (yalnizca halka acik sirketlerde fiyata yansir). */
    public void news(Company c, double pct, String why) {
        if (c.neutral || !c.eq.listed) return;
        c.eq.newsMult *= 1 + pct;
        if (Math.abs(pct) >= 0.03 && (c.player || s.playerHolds(c)))
            s.notify("FİNANS: " + (c.player ? "Hisseniz" : c.name + " hissesi") + " " + (pct > 0 ? "+" : "") + Math.round(pct * 100) + "%: " + why, "openfin");
    }

    static String money(double v) { return Finance.money(v); }

    // ------------------------------------------------------------ 2. halka arz
    /** Halka arz engeli; null: arz yapilabilir. */
    public String ipoBlocker(Company c) {
        Equity e = c.eq;
        if (c.neutral) return "Kamu Üretim halka açılmaz";
        if (c.bankrupt || e.parent >= 0) return "Bağlı ortaklık veya faal olmayan şirket halka arz yapamaz";
        if (e.listed) return "Şirket zaten halka açık";
        if (e.ipoReadyHour >= 0) return "Halka arz hazırlığı sürüyor";
        if (c.quarters < FinanceBalance.IPO_MIN_QUARTERS) return "En az " + FinanceBalance.IPO_MIN_QUARTERS + " çeyrek faaliyet gerekir (şu an " + c.quarters + ")";
        if (e.qNetCount < 4 || last4Net(e) <= 0) return "Son 4 çeyrekte toplam net kâr pozitif olmalı (" + money(last4Net(e)) + ")";
        if (s.finance.rating(c).ordinal() > Rating.BB.ordinal()) return "Kredi notu en az BB olmalı (şu an " + s.finance.rating(c) + ")";
        if (s.macro.active(Macro.EventType.CRISIS)) return "Halka arz penceresi kapalı (küresel finans krizi)";
        return null;
    }

    public static double clampIpo(double f) { return Math.max(FinanceBalance.IPO_MIN, Math.min(FinanceBalance.IPO_MAX, f)); }

    /** Arz onizlemesi: { kurucu payi (sonra), en dusuk hasilat, en yuksek hasilat, yeni hisse }. */
    public double[] ipoEstimate(Company c, double fraction) {
        double f = clampIpo(fraction), n = c.eq.shares * f, ps = c.eq.fundamental / c.eq.shares;
        return new double[] { c.eq.founder / (c.eq.shares + n), n * ps * FinanceBalance.IPO_PRICE_LOW * (1 - FinanceBalance.ISSUE_COST),
            n * ps * FinanceBalance.IPO_PRICE_HIGH * (1 - FinanceBalance.ISSUE_COST), n };
    }

    /** Halka arz hazirligini baslatir (6 ay). Null: tamam. */
    public String startIpo(Company c, double fraction) {
        String why = ipoBlocker(c);
        if (why != null) return why;
        c.eq.ipoFraction = clampIpo(fraction);
        c.eq.ipoReadyHour = s.hour + (long) FinanceBalance.IPO_PREP_MONTHS * Balance.HOURS_PER_MONTH;
        c.eq.ipoCrisisPending = false;
        if (c.player) s.notify("FİNANS: Halka arz hazırlığı başladı (aracı kurum, izahname, düzenleyici onayı): 6 ay. Arz oranı %" + Math.round(c.eq.ipoFraction * 100) + ".", "openfin");
        else s.notify("FİNANS: " + c.name + " halka arz hazırlığına başladı.");
        return null;
    }

    public boolean ipoPreparing(Company c) { return c.eq.ipoReadyHour >= 0; }

    /** Kriz sirasinda: arzi ertele (3 ay) veya iptal et. */
    public void postponeIpo(Company c) {
        c.eq.ipoReadyHour = Math.max(c.eq.ipoReadyHour, s.hour) + (long) FinanceBalance.IPO_POSTPONE_MONTHS * Balance.HOURS_PER_MONTH;
        c.eq.ipoCrisisPending = false;
        if (c.player) s.notify("FİNANS: Halka arz " + FinanceBalance.IPO_POSTPONE_MONTHS + " ay ertelendi.", "openfin");
    }

    public void cancelIpo(Company c) {
        c.eq.ipoReadyHour = -1; c.eq.ipoCrisisPending = false;
        if (c.player) s.notify("FİNANS: Halka arz iptal edildi.", "openfin");
    }

    /** Arz gunu talebi (0..1): piyasa duyarliligi ve rastgele talep. */
    double ipoDemand(Company c) {
        double d = 0.5 + (sentiment(c) - 1) * 2 + 0.15 * s.rng.nextGaussian();
        return Math.max(0, Math.min(1, d));
    }

    private void completeIpo(Company c) {
        Equity e = c.eq;
        double ps = e.fundamental / e.shares;
        double d = ipoDemand(c);
        double price = ps * (FinanceBalance.IPO_PRICE_LOW + (FinanceBalance.IPO_PRICE_HIGH - FinanceBalance.IPO_PRICE_LOW) * d);
        double n = e.shares * e.ipoFraction, gross = n * price, cost = gross * FinanceBalance.ISSUE_COST;
        e.shares += n; e.listed = true; e.price = price; e.newsMult = 1;
        e.ipoReadyHour = -1; e.ipoHour = s.hour; e.ipoPrice = price; e.ipoProceeds = gross - cost; e.lastIssueHour = s.hour;
        c.cash += gross - cost;
        c.valuation = value(c); c.stockPrice = price;
        s.notify("FİNANS: " + (c.player ? "Şirketiniz" : c.name) + " halka açıldı: " + Math.round(n / 1e5) / 10.0 + "M hisse, hisse başı " + String.format(java.util.Locale.ROOT, "%.2f $", price)
            + ", hasılat " + money(gross - cost) + " (maliyet " + money(cost) + "). Kurucu payı %" + Math.round(e.founderPct() * 1000) / 10.0 + ".", c.player ? "openfin" : null);
    }

    // ------------------------------------------------------------ 3. sermaye islemleri
    /** Bedelli sermaye artirimi engeli. */
    public String issueBlocker(Company c) {
        if (c.neutral) return "Kamu Üretim sermaye artırımı yapmaz";
        if (!c.eq.listed) return "Yalnızca halka açık şirket yeni hisse basabilir";
        long next = c.eq.lastIssueHour + (long) FinanceBalance.RIGHTS_PER_YEARS * Balance.HOURS_PER_YEAR;
        if (s.hour < next) return "Yılda en fazla bir ihraç: " + ((next - s.hour) / Balance.HOURS_PER_MONTH + 1) + " ay sonra";
        return null;
    }

    public static double clampIssue(double f) { return Math.max(FinanceBalance.RIGHTS_MIN, Math.min(FinanceBalance.RIGHTS_MAX, f)); }

    /** Iskonto: talep zayifsa yuksek (%10-20). */
    double issueDiscount(Company c) {
        double d = Math.max(0, Math.min(1, 0.5 + (sentiment(c) - 1) * 2));
        return FinanceBalance.RIGHTS_DISCOUNT_MAX - (FinanceBalance.RIGHTS_DISCOUNT_MAX - FinanceBalance.RIGHTS_DISCOUNT_MIN) * d;
    }

    /** Onizleme: { kurucu payi sonra, net hasilat, iskonto, duyuru dususu, ihrac fiyati, yeni hisse }. */
    public double[] issueEstimate(Company c, double fraction) {
        double f = clampIssue(fraction), n = c.eq.shares * f, disc = issueDiscount(c), px = c.eq.price * (1 - disc);
        double drop = FinanceBalance.RIGHTS_DROP_MIN + (FinanceBalance.RIGHTS_DROP_MAX - FinanceBalance.RIGHTS_DROP_MIN) * (f - FinanceBalance.RIGHTS_MIN) / (FinanceBalance.RIGHTS_MAX - FinanceBalance.RIGHTS_MIN);
        return new double[] { c.eq.founder / (c.eq.shares + n), n * px * (1 - FinanceBalance.ISSUE_COST), disc, drop, px, n };
    }

    /** Bedelli sermaye artirimi (yeni hisse). Null: tamam. */
    public String issue(Company c, double fraction) {
        String why = issueBlocker(c);
        if (why != null) return why;
        double[] q = issueEstimate(c, fraction);
        c.eq.shares += q[5];
        c.cash += q[1];
        c.eq.lastIssueHour = s.hour;
        c.eq.newsMult *= 1 - q[3];
        c.eq.price *= 1 - q[3];
        s.notify("FİNANS: " + (c.player ? "Sermaye artırımı" : c.name + " sermaye artırımı") + ": " + Math.round(q[5] / 1e5) / 10.0 + "M yeni hisse, "
            + Math.round(q[2] * 100) + "% iskontolu, net " + money(q[1]) + ". Hisse duyuruyla %" + Math.round(q[3] * 100) + " düştü; kurucu payı %" + Math.round(c.eq.founderPct() * 1000) / 10.0 + ".", c.player ? "openfin" : null);
        return null;
    }

    /** Geri alim butcesi (gunluk en fazla serbest dolasimin %2'si; alinan hisseler iptal edilir). */
    public String setBuyback(Company c, double budget) {
        if (!c.eq.listed) return "Yalnızca halka açık şirket geri alım yapar";
        c.eq.buybackBudget = Math.max(0, budget);
        return null;
    }

    /** Hisse alimi fiyat etkisi: alinan hisse / toplam hisse basina PRICE_IMPACT. */
    void impact(Company t, double sharesBought) {
        double up = FinanceBalance.PRICE_IMPACT * sharesBought / t.eq.shares;
        t.eq.newsMult *= 1 + up; t.eq.price *= 1 + up;
    }

    private void buybacks(Company c) {
        Equity e = c.eq;
        if (e.buybackBudget <= 1 || !e.listed) return;
        double n = Math.min(e.free() * FinanceBalance.DAILY_TRADE_MAX, Math.min(e.buybackBudget, Math.max(0, c.cash)) / e.price);
        if (n <= 0) { e.buybackBudget = 0; return; }
        double cost = n * e.price;
        c.cash -= cost; e.buybackBudget -= cost; e.shares -= n;
        impact(c, n);
        s.mna.buybackDuringOffer(c, FinanceBalance.PRICE_IMPACT * n / e.shares);
        if (e.buybackBudget <= 1 && c.player) s.notify("FİNANS: Geri alım tamamlandı. Kurucu payı %" + Math.round(e.founderPct() * 1000) / 10.0 + ".", "openfin");
    }

    /** Temettu engeli (taahhut ihlali bayragi, gecikme). */
    public String dividendBlocker(Company c) {
        if (c.dividendBan) return "Kredi taahhüdü ihlali: temettü dağıtılamaz";
        if (s.finance.anyLate(c)) return "Geciken kredi taksiti varken temettü dağıtılamaz";
        return null;
    }

    public void setDividend(Company c, int pct) { c.eq.dividendPct = pct >= 50 ? 50 : pct >= 30 ? 30 : 0; }

    /** Ceyrek sonu temettu: karin politika orani; kurucu payina dusen kisim sirkette kalir, diger ortaklara odenir. */
    private void dividend(Company c, double qNet) {
        Equity e = c.eq;
        e.lastDividend = 0;
        if (e.dividendPct <= 0 || qNet <= 0 || c.neutral || dividendBlocker(c) != null) return;
        double total = qNet * e.dividendPct / 100.0;
        double out = total * (1 - e.founderPct());
        if (out <= 0 || c.cash < out) return;
        c.cash -= out;
        for (int j = 0; j < s.companies.size(); j++) {
            double h = e.held(j);
            if (h <= 0) continue;
            Company o = s.companies.get(j);
            double amt = total * h / e.shares;
            o.cash += amt; o.dividendIncome += amt;
        }
        e.lastDividend = total;
    }

    /** Rakip YZ: kosullar saglanir ve buyume icin sermaye gerekirse halka arz (oran %25-40); krizde erteler. */
    void rivalMonthly(Company c) {
        Equity e = c.eq;
        if (ipoPreparing(c) || e.listed || ipoBlocker(c) != null) return;
        double room = Math.max(0, s.finance.creditLimit(c) - s.finance.limitUsage(c));
        double need = Math.max(FinanceBalance.RIVAL_IPO_NEED, FinanceBalance.RIVAL_IPO_NEED_ASSETS * s.assetValue(c));
        if (c.cash + room >= need) return;
        startIpo(c, FinanceBalance.RIVAL_IPO_MIN + (FinanceBalance.RIVAL_IPO_MAX - FinanceBalance.RIVAL_IPO_MIN) * s.rng.nextDouble());
    }

    /** Gunluk sermaye islemleri: halka arz takvimi, geri alim. */
    private void dailyOps(Company c) {
        Equity e = c.eq;
        if (e.ipoReadyHour >= 0) {
            boolean crisis = s.macro.active(Macro.EventType.CRISIS);
            if (crisis && !e.ipoCrisisPending) {
                if (c.player && !s.aiPlayer) {
                    e.ipoCrisisPending = true;
                    s.notify("FİNANS: Halka arz hazırlığı sırasında küresel finans krizi çıktı. Arzı erteleyin veya iptal edin (Finans → Sermaye).", "ipocrisis");
                } else postponeIpo(c);
            } else if (!crisis && !e.ipoCrisisPending && s.hour >= e.ipoReadyHour) {
                if (ipoBlockerAtListing(c) == null) completeIpo(c);
                else postponeIpo(c);
            }
        }
        buybacks(c);
    }

    /** Arz gununde: kar ve not kosullari hala gecerli mi. */
    String ipoBlockerAtListing(Company c) {
        if (c.bankrupt) return "faal değil";
        if (last4Net(c.eq) <= 0) return "son 4 çeyrek zarar";
        if (s.finance.rating(c).ordinal() > Rating.BB.ordinal()) return "not BB altı";
        return null;
    }
}
