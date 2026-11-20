package com.energytycoon;

import com.energytycoon.sim.*;
import com.energytycoon.world.MapPreset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapScenarioTest {
    @Test
    void allPresetsGenerateValidMaps() {
        for (MapPreset mp : MapPreset.values()) {
            for (int size : GameSettings.SIZES) {
                for (long seed = 1; seed <= 5; seed++) {
                    GameSettings g = new GameSettings();
                    g.preset = mp; g.size = size; g.seed = seed; g.cities = size == 96 ? 9 : 5;
                    GameState s = GameState.newGame(g);
                    assertTrue(s.cities.size() >= Math.min(g.cities, 4), mp + "/" + size + "/" + seed + " sehir=" + s.cities.size());
                    for (City c : s.cities) assertFalse(s.map.isWater(c.x, c.y));
                    int land = 0, rivers = 0;
                    for (int i = 0; i < s.map.size * s.map.size; i++) { if (!s.map.water[i]) land++; if (s.map.river[i]) rivers++; }
                    double lf = land / (double) (s.map.size * s.map.size);
                    assertTrue(lf > 0.25 && lf < 0.995, mp + " kara orani " + lf);
                    if (mp == MapPreset.RIVER_VALLEY || mp == MapPreset.HIGHLANDS) assertTrue(rivers > 10, mp + " nehir " + rivers);
                }
            }
        }
    }

    @Test
    void cityAndRivalSettingsRespected() {
        GameSettings g = new GameSettings();
        g.size = 96; g.cities = 10; g.rivals = 3; g.seed = 4;
        GameState s = GameState.newGame(g);
        assertEquals(10, s.cities.size());
        assertEquals(4, s.companies.stream().filter(c -> !c.neutral).count(), "oyuncu + 3 rakip");
        assertNotNull(s.neutral, "kamu eski filosu");
        g.rivals = 0;
        assertEquals(1, GameState.newGame(g).companies.stream().filter(c -> !c.neutral).count());
    }

    @Test
    void desertHasStrongerSun() {
        double desert = avgSun(MapPreset.DESERT_COAST), hi = avgSun(MapPreset.HIGHLANDS);
        assertTrue(desert > hi + 0.1);
    }

    static double avgSun(MapPreset p) {
        GameSettings g = new GameSettings(); g.preset = p; g.seed = 3;
        GameState s = GameState.newGame(g);
        double sum = 0; int n = 0;
        for (int i = 0; i < s.map.size * s.map.size; i++) if (!s.map.water[i]) { sum += s.map.sun[i]; n++; }
        return sum / n;
    }

    @Test
    void scenariosSetUpAndRunStable() {
        for (Scenario sc : Scenarios.all()) {
            GameSettings g = new GameSettings();
            g.scenarioId = sc.id; g.seed = 21;
            GameState s = GameState.newGame(g);
            if (sc.forcedPreset != null) assertEquals(sc.forcedPreset, s.map.preset);
            if (sc.id.equals("coal")) assertTrue(s.countPlants(s.player, PlantType.COAL) >= 3);
            for (int y = 0; y < 6 && !s.gameOver; y++) for (int i = 0; i < Balance.HOURS_PER_YEAR; i++) s.tick();
            assertTrue(Double.isFinite(s.player.cash));
            for (Objective o : sc.objectives) assertTrue(Double.isFinite(o.value(s)), sc.id + " " + o.label);
            System.out.printf("senaryo %-8s yil %d bitti=%s kazandi=%s nakit=%.0fM  %s%n", sc.id, s.year(), s.gameOver, s.won,
                s.player.cash / 1e6, sc.objectives.stream().map(o -> o.label + "=" + o.status(s)).toList());
        }
    }

    @Test
    void deadlineFailsScenario() {
        GameSettings g = new GameSettings();
        g.scenarioId = "crisis"; g.seed = 5;
        GameState s = GameState.newGame(g);
        while (!s.gameOver && s.year() <= 2031) s.tick();
        assertTrue(s.gameOver || s.won);
        if (!s.won) assertTrue(s.gameOverReason.contains("Süre doldu") || s.gameOverReason.contains("iflas"));
    }

    @Test
    void objectiveEvaluation() {
        GameState s = GameState.newGame(3, "T");
        Objective noCoal = Scenarios.get("coal").objectives.get(0);
        assertTrue(noCoal.met(s));
        s.placeLegacy(s.player, s.catalog.defaultFor(PlantType.COAL), s.cities.get(0), 0);
        assertFalse(noCoal.met(s));
    }
}
