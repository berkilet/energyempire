package com.energytycoon;

import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.GameState;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HarnessTest {
    @Test
    void sameSeedSameGame() {
        List<SimHarness.Month> a = SimHarness.runMonths(SimHarness.newGame(SimHarness.reference(7)), 4);
        List<SimHarness.Month> b = SimHarness.runMonths(SimHarness.newGame(SimHarness.reference(7)), 4);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).csv(), b.get(i).csv(), "ay " + (i + 1));
    }

    @Test
    void runsTwoYearsHeadless() {
        GameState s = SimHarness.newGame(SimHarness.reference(1));
        List<SimHarness.Month> m = SimHarness.runMonths(s, 24);
        assertEquals(24, m.size());
        for (SimHarness.Month r : m) {
            assertTrue(Double.isFinite(r.avgPrice) && Double.isFinite(r.servedRatio));
            assertTrue(r.servedRatio >= 0 && r.servedRatio <= 1.0001);
        }
    }

    @Test
    void baselineRecorded() throws Exception {
        Path p = Path.of("src/test/resources/baseline/faz0_seed1_free_normal_64_5sehir_2rakip.csv");
        assertTrue(Files.exists(p), "Faz 0 referans dosyası yok");
        assertEquals(25, Files.readAllLines(p).size(), "başlık + 24 ay");
    }
}
