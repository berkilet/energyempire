package com.energytycoon.harness;

import com.energytycoon.sim.*;

import java.util.*;

/**
 * Denge olcumu (render yok).
 *   --mode decade   : 64 ve 96 harita, oyuncu botu acik, 10 yil: rakip TM/yukseltme, UŞİ/kendi hatti, kuyruk, ihaleler
 *   --mode shares   : pazar payi hedefli senaryolar 2040'a kadar: 2030/2035/2040 paylari
 */
public final class BalanceCheck {
    public static void main(String[] args) {
        String mode = "decade"; int seeds = 3; int years = 10;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--mode")) mode = args[i + 1];
            if (args[i].equals("--seeds")) seeds = Integer.parseInt(args[i + 1]);
            if (args[i].equals("--years")) years = Integer.parseInt(args[i + 1]);
        }
        if (mode.equals("shares")) shares(seeds); else decade(seeds, years);
    }

    static GameState game(long seed, int size, int cities, String scenario) {
        GameSettings g = SimHarness.reference(seed);
        g.size = size; g.cities = cities; g.scenarioId = scenario;
        return GameState.newGame(g);
    }

    static void decade(int seeds, int years) {
        for (int[] cfg : new int[][] { { 64, 5 }, { 96, 12 } }) {
            int tms = 0, ups = 0, par = 0, ownC = 0, opC = 0, rivOwn = 0, rivOp = 0, plOwn = 0, plOp = 0;
            List<double[]> queue = new ArrayList<>();
            List<String> tenderLines = new ArrayList<>();
            int upgradesNoSecond = 0; long maxNoSecondHours = 0;
            for (long seed = 1; seed <= seeds; seed++) {
                GameState s = game(seed, cfg[0], cfg[1], "free");
                s.aiPlayer = true;
                for (int h = 0; h < years * Balance.HOURS_PER_YEAR && !s.gameOver; h++) { s.tick(); s.messages.clear(); }
                tms += s.statRivalTms; ups += s.statRivalUpgrades; par += s.statRivalParallel;
                for (long[] u : s.statUpgradeLog) if (u[2] == 0) { upgradesNoSecond++; maxNoSecondHours = Math.max(maxNoSecondHours, u[3]); }
                for (Map.Entry<Company, int[]> e : s.statConnections.entrySet()) {
                    if (e.getKey().neutral) continue;
                    int[] v = e.getValue();
                    ownC += v[0]; opC += v[1];
                    if (e.getKey().player) { plOwn += v[0]; plOp += v[1]; } else { rivOwn += v[0]; rivOp += v[1]; }
                }
                queue.addAll(s.operator.statQueue);
                long rivTmNow = s.substations.stream().filter(t -> t.owner != null && !t.owner.player).count();
                long bankrupt = s.companies.stream().filter(x -> x.bankrupt).count();
                Map<String, Integer> sold = new TreeMap<>();
                for (Listing l : s.sales.all) sold.merge(l.status == Listing.Status.SOLD ? "satıldı→" + (l.buyer == null ? "UŞİ" : l.buyer.player ? "oyuncu" : l.buyer.neutral ? "Kamu" : "rakip") : l.status.label, 1, Integer::sum);
                System.out.printf(Locale.ROOT, "   satış ilanları %s · iflas eden %d%n", sold, bankrupt);
                for (Tender t : s.tenders.all)
                    tenderLines.add(String.format(Locale.ROOT, "  tohum %d · %s %d · %s %.0f MW · %s · teklifler %s · kazanan %s",
                        seed, Balance.START_YEAR + t.openedHour / Balance.HOURS_PER_YEAR, (t.openedHour / Balance.HOURS_PER_MONTH) % 12 + 1,
                        t.city.name, t.mw, t.status.label,
                        t.bids.stream().map(b -> b.company.name + " $" + Math.round(b.price)).toList(),
                        t.winner == null ? "-" : t.winner.name + " $" + Math.round(t.price)));
                System.out.printf(Locale.ROOT, "%dx%d tohum %d: rakip TM %d (haritada %d), yükseltme %d, paralel %d, santral %d%n",
                    cfg[0], cfg[0], seed, s.statRivalTms, rivTmNow, s.statRivalUpgrades, s.statRivalParallel, s.plants.size());
            }
            System.out.printf(Locale.ROOT, "== %dx%d, %d şehir, %d tohum × %d yıl ==%n", cfg[0], cfg[0], cfg[1], seeds, years);
            System.out.printf(Locale.ROOT, "rakip TM: %d · rakip hat yükseltmesi: %d (ikinci yolsuz %d, en uzun kesinti %d gün) · paralel hat: %d%n",
                tms, ups, upgradesNoSecond, maxNoSecondHours / 24, par);
            System.out.printf(Locale.ROOT, "bağlantılar: toplam kendi %d / UŞİ %d (UŞİ %%%.0f) · oyuncu botu %d/%d · rakipler %d/%d%n",
                ownC, opC, 100.0 * opC / Math.max(1, ownC + opC), plOwn, plOp, rivOwn, rivOp);
            if (!queue.isEmpty()) {
                double avg = queue.stream().mapToDouble(q -> q[0]).average().orElse(0), max = queue.stream().mapToDouble(q -> q[0]).max().orElse(0);
                double avgTotal = queue.stream().mapToDouble(q -> q[2]).average().orElse(0);
                System.out.printf(Locale.ROOT, "UŞİ kuyruğu: %d başvuru · kuyrukta bekleme ort. %.1f ay, en uzun %.1f ay · başvurudan inşaata ort. %.1f ay%n",
                    queue.size(), avg, max, avgTotal);
                Map<Integer, double[]> bins = new TreeMap<>();
                for (double[] q : queue) { int b = (int) Math.min(6, q[1]); double[] v = bins.computeIfAbsent(b, k -> new double[2]); v[0] += q[0]; v[1]++; }
                StringBuilder sb = new StringBuilder("  kuyruk uzunluğuna göre bekleme: ");
                for (Map.Entry<Integer, double[]> e : bins.entrySet())
                    sb.append(String.format(Locale.ROOT, "[önde %d%s: %.1f ay, n=%d] ", e.getKey(), e.getKey() == 6 ? "+" : "", e.getValue()[0] / e.getValue()[1], (int) e.getValue()[1]));
                System.out.println(sb);
            } else System.out.println("UŞİ kuyruğu: inşaata geçen başvuru yok");
            // pasif oyuncu: oyuncu hic yatirim yapmaz (kararan sehirler -> acil ihale)
            for (long seed = 1; seed <= seeds; seed++) {
                GameState s = game(seed, cfg[0], cfg[1], "free");
                for (int h = 0; h < years * Balance.HOURS_PER_YEAR && !s.gameOver; h++) { s.tick(); s.messages.clear(); }
                for (Tender t : s.tenders.all)
                    tenderLines.add(String.format(Locale.ROOT, "  pasif oyuncu, tohum %d · %d/%02d · %s %.0f MW · %s · teklifler %s · kazanan %s",
                        seed, Balance.START_YEAR + t.openedHour / Balance.HOURS_PER_YEAR, (t.openedHour / Balance.HOURS_PER_MONTH) % 12 + 1,
                        t.city.name, t.mw, t.status.label,
                        t.bids.stream().map(b -> b.company.name + " $" + Math.round(b.price)).toList(),
                        t.winner == null ? "-" : t.winner.name + " $" + Math.round(t.price)));
                double minSat = s.cities.stream().mapToDouble(c -> c.satisfaction).min().orElse(1);
                tenderLines.add(String.format(Locale.ROOT, "  pasif oyuncu, tohum %d: son yılda en düşük şehir memnuniyeti %%%.0f", seed, minSat * 100));
            }
            System.out.println("ihaleler (" + tenderLines.size() + " satır):");
            tenderLines.forEach(System.out::println);
        }
    }

    static void shares(int seeds) {
        for (String sc : new String[] { "green", "coal" }) {
            Map<String, double[]> acc = new LinkedHashMap<>();
            for (long seed = 1; seed <= seeds; seed++) {
                GameState s = game(seed, 64, 5, sc);
                s.aiPlayer = true;
                long[] wonYear = { 0 };
                for (int year : new int[] { 2030, 2035, 2040 }) {
                    while (s.year() < year + 1 && !s.gameOver) {
                        s.tick(); s.messages.clear();
                        if (s.won && wonYear[0] == 0) wonYear[0] = s.year();
                    }
                    // yil sonu: son 12 ayin uretimiyle pay
                    double tot = 0;
                    Map<String, Double> gen = new LinkedHashMap<>();
                    for (Company c : s.companies) {
                        double g = c.lastMwhGenerated;
                        tot += g;
                        String key = c.player ? "Oyuncu botu" : c.neutral ? "Kamu Üretim" : "Rakipler";
                        gen.merge(key, g, Double::sum);
                    }
                    for (Map.Entry<String, Double> e : gen.entrySet())
                        acc.computeIfAbsent(e.getKey() + " " + year, k -> new double[2])[0] += tot > 0 ? e.getValue() / tot : 0;
                    acc.computeIfAbsent("_n " + year, k -> new double[2])[0] += 1;
                    if (s.gameOver) break;
                }
                StringBuilder ob = new StringBuilder();
                for (Objective o : s.scenario.objectives) ob.append(o.label).append(' ').append(o.status(s)).append("; ");
                System.out.println("  tohum " + seed + ": oyuncu botu hedeflere " + (wonYear[0] > 0 ? wonYear[0] + "'de ulaştı" : "ulaşamadı")
                    + (s.gameOver ? " (oyun bitti: " + s.gameOverReason + ")" : "") + " · son durum: " + ob);
            }
            System.out.println("== senaryo " + sc + " (" + Scenarios.get(sc).title + ", son tarih " + Scenarios.get(sc).deadlineYear + "), " + seeds + " tohum ortalaması, Aralık ayı payı ==");
            for (String who : new String[] { "Rakipler", "Kamu Üretim", "Oyuncu botu" }) {
                StringBuilder sb = new StringBuilder(String.format("  %-12s", who));
                for (int year : new int[] { 2030, 2035, 2040 }) {
                    double[] v = acc.get(who + " " + year); double n = acc.getOrDefault("_n " + year, new double[] { 1 })[0];
                    sb.append(String.format(Locale.ROOT, "  %d: %%%.0f", year, v == null ? 0 : 100 * v[0] / n));
                }
                System.out.println(sb);
            }
        }
    }
}
