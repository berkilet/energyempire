package com.energytycoon.sim;

/** Mevcut santrale yapilabilen yukseltmeler. */
public enum Upgrade {
    TRACKER(PlantType.SOLAR, "Tek eksenli tracker", "Paneller güneşi takip eder: +%18 üretim", 0.14, 45, false),
    ROBOT_CLEAN(PlantType.SOLAR, "Robotik panel temizliği", "+%4 üretim, degradasyon yarıya iner", 0.04, 15, false),
    REPOWER(PlantType.WIND, "Repowering", "Yeni kanat ve jeneratör: yaş sıfırlanır, +%15 kapasite", 0.45, 90, true),
    CCS(PlantType.COAL, "Karbon yakalama (CCS)", "CO₂ −%85, verim −%9 puanın onda biri", 0.55, 240, true),
    COFIRE(PlantType.COAL, "Biyokütle ortak yakma", "CO₂ −%30, yakıt maliyeti +%12", 0.08, 30, false),
    GAS_CCS(PlantType.GAS, "Karbon yakalama (CCS)", "CO₂ −%85, verim −%8", 0.60, 200, true),
    HYDROGEN(PlantType.GAS, "Hidrojen karışımı", "CO₂ −%20, yakıt maliyeti +%15", 0.06, 30, false),
    HYDRO_RETROFIT(PlantType.HYDRO, "Türbin yenileme", "+%10 üretim, yaş sıfırlanır", 0.20, 120, true),
    LIFE_EXT(PlantType.NUCLEAR, "Ömür uzatma", "Yaş sıfırlanır, arıza riski azalır", 0.22, 150, true);

    public final PlantType type;
    public final String label, desc;
    public final double costFraction;
    public final int days;
    /** Calisma sirasinda santral devre disi mi? */
    public final boolean offline;

    Upgrade(PlantType t, String label, String desc, double cost, int days, boolean offline) {
        this.type = t; this.label = label; this.desc = desc; this.costFraction = cost; this.days = days; this.offline = offline;
    }

    public double cost(Plant p) { return p.equipment.listCapex() * costFraction; }
}
