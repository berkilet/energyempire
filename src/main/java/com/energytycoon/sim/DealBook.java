package com.energytycoon.sim;

import java.util.ArrayList;

/**
 * Birlesme-satin alma ve rekabet otoritesinin kalici kayitlari (kayitta oldugu gibi serilestirilir; sirketlere liste sirasiyla atif).
 */
public final class DealBook {
    /** Rekabet otoritesi incelemesi. */
    public static final class Review {
        public int id, acquirer, target;
        public long startHour, endHour;
        /** 0 onay, 1 kosullu onay, 2 ret. */
        public int outcome;
        /** On inceleme (30 gun) / nihai inceleme (4 ay). */
        public boolean finalPhase, commented, decided, commitmentAccepted, exempt, notifiable;
        public double divestMW, valueRatio, maxShare, national;
        public String reason = "";
        public int[] marketPlants = new int[0];
        public Review() {}
    }

    /** Satis taahhudu: belirtilen santrallerden (ilgili pazar) en az divestMW satilacak. */
    public static final class Commitment {
        public int company, reviewId;
        public double requiredMW, baseMW;
        public long deadline;
        public int[] plants = new int[0];
        public boolean done, enforced;
        public Commitment() {}
    }

    /** Pay alim teklifi (cagri) veya zorunlu pay alim teklifi. */
    public static final class TenderOffer {
        public int id, bidder, target;
        public double price, prePrice, cash, loanApproved, swapShares, defenseUplift;
        public long startHour, endHour;
        public boolean mandatory, dividendDefense, playerTenders, closed, knight, knightInvited;
        public int loanBank;
        public String result = "";
        public TenderOffer() {}
    }

    /** Piyasadan alim emri (gunluk en fazla hisselerin %2'si). */
    public static final class Order {
        public int buyer, target;
        public double remaining;
        public Order() {}
    }

    /** Odenen fiyat kaydi (zorunlu teklif fiyati: son 6 ayin en yuksegi). */
    public static final class Purchase {
        public int buyer, target;
        public long hour;
        public double price;
        public Purchase() {}
    }

    public ArrayList<Review> reviews = new ArrayList<>();
    public ArrayList<Commitment> commitments = new ArrayList<>();
    public ArrayList<TenderOffer> offers = new ArrayList<>();
    public ArrayList<Order> orders = new ArrayList<>();
    public ArrayList<Purchase> purchases = new ArrayList<>();
    public ArrayList<String> achievements = new ArrayList<>();
    /** Olcum: kontrol degisikligi girisimleri ve otorite kararlari. */
    public int attempts, approvals, conditionals, rejections, takeovers, failedOffers;
    public ArrayList<String> rejectReasons = new ArrayList<>();
    /** Konsolidasyon senaryosu: gevsek otorite (bolgesel esik %80, ulusal sinir yok). */
    public boolean relaxed;
    /** Duzenlenmis tekel (kamu hizmeti modu) olan grubun ana sirketi; -1 yok. */
    public int regulated = -1;
    public int nextId = 1;
}
