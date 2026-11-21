package com.energytycoon.sim;

/** Kredi: esit taksitli (annuite), once faiz sonra anapara. Degisken faiz 3 ayda bir guncellenir, taksit kalan vadeye gore yeniden hesaplanir. */
public final class Loan {
    public enum Type { COMMERCIAL("Ticari"), ECA("ECA"), REVOLVER("İşletme kredisi");
        public final String label; Type(String l) { label = l; } }
    public enum Status { ACTIVE("Aktif"), LATE("Gecikmede"), DEFAULTED("Temerrüt"), RESTRUCTURED("Yapılandırıldı"), CLOSED("Kapandı");
        public final String label; Status(String l) { label = l; } }

    public int id;
    public Type type;
    public Bank bank;
    /** ECA: tedarikci ulkesi; bagli santral (0 yok). */
    public String ecaOrigin;
    public int linkedPlant;
    public double principal, outstanding;
    public boolean fixed;
    /** Gecerli yillik faiz ve politika faizi ustundeki marj (degiskende rate = politika + spread + ekler). */
    public double rate, spread;
    public int tenorMonths, graceMonths, frequencyMonths;
    public long startHour, nextDueHour, maturityHour, graceEndHour, lastResetHour;
    public int covenantBreaches, daysLate;
    public Status status = Status.ACTIVE;
    /** Tahakkuk etmis (odenmemis) faiz; vadesi gelmis odenmemis tutar; erken talep (taahhut ihlali). */
    public double accrued, dueAmount, demand;
    /** Vadesi gelmis tutarin anapara kismi (odenince bakiyeden duser); son ceyrek DSCR. */
    public double pendingPrincipal, lastDscr = Double.NaN;
    public int onTimePayments;
    public boolean everLate;
    /** Ekler: taahhut ihlali (+1 puan), DSCR (+0,5 puan), gecikme (+2 puan), yapilandirma (+2 puan). */
    public boolean covenantPenalty, dscrPenalty, restructured;
    public int dscrLowQuarters;
    /** Isletme kredisi: limit ve kullanimin basladigi saat. */
    public double limit;
    public long drawnSinceHour = -1;

    public boolean open() { return status != Status.CLOSED && status != Status.DEFAULTED; }

    public double periodHours() { return frequencyMonths * (double) Balance.HOURS_PER_MONTH; }

    public int remainingPeriods() {
        return Math.max(1, (int) Math.round((maturityHour - nextDueHour) / periodHours()) + 1);
    }

    /** Ek primler dahil gecerli faiz. */
    public double effectiveRate() {
        return rate + (covenantPenalty ? FinanceBalance.COVENANT_RATE_PENALTY : 0) + (dscrPenalty ? FinanceBalance.ECA_DSCR_PENALTY : 0)
            + (status == Status.LATE ? FinanceBalance.LATE_RATE : 0);
    }

    /** Annuite taksiti: P * r / (1 - (1 + r)^-n), r = yillik faiz / yilda donem sayisi. */
    public static double annuity(double principal, double annualRate, int periodsPerYear, int n) {
        double r = annualRate / periodsPerYear;
        if (n <= 0) return principal;
        if (Math.abs(r) < 1e-12) return principal / n;
        return principal * r / (1 - Math.pow(1 + r, -n));
    }

    public String name() {
        return type == Type.ECA ? ecaOrigin + " İhracat Kredi Kuruluşu · " + bank.label : type.label + " · " + bank.label;
    }
}
