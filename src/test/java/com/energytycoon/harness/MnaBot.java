package com.energytycoon.harness;

import com.energytycoon.sim.*;

import java.util.Map;
import java.util.TreeMap;

/**
 * Kullanilabilirlik botu (oyuncu tarafi, oyuncunun arayuzde kullandigi ayni API): halka arz yap, ucuz hedefi sec,
 * kredi ve nakitle cagri yap; kurucu kontrolundeki hedefte kurucudan kontrol payi; finanse edilemiyorsa piyasadan pay.
 * Her ay islem yapilmadiysa sebebi sayilir (why): kredili devralma ve kosullu onayin neden olmadigini olcmek icin.
 */
public final class MnaBot {
    private MnaBot() {}

    /** Ay basina "islem yapilmadi / yapildi" sebepleri. */
    public static final Map<String, Integer> why = new TreeMap<>();
    /** Kosullu onay firsati varken (onizleme kosullu) botu durduran sebepler. */
    public static final Map<String, Integer> whyCond = new TreeMap<>();

    static boolean condAvailable;

    static void note(String r) {
        why.merge(r, 1, Integer::sum);
        if (condAvailable) whyCond.merge(r, 1, Integer::sum);
    }

    /** Aylik cagrilir. leverageMode: degeri kendisine yakin (oran 1,0-1,5) bir rakibi agirlikla krediyle devralmayi dener. */
    public static void monthly(GameState s, boolean leverageMode) {
        Company p = s.player;
        if (p.bankrupt || s.gameOver) return;
        if (System.getenv("MNA_NOIPO") == null && !p.eq.listed && !s.capital.ipoPreparing(p) && s.capital.ipoBlocker(p) == null) s.capital.startIpo(p, 0.30);
        if (s.capital.ipoPreparing(p) && p.eq.ipoCrisisPending) s.capital.postponeIpo(p);
        condAvailable = false;
        for (Company c : s.companies)
            if (s.mna.active(c) && c != p && !s.competition.sameGroup(p, c) && s.competition.preview(p, c).outcome == Competition.CONDITIONAL) condAvailable = true;
        int pi = s.companies.indexOf(p);
        for (DealBook.TenderOffer t : s.deals.offers) if (!t.closed && t.bidder == pi) { note("acik teklif bekleniyor"); return; }
        for (DealBook.Review r : s.deals.reviews) {
            if (!r.decided && r.acquirer == pi) { note("rekabet incelemesi bekleniyor"); return; }
            if (r.decided && r.acquirer == pi && s.hour - r.endHour < 24L * Balance.HOURS_PER_MONTH) { note("devralma sonrasi 24 ay"); return; }
        }
        // temkin: oturmus, notu en az BB, gecikmesiz, kaldiraci makul sirket (halka arz kosullariyla ayni esikler)
        if (p.quarters < 8) { note("bot: 8 ceyrek dolmadi"); return; }
        if (s.finance.rating(p).ordinal() > Rating.BB.ordinal()) { note("bot: not BB alti"); return; }
        if (s.finance.leverage(p) > 3.0) { note("bot: ND/FAVOK > 3"); return; }
        if (p.mustSellBy > 0 || p.restructProtectUntil > s.hour) { note("bot: yapilandirma suruyor"); return; }
        for (Loan l : p.loans) if (l.status == Loan.Status.LATE) { note("bot: gecikme"); return; }
        Company target = pick(s, leverageMode);
        if (target == null) { note(leverageMode ? "deger orani 1,0-1,5 bandinda hedef yok" : "hedef yok"); return; }
        Competition.Preview pv = s.competition.preview(p, target);
        if (pv.outcome == Competition.REJECT) { note("rekabet: ret"); return; }
        double svc = 0;
        for (double v : s.finance.schedule12(p)) svc += v;
        double reserve = 250e6 + svc / 2;
        if (target.eq.listed && target.eq.founderPct() < FinanceBalance.CONTROL) {
            double price = target.eq.price * 1.32;
            double cost = price * s.mna.tenderShares(p, target, price);
            Mna.Financing f = financing(s, p, target, cost, reserve, leverageMode);
            if (f == null) { note("cagri: finansman yetmedi"); return; }
            String err = s.mna.launchTender(p, target, price, f);
            note(err == null ? "ISLEM: cagri" : "cagri engeli: " + err.replaceAll("[0-9.,]+", "#"));
            return;
        }
        // kurucu kontrolde (kapali veya halka acik): finansman yetiyorsa kurucudan kontrol payi (%51), istedigi primle
        if (!target.player && target.eq.founder > 0) {
            double ctrl = Math.min(target.eq.founder, Math.max(0, target.eq.shares * 0.51 - s.mna.groupStake(p, target) * target.eq.shares));
            if (ctrl > 0) {
                Mna.Ask ask = s.mna.founderAsk(target, ctrl);
                Mna.Reply r = s.mna.founderReply(target, ctrl, ask.perShare * 0.92, false);
                double price = r.kind == Mna.COUNTER ? r.perShare : r.kind == Mna.ACCEPT ? ask.perShare * 0.92 : -1;
                if (price > 0) {
                    // zorunlu pay alim teklifi dahil toplam maliyet butcelenir; kurucu odemesi bunun ilk kismi
                    double mand = s.mna.mandatoryCostEstimate(p, target, price);
                    Mna.Financing f = financing(s, p, target, ctrl * price + mand, reserve, leverageMode);
                    if (f != null) f = financing(s, p, target, ctrl * price, reserve + mand, leverageMode);
                    if (f != null) {
                        String err = s.mna.buyFromFounder(p, target, ctrl, price, f);
                        if (err == null) { note("ISLEM: kurucudan kontrol payi"); return; }
                        note("kurucu kontrol engeli: " + err.replaceAll("[0-9.,]+", "#"));
                    } else note("kurucu kontrol payi: finansman yetmedi");
                } else note("kurucu teklifi reddetti");
            }
        }
        if (target.eq.listed) {
            // kontrol payi finanse edilemiyorsa: piyasadan pay topla (en fazla %25; finansal yatirim nakitle)
            if (s.mna.stake(p, target) < 0.25 && s.mna.order(p, target) == null) {
                double n = Math.min(target.eq.free(), target.eq.shares * 0.25 - target.eq.held(s.companies.indexOf(p)));
                if (n > 0 && n * target.eq.price < p.cash - reserve) { s.mna.placeOrder(p, target, n); note("ISLEM: piyasadan pay"); }
            }
            return;
        }
        // kapali sirket: kurucudan %25 pay (finansal yatirim, nakitle)
        double want = Math.min(target.eq.founder, target.eq.shares * 0.25);
        if (want <= 0 || s.mna.stake(p, target) >= 0.25) return;
        Mna.Ask a = s.mna.founderAsk(target, want);
        Mna.Reply r = s.mna.founderReply(target, want, a.perShare * 0.92, false);
        double price = r.kind == Mna.COUNTER ? r.perShare : r.kind == Mna.ACCEPT ? a.perShare * 0.92 : -1;
        if (price <= 0 || want * price > p.cash - reserve) { note("azinlik payi: nakit yetmedi"); return; }
        Mna.Financing f = new Mna.Financing(); f.cash = want * price;
        if (s.mna.buyFromFounder(p, target, want, price, f) == null) note("ISLEM: kurucudan azinlik payi");
    }

    static Company pick(GameState s, boolean leverageMode) {
        Company p = s.player, best = null;
        double bestScore = -1e18, vp = s.capital.value(p);
        for (Company c : s.companies) {
            if (!s.mna.active(c) || c == p || s.competition.sameGroup(p, c) || s.mna.openOffer(c) != null || s.competition.pendingReview(c) != null) continue;
            double v = Math.max(1, s.capital.value(c));
            if (leverageMode) {
                double ratio = vp / v;
                if (ratio < 1.0 || ratio > 1.5) continue;
                double score = -Math.abs(ratio - 1.2);
                if (score > bestScore) { bestScore = score; best = c; }
                continue;
            }
            double cheap = c.eq.fundamental > 0 ? 1 - v / c.eq.fundamental : 0;
            Competition.Preview pv = s.competition.preview(p, c);
            double score = (pv.outcome == Competition.CONDITIONAL ? 1.0 : pv.outcome == Competition.REJECT ? -5 : 0) + cheap + 0.2 * s.finance.rating(c).ordinal() + (c.eq.listed && c.eq.founderPct() < 0.5 ? 0.5 : 0) - v / Math.max(1, vp) * 0.3;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        return best;
    }

    /** Nakit (rezerv ustu) + en ucuz devralma kredisi; leverageMode'da once kredi. */
    static Mna.Financing financing(GameState s, Company p, Company t, double cost, double reserve, boolean leverageMode) {
        Mna.Financing f = new Mna.Financing();
        double cashAvail = Math.max(0, p.cash - reserve);
        Bank b = s.finance.bestAcquisitionBank(p, 10e6, t);
        double loanMax = b == null ? 0 : s.finance.maxAcquisitionLoan(p, b, t);
        // temkinli alici: birlesik (pro forma) Net borc / FAVOK en fazla 3 (banka siniri 4-4,5)
        double e4 = s.finance.groupEbitda4(p) + Math.max(0, s.finance.ebitda4(t));
        double ndNow = s.finance.groupNetDebt(p) + Math.max(0, t.debt() - t.cash);
        loanMax = Math.max(0, Math.min(loanMax, 3.0 * e4 - ndNow));
        if (leverageMode) {
            f.loan = Math.min(loanMax, cost); f.bank = b;
            f.cash = Math.min(cashAvail, cost - f.loan);
        } else {
            f.cash = Math.min(cashAvail, cost);
            f.loan = Math.min(loanMax, cost - f.cash); f.bank = b;
        }
        if (f.loan <= 0) { f.loan = 0; f.bank = null; }
        // pay takasi (yalnizca halka acik alici, kredili devralma modunda kullanilmaz): kurucu payi %55'in altina inmeyecek kadar yeni hisse
        if (!leverageMode && f.total() < cost - 1 && p.eq.listed && p.eq.price > 0) {
            double maxNew = Math.max(0, p.eq.founder / 0.55 - p.eq.shares);
            f.swapValue = Math.min(cost - f.total(), maxNew * p.eq.price);
        }
        if (f.total() < cost - 1) return null;
        return f;
    }
}
