package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.List;

/** UŞİ acil kapasite ihalesi: teknoloji tarafsiz, 10 yillik garantili alim fiyati, en dusuk teklif kazanir. */
public final class Tender {
    public enum Status {
        OPEN("Teklif alınıyor"), AWARDED("Kurulum bekleniyor"), ACTIVE("Sözleşme yürürlükte"),
        FORFEITED("Teminat yandı"), EXPIRED("Süresi doldu"), NO_BIDS("Teklif gelmedi");
        public final String label;
        Status(String l) { label = l; }
    }

    public static final class Bid {
        public Company company;
        public double price, deposit;
        public long hour;
    }

    public int id;
    public City city;
    public double mw;
    public long openedHour, closeHour;
    public Status status = Status.OPEN;
    public final List<Bid> bids = new ArrayList<>();
    public Company winner;
    public double price, deposit;
    public long awardedHour, commissionDeadline, contractEndHour;
    /** Kazanandan sonra kurulan (id > plantIdMark) santraller ihale kapsamina girebilir. */
    public int plantIdMark;
    /** Bu saatte teklif fiyatindan odenebilecek kalan MW. */
    transient double hourBudget;
    /** Odenen toplam garantili gelir ve uretim (raporlama). */
    public double paidRevenue, paidMWh;

    public Bid bidOf(Company c) {
        for (Bid b : bids) if (b.company == c) return b;
        return null;
    }

    public Bid best() {
        Bid best = null;
        for (Bid b : bids) if (best == null || b.price < best.price - 1e-9) best = b;
        return best;
    }
}
