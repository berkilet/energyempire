package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** İlk sürümün (v2) koduyla gerçekten yazılmış kayıt dosyası. */
class LegacySaveTest {
    static final Path V2 = Path.of("src/test/resources/saves/v2_seed1.json");

    static SaveData read(Path p) throws Exception {
        return new Json().fromJson(SaveData.class, Files.readString(p));
    }

    @Test
    void realV2FileIsDetectedAsV2() throws Exception {
        SaveData d = read(V2);
        assertEquals(0, d.version, "v2 kodu sürüm alanını yazmıyordu");
        assertEquals(2, d.effectiveVersion());
    }

    @Test
    void realV2FileLoadsAndRunsOneMonth() throws Exception {
        GameState s = read(V2).restore();
        assertTrue(s.settings.rivalsStartWithFleet, "v2 oyunları rakip filosuyla başlamıştı");
        assertNull(s.neutral, "v2'de kamu filosu yok");
        assertEquals(5, s.plants.size());
        long h0 = s.hour;
        for (int i = 0; i < Balance.HOURS_PER_MONTH; i++) s.tick();
        assertEquals(h0 + Balance.HOURS_PER_MONTH, s.hour);
        assertFalse(s.gameOver);
        assertTrue(Double.isFinite(s.player.cash));
        assertTrue(s.plants.stream().anyMatch(p -> p.lifetimeMWh > 0));
    }

    @Test
    void newSavesWriteVersionExplicitly() {
        GameState s = GameState.newGame(1, "T");
        String json = new Json().toJson(SaveData.from(s));
        assertTrue(json.contains("version:" + SaveData.CURRENT_VERSION) || json.contains("\"version\":" + SaveData.CURRENT_VERSION), json.substring(0, 80));
        assertEquals(SaveData.CURRENT_VERSION, new Json().fromJson(SaveData.class, json).effectiveVersion());
    }
}
