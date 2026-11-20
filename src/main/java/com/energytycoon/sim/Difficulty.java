package com.energytycoon.sim;

public enum Difficulty {
    EASY("Kolay", 1.40, -0.01, 0.6, 0.75),
    NORMAL("Normal", 1.00, 0.00, 1.0, 1.00),
    HARD("Zor", 0.70, 0.02, 1.5, 1.35);

    public final String label;
    /** cashMult: tum ozel sirketlerin (oyuncu ve rakipler) esit baslangic nakdi carpani. */
    public final double cashMult, rateAdd, fineMult;
    /**
     * Rakip davranisi: karar sikligi (GridBalance.RIVAL_BUILD_CHANCE ile carpilir) ve karar icin gereken
     * nakit esigi (GridBalance.RIVAL_DECISION_CASH / rivalMult). Rakiplere kaynak (nakit) vermez.
     */
    public final double rivalMult;

    Difficulty(String label, double cash, double rate, double fine, double rival) {
        this.label = label; this.cashMult = cash; this.rateAdd = rate; this.fineMult = fine; this.rivalMult = rival;
    }
}
