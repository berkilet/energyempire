package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pay alimi ve devralma (oyuncu ve rakipler ayni kurallar): kurucudan pay alimi (muzakere), piyasadan alim ve bildirim esikleri,
 * pay alim teklifi (cagri) ve savunma, zorunlu pay alim teklifi, rekabet onayi sonrasi kontrol (bagli ortaklik),
 * ortakliktan cikarma ve birlesme. Oyuncunun kurucu payi satilmaz (oyuncu = sirket); Kamu disarida.
 */
public final class Mna {
    private final GameState s;

    Mna(GameState s) { this.s = s; }

    DealBook d() { return s.deals; }
    int idx(Company c) { return s.companies.indexOf(c); }
    Company co(int i) { return s.companies.get(i); }
    static String money(double v) { return Finance.money(v); }
    static String pct(double v) { return String.format(Locale.ROOT, "%.1f", v * 100).replace('.', ','); }

    public boolean active(Company c) { return c != null && !c.neutral && !c.bankrupt && !c.eq.merged; }

    /** Grubun (ana sirket + bagli ortakliklar) hedefteki toplam hisse orani. */
    public double groupStake(Company buyer, Company target) {
        Set<Company> g = s.competition.group(buyer);
        double h = 0;
        for (Company m : g) if (m != target) h += target.eq.held(idx(m));
        return target.eq.shares > 0 ? h / target.eq.shares : 0;
    }

    public double stake(Company holder, Company target) { return target.eq.pct(idx(holder)); }

    /** Hedefi kontrol eden grup (onayli bagli ortaklik) mi. */
    public boolean controls(Company buyer, Company target) { return target.eq.parent >= 0 && s.competition.top(target) == s.competition.top(buyer); }

    /** Zor durum: not B veya alti, gecikme ya da temerrut. */
    public boolean distressed(Company c) {
        return s.finance.rating(c).ordinal() >= Rating.B.ordinal() || s.finance.anyLate(c) || s.competition.failing(c);
    }

    /** 12 aylik takvimde nakit acigi ongoruluyor mu. */
    public boolean cashNeed(Company c) {
        double[] p = s.finance.cashProjection(c, s.finance.schedule12(c));
        for (double v : p) if (v < 0) return true;
        return false;
    }

    // ------------------------------------------------------------ kurucudan pay alimi
    public static final class Ask {
        public double premium, perShare;
        public boolean controlLoss;
        public String reason;
    }

    /** Kurucunun istedigi fiyat: temel deger x (1 + istenen prim) ve gerekcesi. */
    public Ask founderAsk(Company target, double shares) {
        Ask a = new Ask();
        Equity e = target.eq;
        double seed = e.askSeed;
        a.controlLoss = e.founder >= e.shares * FinanceBalance.CONTROL && (e.founder - shares) < e.shares * FinanceBalance.CONTROL;
        if (distressed(target)) {
            a.premium = FinanceBalance.ASK_DISTRESS_MAX * seed;
            a.reason = "Şirket zor durumda (not " + s.finance.rating(target) + (s.finance.anyLate(target) ? ", gecikme" : "") + "): kurucu düşük primle satmaya razı.";
        } else if (a.controlLoss) {
            a.premium = FinanceBalance.ASK_CONTROL_MIN + (FinanceBalance.ASK_CONTROL_MAX - FinanceBalance.ASK_CONTROL_MIN) * seed;
            a.reason = "Kurucu kontrolü kaybetmek istemiyor; %" + Math.round(a.premium * 100) + " prim altında satmaz.";
        } else if (cashNeed(target)) {
            a.premium = FinanceBalance.ASK_CASH_MIN + (FinanceBalance.ASK_CASH_MAX - FinanceBalance.ASK_CASH_MIN) * seed;
            a.reason = "Şirketin 12 aylık takviminde nakit açığı var: kurucu ılımlı prim istiyor.";
        } else {
            a.premium = FinanceBalance.ASK_BASE_MIN + (FinanceBalance.ASK_BASE_MAX - FinanceBalance.ASK_BASE_MIN) * seed;
            a.reason = "Kontrol kurucuda kalıyor; olağan prim isteniyor.";
        }
        a.perShare = fundamentalPerShare(target) * (1 + a.premium);
        return a;
    }

    public double fundamentalPerShare(Company t) { return t.eq.shares > 0 ? t.eq.fundamental / t.eq.shares : 0; }

    public String founderOfferBlocker(Company buyer, Company target, double shares) {
        if (!active(buyer) || !active(target)) return "Taraflardan biri faal değil";
        if (target.neutral) return "Kamu Üretim satın alınamaz";
        if (target.player) return "Oyuncunun kurucu payı satılmaz (yalnızca hisse ihracıyla azalır)";
        if (s.competition.sameGroup(buyer, target)) return "Aynı grup";
        if (buyer.eq.parent >= 0) return "Bağlı ortaklık devralma yapamaz";
        if (shares <= 0) return "Hisse adedi girin";
        if (shares > target.eq.founder + 1e-6) return "Kurucunun " + Math.round(target.eq.founder / 1e5) / 10.0 + "M hissesi var";
        if (s.competition.pendingReview(target) != null) return "Hedef için rekabet incelemesi sürüyor";
        if (wouldControl(buyer, target, shares) && s.competition.preview(buyer, target).outcome == Competition.REJECT) return "Rekabet otoritesi reddeder: " + s.competition.preview(buyer, target).reason;
        return null;
    }

    boolean wouldControl(Company buyer, Company target, double extra) {
        return groupStake(buyer, target) + extra / target.eq.shares > FinanceBalance.CONTROL && !controls(buyer, target);
    }

    public static final int ACCEPT = 0, COUNTER = 1, REJECT = 2;

    public static final class Reply {
        public int kind;
        public double perShare;
        public String reason;
    }

    /** Kurucunun yaniti: kabul, bir kez karsi teklif (ilk turda) veya ret; gerekce kisa bir cumle. */
    public Reply founderReply(Company target, double shares, double offerPerShare, boolean finalRound) {
        Ask a = founderAsk(target, shares);
        Reply r = new Reply();
        r.perShare = a.perShare;
        if (offerPerShare >= a.perShare - 1e-9) { r.kind = ACCEPT; r.reason = "Kurucu teklifi kabul etti. " + a.reason; return r; }
        if (!finalRound && offerPerShare >= a.perShare * FinanceBalance.COUNTER_BAND) {
            r.kind = COUNTER; r.reason = a.reason + " Karşı teklif: hisse başı " + String.format(Locale.ROOT, "%.2f $", a.perShare) + ".";
            return r;
        }
        r.kind = REJECT;
        r.reason = finalRound ? "Kurucu son teklifi reddetti. " + a.reason : "Teklif istenen fiyatın çok altında. " + a.reason;
        return r;
    }

    /** Finansman: nakit, devralma kredisi (banka, tutar), pay takasi (alici hissesiyle odenen tutar). */
    public static final class Financing {
        public double cash, loan, swapValue;
        public Bank bank;
        public double total() { return cash + loan + swapValue; }
    }

    /** Finansman gecerli mi (null: gecerli). */
    public String financingBlocker(Company buyer, Company target, Financing f, double cost) {
        if (f.cash > buyer.cash + 1) return "Nakit yetersiz (" + money(buyer.cash) + ")";
        if (f.loan > 0 && (f.bank == null || s.finance.acquisitionRefusal(buyer, f.bank, f.loan, target) != null))
            return "Devralma kredisi: " + (f.bank == null ? "banka seçin" : s.finance.acquisitionRefusal(buyer, f.bank, f.loan, target));
        if (f.swapValue > 0 && !buyer.eq.listed) return "Pay takası yalnızca halka açık şirketin hissesiyle yapılabilir";
        if (f.total() < cost - 1) return "Finansman yetersiz: " + money(f.total()) + " < " + money(cost);
        return null;
    }

    /** Kurucudan satin alim (anlasilan fiyat). Null: tamam. */
    public String buyFromFounder(Company buyer, Company target, double shares, double perShare, Financing f) {
        String why = founderOfferBlocker(buyer, target, shares);
        if (why == null) why = financingBlocker(buyer, target, f, shares * perShare);
        if (why != null) return why;
        pay(buyer, target, shares * perShare, f, "kurucu");
        double before = groupStake(buyer, target);
        target.eq.founder -= shares;
        target.eq.add(idx(buyer), shares);
        record(buyer, target, perShare);
        s.notify("PAY: " + buyer.name + ", " + target.name + " kurucusundan %" + pct(shares / target.eq.shares) + " pay aldı (hisse başı "
            + String.format(Locale.ROOT, "%.2f $", perShare) + ", toplam " + money(shares * perShare) + ").", involved(buyer, target) ? "openmna" : null);
        thresholds(buyer, target, before);
        afterStakeChange(buyer, target, false);
        return null;
    }

    /** Odeme: nakit, kredi (imzalanir), pay takasi (alici yeni hisse basar, serbest dolasima). */
    void pay(Company buyer, Company target, double amount, Financing f, String to) {
        double left = amount;
        double c = Math.min(left, Math.min(f.cash, Math.max(0, buyer.cash)));
        buyer.cash -= c; left -= c;
        if (left > 1 && f.loan > 0 && f.bank != null) {
            double l = Math.min(left, f.loan);
            if (s.finance.signAcquisition(buyer, f.bank, l, target) != null) { buyer.cash -= l; left -= l; }
        }
        if (left > 1 && f.swapValue > 0 && buyer.eq.listed && buyer.eq.price > 0) {
            double v = Math.min(left, f.swapValue);
            buyer.eq.shares += v / buyer.eq.price;
            left -= v;
        }
        if (left > 1) buyer.cash -= left; // kalan nakitten (gerekirse isletme kredisi otomatik)
    }

    void record(Company buyer, Company target, double price) {
        DealBook.Purchase p = new DealBook.Purchase();
        p.buyer = idx(buyer); p.target = idx(target); p.hour = s.hour; p.price = price;
        d().purchases.add(p);
    }

    /** Son 6 ayda grubun hedef icin odedigi en yuksek hisse fiyati. */
    public double maxPaid(Company buyer, Company target) {
        Set<Company> g = s.competition.group(buyer);
        double m = 0;
        long from = s.hour - (long) FinanceBalance.MANDATORY_LOOKBACK_MONTHS * Balance.HOURS_PER_MONTH;
        for (DealBook.Purchase p : d().purchases) if (p.target == idx(target) && p.hour >= from && g.contains(co(p.buyer))) m = Math.max(m, p.price);
        return m;
    }

    boolean involved(Company a, Company b) {
        Company pt = s.competition.top(s.player);
        return s.competition.top(a) == pt || s.competition.top(b) == pt || s.playerHolds(b);
    }

    // ------------------------------------------------------------ piyasadan alim ve satis
    public String marketBlocker(Company buyer, Company target, double shares) {
        if (!active(buyer) || !active(target)) return "Taraflardan biri faal değil";
        if (!target.eq.listed) return "Hedef halka açık değil: yalnızca kurucuya teklif götürülebilir";
        if (s.competition.sameGroup(buyer, target)) return "Aynı grup";
        if (buyer.eq.parent >= 0) return "Bağlı ortaklık pay toplayamaz";
        if (shares <= 0) return "Hisse adedi girin";
        if (shares > target.eq.free() + 1e-6) return "Serbest dolaşımda " + Math.round(target.eq.free() / 1e5) / 10.0 + "M hisse var";
        if (shares * target.eq.price > buyer.cash) return "Nakit yetersiz (yaklaşık " + money(shares * target.eq.price) + ")";
        if (wouldControl(buyer, target, shares)) {
            if (s.competition.pendingReview(target) != null) return "Hedef için rekabet incelemesi sürüyor";
            Competition.Preview p = s.competition.preview(buyer, target);
            if (p.outcome == Competition.REJECT) return "Rekabet otoritesi reddeder: " + p.reason;
        }
        return null;
    }

    public String placeOrder(Company buyer, Company target, double shares) {
        String why = marketBlocker(buyer, target, shares);
        if (why != null) return why;
        DealBook.Order o = null;
        for (DealBook.Order x : d().orders) if (x.buyer == idx(buyer) && x.target == idx(target)) o = x;
        if (o == null) { o = new DealBook.Order(); o.buyer = idx(buyer); o.target = idx(target); d().orders.add(o); }
        o.remaining += shares;
        return null;
    }

    public DealBook.Order order(Company buyer, Company target) {
        for (DealBook.Order x : d().orders) if (x.buyer == idx(buyer) && x.target == idx(target)) return x;
        return null;
    }

    public void cancelOrder(Company buyer, Company target) { d().orders.removeIf(o -> o.buyer == idx(buyer) && o.target == idx(target)); }

    /** Gunluk emir isleme: hisselerin en fazla %2'si; her %1 alim fiyati ~%1,5 yukseltir. */
    void executeOrders() {
        for (DealBook.Order o : new ArrayList<>(d().orders)) {
            Company b = co(o.buyer), t = co(o.target);
            if (!active(b) || !active(t) || !t.eq.listed || s.competition.sameGroup(b, t)) { d().orders.remove(o); continue; }
            double n = Math.min(o.remaining, Math.min(t.eq.shares * FinanceBalance.DAILY_TRADE_MAX, t.eq.free()));
            if (n > 0 && wouldControl(b, t, n)) {
                Competition.Preview p = s.competition.preview(b, t);
                if (p.outcome == Competition.REJECT || s.competition.pendingReview(t) != null) {
                    // kontrol esiginde durur: yalnizca %50'ye kadar
                    n = Math.max(0, FinanceBalance.CONTROL * t.eq.shares - groupStake(b, t) * t.eq.shares - 1);
                    o.remaining = Math.min(o.remaining, n);
                }
            }
            n = Math.min(n, Math.max(0, b.cash) / t.eq.price);
            if (n <= 1) { if (o.remaining <= 1 || b.cash <= 0 || t.eq.free() <= 1) d().orders.remove(o); continue; }
            double before = groupStake(b, t);
            double cost = n * t.eq.price;
            b.cash -= cost;
            t.eq.add(o.buyer, n);
            record(b, t, t.eq.price);
            s.capital.impact(t, n);
            o.remaining -= n;
            thresholds(b, t, before);
            afterStakeChange(b, t, false);
            if (o.remaining <= 1) { d().orders.remove(o); if (b.player) s.notify("PAY: " + t.name + " alım emriniz tamamlandı (payınız %" + pct(stake(b, t)) + ").", "openmna"); }
        }
    }

    /** Pay satisi (piyasaya): fiyat etkisi asagi, tek seferde. */
    public String sellShares(Company holder, Company target, double shares) {
        double h = target.eq.held(idx(holder));
        if (shares <= 0 || shares > h + 1e-6) return "Elinizde " + Math.round(h / 1e5) / 10.0 + "M hisse var";
        if (!target.eq.listed) return "Halka kapalı şirketin payı piyasada satılamaz";
        if (controls(holder, target) && (h - shares) / target.eq.shares <= FinanceBalance.CONTROL) return "Bağlı ortaklıkta kontrol payı satılamaz";
        double px = target.eq.price * (1 - FinanceBalance.PRICE_IMPACT * shares / target.eq.shares / 2);
        holder.cash += shares * px;
        target.eq.add(idx(holder), -shares);
        s.capital.impact(target, -shares / 2);
        return null;
    }

    /** Esik bildirimleri: %5, 10, 15, 20, 25, 33, 50, 67. Hedef hissesi %5-10 yukselir; hedef YZ savunma moduna gecer. */
    void thresholds(Company buyer, Company target, double before) {
        double after = groupStake(buyer, target);
        int bi = idx(s.competition.top(buyer));
        int last = bi < target.eq.disc.length ? target.eq.disc[bi] : 0, now = 0;
        for (int k = 0; k < FinanceBalance.DISCLOSURE.length; k++) if (after >= FinanceBalance.DISCLOSURE[k] - 1e-9) now = k + 1;
        if (now <= last) return;
        if (bi >= target.eq.disc.length) target.eq.disc = java.util.Arrays.copyOf(target.eq.disc, bi + 1);
        target.eq.disc[bi] = now;
        double thr = FinanceBalance.DISCLOSURE[now - 1];
        double jump = FinanceBalance.DISCLOSE_JUMP_MIN + (FinanceBalance.DISCLOSE_JUMP_MAX - FinanceBalance.DISCLOSE_JUMP_MIN) * s.rng.nextDouble();
        if (target.eq.listed) { target.eq.newsMult *= 1 + jump; target.eq.price *= 1 + jump; }
        target.eq.defenseUntil = s.hour + FinanceBalance.DEFENSE_DAYS * 24L;
        boolean pl = target.player || buyer.player;
        s.notify("PAY BİLDİRİMİ: " + s.competition.top(buyer).name + ", " + (target.player ? "şirketinizde" : target.name + "'de") + " %" + Math.round(thr * 100)
            + " eşiğini geçti (pay %" + pct(after) + ")." + (target.eq.listed ? " " + target.name + " hissesi %" + Math.round(jump * 100) + " yükseldi." : ""), pl || involved(buyer, target) ? "openmna" : null);
        if (!target.player || s.aiPlayer) aiDefense(target, false);
    }

    // ------------------------------------------------------------ kontrol esigi
    /** Pay degisimi sonrasi: grup %50'yi gectiyse rekabet basvurusu ve (cagri disinda) zorunlu pay alim teklifi. */
    void afterStakeChange(Company buyer, Company target, boolean viaTender) {
        if (controls(buyer, target) || groupStake(buyer, target) <= FinanceBalance.CONTROL) {
            if (controls(buyer, target) && groupStake(buyer, target) >= FinanceBalance.SQUEEZE_OUT) squeezeOut(s.competition.top(buyer), target);
            return;
        }
        Company acq = s.competition.top(buyer);
        if (s.competition.pendingReview(target) == null) {
            Competition.Preview p = s.competition.preview(acq, target);
            boolean accept = acq.player && !s.aiPlayer ? true : s.competition.aiAcceptsCommitment(p); // oyuncu onizlemeyi gorup islemi onayladi
            s.competition.file(acq, target, accept);
        }
        if (!viaTender && openOffer(target) == null) mandatoryOffer(acq, target);
    }

    /** Zorunlu pay alim teklifi: son 6 ayin en yuksek fiyatindan, 30 gun. */
    void mandatoryOffer(Company acq, Company target) {
        double others = target.eq.shares * (1 - groupStake(acq, target));
        if (others <= 1) return;
        DealBook.TenderOffer t = new DealBook.TenderOffer();
        t.id = d().nextId++; t.bidder = idx(acq); t.target = idx(target); t.mandatory = true;
        t.price = Math.max(maxPaid(acq, target), target.eq.listed ? target.eq.price : fundamentalPerShare(target));
        t.prePrice = target.eq.listed ? target.eq.price : fundamentalPerShare(target);
        t.startHour = s.hour; t.endHour = s.hour + FinanceBalance.TENDER_DAYS * 24L;
        t.cash = Math.max(0, acq.cash);
        Bank b = s.finance.bestAcquisitionBank(acq, 10e6, target);
        if (b != null) { t.loanBank = b.ordinal() + 1; t.loanApproved = s.finance.maxAcquisitionLoan(acq, b, target); }
        d().offers.add(t);
        s.notify("ZORUNLU PAY ALIM TEKLİFİ: " + acq.name + ", " + target.name + " ortaklarına hisse başı " + String.format(Locale.ROOT, "%.2f $", t.price)
            + " teklif etti (30 gün).", involved(acq, target) ? "openmna" : null);
    }

    // ------------------------------------------------------------ pay alim teklifi (cagri)
    public DealBook.TenderOffer openOffer(Company target) {
        for (DealBook.TenderOffer t : d().offers) if (!t.closed && t.target == idx(target)) return t;
        return null;
    }

    public List<DealBook.TenderOffer> openOffers(Company target) {
        List<DealBook.TenderOffer> r = new ArrayList<>();
        for (DealBook.TenderOffer t : d().offers) if (!t.closed && t.target == idx(target)) r.add(t);
        return r;
    }

    /** Teklif tutari: alici grubu disindaki serbest dolasim + diger ortaklar (+ fiyat kurucunun istedigini karsiliyorsa kurucu payi). */
    public double tenderShares(Company bidder, Company target, double price) {
        double n = target.eq.free();
        Set<Company> g = s.competition.group(bidder);
        for (int j = 0; j < s.companies.size(); j++) if (!g.contains(co(j))) n += target.eq.held(j);
        if (!target.player && target.eq.founder > 0 && price >= founderAsk(target, target.eq.founder).perShare) n += target.eq.founder;
        return n;
    }

    public String tenderBlocker(Company bidder, Company target, double price, Financing f, boolean knight) {
        if (!active(bidder) || !active(target)) return "Taraflardan biri faal değil";
        if (!target.eq.listed) return "Çağrı yalnızca halka açık şirkete yapılır (kapalı şirkette kurucuya teklif götürün)";
        if (s.competition.sameGroup(bidder, target)) return "Aynı grup";
        if (bidder.eq.parent >= 0) return "Bağlı ortaklık çağrı yapamaz";
        if (target.eq.founderPct() >= FinanceBalance.CONTROL) return "Kurucu payı %" + Math.round(target.eq.founderPct() * 100) + ": şirket dağınık ortaklı değil, düşmanca çağrıya kapalı (kurucuya teklif götürün)";
        DealBook.TenderOffer open = openOffer(target);
        if (open != null && !knight) return "Hedefe açık bir teklif var (" + co(open.bidder).name + ")";
        if (knight && open != null && price < open.price * (1 + FinanceBalance.KNIGHT_STEP) - 1e-9) return "Rakip tekliften en az %5 yüksek olmalı";
        double min = target.eq.price * (1 + FinanceBalance.TENDER_MIN_PREMIUM);
        if (!knight && price < min - 1e-9) return "Teklif piyasa fiyatının en az %20 üstünde olmalı (≥ " + String.format(Locale.ROOT, "%.2f $", min) + ")";
        if (s.competition.pendingReview(target) != null) return "Hedef için rekabet incelemesi sürüyor";
        Competition.Preview p = s.competition.preview(bidder, target);
        if (p.outcome == Competition.REJECT) return "Rekabet otoritesi reddeder: " + p.reason;
        return financingBlocker(bidder, target, f, price * tenderShares(bidder, target, price));
    }

    /** Cagri baslat (30 gun). Null: tamam. */
    public String launchTender(Company bidder, Company target, double price, Financing f) {
        boolean knight = openOffer(target) != null;
        String why = tenderBlocker(bidder, target, price, f, knight);
        if (why != null) return why;
        DealBook.TenderOffer t = new DealBook.TenderOffer();
        t.id = d().nextId++; t.bidder = idx(bidder); t.target = idx(target); t.price = price;
        DealBook.TenderOffer first = openOffer(target);
        t.prePrice = first != null ? first.prePrice : target.eq.price;
        t.knight = knight;
        t.startHour = s.hour; t.endHour = first != null ? Math.max(first.endHour, s.hour + 10 * 24L) : s.hour + FinanceBalance.TENDER_DAYS * 24L;
        if (first != null) for (DealBook.TenderOffer o : openOffers(target)) o.endHour = t.endHour;
        t.cash = f.cash; t.loanApproved = f.loan; t.loanBank = f.bank == null ? 0 : f.bank.ordinal() + 1; t.swapShares = f.swapValue;
        d().offers.add(t);
        s.notify("ÇAĞRI: " + bidder.name + ", " + (target.player ? "şirketiniz" : target.name) + " için hisse başı " + String.format(Locale.ROOT, "%.2f $", price)
            + " pay alım teklifi yaptı (piyasa " + String.format(Locale.ROOT, "%.2f $", t.prePrice) + ", prim %" + Math.round((price / t.prePrice - 1) * 100) + ", 30 gün).",
            target.player && !s.aiPlayer ? "defense" : involved(bidder, target) ? "openmna" : null);
        if (!target.player || s.aiPlayer) aiDefense(target, true);
        return null;
    }

    /** Serbest dolasim kabul orani (prim -> oran, dogrusal). */
    public static double acceptance(double premium) {
        double[] x = FinanceBalance.ACCEPT_PREMIUM, y = FinanceBalance.ACCEPT_RATE;
        if (premium <= x[0]) return y[0];
        for (int i = 1; i < x.length; i++) if (premium <= x[i]) return y[i - 1] + (y[i] - y[i - 1]) * (premium - x[i - 1]) / (x[i] - x[i - 1]);
        return y[y.length - 1];
    }

    /** Teklifin etkin primi: savunma etkileri (geri alim fiyat etkisi, temettu artisi) dusulur. */
    public double effectivePremium(DealBook.TenderOffer t) {
        return t.price / t.prePrice - 1 - t.defenseUplift - (t.dividendDefense ? FinanceBalance.DIVIDEND_DEFENSE : 0);
    }

    // ---- savunma (hedef)
    public String defendBuyback(Company target, double budget) {
        return s.capital.setBuyback(target, budget);
    }

    public String defendDividend(Company target) {
        String why = s.capital.dividendBlocker(target);
        if (why != null) return why;
        if (target.eq.dividendPct >= 50) return "Temettü zaten en yüksek oranda (%50)";
        s.capital.setDividend(target, 50);
        for (DealBook.TenderOffer t : openOffers(target)) t.dividendDefense = true;
        s.notify("SAVUNMA: " + target.name + " temettü oranını %50'ye çıkardı.", involved(target, target) ? "openmna" : null);
        return null;
    }

    /** Beyaz sovalye daveti: davet edilen YZ, finansmani yetiyor ve otorite reddetmiyorsa %5 yuksek karsi teklif verir. */
    public String inviteKnight(Company target, Company knight) {
        DealBook.TenderOffer hostile = openOffer(target);
        if (hostile == null) return "Açık teklif yok";
        if (knight == target || s.competition.sameGroup(knight, co(hostile.bidder))) return "Bu şirket davet edilemez";
        for (DealBook.TenderOffer t : openOffers(target)) if (t.knightInvited) return "Beyaz şövalye zaten davet edildi";
        hostile.knightInvited = true;
        if (knight.player && !s.aiPlayer) {
            s.notify("BEYAZ ŞÖVALYE: " + target.name + " sizi karşı teklif vermeye davet ediyor (en az hisse başı "
                + String.format(Locale.ROOT, "%.2f $", hostile.price * (1 + FinanceBalance.KNIGHT_STEP)) + "). Birleşme ve Satın Alma sekmesinden çağrı yapabilirsiniz.", "openmna");
            return null;
        }
        double price = topPrice(target) * (1 + FinanceBalance.KNIGHT_STEP);
        Financing f = aiFinancing(knight, target, price * tenderShares(knight, target, price));
        if (f == null || launchTender(knight, target, price, f) != null) {
            s.notify("BEYAZ ŞÖVALYE: " + knight.name + ", " + target.name + " için karşı teklif vermedi.", involved(target, knight) ? "openmna" : null);
            return knight.name + " karşı teklif vermedi (finansman veya rekabet engeli)";
        }
        return null;
    }

    double topPrice(Company target) { double m = 0; for (DealBook.TenderOffer t : openOffers(target)) m = Math.max(m, t.price); return m; }

    /** Hedef YZ'nin savunmasi: geri alim (nakit fazlasiyla), teklifte temettu artisi ve beyaz sovalye. */
    void aiDefense(Company target, boolean tender) {
        if (target.neutral || !target.eq.listed) return;
        double spare = target.cash - RivalAI.reserve(s, target);
        if (spare > 50e6 && target.eq.buybackBudget <= 1)
            s.capital.setBuyback(target, Math.min(spare * FinanceBalance.AI_BUYBACK_CASH, FinanceBalance.AI_BUYBACK_SHARES * target.eq.shares * target.eq.price));
        if (!tender) return;
        if (s.capital.dividendBlocker(target) == null && target.eq.dividendPct < 50) defendDividend(target);
        DealBook.TenderOffer h = openOffer(target);
        if (h == null) return;
        Company best = null; double bestCash = 0;
        for (Company c : s.companies) {
            if (!active(c) || c == target || c.player || c.eq.parent >= 0 || s.competition.sameGroup(c, co(h.bidder))) continue;
            if (c.cash > bestCash) { bestCash = c.cash; best = c; }
        }
        if (best != null) inviteKnight(target, best);
    }

    /** Teklif suresi dolanlari sonuclandir (rakip teklifler birlikte: ortaklar en yuksek fiyati secer). */
    void settleOffers() {
        List<Integer> targets = new ArrayList<>();
        for (DealBook.TenderOffer t : d().offers) if (!t.closed && s.hour >= t.endHour && !targets.contains(t.target)) targets.add(t.target);
        for (int ti : targets) {
            List<DealBook.TenderOffer> list = openOffers(co(ti));
            DealBook.TenderOffer best = null;
            for (DealBook.TenderOffer t : list) if (best == null || t.price > best.price) best = t;
            for (DealBook.TenderOffer t : list) {
                t.closed = true;
                if (t != best) { t.result = "Daha yüksek rakip teklif kazandı"; d().failedOffers++; s.notify("ÇAĞRI: " + co(t.bidder).name + " teklifi rakip teklif karşısında sonuçsuz kaldı.", involved(co(t.bidder), co(t.target)) ? "openmna" : null); }
            }
            if (best != null) settle(best);
        }
    }

    void settle(DealBook.TenderOffer t) {
        Company bidder = co(t.bidder), target = co(t.target);
        if (!active(bidder) || !active(target)) { t.result = "Taraf faal değil"; return; }
        double prem = effectivePremium(t);
        double acc = acceptance(prem);
        Set<Company> g = s.competition.group(bidder);
        // satanlar: serbest dolasim (kabul orani), diger sirketler (degere gore), oyuncu (secimine gore), kurucu (istedigi fiyat)
        double fromFree = target.eq.free() * acc;
        double[] fromHolders = new double[s.companies.size()];
        for (int j = 0; j < s.companies.size(); j++) {
            Company h = co(j);
            double held = target.eq.held(j);
            if (held <= 0 || g.contains(h)) continue;
            boolean sell = h.player && !s.aiPlayer ? t.playerTenders : t.price >= fundamentalPerShare(target) * FinanceBalance.HOLDER_ACCEPT;
            if (sell) fromHolders[j] = held;
        }
        double fromFounder = !target.player && target.eq.founder > 0 && t.price >= founderAsk(target, target.eq.founder).perShare ? target.eq.founder : 0;
        double n = fromFree + fromFounder;
        for (double v : fromHolders) n += v;
        // finansman: nakit (teklifte gosterilen ve eldeki), onayli kredi, pay takasi
        double cashAvail = Math.max(0, Math.min(t.cash > 0 ? t.cash : bidder.cash, bidder.cash));
        Bank bank = t.loanBank > 0 ? Bank.values()[t.loanBank - 1] : null;
        double loanAvail = bank != null && t.loanApproved > 0 ? Math.min(t.loanApproved, s.finance.maxAcquisitionLoan(bidder, bank, target)) : 0;
        double swapAvail = bidder.eq.listed ? t.swapShares : 0;
        double canPay = cashAvail + loanAvail + swapAvail;
        double scale = n * t.price <= canPay + 1 ? 1 : canPay / (n * t.price);
        if (scale < 1) { fromFree *= scale; fromFounder *= scale; for (int j = 0; j < fromHolders.length; j++) fromHolders[j] *= scale; n *= scale; }
        if (n <= 1) { t.result = "Kabul eden olmadı"; d().failedOffers++; s.notify("ÇAĞRI: " + bidder.name + " → " + target.name + " teklifi sonuçsuz (kabul yok).", involved(bidder, target) ? "openmna" : null); return; }
        Financing f = new Financing();
        f.cash = cashAvail; f.loan = loanAvail; f.bank = bank; f.swapValue = swapAvail;
        double before = groupStake(bidder, target);
        pay(bidder, target, n * t.price, f, "ortaklar");
        for (int j = 0; j < fromHolders.length; j++) if (fromHolders[j] > 0) { co(j).cash += fromHolders[j] * t.price; target.eq.add(j, -fromHolders[j]); }
        target.eq.founder -= fromFounder;
        target.eq.add(t.bidder, n);
        record(bidder, target, t.price);
        double after = groupStake(bidder, target);
        t.result = "Kabul %" + Math.round(acc * 100) + " · pay %" + pct(before) + " → %" + pct(after);
        if (target.eq.listed) target.eq.newsMult = 1;
        s.notify("ÇAĞRI SONUCU: " + bidder.name + " → " + target.name + ": serbest dolaşımın %" + Math.round(acc * 100) + "'i kabul etti; pay %" + pct(before) + " → %" + pct(after)
            + (after > FinanceBalance.CONTROL ? ". Rekabet otoritesi onayı bekleniyor." : ". Kontrol eşiği aşılamadı."), involved(bidder, target) ? "openmna" : null);
        if (after <= FinanceBalance.CONTROL && !t.mandatory) d().failedOffers++;
        thresholds(bidder, target, before);
        afterStakeChange(bidder, target, true);
    }

    // ------------------------------------------------------------ kontrol ve birlesme
    /** Otorite karari: onayda kontrol (bagli ortaklik), retde grup payi %50'nin altina indirilir. */
    void onDecision(DealBook.Review r, boolean ok) {
        Company acq = co(r.acquirer), target = co(r.target);
        if (!ok) { sellDown(acq, target); return; }
        if (groupStake(acq, target) <= FinanceBalance.CONTROL || !active(target)) return;
        Company top = s.competition.top(acq);
        target.eq.parent = idx(top);
        target.eq.strategy = 0;
        target.eq.buybackBudget = 0;
        d().takeovers++;
        if (target.player) {
            s.gameOver = true;
            s.gameOverReason = "Şirketiniz devralındı: " + top.name + " kontrolü aldı (pay %" + pct(groupStake(acq, target)) + ").";
        }
        s.notify("DEVRALMA: " + top.name + ", " + target.name + "'in kontrolünü aldı (pay %" + pct(groupStake(acq, target)) + "). " + target.name + " artık bağlı ortaklık.", involved(acq, target) ? "openmna" : null);
        if (groupStake(acq, target) >= FinanceBalance.SQUEEZE_OUT) squeezeOut(top, target);
    }

    /** Retde: grubun %50'yi asan payi piyasaya (halka kapalida kurucuya) ayni fiyattan satilir. */
    void sellDown(Company acq, Company target) {
        Set<Company> g = s.competition.group(acq);
        double excess = (groupStake(acq, target) - FinanceBalance.CONTROL) * target.eq.shares + 1;
        if (excess <= 1) return;
        for (Company m : g) {
            double h = target.eq.held(idx(m));
            double sell = Math.min(h, excess);
            if (sell <= 0) continue;
            double px = target.eq.listed ? target.eq.price : fundamentalPerShare(target);
            m.cash += sell * px;
            target.eq.add(idx(m), -sell);
            if (!target.eq.listed) target.eq.founder += sell;
            excess -= sell;
        }
        s.notify("DEVRALMA: Ret nedeniyle " + acq.name + " " + target.name + " payını %50'nin altına indirdi.", involved(acq, target) ? "openmna" : null);
    }

    /**
     * %90'in ustu: kalan hisseler son fiyattan zorla alinir, sirketler birlesir. Bir haktir: ana sirket bedeli nakit
     * (rezerv ustu) ve devralma kredisiyle karsilayamiyorsa sirket bagli ortaklik olarak kalir.
     */
    void squeezeOut(Company parent, Company target) {
        if (target.eq.merged || !active(target)) return;
        double px = target.eq.listed ? target.eq.price : fundamentalPerShare(target);
        Set<Company> g = s.competition.group(parent);
        double rest = (target.eq.free() + target.eq.founder) * px;
        for (int j = 0; j < s.companies.size(); j++) if (!g.contains(co(j))) rest += target.eq.held(j) * px;
        double spare = parent.cash - RivalAI.reserve(s, parent);
        if (rest > spare) {
            Bank b = s.finance.bestAcquisitionBank(parent, Math.min(rest - Math.max(0, spare), 10e6), target);
            if (b == null || s.finance.signAcquisition(parent, b, rest - Math.max(0, spare), target) == null) return;
        }
        double cost = 0;
        for (int j = 0; j < s.companies.size(); j++) {
            Company h = co(j);
            double held = target.eq.held(j);
            if (held <= 0 || g.contains(h)) continue;
            h.cash += held * px; cost += held * px; target.eq.add(j, -held);
        }
        cost += (target.eq.free() + target.eq.founder) * px;
        target.eq.add(idx(parent), target.eq.free() + target.eq.founder);
        target.eq.founder = 0;
        parent.cash -= cost;
        merge(parent, target);
    }

    /** Birlesme: santraller, hatlar, TM'ler, krediler, nakit, ihale sozlesmeleri ve banka iliskileri ana sirkete gecer. */
    void merge(Company parent, Company target) {
        for (Plant p : s.plants) if (p.owner == target) { p.owner = parent; p.preferredTm = 0; }
        for (Substation t : s.substations) if (t.owner == target) t.owner = parent;
        for (PowerLine l : s.lines) { if (l.owner == target) l.owner = parent; if (l.requester == target) l.requester = parent; }
        for (Tender tn : s.tenders.all) if (tn.winner == target) tn.winner = parent;
        for (Listing l : s.sales.all) if (l.seller == target && l.status == Listing.Status.OPEN) l.status = Listing.Status.WITHDRAWN;
        parent.loans.addAll(target.loans); target.loans.clear();
        parent.cash += target.cash; target.cash = 0;
        for (int b = 0; b < 3; b++) parent.bankScore[b] = Math.max(parent.bankScore[b], target.bankScore[b]);
        int ti = idx(target), pi = idx(parent);
        for (Company x : s.companies) {
            double h = x.eq.held(ti);
            if (h > 0 && x != parent) { x.eq.add(pi, h); x.eq.add(ti, -h); }
            if (x.eq.parent == ti) x.eq.parent = pi;
        }
        target.eq.merged = true; target.eq.parent = pi; target.bankrupt = true; target.eq.listed = false;
        d().orders.removeIf(o -> o.buyer == ti || o.target == ti);
        s.markTopologyDirty();
        s.notify("BİRLEŞME: " + target.name + " ortaklıktan çıkarıldı ve " + parent.name + " ile birleşti: santraller, hatlar, krediler devralındı.", involved(parent, target) ? "openmna" : null);
    }

    /** Bagli ortaklik stratejisi: 0 buyume, 1 temkinli, 2 nakit uret. */
    public String setStrategy(Company parent, Company sub, int strategy) {
        if (!controls(parent, sub)) return "Kontrol (onaylı bağlı ortaklık) yok";
        sub.eq.strategy = Math.max(0, Math.min(2, strategy));
        if (sub.eq.strategy == 2 && sub.eq.listed) s.capital.setDividend(sub, 50);
        return null;
    }

    public static final String[] STRATEGY = { "Büyüme", "Temkinli", "Nakit üret" };

    // ------------------------------------------------------------ zamanlama
    void daily() {
        executeOrders();
        settleOffers();
        // cagri suresince hedef fiyati teklife yakin (arbitraj)
        for (DealBook.TenderOffer t : d().offers) {
            if (t.closed) continue;
            Company tg = co(t.target);
            double base = s.capital.targetPrice(tg) / Math.max(1e-9, tg.eq.newsMult);
            if (base > 0) tg.eq.newsMult = Math.max(tg.eq.newsMult, t.price * 0.97 / base);
        }
    }

    /** Aylik: istirak/bagli ortaklik kar payi, eski alim kayitlarinin temizlenmesi, pazarlik tohumu. */
    void monthly() {
        for (Company h : s.companies) {
            if (!active(h)) continue;
            int hi = idx(h);
            for (Company t : s.companies) {
                if (t == h || t.eq.merged) continue;
                double p = t.eq.pct(hi);
                if (p >= FinanceBalance.ASSOCIATE) h.affiliateIncome += p * t.lastNet;
            }
        }
        long cut = s.hour - (long) (FinanceBalance.MANDATORY_LOOKBACK_MONTHS + 1) * Balance.HOURS_PER_MONTH;
        d().purchases.removeIf(p -> p.hour < cut);
        for (Company c : s.companies) c.eq.askSeed = s.rng.nextDouble();
        // savunmadaki geri alimlarin cagri primine etkisi: savunma suresince yapilan alimlar
    }

    /** Geri alimin acik teklife etkisi (Capital.buybacks'ten): fiyat yukselisi etkin primi dusurur. */
    void buybackDuringOffer(Company target, double up) {
        for (DealBook.TenderOffer t : openOffers(target)) t.defenseUplift += up;
    }

    // ------------------------------------------------------------ rakip YZ
    /** Rakip YZ finansmani: kullanilabilir nakit (rezerv ustu) + en ucuz devralma kredisi. Null: yetmez. */
    Financing aiFinancing(Company c, Company target, double cost) {
        Financing f = new Financing();
        f.cash = Math.max(0, Math.min(cost, c.cash - RivalAI.reserve(s, c)));
        double need = cost - f.cash;
        if (need > 1) {
            Bank b = s.finance.bestAcquisitionBank(c, Math.min(need, 10e6), target);
            if (b == null) return null;
            double max = s.finance.maxAcquisitionLoan(c, b, target);
            if (max < need - 1) return null;
            f.loan = need; f.bank = b;
            if (s.finance.acquisitionRefusal(c, b, need, target) != null) return null;
        }
        return f;
    }

    static double appetite(Company c) { return c.aiStyle.equals("fossil") ? 1.5 : c.aiStyle.equals("green") ? 0.6 : 1.0; }

    /** Rakip devralma davranisi: notu dusuk, ucuz (fiyat / temel deger) ve kendi pazarindaki hedefler; saldirgan stil daha sik dener. */
    public void rivalMonthly(Company c) {
        if (!active(c) || c.eq.parent >= 0 || distressed(c)) return;
        if (s.rng.nextDouble() >= FinanceBalance.RIVAL_MNA_CHANCE * appetite(c)) return;
        Company best = null; double bestScore = -1e9;
        for (Company t : s.companies) {
            if (!active(t) || t == c || s.competition.sameGroup(c, t) || s.competition.pendingReview(t) != null || openOffer(t) != null) continue;
            double cheap = t.eq.listed && t.eq.fundamental > 0 ? 1 - t.eq.price * t.eq.shares / t.eq.fundamental : 0;
            double score = cheap + 0.15 * s.finance.rating(t).ordinal() + (sharesMarket(c, t) ? 0.3 : 0);
            if (score > bestScore) { bestScore = score; best = t; }
        }
        if (best == null) return;
        Company t = best;
        Competition.Preview p = s.competition.preview(c, t);
        if (p.outcome == Competition.REJECT || p.outcome == Competition.CONDITIONAL && !s.competition.aiAcceptsCommitment(p)) return;
        // a) zor durumdaki kapali sirket: kurucudan kontrol payi
        if (!t.player && !t.eq.listed && distressed(t) && t.eq.founder > t.eq.shares * 0.52) {
            double need = t.eq.shares * 0.51 - groupStake(c, t) * t.eq.shares;
            Ask a = founderAsk(t, need);
            Financing f = aiFinancing(c, t, need * a.perShare);
            if (f != null && buyFromFounder(c, t, need, a.perShare, f) == null) return;
        }
        // a2) kurucu kontrolundeki sirket (oyuncu disinda): finansman yetiyorsa kurucudan kontrol payi (istenen primle)
        if (!t.player && t.eq.founder >= t.eq.shares * FinanceBalance.CONTROL) {
            double need = t.eq.shares * 0.51 - groupStake(c, t) * t.eq.shares;
            Ask a = founderAsk(t, need);
            Financing f = aiFinancing(c, t, need * a.perShare);
            if (f != null && buyFromFounder(c, t, need, a.perShare, f) == null) return;
        }
        // b) dagink ortakli halka acik sirket: cagri
        if (t.eq.listed && t.eq.founderPct() < FinanceBalance.CONTROL) {
            double prem = FinanceBalance.RIVAL_TENDER_PREMIUM_MIN + (FinanceBalance.RIVAL_TENDER_PREMIUM_MAX - FinanceBalance.RIVAL_TENDER_PREMIUM_MIN) * s.rng.nextDouble();
            double price = t.eq.price * (1 + prem);
            Financing f = aiFinancing(c, t, price * tenderShares(c, t, price));
            if (f != null && launchTender(c, t, price, f) == null) return;
        }
        // c) halka acik hedefte kucuk pay (giris payi)
        if (t.eq.listed && stake(c, t) < FinanceBalance.RIVAL_TOEHOLD && order(c, t) == null) {
            double n = Math.min(t.eq.free(), t.eq.shares * FinanceBalance.RIVAL_TOEHOLD);
            if (n * t.eq.price < c.cash - RivalAI.reserve(s, c) - 100e6) placeOrder(c, t, n);
        }
    }

    boolean sharesMarket(Company a, Company b) {
        for (GameState.Component comp : s.components()) {
            boolean x = false, y = false;
            for (Plant p : comp.plants) { if (p.owner == a) x = true; if (p.owner == b) y = true; }
            if (x && y) return true;
        }
        return false;
    }
}
