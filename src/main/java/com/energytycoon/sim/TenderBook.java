package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Acil kapasite ihaleleri: tetiklenme, teklif, kazanan, teminat ve garantili odeme. */
public final class TenderBook {
    private final GameState s;
    public final List<Tender> all = new ArrayList<>();
    final Map<Integer, Tender> byId = new HashMap<>();

    TenderBook(GameState s) { this.s = s; }

    public Tender get(int id) { return byId.get(id); }

    void add(Tender t) { all.add(t); byId.put(t.id, t); }

    public List<Tender> open() {
        List<Tender> r = new ArrayList<>();
        for (Tender t : all) if (t.status == Tender.Status.OPEN) r.add(t);
        return r;
    }

    /** Kazanilan (oyuncu ya da verilen sirket) ihaleler. */
    public List<Tender> wonBy(Company c) {
        List<Tender> r = new ArrayList<>();
        for (Tender t : all) if (t.winner == c) r.add(t);
        return r;
    }

    /** Tahmini santral bedeli: ihale MW x referans santralin (kombine cevrim) MW basi bedeli. */
    public double estimatedPlantCost(Tender t) {
        Equipment ref = s.catalog.get(Balance.TENDER_REFERENCE_EQ);
        return t.mw * ref.capex() / ref.capacityMW;
    }

    public double depositFor(Tender t) { return estimatedPlantCost(t) * Balance.TENDER_DEPOSIT; }

    public int daysLeft(Tender t) { return (int) Math.max(0, (t.closeHour - s.hour) / 24); }

    /** Null: teklif kaydedildi/guncellendi; aksi halde sebep. Ilk teklifte teminat yatirilir. */
    public String bid(Company c, Tender t, double price) {
        if (t.status != Tender.Status.OPEN) return "İhale teklife kapalı";
        if (price < Balance.TENDER_MIN_PRICE || price > Balance.TENDER_MAX_PRICE)
            return "Teklif $" + (int) Balance.TENDER_MIN_PRICE + "–" + (int) Balance.TENDER_MAX_PRICE + "/MWh aralığında olmalı";
        Tender.Bid b = t.bidOf(c);
        if (b == null) {
            double dep = depositFor(t);
            if (c.cash < dep) return "Teminat için yetersiz nakit (" + Math.round(dep / 1e5) / 10.0 + "M $)";
            c.cash -= dep;
            b = new Tender.Bid(); b.company = c; b.deposit = dep;
            t.bids.add(b);
        }
        b.price = price; b.hour = s.hour;
        return null;
    }

    /** Aylik: sehir memnuniyeti takibi, ihale acma/kapama, kurulum ve sozlesme takibi. */
    void monthly() {
        for (City c : s.cities) {
            c.lowSatMonths = c.satisfaction < Balance.TENDER_SATISFACTION ? c.lowSatMonths + 1 : 0;
            if (c.lowSatMonths >= Balance.TENDER_LOW_MONTHS && s.hour - c.lastTenderHour >= (long) Balance.TENDER_COOLDOWN_YEARS * Balance.HOURS_PER_YEAR
                && !hasPending(c)) openTender(c);
        }
        for (Tender t : all) {
            switch (t.status) {
                case OPEN -> { if (s.hour >= t.closeHour) close(t); } // gunluk kontrol de var (daily)
                case AWARDED -> checkCommission(t);
                case ACTIVE -> { if (s.hour >= t.contractEndHour) { t.status = Tender.Status.EXPIRED; release(t); } }
                default -> {}
            }
        }
    }

    /** Gunluk: suresi dolan teklif donemlerini kapat (60 gun tam uygulanir). */
    void daily() {
        for (Tender t : all) if (t.status == Tender.Status.OPEN && s.hour >= t.closeHour) close(t);
    }

    private boolean hasPending(City c) {
        for (Tender t : all) if (t.city == c && (t.status == Tender.Status.OPEN || t.status == Tender.Status.AWARDED)) return true;
        return false;
    }

    /** Karsilanmayan gercek tepe talebi, 50 MW'a yukari yuvarlanir. */
    public double unmetPeak(City c) {
        double peak = c.peakMW() * GameState.maxDemandMult();
        double unmet = peak * Math.max(0, 1 - c.lastServedRatio);
        return Math.max(Balance.TENDER_ROUND_MW, Math.ceil(unmet / Balance.TENDER_ROUND_MW) * Balance.TENDER_ROUND_MW);
    }

    public Tender openTender(City c) {
        Tender t = new Tender();
        t.id = s.nextId++; t.city = c; t.mw = unmetPeak(c);
        t.openedHour = s.hour; t.closeHour = s.hour + Balance.TENDER_BID_DAYS * 24L;
        c.lastTenderHour = s.hour;
        add(t);
        s.notify("İHALE: UŞİ, " + c.name + " için " + Math.round(t.mw) + " MW acil kapasite ihalesi açtı · 10 yıl garantili fiyat · teklif "
            + Balance.TENDER_BID_DAYS + " gün", "tender:" + t.id);
        return t;
    }

    private void close(Tender t) {
        Tender.Bid best = t.best();
        for (Tender.Bid b : t.bids) if (b != best) b.company.cash += b.deposit; // kaybedenlere teminat iadesi
        if (best == null) { t.status = Tender.Status.NO_BIDS; s.notify("İHALE: " + t.city.name + " ihalesine teklif gelmedi"); return; }
        t.winner = best.company; t.price = best.price; t.deposit = best.deposit;
        t.status = Tender.Status.AWARDED; t.awardedHour = s.hour;
        t.commissionDeadline = s.hour + (long) Balance.TENDER_BUILD_MONTHS * Balance.HOURS_PER_MONTH;
        t.plantIdMark = s.nextId;
        s.notify("İHALE: " + t.city.name + " " + Math.round(t.mw) + " MW · kazanan " + t.winner.name + " ($" + Math.round(t.price) + "/MWh). "
            + Balance.TENDER_BUILD_MONTHS + " ay içinde devreye alınmalı", "tender:" + t.id);
    }

    /** Kazananin ihaleden sonra kurdugu, sehre <= 15 kare ve sehrin sebeke parcasina bagli calisan santralleri. */
    public List<Plant> eligiblePlants(Tender t) {
        List<Plant> r = new ArrayList<>();
        GameState.Component cc = s.componentOf(t.city);
        for (Plant p : s.plants) {
            if (p.owner != t.winner || p.id < t.plantIdMark || !p.operational()) continue;
            if (p.tenderId != 0 && p.tenderId != t.id) continue;
            if (Math.hypot(p.x - t.city.x, p.y - t.city.y) > Balance.TENDER_MAX_DIST) continue;
            if (cc == null || s.componentOf(p) != cc) continue;
            r.add(p);
        }
        return r;
    }

    public double commissionedMW(Tender t) {
        double mw = 0;
        for (Plant p : eligiblePlants(t)) mw += p.capacityMW();
        return mw;
    }

    private void checkCommission(Tender t) {
        List<Plant> ok = eligiblePlants(t);
        double mw = 0;
        for (Plant p : ok) mw += p.capacityMW();
        if (mw >= t.mw - 1e-6) {
            for (Plant p : ok) p.tenderId = t.id;
            t.status = Tender.Status.ACTIVE;
            t.contractEndHour = s.hour + (long) Balance.TENDER_CONTRACT_YEARS * Balance.HOURS_PER_YEAR;
            t.winner.cash += t.deposit; // teminat iadesi
            s.notify("İHALE: " + t.city.name + " kapasitesi devrede · " + t.winner.name + " " + Balance.TENDER_CONTRACT_YEARS
                + " yıl $" + Math.round(t.price) + "/MWh alacak", "tender:" + t.id);
        } else if (s.hour > t.commissionDeadline) {
            t.status = Tender.Status.FORFEITED;
            s.notify("İHALE: " + t.winner.name + " " + t.city.name + " kapasitesini süresinde kurmadı · teminat (" + Math.round(t.deposit / 1e5) / 10.0 + "M $) yandı", "tender:" + t.id);
        }
    }

    private void release(Tender t) {
        for (Plant p : s.plants) if (p.tenderId == t.id) p.tenderId = 0;
    }

    /** Saat basinda: aktif sozlesmelerin MW butcesi. */
    void resetHour() {
        for (Tender t : all) if (t.status == Tender.Status.ACTIVE) t.hourBudget = t.mw;
    }

    /** Santralin bu saatki gelir: ihale kapsamindaki pay teklif fiyatindan, kalani piyasa fiyatindan. */
    double revenue(Plant p, double out, double marketPrice) {
        if (p.tenderId == 0) return out * marketPrice;
        Tender t = byId.get(p.tenderId);
        if (t == null || t.status != Tender.Status.ACTIVE) return out * marketPrice;
        double covered = Math.min(out, t.hourBudget);
        t.hourBudget -= covered;
        t.paidRevenue += covered * t.price; t.paidMWh += covered;
        return covered * t.price + (out - covered) * marketPrice;
    }
}
