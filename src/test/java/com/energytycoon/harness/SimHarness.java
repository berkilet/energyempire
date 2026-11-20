package com.energytycoon.harness;

import com.energytycoon.sim.Balance;
import com.energytycoon.sim.Company;
import com.energytycoon.sim.Difficulty;
import com.energytycoon.sim.GameSettings;
import com.energytycoon.sim.GameState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Render olmadan oyun kurup saat saat ilerleten yardimci (testler ve SimCheck icin). */
public final class SimHarness {
    private SimHarness() {}

    /** Ay sonu olcumu. */
    public static final class Month {
        public int index;
        public long year;
        public int month;
        public double servedRatio, avgPrice, playerCash, playerMwh, totalMwh;
        public int plants;

        public String csv() {
            return String.format(Locale.ROOT, "%d,%d,%d,%.6f,%.4f,%.0f,%.0f,%.0f,%d",
                index, year, month + 1, servedRatio, avgPrice, playerCash, playerMwh, totalMwh, plants);
        }
    }

    public static final String CSV_HEADER = "ay,yil,takvim_ay,karsilanan_talep,ort_fiyat,oyuncu_nakit,oyuncu_mwh,toplam_mwh,santral";

    /** Faz 0 referans ayarlari: seed=1, Serbest Oyun, NORMAL, 64 harita, 5 sehir, 2 rakip. */
    public static GameSettings reference(long seed) {
        GameSettings g = new GameSettings();
        g.seed = seed; g.scenarioId = "free"; g.difficulty = Difficulty.NORMAL;
        g.size = 64; g.cities = 5; g.rivals = 2; g.companyName = "Test A.Ş.";
        return g;
    }

    public static GameState newGame(GameSettings g) { return GameState.newGame(g); }

    /** n saat ilerletir. */
    public static void run(GameState s, long hours) {
        for (long i = 0; i < hours && !s.gameOver; i++) s.tick();
    }

    /** Ay sinirina kadar ilerletip 'months' ay boyunca her ay sonu olcum alir. Fiyat: ay icindeki saatlik ortalama. */
    public static List<Month> runMonths(GameState s, int months) {
        List<Month> out = new ArrayList<>();
        for (int m = 0; m < months && !s.gameOver; m++) {
            double priceSum = 0; int n = 0;
            do {
                s.tick();
                priceSum += s.statPrice; n++;
            } while (s.hour % Balance.HOURS_PER_MONTH != 0 && !s.gameOver);
            Month r = new Month();
            r.index = m + 1;
            long prev = s.hour - 1;
            r.year = Balance.START_YEAR + prev / Balance.HOURS_PER_YEAR;
            r.month = (int) ((prev / Balance.HOURS_PER_MONTH) % 12);
            r.servedRatio = s.lastServedRatio;
            r.avgPrice = n > 0 ? priceSum / n : 0;
            r.playerCash = s.player.cash;
            r.playerMwh = s.player.lastMwhGenerated;
            double tot = 0;
            for (Company c : s.companies) tot += c.lastMwhGenerated;
            r.totalMwh = tot;
            r.plants = s.plants.size();
            out.add(r);
        }
        return out;
    }
}
