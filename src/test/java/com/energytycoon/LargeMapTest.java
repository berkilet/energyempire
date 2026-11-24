package com.energytycoon;

import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 128x128 harita, 16 sehir ve 7 rakip: kurulum ve iki yillik simulasyon tutarli. */
class LargeMapTest {
    @Test
    void bigMapWithSevenRivals() {
        GameSettings g = SimHarness.reference(5);
        g.size = 128; g.cities = GameSettings.maxCities(128); g.rivals = 7;
        GameState s = GameState.newGame(g);
        assertEquals(128, s.map.size);
        assertEquals(16, s.cities.size(), "128 haritada 16 sehir");
        long rivals = s.companies.stream().filter(c -> !c.player && !c.neutral).count();
        assertEquals(7, rivals, "7 rakip");
        Set<String> names = new HashSet<>(); Set<Integer> colors = new HashSet<>();
        for (Company c : s.companies) { assertTrue(names.add(c.name), "sirket adlari benzersiz"); assertTrue(colors.add(c.colorRgb), "renkler benzersiz"); }
        for (int h = 0; h < 2 * Balance.HOURS_PER_YEAR; h++) { s.tick(); s.messages.clear(); }
        for (Company c : s.companies) assertFalse(Double.isNaN(c.cash), c.name + " nakit NaN");
        long rivalPlants = s.plants.stream().filter(p -> p.owner != null && !p.owner.player && !p.owner.neutral).count();
        System.out.println("128 harita, 7 rakip: 2 yilda rakip santrali " + rivalPlants + ", iflas " + s.companies.stream().filter(c -> c.bankrupt).count());
        assertTrue(rivalPlants > 0, "rakipler yatirim yapiyor");
    }

    @Test
    void rivalLimitsFollowMapSize() {
        assertEquals(5, GameSettings.maxRivals(64));
        assertEquals(7, GameSettings.maxRivals(128));
        GameSettings g = new GameSettings(); g.size = 64; g.rivals = 9;
        assertEquals(5, g.effective().rivals);
    }
}
