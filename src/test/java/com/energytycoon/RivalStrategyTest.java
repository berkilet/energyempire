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
    void rivalsLearnAndAdapt() {
        GameSettings g = SimHarness.reference(2); g.rivals = 3; g.cities = 6;
        GameState s = GameState.newGame(g);
        Company f = byStyle(s, "fossil"), gr = byStyle(s, "green");
        double b0 = f.carbonBelief, w0 = f.priorWeight;
        assertTrue(b0 < 0.5, "fosil kokenli sirket baslangicta karbonu hafife alir");
        assertTrue(gr.carbonBelief > 1.0, "yesil kokenli sirket karbon artisini fazla bekler");
        int announcements = 0;
        for (int h = 0; h < 8 * Balance.HOURS_PER_YEAR; h++) {
            s.tick();
            for (String m : s.messages) if (m.startsWith("STRATEJİ")) announcements++;
            s.messages.clear();
        }
        assertTrue(f.carbonBelief > b0 + 0.3, "beklenti gozleme yaklasir: " + b0 + " -> " + f.carbonBelief);
        assertTrue(Math.abs(gr.carbonBelief - 1) < 0.2, "yesil sirketin beklentisi de gercege yaklasir");
        assertTrue(f.priorWeight < w0 * 0.8, "tarzin agirligi zamanla azalir");
        System.out.println("8 yilda strateji duyurusu: " + announcements + ", fosil kokenli yonelim: " + f.orientation);
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
