package com.energytycoon.sim;

/** Kurgusal bankalar. */
public enum Bank {
    KIYI("Kıyıbank", "Genel ticari banka · ortalama koşullar, en geniş limit"),
    YESIL("Yeşilvadi Kalkınma Bankası", "Yenilenebilir odaklı · yenilenebilir krediye −0,5 puan, 10 yıla kadar vade; fosil santrale kredi vermez; taahhüt Net borç/FAVÖK ≤ 4,0"),
    DEMIRTAS("Demirtaş Yatırım Bankası", "Risk iştahı yüksek · CCC nota da kredi verir (+2 puan); diğer notlarda +0,5 puan");

    public final String label, profile;
    Bank(String l, String p) { label = l; profile = p; }

    public double concentration() { return this == KIYI ? FinanceBalance.CONCENTRATION_KIYI : FinanceBalance.CONCENTRATION_OTHER; }

    public double covenantLimit() { return this == YESIL ? FinanceBalance.COVENANT_ND_EBITDA_YESIL : FinanceBalance.COVENANT_ND_EBITDA; }

    /** Bankanin marj farki (not primine eklenir). */
    public double margin(Rating r) {
        return switch (this) { case DEMIRTAS -> r == Rating.CCC ? FinanceBalance.DEMIRTAS_CCC : FinanceBalance.DEMIRTAS_EXTRA; default -> 0; };
    }

    public boolean lendsTo(Rating r) { return r != Rating.CCC || this == DEMIRTAS; }

    public int maxYears() { return this == YESIL ? FinanceBalance.YESIL_MAX_YEARS : FinanceBalance.COMMERCIAL_MAX_YEARS; }

    /** Iliski kademesine gore prim indirimi ve pesin masraf carpani. */
    public static double tierDiscount(int score) { return score >= FinanceBalance.TIER3 ? FinanceBalance.TIER3_DISCOUNT : score >= FinanceBalance.TIER2 ? FinanceBalance.TIER2_DISCOUNT : 0; }
    public static double feeMult(int score) { return score >= FinanceBalance.TIER2 ? 0 : score >= FinanceBalance.TIER1 ? 0.5 : 1; }
    public static boolean noPrepayPenalty(int score) { return score >= FinanceBalance.TIER3; }
    public static String tierLabel(int score) {
        return score >= FinanceBalance.TIER3 ? "Kademe 3" : score >= FinanceBalance.TIER2 ? "Kademe 2" : score >= FinanceBalance.TIER1 ? "Kademe 1" : "—";
    }
}
