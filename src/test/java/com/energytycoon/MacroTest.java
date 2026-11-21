package com.energytycoon;

import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Makro ortam: politika faizi sınırları, rejimler, olay sıklıkları, toplantı adımları, beklenti isabeti. */
class MacroTest {
    @Test
    void fiftyYearsOfPolicy() {
        int seeds = 3; double years = 50;
        int[] events = new int[Macro.EventType.values().length];
        int[] regimes = new int[3];
        double min = 1, max = 0; int hits = 0, forecasts = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            GameSettings g = SimHarness.reference(seed);
            g.rivals = 0; g.size = 48; g.cities = 3;
            GameState s = GameState.newGame(g);
            java.util.ArrayDeque<double[]> pending = new java.util.ArrayDeque<>(); // {hedef ay, beklenen faiz}
            int months = 0;
            for (int h = 0; h < years * Balance.HOURS_PER_YEAR; h++) {
                s.tick(); s.messages.clear();
                if (s.hour % Balance.HOURS_PER_MONTH == 0) {
                    months++;
                    Macro m = s.macro;
                    min = Math.min(min, m.policyRate); max = Math.max(max, m.policyRate);
                    // 12 ay sonrasi beklentisi: +-0,5 puan icinde mi
                    while (!pending.isEmpty() && pending.peek()[0] <= months) {
                        double[] p = pending.poll(); forecasts++;
                        if (Math.abs(p[1] - m.policyRate) <= 0.005 + 1e-9) hits++;
                    }
                    pending.add(new double[] { months + 12, m.expectedPath[11] });
                }
            }
            for (int i = 0; i < events.length; i++) events[i] += s.macro.eventCounts[i];
            for (int i = 0; i < 3; i++) regimes[i] += s.macro.regimeMonths[i];
        }
        System.out.printf("politika faizi: %%%.2f – %%%.2f%n", min * 100, max * 100);
        System.out.printf("rejim ay payı: gevşeme %%%.0f, nötr %%%.0f, sıkılaşma %%%.0f%n",
            100.0 * regimes[0] / (regimes[0] + regimes[1] + regimes[2]), 100.0 * regimes[1] / (regimes[0] + regimes[1] + regimes[2]), 100.0 * regimes[2] / (regimes[0] + regimes[1] + regimes[2]));
        for (Macro.EventType t : Macro.EventType.values())
            System.out.printf("olay %-24s: %d kez → ortalama %.1f yılda bir%n", t.label, events[t.ordinal()], events[t.ordinal()] == 0 ? Double.NaN : seeds * years / events[t.ordinal()]);
        System.out.printf("12 ay sonrası beklentisi ±0,5 puan isabeti: %%%.0f%n", 100.0 * hits / forecasts);
        assertTrue(min >= FinanceBalance.POLICY_MIN - 1e-12 && max <= FinanceBalance.POLICY_MAX + 1e-12);
        for (int r : regimes) assertTrue(r > 0, "üç rejim de görülmeli");
    }

    @Test
    void meetingSteps() {
        assertEquals(0.0525, Macro.meetingStep(0.045, 0.075, false), 1e-12, "3 puan uzaklık: 0,75 adım");
        assertEquals(0.050, Macro.meetingStep(0.045, 0.060, false), 1e-12, "1,5 puan: 0,50 adım");
        assertEquals(0.0475, Macro.meetingStep(0.045, 0.050, false), 1e-12, "0,5 puan: 0,25 adım");
        assertEquals(0.050, Macro.meetingStep(0.045, 0.050, true), 1e-12, "enflasyon şoku: en az 0,50 (hedefi aşmadan)");
        assertEquals(0.025, Macro.meetingStep(0.025, 0.025, false), 1e-12);
    }
}
