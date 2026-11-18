package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Kurgusal tedarikciler ve modeller. Her oyun kendi kopyasini tutar (fiyat endeksleri dalgalanir). */
public final class EquipmentCatalog {
    public final Map<String, Supplier> suppliers = new LinkedHashMap<>();
    public final Map<String, Equipment> equipment = new LinkedHashMap<>();

    public EquipmentCatalog() {
        Supplier sunpeak = sup("sunpeak", "SunPeak", "Uzak Doğu", 0.68, 10);
        Supplier helion = sup("helion", "Helion Modül", "Avrupa", 0.85, 12);
        Supplier lumina = sup("lumina", "Lumina PV", "Kuzey Amerika", 0.92, 15);
        Supplier kuzey = sup("kuzey", "Kuzey Güneş", "Yerli", 0.80, 12);
        Supplier ruzgarsan = sup("ruzgarsan", "Rüzgarsan", "Yerli", 0.66, 5);
        Supplier nordwind = sup("nordwind", "Nordwind", "İskandinavya", 0.88, 10);
        Supplier aeolus = sup("aeolus", "Aeolus Türbin", "Avrupa", 0.93, 12);
        Supplier boreal = sup("boreal", "Boreal Energy", "Kuzey Amerika", 0.82, 10);
        Supplier atlas = sup("atlas", "Atlas Turbines", "Avrupa", 0.85, 8);
        Supplier meridian = sup("meridian", "Meridian Power", "Kuzey Amerika", 0.92, 10);
        Supplier ferrum = sup("ferrum", "Ferrum Kazan", "Yerli", 0.78, 6);
        Supplier taurus = sup("taurus", "Taurus Heavy", "Uzak Doğu", 0.88, 10);
        Supplier hydrova = sup("hydrova", "Hydrova", "Avrupa", 0.90, 15);
        Supplier alpina = sup("alpina", "Alpina Hidro", "Alpler", 0.86, 12);
        Supplier atomtek = sup("atomtek", "Atomtek", "Avrupa", 0.90, 20);
        Supplier polaris = sup("polaris", "Polaris SMR", "Kuzey Amerika", 0.78, 15);

        //  id, type, supplier, model, tech, MW, capex/MW, eff, outMult, a, b, deg, build, om, year, visA, visB, not
        // GUNES: a = sicak saha kaybi
        add("sp-perc", PlantType.SOLAR, sunpeak, "SP-540", "Mono PERC", 100, 0.92e6, 1, 1.00, 0.10, 0, 0.0065, 0.9, 1.0, 2025, 0f, 0f,
            "En ucuz panel. Sıcak bölgelerde verim kaybı yüksek, hızlı yaşlanır.");
        add("hx-topcon", PlantType.SOLAR, helion, "HX-580", "N-tipi TOPCon", 100, 1.06e6, 1, 1.07, 0.06, 0, 0.0040, 1.0, 1.0, 2025, 1f, 0f,
            "Dengeli fiyat/performans, düşük degradasyon.");
        add("lu-hjt", PlantType.SOLAR, lumina, "Bifacial HJT-610", "Heteroeklem, çift yüzlü", 120, 1.22e6, 1, 1.14, 0.02, 0, 0.0025, 1.0, 0.95, 2025, 2f, 0f,
            "Arka yüzden de üretir; sıcakta en az kayıp. Pahalı ama uzun ömürlü.");
        add("kg-tandem", PlantType.SOLAR, kuzey, "KG Tandem-700", "Perovskit-silisyum tandem", 150, 1.28e6, 1, 1.32, 0.04, 0, 0.0085, 1.0, 1.0, 2033, 3f, 0f,
            "Yeni nesil yüksek verim; degradasyonu henüz yüksek.");

        // RUZGAR: a = cut-in, b = rated (ruzgar*saha birimi); visA = kule yuksekligi, visB = rotor yaricapi
        add("rs-36", PlantType.WIND, ruzgarsan, "RS-3.6/110", "IEC-I yüksek rüzgar", 54, 1.18e6, 1, 1.0, 0.13, 0.95, 0.0050, 0.9, 1.15, 2025, 0.95f, 0.50f,
            "Ucuz ve yerli; yalnızca güçlü rüzgarlı sahalarda verimli.");
        add("ae-50", PlantType.WIND, aeolus, "AE-5.0/140", "IEC-II dengeli", 60, 1.35e6, 1, 1.0, 0.08, 0.88, 0.0035, 1.0, 1.0, 2025, 1.10f, 0.62f,
            "Güvenilir, her sahada iyi performans.");
        add("nw-45", PlantType.WIND, nordwind, "NW-4.5/155 LowWind", "IEC-III düşük rüzgar", 63, 1.50e6, 1, 1.0, 0.04, 0.78, 0.0035, 1.0, 1.05, 2025, 1.25f, 0.72f,
            "Büyük rotor: zayıf rüzgarda bile döner. Pahalı.");
        add("bx-72", PlantType.WIND, boreal, "BX-7.2/172", "Yeni nesil dev türbin", 86, 1.38e6, 1, 1.0, 0.06, 0.84, 0.0040, 1.1, 0.9, 2028, 1.40f, 0.82f,
            "Büyük ölçek: MW başına düşük işletme maliyeti.");

        // GAZ
        add("at-oc", PlantType.GAS, atlas, "AT-OC150", "Açık çevrim (OCGT)", 150, 0.60e6, 0.36, 1, 0, 0, 0.0020, 0.55, 0.8, 2025, 0f, 0f,
            "Ucuz ve hızlı kurulur; düşük verim. Puant santrali olarak idealdir.");
        add("at-cc", PlantType.GAS, atlas, "AT-CC200", "Kombine çevrim (CCGT)", 200, 0.92e6, 0.53, 1, 0, 0, 0.0020, 1.0, 1.0, 2025, 1f, 0f,
            "Standart kombine çevrim.");
        add("mp-ha", PlantType.GAS, meridian, "MP-9HA", "H-sınıfı kombine çevrim", 300, 1.02e6, 0.60, 1, 0, 0, 0.0015, 1.2, 1.0, 2025, 2f, 0f,
            "En yüksek verim; büyük yatırım.");

        // KOMUR
        add("fk-sub", PlantType.COAL, ferrum, "FK-200", "Subkritik", 200, 1.55e6, 0.35, 1, 0, 0, 0.0030, 0.9, 1.0, 2025, 0f, 0f,
            "Eski teknoloji, ucuz ama emisyonu yüksek.");
        add("fk-sc", PlantType.COAL, ferrum, "FK-300 SC", "Süperkritik", 300, 1.85e6, 0.41, 1, 0, 0, 0.0025, 1.0, 1.0, 2025, 1f, 0f,
            "Orta sınıf verim.");
        add("th-usc", PlantType.COAL, taurus, "TH-USC 450", "Ultra-süperkritik", 450, 2.20e6, 0.46, 1, 0, 0, 0.0020, 1.15, 0.95, 2025, 2f, 0f,
            "Kömürde en yüksek verim; soğutma kuleli büyük tesis.");

        // HIDRO: a = tasarim dusu (0 alcak, 1 yuksek)
        add("hv-kaplan", PlantType.HYDRO, hydrova, "Kaplan K-100", "Kaplan (alçak düşü)", 100, 2.35e6, 1, 1.0, 0.0, 0, 0.0010, 0.9, 1.0, 2025, 0f, 0f,
            "Düz, alçak arazide ve nehir ağızlarında en iyisi.");
        add("hv-francis", PlantType.HYDRO, hydrova, "Francis F-120", "Francis (orta düşü)", 120, 2.60e6, 1, 1.0, 0.5, 0, 0.0010, 1.0, 1.0, 2025, 1f, 0f,
            "Her yükseklikte makul performans.");
        add("al-pelton", PlantType.HYDRO, alpina, "Pelton P-140", "Pelton (yüksek düşü)", 140, 2.85e6, 1, 1.0, 1.0, 0, 0.0010, 1.1, 1.0, 2025, 2f, 0f,
            "Dağlık, yüksek arazideki göllerde çok verimli.");

        // NUKLEER
        add("atr-600", PlantType.NUCLEAR, atomtek, "ATR-600", "Gen III+ basınçlı su", 600, 4.30e6, 0.33, 1, 0, 0, 0.0010, 1.0, 1.0, 2025, 0f, 0f,
            "Büyük baz yük santrali. Çok pahalı, çok uzun inşaat.");
        add("ps-smr", PlantType.NUCLEAR, polaris, "PSMR-300", "Küçük modüler reaktör", 300, 4.90e6, 0.32, 1, 0, 0, 0.0010, 0.62, 1.1, 2030, 1f, 0f,
            "Fabrikada üretilir: inşaat süresi kısa, MW başına pahalı.");
    }

    private Supplier sup(String id, String name, String origin, double rep, int warranty) {
        Supplier s = new Supplier(id, name, origin, rep, warranty);
        suppliers.put(id, s);
        return s;
    }

    private void add(String id, PlantType t, Supplier s, String model, String tech, double mw, double capex, double eff, double out,
                     double a, double b, double deg, double build, double om, int year, float visA, float visB, String note) {
        equipment.put(id, new Equipment(id, t, s, model, tech, mw, capex, eff, out, a, b, deg, build, om, year, visA, visB, note));
    }

    public Equipment get(String id) { return equipment.get(id); }

    public List<Equipment> forType(PlantType t) {
        List<Equipment> l = new ArrayList<>();
        for (Equipment e : equipment.values()) if (e.type == t) l.add(e);
        return l;
    }

    public List<Equipment> available(PlantType t, long year) {
        List<Equipment> l = new ArrayList<>();
        for (Equipment e : forType(t)) if (e.availableYear <= year) l.add(e);
        return l;
    }

    /** v1 kayitlari ve varsayilan secim icin. */
    public Equipment defaultFor(PlantType t) {
        return switch (t) {
            case SOLAR -> get("hx-topcon"); case WIND -> get("ae-50"); case GAS -> get("at-cc");
            case COAL -> get("fk-sc"); case HYDRO -> get("hv-francis"); case NUCLEAR -> get("atr-600");
        };
    }

    public Equipment cheapest(PlantType t, long year) {
        Equipment best = null;
        for (Equipment e : available(t, year)) if (best == null || e.capex() < best.capex()) best = e;
        return best;
    }

    public Equipment newest(PlantType t, long year) {
        Equipment best = null;
        for (Equipment e : available(t, year)) if (best == null || e.availableYear > best.availableYear
                || (e.availableYear == best.availableYear && e.capexPerMW > best.capexPerMW)) best = e;
        return best;
    }

    public Collection<Supplier> allSuppliers() { return suppliers.values(); }
}
