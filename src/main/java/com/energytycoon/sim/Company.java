package com.energytycoon.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class Company {
    public String name;
    public boolean player;
    /** Tarafsiz kamu sirketi (eski filo): YZ calistirmaz, iflas etmez, siralamalara girmez. */
    public boolean neutral;
    /** Rakip YZ tarzi: fossil, green, balanced. */
    public String aiStyle = "balanced";
    public int colorRgb;
    public double cash;
    /** Krediler (ticari, ECA, isletme kredisi). Borc bunlarin bakiyesinden turetilir. */
    public final List<Loan> loans = new ArrayList<>();

    /** Toplam borc bakiyesi (acik kredilerin anapara bakiyesi). */
    public double debt() {
        double d = 0;
        for (Loan l : loans) if (l.open()) d += l.outstanding;
        return d;
    }

    // ---- kredi notu ve finans durumu
    public Rating rating = Rating.BB;
    /** Gecikmede bir kademe dusuk not. */
    public boolean lateNotch;
    /** Ceyrek sayisi ve son 4 ceyregin FAVOK, faiz gideri, yenilenebilir ve toplam uretimi (halka). */
    public int quarters;
    public double qEbitdaAcc, qInterestAcc, qRenewAcc, qGenAcc;
    public double[] qEbitda = new double[4], qInterest = new double[4], qRenew = new double[4], qGen = new double[4];
    public int[] bankScore = { FinanceBalance.REL_START, FinanceBalance.REL_START, FinanceBalance.REL_START };
    public long[] bankBanUntil = new long[3];
    public boolean[] bankCovenantBreach = new boolean[3];
    public int[] bankBreachQuarters = new int[3];
    public boolean dividendBan;
    public long defaultCapUntil, restructProtectUntil, mustSellBy, restructOfferHour;
    public long negativeSinceHour = -1;
    public boolean restructUsed, restructOfferPending;
    public int plantsAtRestruct;
    /** Pesin masraflar, sigorta primleri, erken kapama cezalari (bu ay / gecen ay). */
    public double fees, lastFees;
    /** Nakit akisi: kredilere odenen (anapara+faiz) ve yatirim harcamasi (bu ay / gecen ay). */
    public double debtServicePaid, lastDebtService, lastCapex;
    /** Olcum: gecikme, temerrut, yapilandirma sayilari. */
    public int lateEvents, defaults, restructures;
    public boolean bankrupt;
    public int negativeMonths;

    // Aylik P&L (devam eden ay)
    public double revenue, fuelCost, omCost, carbonCost, interest, fines, capexSpent;
    /** Trafo merkezi erisim bedeli: alinan / odenen (devam eden ay). */
    public double accessIncome, accessCost;
    /** UŞİ yillik baglanti bedelinin bu aya dusen kismi. */
    public double connectionCharge, lastConnectionCharge;
    // Gecen ay
    public double lastRevenue, lastFuel, lastOm, lastCarbon, lastInterest, lastFines, lastNet;
    public double lastAccessIncome, lastAccessCost;
    public double mwhGenerated, lastMwhGenerated;
    public double co2Tons, lastCo2;
    public double renewMwh, lastRenewMwh;
    /** Diger sirketlerden alinan temettu (nakit) ve istirak/bagli ortaklik kar payi (nakitsiz), bu ay / gecen ay. */
    public double dividendIncome, lastDividendIncome, affiliateIncome, lastAffiliateIncome;

    public double lastRenewShare() { return lastMwhGenerated > 0 ? lastRenewMwh / lastMwhGenerated : 0; }
    /** Son 12 ayin net kari. */
    public final ArrayDeque<Double> netHistory = new ArrayDeque<>();
    public final List<Double> valuationHistory = new ArrayList<>();
    public final List<Double> cashHistory = new ArrayList<>();
    public double valuation;
    public double stockPrice;
    /** Ortaklik tablosu, hisse fiyati, halka arz ve kontrol durumu. */
    public Equity eq = new Equity();

    public Company(String name, boolean player, int colorRgb, double cash) {
        this.name = name; this.player = player; this.colorRgb = colorRgb; this.cash = cash;
    }

    public double trailingNet() {
        double s = 0;
        for (double d : netHistory) s += d;
        return s;
    }

    /** Bu ayin FAVOK'u: faaliyet geliri - faaliyet giderleri (faiz, finansman masrafi ve amortisman haric). */
    public double monthEbitda() {
        return revenue - fuelCost - omCost - carbonCost - fines + accessIncome - accessCost - connectionCharge;
    }

    public double monthNetSoFar() {
        return revenue - fuelCost - omCost - carbonCost - interest - fines + accessIncome - accessCost - connectionCharge - fees;
    }

}
