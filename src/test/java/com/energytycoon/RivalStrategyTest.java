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
        Equipment coal = s.catalog.get("th-usc"); // devreye girecek verimlilikte (ultra-superkritik) bir komur santrali
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
        int checked = 0;
        for (int y = 0; y < 10; y++) {
            for (int h = 0; h < Balance.HOURS_PER_YEAR; h++) { s.tick(); s.messages.clear(); }
            for (Company c : s.companies) {
                if (c.player || c.neutral || c.bankrupt) continue;
                for (City city : s.cities) for (Equipment e : RivalStrategy.candidates(s, c, city)) {
                    if (e == null) continue;
                    checked++;
                    assertTrue(RivalStrategy.projectIndex(s, c, e, city) >= c.hurdle - 1e-9, c.name + " (" + c.aiStyle + ") kendi esiginin altinda proje secti: " + e.fullName());
                }
            }
        }
        System.out.println("tutarlilik kontrolu: " + checked + " aday proje, hepsi sirketin kendi beklentisiyle esigin ustunde");
        assertTrue(checked > 0);
    }

    @Test
    void charactersDifferBetweenGames() {
        // ayni kokenli sirket farkli oyunlarda farkli karakterle baslar (yeniden oynanabilirlik)
        java.util.Set<Long> beliefs = new java.util.HashSet<>();
        double lo = 9, hi = -9;
        for (long seed = 1; seed <= 12; seed++) {
            GameState s = GameState.newGame(SimHarness.reference(seed));
            Company f = byStyle(s, "fossil");
            beliefs.add(Math.round(f.carbonBelief * 1000));
            lo = Math.min(lo, f.carbonBelief); hi = Math.max(hi, f.carbonBelief);
        }
        assertTrue(beliefs.size() >= 10, "her oyunda farkli karakter");
        assertTrue(hi - lo > 0.3, "fosil kokenli sirketlerin karbon beklentisi genis dagilir: " + lo + ".." + hi);
    }

    @Test
    void strategySurvivesSave() {
        GameState s = GameState.newGame(SimHarness.reference(4));
        Company f = byStyle(s, "fossil");
        f.carbonBelief = 0.77; f.priorWeight = 0.5; f.hurdle = -0.07; f.scarcityView = 1.3; f.techView[PlantType.WIND.ordinal()] = 0.21; f.orientation = "dengeli";
        GameState r = new Json().fromJson(SaveData.class, new Json().toJson(SaveData.from(s))).restore();
        Company g = byStyle(r, "fossil");
        assertEquals(0.77, g.carbonBelief, 1e-9);
        assertEquals(0.5, g.priorWeight, 1e-9);
        assertEquals(0.21, g.techView[PlantType.WIND.ordinal()], 1e-9);
        assertEquals("dengeli", g.orientation);
        assertEquals(-0.07, g.hurdle, 1e-9);
        assertEquals(1.3, g.scarcityView, 1e-9);
    }
}
