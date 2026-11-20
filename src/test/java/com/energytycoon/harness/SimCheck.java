package com.energytycoon.harness;

import com.energytycoon.sim.Company;
import com.energytycoon.sim.GameSettings;
import com.energytycoon.sim.GameState;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Headless kontrol: oyunu render olmadan N ay koşturur, aylık ölçümleri yazdırır ve isteğe bağlı CSV'ye kaydeder.
 * Kullanım: ./gradlew simCheck --args="--months 24 --seed 1 --size 64 --out dosya.csv"
 */
public final class SimCheck {
    public static void main(String[] args) throws Exception {
        int months = 24; long seed = 1; int size = 64, cities = 5; String out = null;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--months" -> months = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--size" -> size = Integer.parseInt(args[++i]);
                case "--cities" -> cities = Integer.parseInt(args[++i]);
                case "--out" -> out = args[++i];
                default -> { }
            }
        }
        GameSettings g = SimHarness.reference(seed);
        g.size = size; g.cities = cities;
        long t0 = System.nanoTime();
        GameState s = SimHarness.newGame(g);
        long tStart = System.nanoTime();
        List<SimHarness.Month> rows = SimHarness.runMonths(s, months);
        long tEnd = System.nanoTime();
        long ticks = s.hour - 8;
        System.out.printf(Locale.ROOT, "SimCheck seed=%d boyut=%d şehir=%d ay=%d | kurulum %.0f ms | tick ort. %.3f ms%n",
            seed, size, cities, rows.size(), (tStart - t0) / 1e6, (tEnd - tStart) / 1e6 / Math.max(1, ticks));
        System.out.println(SimHarness.CSV_HEADER);
        for (SimHarness.Month r : rows) System.out.println(r.csv());
        for (Company c : s.companies)
            System.out.printf(Locale.ROOT, "  %-22s nakit=%8.0fM borç=%6.0fM değer=%8.0fM santral=%d%n",
                c.name, c.cash / 1e6, c.debt / 1e6, c.valuation / 1e6, s.totalPlantsOf(c));
        if (s.gameOver) System.out.println("OYUN BİTTİ: " + s.gameOverReason);
        if (out != null) {
            Path p = Path.of(out);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8))) {
                w.println(SimHarness.CSV_HEADER);
                for (SimHarness.Month r : rows) w.println(r.csv());
            }
            System.out.println("Yazıldı: " + p.toAbsolutePath());
        }
    }
}
