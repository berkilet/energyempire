package com.energytycoon.harness;

import com.energytycoon.sim.*;

import java.util.*;
import java.util.Arrays;

/**
 * Denge olcumu (render yok).
 *   --mode decade   : 64 ve 96 harita, oyuncu botu acik, 10 yil: rakip TM/yukseltme, UŞİ/kendi hatti, kuyruk, ihaleler
 *   --mode shares   : pazar payi hedefli senaryolar 2040'a kadar: 2030/2035/2040 paylari
 */
public final class BalanceCheck {
    public static void main(String[] args) {
        String mode = "decade"; int seeds = 3; int years = 10;
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals("--size")) onlySize = Integer.parseInt(args[i + 1]);
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--mode")) mode = args[i + 1];
            if (args[i].equals("--seeds")) seeds = Integer.parseInt(args[i + 1]);
            if (args[i].equals("--years")) years = Integer.parseInt(args[i + 1]);
        }
        if (mode.equals("mna")) mna(seeds, false); else if (mode.equals("leverage")) mna(seeds, true); else if (mode.equals("valuation")) valuation(seeds); else if (mode.equals("shares")) shares(seeds); else if (mode.equals("finance")) finance(seeds); else decade(seeds, years);
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

    /** Finans dengesi: NORMAL, 2040'a kadar; faiz yolu, yillik notlar, Net borc/FAVOK, gecikme/temerrut/iflas, kredi turu oranlari. */
    static void finance(int seeds) {
        for (int[] cfg : new int[][] { { 64, 5 }, { 96, 12 } }) {
            int late = 0, def = 0, restr = 0, bankrupt = 0, firms = 0, bankruptFirst10 = 0;
            double ecaVol = 0, comVol = 0, revPeak = 0; int ecaN = 0, comN = 0;
            Map<String, Integer> defByType = new TreeMap<>();
            for (long seed = 1; seed <= seeds; seed++) {
                GameState s = game(seed, cfg[0], cfg[1], "free");
                s.aiPlayer = true;
                StringBuilder rates = new StringBuilder(), ratings = new StringBuilder();
                java.util.Map<Company, StringBuilder> rt = new java.util.LinkedHashMap<>();
                for (Company c : s.companies) if (!c.neutral) rt.put(c, new StringBuilder());
                java.util.Map<Loan, Boolean> seen = new java.util.IdentityHashMap<>();
                while (s.year() < 2041 && !s.gameOver) {
                    s.tick(); s.messages.clear();
                    if (s.hour % Balance.HOURS_PER_MONTH == 0)
                        for (Company c : s.companies) for (Loan l : c.loans) if (seen.put(l, Boolean.TRUE) == null) {
                            if (l.type == Loan.Type.ECA) { ecaN++; ecaVol += l.principal; }
                            else if (l.type == Loan.Type.COMMERCIAL) { comN++; comVol += l.principal; }
                        }
                    if (s.hour % Balance.HOURS_PER_YEAR == 0) {
                        rates.append(String.format(Locale.ROOT, " %d:%.2f", s.year() - 1, s.macro.policyRate * 100));
                        for (Map.Entry<Company, StringBuilder> e : rt.entrySet()) {
                            Company c = e.getKey();
                            double lev = s.finance.leverage(c);
                            e.getValue().append(String.format(Locale.ROOT, " %s(%s)", c.bankrupt ? "İFLAS" : s.finance.rating(c).name(),
                                Double.isInfinite(lev) ? "∞" : String.format(Locale.ROOT, "%.1f", lev)));
                        }
                        for (Company c : s.companies) for (Loan l : c.loans) if (l.type == Loan.Type.REVOLVER) revPeak = Math.max(revPeak, l.outstanding);
                    }
                    if (s.year() == Balance.START_YEAR + 10 && s.hour % Balance.HOURS_PER_YEAR == 0)
                        for (Company c : s.companies) if (!c.player && !c.neutral && c.bankrupt) bankruptFirst10++;
                }
                System.out.println("-- " + cfg[0] + "x" + cfg[0] + " tohum " + seed + (s.gameOver ? " (oyun bitti: " + s.gameOverReason + ")" : ""));
                System.out.println("   politika faizi (yıl sonu %):" + rates);
                for (Map.Entry<Company, StringBuilder> e : rt.entrySet()) {
                    Company c = e.getKey();
                    System.out.println("   " + String.format("%-15s", c.player ? "Oyuncu botu" : c.name) + " not(Net borç/FAVÖK):" + e.getValue());
                    firms++; late += c.lateEvents; def += c.defaults; restr += c.restructures; if (c.bankrupt) bankrupt++;
                    for (Loan l : c.loans) {
                        if (l.status == Loan.Status.DEFAULTED) defByType.merge(l.type.label + (l.type == Loan.Type.ECA && l.dscrLowQuarters >= FinanceBalance.ECA_DSCR_DEFAULT_QUARTERS ? " (DSCR)" : l.daysLate >= FinanceBalance.DEFAULT_DAYS ? " (90 gün)" : " (iflas/çapraz)"), 1, Integer::sum);
                    }
                }
            }
            System.out.printf(Locale.ROOT, "== %dx%d, %d tohum, 2025–2040: %d şirket · gecikme %d · temerrüt %d · yapılandırma %d · iflas %d (ilk 10 yılda iflas eden rakip %d)%n",
                cfg[0], cfg[0], seeds, firms, late, def, restr, bankrupt, bankruptFirst10);
            System.out.printf(Locale.ROOT, "   krediler: ECA %d adet %.0fM$ (%%%.0f hacim) · ticari %d adet %.0fM$ · işletme kredisi en yüksek kullanım %.0fM$%n",
                ecaN, ecaVol / 1e6, 100 * ecaVol / Math.max(1, ecaVol + comVol), comN, comVol / 1e6, revPeak / 1e6);
            System.out.println("   temerrüde düşen krediler: " + defByType);
        }
    }

    /** Degerleme: oyuncu botu degeri (yeni F/K) ve eski formul (ozsermaye + 4 x yillik kar), senaryo hedefleri icin. */
    static void valuation(int seeds) {
        for (String sc : new String[] { "free", "crisis", "islands" }) {
            for (long seed = 1; seed <= seeds; seed++) {
                GameState s = game(seed, 64, 5, sc);
                s.aiPlayer = true;
                StringBuilder b = new StringBuilder();
                while (s.year() < 2036 && !s.gameOver) {
                    s.tick(); s.messages.clear();
                    if (s.hour % Balance.HOURS_PER_YEAR == 0) {
                        Company p = s.player;
                        double eq = p.cash + s.assetValue(p) - p.debt(), annual = s.capital.normalizedProfit(p);
                        double old = Math.max(0, eq + 4 * Math.max(0, annual));
                        b.append(String.format(Locale.ROOT, " %d:%.2f/%.2f(x%.1f)", s.year() - 1, p.valuation / 1e9, old / 1e9, s.capital.multiple(p)));
                    }
                }
                System.out.println(sc + " tohum " + seed + " değer milyar $ yeni/eski(F/K):" + b);
            }
        }
    }

    /**
     * Devralma kullanilabilirligi: oyuncu botu (MnaBot) + rakip YZ, NORMAL, 2040'a kadar.
     * leverage: bot degeri kendisine yakin (oran 1,0-1,5) rakibi agirlikla krediyle devralmayi dener; sonraki 3 yil izlenir.
     */
    static int onlySize;

    static void mna(int seeds, boolean leverage) {
        if (System.getenv("NO_CAP") != null) Competition.priceCapEnabled = false;
        for (int[] cfg : new int[][] { { 64, 5 }, { 96, 12 } }) {
            if (onlySize > 0 && cfg[0] != onlySize) continue;
            int a = 0, b = 0, c = 0, attempts = 0, appr = 0, cond = 0, rej = 0, tk = 0, failed = 0, ipos = 0;
            Map<String, Integer> reasons = new TreeMap<>();
            for (long seed = 1; seed <= seeds; seed++) {
                if (System.getenv("ONLY_SEED") != null && seed != Long.parseLong(System.getenv("ONLY_SEED"))) continue;
                GameState s = game(seed, cfg[0], cfg[1], "free");
                s.aiPlayer = true;
                int pi = s.companies.indexOf(s.player);
                boolean gotA = false; int playerTk = 0, playerCond = 0, condMonths = 0;
                double maxComb = 0, minRatio = 1e9;
                List<String> log = new ArrayList<>();
                int[] parent = new int[s.companies.size()]; Arrays.fill(parent, -1);
                boolean[] listed = new boolean[s.companies.size()];
                int trackFrom = -1; StringBuilder track = new StringBuilder();
                while (s.year() < 2041 && !s.gameOver) {
                    s.tick(); s.messages.clear();
                    if (s.hour % Balance.HOURS_PER_MONTH != 0) continue;
                    MnaBot.monthly(s, leverage);
                    for (Company x : s.companies) {
                        if (x == s.player || x.neutral || x.bankrupt || s.competition.sameGroup(x, s.player)) continue;
                        Competition.Preview pv = s.competition.preview(s.player, x);
                        if (pv.outcome == Competition.CONDITIONAL) condMonths++;
                        if (pv.valueRatio < FinanceBalance.SCALE_EXEMPTION_RATIO) for (Competition.Row r : pv.rows) maxComb = Math.max(maxComb, r.combined);
                        minRatio = Math.min(minRatio, pv.valueRatio);
                    }
                    for (Company x : s.companies) if (x != s.player && s.mna.stake(s.player, x) > 0.20) gotA = true;
                    for (int i = 0; i < s.companies.size(); i++) {
                        Company x = s.companies.get(i);
                        if (x.eq.listed && !listed[i]) { listed[i] = true; log.add(String.format(Locale.ROOT, "%d halka arz: %s (kurucu %%%.0f)", s.year(), x.player ? "Oyuncu" : x.name, x.eq.founderPct() * 100)); }
                        if (x.eq.parent != parent[i] && x.eq.parent >= 0 && !x.eq.merged) {
                            Company acq = s.companies.get(x.eq.parent);
                            log.add(String.format(Locale.ROOT, "%d devralma: %s → %s", s.year(), acq.player ? "Oyuncu" : acq.name, x.player ? "Oyuncu" : x.name));
                            if (acq.player) { playerTk++; if (trackFrom < 0) trackFrom = (int) s.year(); }
                        }
                        if (x.eq.merged && parent[i] != -2) { log.add(s.year() + " birleşme: " + x.name); parent[i] = -2; continue; }
                        if (parent[i] != -2) parent[i] = x.eq.parent;
                    }
                    if (System.getenv("MNA_TRACE") != null && s.month() == 0) {
                        Company q = s.player;
                        log.add(String.format(Locale.ROOT, "%d iz: hisse %.2fM temettü %%%d geri alım %.0fM hakim %s tavanlı santral %d nakit %.0fM borç %.0fM ND/FAVÖK %.1f not %s gecikme %d payı olan şirket %d değer %.0fM", s.year() - 1, q.eq.shares / 1e6, q.eq.dividendPct, q.eq.buybackBudget / 1e6, q.eq.dominant, (int) s.plants.stream().filter(x -> x.owner == q && x.priceCap > 0).count(), q.cash / 1e6, q.debt() / 1e6,
                            s.finance.leverage(q), s.finance.rating(q), q.lateEvents, (int) s.companies.stream().filter(x -> x != q && s.mna.stake(q, x) > 0).count(), s.capital.value(q) / 1e6));
                    }
                    if (leverage && trackFrom > 0 && s.month() == 0 && s.year() <= trackFrom + 3) {
                        double lev = s.finance.leverage(s.player);
                        track.append(String.format(Locale.ROOT, " %d: ND/FAVÖK %s not %s gecikme %d", s.year() - 1, Double.isInfinite(lev) ? "∞" : String.format(Locale.ROOT, "%.1f", lev), s.finance.rating(s.player), s.player.lateEvents));
                    }
                }
                for (DealBook.Review r : s.deals.reviews) if (r.acquirer == pi && r.decided && r.outcome == Competition.CONDITIONAL && r.commitmentAccepted) playerCond++;
                if (gotA) a++; if (playerTk > 0) b++; if (playerCond > 0) c++;
                attempts += s.deals.attempts; appr += s.deals.approvals; cond += s.deals.conditionals; rej += s.deals.rejections; tk += s.deals.takeovers; failed += s.deals.failedOffers;
                for (String r : s.deals.rejectReasons) reasons.merge(r.replaceAll("[0-9]+", "#"), 1, Integer::sum);
                for (boolean l : listed) if (l) ipos++;
                System.out.println("-- " + cfg[0] + "x" + cfg[0] + " tohum " + seed + (s.gameOver ? " (" + s.gameOverReason + ")" : "") + ": bot %20+ pay " + gotA + ", bot devralma " + playerTk + ", koşullu " + playerCond
                    + " · oyuncu kurucu payı %" + Math.round(s.player.eq.founderPct() * 100)
                    + String.format(Locale.ROOT, " · koşullu onay fırsatı (ay) %d · oran<1,5 iken en yüksek bölgesel birleşik pay %%%.0f · en düşük değer oranı %.2f", condMonths, maxComb * 100, minRatio));
                for (String l : log) System.out.println("   " + l);
                if (leverage) System.out.println("   devralma sonrası:" + (track.length() == 0 ? " devralma yok" : track));
            }
            System.out.printf(Locale.ROOT, "== %dx%d, %d tohum: bot (a) %%20+ pay %d, (b) devralma %d, (c) koşullu onayla devralma %d · halka arz %d · otorite: başvuru %d, onay %d, koşullu %d, ret %d · kontrol devri %d · sonuçsuz teklif %d%n",
                cfg[0], cfg[0], seeds, a, b, c, ipos, attempts, appr, cond, rej, tk, failed);
            System.out.println("   ret sebepleri: " + reasons);
        }
    }
}
