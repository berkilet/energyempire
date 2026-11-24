package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Rakip stratejisi statik degil: beklentiler ogrenilir, yonelim degisebilir, kayitta korunur. */
class RivalStrategyTest {
    static Company byStyle(GameState s, String style) {
        for (Company c : s.companies) if (!c.player && !c.neutral && c.aiStyle.equals(style)) return c;
        throw new AssertionError(style);
    }

    @Test
    void carbonExpectationRaisesThermalRisk() {
        GameState s = GameState.newGame(SimHarness.reference(3));
        Company f = byStyle(s, "fossil");
        Equipment coal = s.catalog.cheapest(PlantType.COAL, s.year());
        f.carbonBelief = 0.3;
        double optimistic = RivalStrategy.projectIndex(s, f, coal, s.cities.get(0));
        f.carbonBelief = 1.0;
        double realistic = RivalStrategy.projectIndex(s, f, coal, s.cities.get(0));
        assertTrue(realistic < optimistic, "karbonu ciddiye alan sirket komuru daha dusuk degerler: " + optimistic + " -> " + realistic);
    }

    @Test
    void everyRivalIsRational() {
        GameSettings g = SimHarness.reference(2); g.rivals = 7; g.size = 96; g.cities = 8;
        GameState s = GameState.newGame(g);
        for (Company c : s.companies) if (!c.player && !c.neutral)
            assertEquals(1.0, c.carbonBelief, 1e-9, c.name + ": ilan edilen karbon yolunu dogru bilir");
        int checked = 0;
        for (int y = 0; y < 10; y++) {
            for (int h = 0; h < Balance.HOURS_PER_YEAR; h++) { s.tick(); s.messages.clear(); }
            for (Company c : s.companies) {
                if (c.player || c.neutral || c.bankrupt) continue;
                for (City city : s.cities) for (Equipment e : RivalStrategy.candidates(s, c, city)) {
                    if (e == null) continue;
                    checked++;
                    assertTrue(RivalStrategy.projectIndex(s, c, e, city) >= 0, c.name + " (" + c.aiStyle + ") degeri negatif proje secti: " + e.fullName());
                }
            }
        }
        System.out.println("rasyonellik kontrolu: " + checked + " aday proje, hepsi NBD >= 0");
        assertTrue(checked > 0);
    }

    @Test
    void strategySurvivesSave() {
        GameState s = GameState.newGame(SimHarness.reference(4));
        Company f = byStyle(s, "fossil");
        f.carbonBelief = 0.77; f.priorWeight = 0.5; f.techView[PlantType.WIND.ordinal()] = 0.21; f.orientation = "dengeli";
        GameState r = new Json().fromJson(SaveData.class, new Json().toJson(SaveData.from(s))).restore();
        Company g = byStyle(r, "fossil");
        assertEquals(0.77, g.carbonBelief, 1e-9);
        assertEquals(0.5, g.priorWeight, 1e-9);
        assertEquals(0.21, g.techView[PlantType.WIND.ordinal()], 1e-9);
        assertEquals("dengeli", g.orientation);
    }
}
