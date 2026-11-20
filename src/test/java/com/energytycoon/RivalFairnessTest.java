package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Faz 1: rakip başlangıç eşitliği ve kamu eski filosu. */
class RivalFairnessTest {

    private static List<Company> rivals(GameState s) {
        return s.companies.stream().filter(c -> !c.player && !c.neutral).toList();
    }

    @Test
    void a_rivalsStartEqualAndEmpty() {
        for (Difficulty d : Difficulty.values()) {
            GameSettings g = SimHarness.reference(1);
            g.difficulty = d;
            GameState s = GameState.newGame(g);
            assertEquals(2, rivals(s).size());
            for (Company r : rivals(s)) {
                assertEquals(0, s.totalPlantsOf(r), d + " " + r.name + " santralle başladı");
                assertEquals(s.player.cash, r.cash, 1e-6, d + " " + r.name + " nakdi oyuncudan farklı");
                assertTrue(s.lines.stream().noneMatch(l -> l.owner == r));
            }
            assertEquals(Balance.START_CASH * d.cashMult, s.player.cash, 1e-6);
        }
    }

    @Test
    void b_noCompanyBuildsIntoOverdraft() {
        for (long seed = 1; seed <= 3; seed++) {
            GameState s = GameState.newGame(SimHarness.reference(seed));
            Map<Company, Double> lastCapex = new HashMap<>();
            for (Company c : s.companies) lastCapex.put(c, c.capexSpent);
            int builds = 0;
            for (int h = 0; h < 12 * Balance.HOURS_PER_MONTH; h++) {
                s.tick();
                for (Company c : s.companies) {
                    double prev = lastCapex.get(c);
                    boolean built = c.capexSpent > prev + 1 || (c.capexSpent > 1 && c.capexSpent < prev); // ay sifirlamasindan sonra yeni harcama
                    if (built && !c.neutral) {
                        builds++;
                        // Insaat yalnizca nakit yettiginde: harcamadan hemen sonra nakit eksi olamaz (saatlik isletme akisi icin 5M tolerans).
                        assertTrue(c.cash > -5e6, "seed " + seed + " " + c.name + " insaattan sonra nakit " + c.cash);
                    }
                    lastCapex.put(c, c.capexSpent);
                }
            }
            for (Company r : rivals(s)) assertTrue(r.cash > Balance.BANKRUPT_CASH, r.name + " iflas sinirinda: " + r.cash);
            System.out.println("seed " + seed + ": 12 ayda rakip/oyuncu inşaat olayı " + builds);
        }
    }

    @Test
    void c_firstMonthServedByLegacyFleet() {
        for (long seed = 1; seed <= 5; seed++) {
            GameState s = GameState.newGame(SimHarness.reference(seed));
            assertNotNull(s.neutral);
            assertTrue(s.plants.stream().anyMatch(p -> p.legacy));
            List<SimHarness.Month> m = SimHarness.runMonths(s, 1);
            System.out.printf("seed %d: ilk ay karşılanan talep %.4f, ort. fiyat %.1f%n", seed, m.get(0).servedRatio, m.get(0).avgPrice);
            assertTrue(m.get(0).servedRatio >= 0.95, "seed " + seed + " ilk ay karşılanan " + m.get(0).servedRatio);
        }
    }

    @Test
    void d_optionalOldRivalFleet() {
        GameSettings g = SimHarness.reference(1);
        g.rivalsStartWithFleet = true;
        GameState s = GameState.newGame(g);
        Map<String, List<PlantType>> expected = Map.of(
            "fossil", List.of(PlantType.COAL),
            "green", List.of(PlantType.WIND, PlantType.GAS),
            "balanced", List.of(PlantType.GAS, PlantType.SOLAR));
        for (Company r : rivals(s)) {
            List<PlantType> types = s.plants.stream().filter(p -> p.owner == r).map(p -> p.type).toList();
            assertEquals(expected.get(r.aiStyle), types, r.name);
            for (Plant p : s.plants) if (p.owner == r) {
                assertTrue(p.operational(), "hemen çalışır olmalı");
                assertFalse(p.lines.isEmpty(), "şehre bağlı olmalı");
                assertFalse(p.legacy);
            }
            assertEquals(s.player.cash, r.cash, 1e-6, "filo bedava; nakit değişmez");
        }
    }

    @Test
    void neutralCompanyRules() {
        GameState s = GameState.newGame(SimHarness.reference(2));
        Company n = s.neutral;
        assertTrue(n.neutral && !n.player);
        // Kapsama: her sehre bagli eski kapasite >= 1.05 x gercek tepe
        for (City c : s.cities) {
            double cap = 0;
            for (PowerLine l : c.lines) if (l.other(c) instanceof Plant p && p.legacy) cap += p.capacityMW();
            assertTrue(cap >= c.peakMW() * GameState.maxDemandMult() * GridBalance.LEGACY_COVERAGE - 1, c.name + " kapsama " + cap);
        }
        for (Plant p : s.plants) if (p.legacy) {
            assertSame(n, p.owner);
            assertTrue(p.ageYears(s.hour) >= GridBalance.LEGACY_AGE_MIN - 0.01 && p.ageYears(s.hour) <= GridBalance.LEGACY_AGE_MAX + 0.01);
            assertTrue(p.retireYear >= GridBalance.LEGACY_RETIRE_FROM && p.retireYear <= GridBalance.LEGACY_RETIRE_TO);
            assertNotNull(p.customName);
            assertTrue(p.equipment.id.equals(GridBalance.LEGACY_COAL_ID) || p.equipment.id.equals(GridBalance.LEGACY_GAS_ID));
        }
        // Iflas etmez, YZ calistirmaz: 3 yil sonra hic yeni santral kurmamis olmali.
        long legacyAtStart = s.plants.stream().filter(p -> p.owner == n).count();
        SimHarness.run(s, 3L * Balance.HOURS_PER_YEAR);
        assertFalse(n.bankrupt);
        assertTrue(s.plants.stream().filter(p -> p.owner == n).count() <= legacyAtStart);
    }

    @Test
    void legacyFuelIsMoreExpensive() {
        GameState s = GameState.newGame(SimHarness.reference(3));
        Plant old = s.plants.stream().filter(p -> p.legacy && p.type == PlantType.GAS).findFirst().orElseThrow();
        boolean was = old.legacy;
        double withMult = s.fuelCostPerMWh(old);
        old.legacy = false;
        double without = s.fuelCostPerMWh(old);
        old.legacy = was;
        assertEquals(GridBalance.LEGACY_FUEL_MULT, withMult / without, 1e-9);
    }

    @Test
    void retirementScheduleNoticeAndClosure() {
        GameState s = GameState.newGame(SimHarness.reference(4));
        List<Plant> legacy = s.plants.stream().filter(p -> p.legacy).toList();
        // Esit dagilim: en erken 2027 Ocak, en gec 2034 Aralik
        int min = legacy.stream().mapToInt(p -> p.retireYear * 12 + p.retireMonth).min().orElseThrow();
        int max = legacy.stream().mapToInt(p -> p.retireYear * 12 + p.retireMonth).max().orElseThrow();
        assertEquals(GridBalance.LEGACY_RETIRE_FROM * 12, min);
        assertEquals(GridBalance.LEGACY_RETIRE_TO * 12 + 11, max);
        Plant first = legacy.stream().min((a, b) -> Integer.compare(a.retireYear * 12 + a.retireMonth, b.retireYear * 12 + b.retireMonth)).orElseThrow();
        boolean notice = false, closed = false;
        while (s.year() < first.retireYear + 1 && !s.gameOver) {
            s.tick();
            String m;
            while ((m = s.messages.poll()) != null) {
                if (m.contains(first.customName) && m.contains("kapanacak")) notice = true;
                if (m.contains(first.customName) && m.contains("kapatıldı")) closed = true;
            }
        }
        assertTrue(notice, "6 ay önceden haber yayımlanmalı");
        assertTrue(closed, "kapanış haberi");
        assertFalse(s.plants.contains(first), "kapanan santral kaldırılmalı");
        assertTrue(first.lines.isEmpty());
    }

    @Test
    void scenariosWithOwnFleetUseSmallerLegacyCoverage() {
        for (String id : new String[] { "coal", "crisis" }) {
            GameSettings g = SimHarness.reference(1);
            g.scenarioId = id;
            GameState s = GameState.newGame(g);
            assertEquals(GridBalance.LEGACY_COVERAGE_SCENARIO, s.scenario.legacyCoverage);
            double legacyCap = s.plants.stream().filter(p -> p.legacy).mapToDouble(Plant::capacityMW).sum();
            double peak = s.cities.stream().mapToDouble(c -> c.peakMW() * GameState.maxDemandMult()).sum();
            assertTrue(legacyCap < peak * 0.75, id + " kamu filosu fazla büyük: " + legacyCap + " / " + peak);
        }
    }

    @Test
    void saveV3RoundTripKeepsNeutralAndLegacy() {
        GameState s = GameState.newGame(SimHarness.reference(5));
        SimHarness.run(s, 24 * 10);
        GameState r = new Json().fromJson(SaveData.class, new Json().toJson(SaveData.from(s))).restore();
        assertNotNull(r.neutral);
        assertEquals(s.plants.stream().filter(p -> p.legacy).count(), r.plants.stream().filter(p -> p.legacy).count());
        Plant a = s.plants.stream().filter(p -> p.legacy).findFirst().orElseThrow();
        Plant b = r.plants.stream().filter(p -> p.id == a.id).findFirst().orElseThrow();
        assertEquals(a.retireYear, b.retireYear); assertEquals(a.retireMonth, b.retireMonth);
        assertEquals(a.customName, b.customName); assertSame(r.neutral, b.owner);
        assertFalse(r.settings.rivalsStartWithFleet);
        SimHarness.run(r, 24 * 10);
    }

    @Test
    void loadsV2Save() {
        String v2 = "{version:2,seed:9,hour:2000,nextId:40,won:false,preset:CONTINENT,difficulty:NORMAL,scenario:free,companyName:\"Eski A.Ş.\",size:64,cityCount:1,rivals:1,"
            + "coal:11,gas:22,uranium:4.5,carbon:12,wind:0.3,cloud:0.7,demandNoise:1,"
            + "companies:[{name:\"Eski A.Ş.\",player:true,color:3066993,cash:5e8,debt:0},{name:Anadolu Enerji,player:false,color:15158332,cash:4e8,debt:0,aiStyle:fossil}],"
            + "cities:[{id:1,x:20,y:20,name:Akköy,pop:500000,sat:1}],"
            + "plants:[{id:5,x:24,y:20,owner:1,type:COAL,equipment:fk-sc,buildLeft:0,outage:0,builtAt:0}],"
            + "lines:[{id:6,a:5,b:1,owner:1,cost:4e6}]}";
        GameState r = new Json().fromJson(SaveData.class, v2).restore();
        assertNull(r.neutral, "v2 kayıtta kamu şirketi yoktu");
        assertTrue(r.settings.rivalsStartWithFleet, "v2 oyunları eski filo kuralıyla başlamıştı");
        assertEquals(1, r.plants.size());
        assertFalse(r.plants.get(0).legacy);
        SimHarness.run(r, 24 * 40);
        assertTrue(r.plants.get(0).lifetimeMWh > 0);
    }

    @Test
    void paidBuildChargesPlantAndLineOrNothing() {
        GameState s = GameState.newGame(SimHarness.reference(6));
        Company r = rivals(s).get(0);
        Equipment e = s.catalog.get("at-cc");
        double before = r.cash;
        Plant p = RivalAI.buildNear(s, r, e, s.cities.get(0));
        assertNotNull(p);
        PowerLine l = p.lines.get(0);
        assertSame(r, l.owner);
        assertEquals(before - e.capex() - l.buildCost, r.cash, 1, "santral + hat ödenmeli");
        assertTrue(p.buildHoursLeft > 0, "inşaat süresi işlemeli");
        // Santrale yetip hatta yetmeyen nakit: hiçbir şey kurulmaz, para harcanmaz.
        r.cash = e.capex() + 1;
        int plantsBefore = s.plants.size();
        assertNull(RivalAI.buildNear(s, r, e, s.cities.get(1), 0));
        assertEquals(plantsBefore, s.plants.size());
        assertEquals(e.capex() + 1, r.cash, 1e-6);
    }
}
