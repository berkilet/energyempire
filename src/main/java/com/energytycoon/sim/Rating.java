package com.energytycoon.sim;

/** Kredi notu: iki olcutten (Net borc / FAVOK, FAVOK / faiz) kotu olani belirler. */
public enum Rating {
    A, BBB, BB, B, CCC;

    public double premium() { return FinanceBalance.RATING_PREMIUM[ordinal()]; }

    public Rating down() { return this == CCC ? CCC : values()[ordinal() + 1]; }

    public Rating worse(Rating o) { return ordinal() >= o.ordinal() ? this : o; }

    /** Not hesabi (son 4 ceyrek): FAVOK <= 0 ise CCC. netDebt negatif olabilir (net nakit). */
    public static Rating compute(double ebitda4, double netDebt, double interest4) {
        if (ebitda4 <= 0) return CCC;
        double lev = netDebt / ebitda4;
        double cover = interest4 <= 1 ? Double.POSITIVE_INFINITY : ebitda4 / interest4;
        Rating byLev = CCC, byCover = CCC;
        for (int i = 0; i < 4; i++) if (lev <= FinanceBalance.RATING_ND_EBITDA[i]) { byLev = values()[i]; break; }
        for (int i = 0; i < 4; i++) if (cover >= FinanceBalance.RATING_COVER[i]) { byCover = values()[i]; break; }
        return byLev.worse(byCover);
    }
}
