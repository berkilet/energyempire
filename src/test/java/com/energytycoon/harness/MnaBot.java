package com.energytycoon.harness;

import com.energytycoon.sim.*;

/**
 * Kullanilabilirlik botu (oyuncu tarafi, oyuncunun arayuzde kullandigi ayni API): halka arz yap, ucuz hedefi sec,
 * kredi ve nakitle cagri yap; hedef kapali sirketse kurucuya teklif gotur; halka acik ama kurucusu kontrolde ise piyasadan pay topla.
 */
public final class MnaBot {
    private MnaBot() {}

    /** Aylik cagrilir. leverageMode: degeri kendisine yakin (oran 1,0-1,5) bir rakibi agirlikla krediyle devralmayi dener. */
    public static void monthly(GameState s, boolean leverageMode) {
        Company p = s.player;
        if (p.bankrupt || s.gameOver) return;
        if (System.getenv("MNA_NOIPO") == null && !p.eq.listed && !s.capital.ipoPreparing(p) && s.capital.ipoBlocker(p) == null) s.capital.startIpo(p, 0.30);
        if (s.capital.ipoPreparing(p) && p.eq.ipoCrisisPending) s.capital.postponeIpo(p);
        int pi = s.companies.indexOf(p);
        for (DealBook.TenderOffer t : s.deals.offers) if (!t.closed && t.bidder == pi) return; // acik teklifimiz var
        for (DealBook.Review r : s.deals.reviews) {
            if (!r.decided && r.acquirer == pi) return; // inceleme suruyor
            if (r.decided && r.acquirer == pi && s.hour - r.endHour < 24L * Balance.HOURS_PER_MONTH) return; // devralma sonrasi 24 ay sindirme
        }
        // temkin: oturmus, notu en az BB, gecikmesiz, kaldiraci makul sirket
        if (p.quarters < 8 || s.finance.rating(p).ordinal() > Rating.BB.ordinal() || s.finance.leverage(p) > 3.0 || p.restructUsed) return;
        for (Loan l : p.loans) if (l.status == Loan.Status.LATE) return;
        Company target = pick(s, leverageMode);
        if (target == null) return;
        Competition.Preview pv = s.competition.preview(p, target);
        if (pv.outcome == Competition.REJECT) return;
        double svc = 0;
        for (double v : s.finance.schedule12(p)) svc += v;
        double reserve = 250e6 + svc / 2;
        if (target.eq.listed && target.eq.founderPct() < FinanceBalance.CONTROL) {
            double price = target.eq.price * 1.32;
            double cost = price * s.mna.tenderShares(p, target, price);
            Mna.Financing f = financing(s, p, target, cost, reserve, leverageMode);
            if (f != null) s.mna.launchTender(p, target, price, f);
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
                    Mna.Financing f = financing(s, p, target, ctrl * price, reserve, true);
                    if (f != null && s.mna.buyFromFounder(p, target, ctrl, price, f) == null) return;
                }
            }
        }
        if (target.eq.listed) {
            // kontrol payi finanse edilemiyorsa: piyasadan pay topla (en fazla %25; finansal yatirim nakitle)
            if (s.mna.stake(p, target) < 0.25 && s.mna.order(p, target) == null) {
                double n = Math.min(target.eq.free(), target.eq.shares * 0.25 - target.eq.held(s.companies.indexOf(p)));
                if (n > 0 && n * target.eq.price < p.cash - reserve) s.mna.placeOrder(p, target, n);
            }
            return;
        }
        // kapali sirket: kurucudan pay (zor durumdaysa kontrol, degilse %25)
        double want = s.mna.distressed(target) ? target.eq.shares * 0.51 : target.eq.shares * 0.25;
        want = Math.min(want, target.eq.founder);
        if (want <= 0) return;
        Mna.Ask a = s.mna.founderAsk(target, want);
        Mna.Reply r = s.mna.founderReply(target, want, a.perShare * 0.92, false);
        double price = r.kind == Mna.COUNTER ? r.perShare : r.kind == Mna.ACCEPT ? a.perShare * 0.92 : -1;
        if (price <= 0) return;
        boolean takesControl = s.mna.groupStake(p, target) + want / target.eq.shares > FinanceBalance.CONTROL;
        if (!takesControl && want * price > p.cash - reserve) return; // kontrol getirmeyen pay krediyle alinmaz
        Mna.Financing f = financing(s, p, target, want * price, reserve, leverageMode && takesControl);
        if (f != null) s.mna.buyFromFounder(p, target, want, price, f);
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
        if (leverageMode) {
            f.loan = Math.min(loanMax, cost); f.bank = b;
            f.cash = Math.min(cashAvail, cost - f.loan);
        } else {
            f.cash = Math.min(cashAvail, cost);
            f.loan = Math.min(loanMax, cost - f.cash); f.bank = b;
        }
        if (f.loan <= 0) { f.loan = 0; f.bank = null; }
        if (f.total() < cost - 1) return null;
        return f;
    }
}
