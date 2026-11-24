package com.energytycoon.sim;

import com.energytycoon.world.MapPreset;

/** Yeni oyun ayarlari. */
public final class GameSettings {
    public static final int[] SIZES = { 64, 96, 128 };
    public static final String[] SIZE_LABELS = { "Orta", "Büyük", "Çok Büyük" };

    public MapPreset preset = MapPreset.CONTINENT;
    public int size = 64;
    public int cities = 5;
    public int rivals = 2;
    public Difficulty difficulty = Difficulty.NORMAL;
    public long seed = 1;
    public String scenarioId = "free";
    public String companyName = "Yeni Enerji A.Ş.";
    /** true: rakipler eski surumdeki gibi bedava, calisir durumda santrallerle baslar. */
    public boolean rivalsStartWithFleet = false;
    /** UŞİ her ocak kendi hat planini yapar (ayri sebeke parcalarini baglar, dolu hatlari guclendirir). */
    public boolean operatorGrowth = true;
    /** Kamu eski filosu (tarafsiz, takvimle kapanan eski santraller). */
    public boolean legacyFleet = true;

    public GameSettings() {}

    public GameSettings copy() {
        GameSettings g = new GameSettings();
        g.preset = preset; g.size = size; g.cities = cities; g.rivals = rivals; g.difficulty = difficulty;
        g.seed = seed; g.scenarioId = scenarioId; g.companyName = companyName;
        g.rivalsStartWithFleet = rivalsStartWithFleet; g.operatorGrowth = operatorGrowth; g.legacyFleet = legacyFleet;
        return g;
    }

    public Scenario scenario() { return Scenarios.get(scenarioId); }

    /** Senaryonun zorunlu kildigi ayarlari uygula. */
    public GameSettings effective() {
        GameSettings g = copy();
        Scenario s = scenario();
        if (s.forcedPreset != null) g.preset = s.forcedPreset;
        g.cities = Math.max(2, Math.min(maxCities(g.size), g.cities));
        g.rivals = Math.max(0, Math.min(maxRivals(g.size), g.rivals));
        return g;
    }

    public static int maxCities(int size) { return size <= 48 ? 6 : size <= 64 ? 8 : size <= 96 ? 12 : 16; }

    /** Harita buyudukce daha cok rakip sigar (her rakibe yeterli santral sahasi kalmasi icin). */
    public static int maxRivals(int size) { return Math.min(GameState.RIVALS.length, size <= 64 ? 5 : 7); }
}
