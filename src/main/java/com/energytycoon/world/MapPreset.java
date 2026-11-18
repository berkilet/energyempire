package com.energytycoon.world;

/** Hazir harita tipleri. */
public enum MapPreset {
    //            etiket               aciklama                                                           kara  kabartma nehir gol   col   gunes  ruzgar komur gaz
    CONTINENT("Kıta",            "Bir yanı deniz olan geniş ana kara. Dengeli kaynaklar.",               0.64, 16, 2, 0.66, false, 0.00, 0.00, 1.0, 1.0),
    ISLAND("Ada",                "Tek büyük ada; kıyı rüzgarı güçlü, alan sınırlı.",                    0.46, 15, 2, 0.68, false, 0.02, 0.10, 0.8, 1.0),
    ARCHIPELAGO("Takımadalar",   "Dağınık adalar; hatlar denizden geçmek zorunda, rüzgar bol.",         0.38, 11, 0, 0.99, false, 0.03, 0.15, 0.7, 1.2),
    HIGHLANDS("Dağlık İç Bölge", "Denize kıyısı yok. Yüksek dağlar, göller ve nehirler: hidro cenneti.", 0.98, 22, 4, 0.60, false, -0.04, 0.05, 1.3, 0.6),
    RIVER_VALLEY("Nehir Vadisi", "Büyük bir nehir vadisi ve kolları; verimli ova, bol su.",              0.70, 14, 5, 0.70, false, 0.00, -0.05, 1.0, 1.0),
    DESERT_COAST("Kıyı Çölü",    "Kurak, düz çöl: güneş çok güçlü, su kıt, doğalgaz yatakları zengin.",  0.66, 8, 0, 0.99, true, 0.14, 0.05, 0.6, 1.6);

    public final String label, desc;
    public final double landFraction;
    public final int relief, rivers;
    public final double lakeThreshold;
    public final boolean desert;
    public final double sunBonus, windBonus, coalMult, gasMult;

    MapPreset(String label, String desc, double land, int relief, int rivers, double lake, boolean desert,
              double sun, double wind, double coal, double gas) {
        this.label = label; this.desc = desc; this.landFraction = land; this.relief = relief; this.rivers = rivers;
        this.lakeThreshold = lake; this.desert = desert; this.sunBonus = sun; this.windBonus = wind; this.coalMult = coal; this.gasMult = gas;
    }
}
