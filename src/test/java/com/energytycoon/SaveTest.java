package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SaveTest {
    @Test
    void roundTrip() {
        GameState s = GameState.newGame(99, "Kayit A.Ş.");
        s.placeLegacy(s.player, s.catalog.defaultFor(PlantType.GAS), s.cities.get(0), 0);
        RivalAI.buildNear(s, s.player, PlantType.SOLAR, s.cities.get(1));
        for (int i = 0; i < 24 * 200; i++) s.tick();

        Json json = new Json();
        String txt = json.toJson(SaveData.from(s));
        SaveData back = new Json().fromJson(SaveData.class, txt);
        GameState r = back.restore();

        assertEquals(s.plants.size(), r.plants.size());
        assertEquals(s.lines.size(), r.lines.size());
        assertEquals(s.cities.size(), r.cities.size());
        assertEquals(s.player.cash, r.player.cash, 1e-6);
        assertEquals("Kayit A.Ş.", r.player.name);
        assertEquals(s.hour, r.hour);
        assertEquals(s.plants.get(0).equipment.id, r.plants.get(0).equipment.id);
        for (int i = 0; i < 24 * 60; i++) r.tick();
        assertTrue(Double.isFinite(r.player.cash));
        assertTrue(r.plants.stream().anyMatch(p -> p.lifetimeMWh > 0));
    }

    @Test
    void v2SettingsAndUpgradesRoundTrip() {
        GameSettings g = new GameSettings();
        g.preset = com.energytycoon.world.MapPreset.HIGHLANDS; g.size = 64; g.cities = 4; g.rivals = 1; g.scenarioId = "green"; g.seed = 8;
        GameState s = GameState.newGame(g);
        Plant p = s.placeLegacy(s.player, s.catalog.get("lu-hjt"), s.cities.get(0), 0);
        s.player.cash = 1e9;
        assertNull(s.startUpgrade(p, Upgrade.TRACKER));
        for (int i = 0; i < 24 * 50; i++) s.tick();
        GameState r = new Json().fromJson(SaveData.class, new Json().toJson(SaveData.from(s))).restore();
        assertEquals(com.energytycoon.world.MapPreset.HIGHLANDS, r.map.preset);
        assertEquals(64, r.map.size);
        assertEquals("green", r.scenario.id);
        assertEquals(s.companies.size(), r.companies.size());
        Plant rp = r.plants.stream().filter(x -> x.id == p.id).findFirst().orElseThrow();
        assertEquals("lu-hjt", rp.equipment.id);
        assertTrue(rp.upgrades.contains(Upgrade.TRACKER));
        assertEquals(s.markets.carbonGrowthMult, r.markets.carbonGrowthMult);
    }

    @Test
    void loadsV1Save() {
        // v1 formatinda (ekipman/ayar alani yok) minimal kayit
        String v1 = "{version:1,seed:42,hour:1000,nextId:30,won:false,coal:11,gas:22,uranium:4.5,carbon:12,wind:0.3,cloud:0.7,demandNoise:1,"
            + "companies:[{name:Eski,player:true,color:3066993,cash:5e8,debt:0},{name:Anadolu Enerji,player:false,color:15158332,cash:4e8,debt:0}],"
            + "cities:[{id:1,x:20,y:20,name:Akköy,pop:500000,sat:1}],"
            + "plants:[{id:5,x:24,y:20,owner:0,type:GAS,buildLeft:0,outage:0,builtAt:0}],"
            + "lines:[{id:6,a:5,b:1,owner:0,cost:4e6}]}";
        GameState r = new Json().fromJson(SaveData.class, v1).restore();
        assertEquals(1, r.plants.size());
        assertEquals(r.catalog.defaultFor(PlantType.GAS), r.plants.get(0).equipment);
        assertFalse(r.map.isWater(24, 20));
        for (int i = 0; i < 24 * 40; i++) r.tick();
        assertTrue(r.plants.get(0).lifetimeMWh > 0);
    }
}
