package com.energytycoon.sim;

import com.energytycoon.world.MapPreset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Scenarios {
    private Scenarios() {}
    private static final Map<String, Scenario> ALL = new LinkedHashMap<>();

    static String money(double v) { return String.format(Locale.ROOT, "$%.2fB", v / 1e9); }
    static String pct(double v) { return String.format(Locale.ROOT, "%%%.0f", v * 100); }
    static String num(double v) { return String.format(Locale.ROOT, "%.0f", v); }

    /** Halka acik sirkette fiyat x hisse, kapalida temel deger (Capital.value). */
    static Objective valuation(double t) { return new Objective("Piyasa değeri", s -> s.capital.value(s.player), t, false, Scenarios::money); }
    static Objective renewShare(double t) { return new Objective("Üretiminizde yenilenebilir payı (aylık)", s -> s.player.lastRenewShare(), t, false, Scenarios::pct); }
    static Objective marketShare(double t) { return new Objective("Pazar payınız (aylık)", GameState::lastPlayerMarketShare, t, false, Scenarios::pct); }
    static Objective served(double t) { return new Objective("Karşılanan talep (aylık)", s -> s.lastServedRatio, t, false, Scenarios::pct); }
    static Objective noCoal() { return new Objective("Kömür santraliniz", s -> s.countPlants(s.player, PlantType.COAL), 0, true, Scenarios::num); }

    static {
        reg(new Scenario("free", "Serbest Oyun",
            "Süre sınırı yok. Şirketinizi dilediğiniz gibi büyütün. Hedef: $7 milyar piyasa değeri.",
            null, 0, List.of(valuation(7e9)), s -> {}));
        reg(new Scenario("green", "Yeşil Dönüşüm",
            "Karbon vergisi iki kat hızlı artıyor. " + Balance.SHARE_SCENARIO_DEADLINE + " sonuna kadar üretiminizin %60'ı yenilenebilir olsun, "
          + "pazarın en az %25'ini tutun ve bölgenin talebinin %95'i karşılansın.",
            null, Balance.SHARE_SCENARIO_DEADLINE, List.of(renewShare(0.60), marketShare(0.25), served(0.95)),
            s -> s.markets.carbonGrowthMult = 2.0));
        reg(new Scenario("coal", "Kömürden Çıkış",
            "Eski ve kirli bir kömür filosu devraldınız. " + Balance.SHARE_SCENARIO_DEADLINE + " sonuna kadar tüm kömür santrallerinizi kapatın, "
          + "ama pazarın %30'unu ve %95 arz güvenliğini koruyun. Kamunun eski kömür santralleri en geç "
          + GridBalance.LEGACY_COAL_EXIT_RETIRE_TO + "'de kapanıyor.",
            null, Balance.SHARE_SCENARIO_DEADLINE, List.of(noCoal(), marketShare(0.30), served(0.95)), Scenarios::setupCoal));
        reg(new Scenario("crisis", "Enerji Krizi",
            "Doğalgaz fiyatı kalıcı olarak ikiye katlandı, kömür de pahalı. 2030 sonuna kadar iflas etmeden "
          + "$5,5 milyar değere ulaşın. Elinizdeki eski açık çevrim gaz santralleri krizde zarar ediyor.",
            null, 2030, List.of(valuation(5.5e9)), Scenarios::setupCrisis));
        reg(new Scenario("islands", "Ada Şebekesi",
            "Dağınık adalarda ithal yakıt %80 pahalı. 2033 sonuna kadar talebin %97'sini karşılayın ve $3,5 milyar değere ulaşın.",
            MapPreset.ARCHIPELAGO, 2033, List.of(served(0.97), valuation(3.5e9)), s -> {
                s.markets.coalMult = 1.8; s.markets.gasMult = 1.8; s.markets.uraniumMult = 1.8;
            }));
    }

    private static void reg(Scenario s) { ALL.put(s.id, s); }

    static {
        // Kendi eski filosunu oyuncuya veren senaryolarda kamu filosu daha kucuk (oyuncu filosu da sayilir).
        ALL.get("coal").legacyCoverage = GridBalance.LEGACY_COVERAGE_SCENARIO;
        ALL.get("crisis").legacyCoverage = GridBalance.LEGACY_COVERAGE_SCENARIO;
        ALL.get("coal").legacyCoalRetireTo = GridBalance.LEGACY_COAL_EXIT_RETIRE_TO;
    }

    public static Scenario get(String id) { return ALL.getOrDefault(id, ALL.get("free")); }
    public static List<Scenario> all() { return new ArrayList<>(ALL.values()); }

    private static void setupCoal(GameState s) {
        s.markets.carbonGrowthMult = 1.5;
        Equipment old = s.catalog.get("fk-sub");
        int n = 0;
        for (City c : s.cities) {
            if (n >= Math.min(4, s.cities.size())) break;
            Plant p = s.placeLegacy(s.player, old, c, 22);
            if (p != null) n++;
        }
        s.player.cash = 260e6 * s.settings.difficulty.cashMult;
    }

    private static void setupCrisis(GameState s) {
        s.markets.gasMult = 2.0; s.markets.coalMult = 1.35;
        Equipment oc = s.catalog.get("at-oc");
        for (int i = 0; i < Math.min(2, s.cities.size()); i++) {
            s.placeLegacy(s.player, oc, s.cities.get(s.cities.size() - 1 - i), 12);
        }
        s.player.cash = 380e6 * s.settings.difficulty.cashMult;
    }
}
