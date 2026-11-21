package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ulusal Sebeke Isletmesi (UŞİ): sirketlerin basvurusuyla ucuz ama yavas ve belirsiz hat kurar
 * (planlama -> insaat kuyrugu -> insaat -> aktif), her ocak kendi planini yapar. Sirket degildir; hatlari owner == null.
 */
public final class GridOperator {
    public final String name = GridBalance.OPERATOR_NAME, shortName = GridBalance.OPERATOR_SHORT;
    private final GameState s;
    private static final int HPM = Balance.HOURS_PER_MONTH;

    GridOperator(GameState s) { this.s = s; }

    /** Olcum: insaata gecen her basvuru icin {kuyrukta bekleme (ay), kuyruga girerken siradaki basvuru sayisi, basvurudan insaata (ay)}. */
    public final transient List<double[]> statQueue = new ArrayList<>();

    public int slots() { return GridBalance.OP_BASE_SLOTS + s.cities.size() / GridBalance.OP_CITIES_PER_SLOT; }

    public static double contribution(double lineCost) { return GridBalance.OP_CONTRIBUTION * lineCost; }
    public static double applicationCost(double lineCost) { return GridBalance.OP_APPLICATION_FEE + contribution(lineCost); }
    public static double annualCharge(double lineCost) { return GridBalance.OP_ANNUAL_CHARGE * lineCost; }
    public static int buildHours(LineTier t, double len) { return (int) Math.round(t.buildDays(len) * GridBalance.OP_BUILD_MULT) * 24; }

    /** Basvuru: ucret + katki pesin; hat PLANNED olarak olusur. Null: yapilamadi. */
    public PowerLine apply(Company c, GridNode a, GridNode b, LineTier tier) {
        if (s.checkLine(c, a, b, tier) != null) return null;
        double cost = s.lineCost(a, b, tier), pay = applicationCost(cost);
        if (c.cash < pay) return null;
        c.cash -= pay; c.capexSpent += pay;
        PowerLine l = s.createLine(null, a, b, cost, tier);
        l.status = LineStatus.PLANNED; l.requester = c; l.appliedAtHour = s.hour;
        l.contribution = contribution(cost); l.annualCharge = annualCharge(cost);
        l.buildHoursLeft = GridBalance.OP_PLANNING_MONTHS * HPM;
        l.builtAtHour = s.hour;
        s.statConnections.computeIfAbsent(c, k -> new int[2])[1]++;
        return l;
    }

    /** Insaat kuyrugu: hizlandirilanlar once, sonra basvuru sirasi. */
    public List<PowerLine> queue() {
        List<PowerLine> q = new ArrayList<>();
        for (PowerLine l : s.lines) if (l.status == LineStatus.QUEUED) q.add(l);
        q.sort(Comparator.<PowerLine>comparingInt(l -> l.accelerated ? 0 : 1).thenComparingLong(l -> l.appliedAtHour).thenComparingInt(l -> l.id));
        return q;
    }

    private int building() {
        int n = 0;
        for (PowerLine l : s.lines) if (l.owner == null && !l.operatorPlan && l.status == LineStatus.BUILDING) n++;
        return n;
    }

    /** Saatlik: planlama sayaclari, kuyruk bekletmeleri, bos slota insaat baslatma. */
    void hourly() {
        boolean any = false;
        for (PowerLine l : s.lines) {
            if (l.owner != null) continue;
            if (l.status == LineStatus.PLANNED) {
                any = true;
                if (--l.buildHoursLeft <= 0) {
                    if (l.operatorPlan) s.activateLine(l);
                    else { l.status = LineStatus.QUEUED; l.buildHoursLeft = 0; l.queuedAtHour = s.hour; l.queueLenAtEnqueue = queue().size(); }
                }
            } else if (l.status == LineStatus.QUEUED) { any = true; if (l.holdHours > 0) l.holdHours--; }
        }
        if (!any) return;
        int free = slots() - building();
        if (free <= 0) return;
        for (PowerLine l : queue()) {
            if (free <= 0) break;
            if (l.holdHours > 0) continue;
            l.status = LineStatus.BUILDING;
            l.buildHoursLeft = buildHours(l.tier, l.lengthTiles);
            free--;
            statQueue.add(new double[] { (s.hour - l.queuedAtHour) / (double) HPM, l.queueLenAtEnqueue, (s.hour - l.appliedAtHour) / (double) HPM });
            if (l.requester != null && l.requester.player)
                s.notify(shortName + ": " + label(l) + " hattının inşaatı başladı (" + l.buildHoursLeft / 24 + " gün)");
        }
    }

    /** Aylik: kaymalar, baglanti bedeli tahakkuku; ocakta kendi plani. */
    void monthly() {
        for (PowerLine l : s.lines) {
            if (l.owner != null || l.operatorPlan) continue;
            if ((l.status == LineStatus.PLANNED || l.status == LineStatus.QUEUED) && s.rng.nextDouble() < GridBalance.OP_SLIP_CHANCE) {
                if (l.status == LineStatus.PLANNED) l.buildHoursLeft += HPM; else l.holdHours += HPM;
                if (l.requester != null && l.requester.player) {
                    int[] e = eta(l);
                    s.notify(shortName + ": " + label(l) + " hattı 1 ay gecikti (tahmini " + e[0] + "–" + e[1] + " ay)");
                }
            }
            if (l.requester != null && l.annualCharge > 0 && !l.requester.bankrupt) {
                double m = l.annualCharge / 12;
                l.requester.cash -= m; l.requester.connectionCharge += m;
            }
        }
        if (s.month() == 0 && s.settings.operatorGrowth) plan();
    }

    /** Tahmini devreye giris: {en erken, en gec} ay. */
    public int[] eta(PowerLine l) {
        double m;
        if (l.status == LineStatus.BUILDING || l.status == LineStatus.PLANNED && l.operatorPlan) m = l.buildHoursLeft / (double) HPM;
        else if (l.status == LineStatus.ACTIVE) return new int[] { 0, 0 };
        else {
            double build = buildHours(l.tier, l.lengthTiles) / (double) HPM;
            int ahead = 0;
            if (l.status == LineStatus.QUEUED) { for (PowerLine q : queue()) { if (q == l) break; ahead++; } m = l.holdHours / (double) HPM; }
            else { ahead = queue().size(); m = l.buildHoursLeft / (double) HPM; }
            m += queueWait(ahead, build) + build;
        }
        int min = Math.max(1, (int) Math.ceil(m - 1e-9));
        return new int[] { min, min + GridBalance.OP_ETA_SPREAD_MONTHS };
    }

    private double queueWait(int ahead, double build) {
        int busy = building();
        int waves = (ahead + Math.max(0, busy - slots() + 1)) / slots();
        return waves * build + (busy >= slots() ? build * 0.5 : 0);
    }

    /** Yeni basvuru icin tahmin: {en erken, en gec} ay. */
    public int[] estimate(LineTier t, double len) {
        double build = buildHours(t, len) / (double) HPM;
        double m = GridBalance.OP_PLANNING_MONTHS + queueWait(queue().size(), build) + build;
        int min = (int) Math.ceil(m - 1e-9);
        return new int[] { min, min + GridBalance.OP_ETA_SPREAD_MONTHS };
    }

    public double accelerateCost(PowerLine l) { return GridBalance.OP_ACCELERATE * l.buildCost; }

    /** Hizlandirma (bir kez): planlama en fazla 2 aya iner, kuyruk basina gecer. Null: tamam. */
    public String accelerate(PowerLine l) {
        if (l.requester == null || l.accelerated) return "Bu başvuru zaten hızlandırıldı";
        if (l.status != LineStatus.PLANNED && l.status != LineStatus.QUEUED) return "Yalnızca planlama veya kuyruktaki başvuru hızlandırılabilir";
        double c = accelerateCost(l);
        if (l.requester.cash < c) return "Yetersiz nakit (" + Math.round(c / 1e5) / 10.0 + "M $ gerekli)";
        l.requester.cash -= c; l.requester.capexSpent += c;
        l.accelerated = true;
        if (l.status == LineStatus.PLANNED) l.buildHoursLeft = Math.min(l.buildHoursLeft, GridBalance.OP_ACCELERATE_PLANNING_MONTHS * HPM);
        else l.holdHours = 0;
        return null;
    }

    /** Iptal: planlamada katkinin yarisi iade; kuyrukta iade yok; insaattaki iptal edilemez. Iade tutari (veya -1). */
    public double cancel(PowerLine l) {
        if (l.requester == null || (l.status != LineStatus.PLANNED && l.status != LineStatus.QUEUED)) return -1;
        double refund = l.status == LineStatus.PLANNED ? l.contribution * GridBalance.OP_CANCEL_REFUND : 0;
        l.requester.cash += refund;
        s.removeLine(l, false);
        return refund;
    }

    public double transferValue(PowerLine l) { return l.bookValue(s.hour) * GridBalance.OP_TRANSFER_BOOK; }

    /** Kendi aktif hattini UŞİ'ye devret: defter degerinin %60'i; O&M ve yillik bedel yok. */
    public double transfer(PowerLine l) {
        if (l.owner == null || l.status != LineStatus.ACTIVE) return -1;
        double v = transferValue(l);
        l.owner.cash += v;
        l.owner = null; l.requester = null; l.annualCharge = 0;
        return v;
    }

    /** Ocak plani: (a) ayri sebeke parcalarindaki en yakin sehir ciftleri, (b) gecen yil cok dolu UŞİ hatlarini guclendirme. */
    void plan() {
        int budget = GridBalance.OP_PLAN_MAX_PER_YEAR;
        Map<City, Integer> part = new HashMap<>();
        List<GameState.Component> comps = s.components();
        for (int i = 0; i < comps.size(); i++) for (City c : comps.get(i).cities) part.put(c, i);
        // bekleyen plan/basvuru ile zaten baglanacak parcalari birlestir
        for (PowerLine l : s.lines) if (l.status != LineStatus.ACTIVE && l.a instanceof City ca && l.b instanceof City cb) merge(part, ca, cb);
        while (budget > 0) {
            City ba = null, bb = null; double bd = Double.MAX_VALUE;
            for (City a : s.cities) for (City b : s.cities) {
                if (a.id >= b.id || part.get(a).equals(part.get(b))) continue;
                double d = s.lineLength(a, b);
                if (d <= LineTier.CYG.maxLength && d < bd && s.route(a, b).throughCity == null) { bd = d; ba = a; bb = b; }
            }
            if (ba == null) break;
            announce(ba, bb, LineTier.CYG);
            merge(part, ba, bb);
            budget--;
        }
        List<PowerLine> hot = new ArrayList<>();
        for (PowerLine l : s.lines)
            if (l.owner == null && l.status == LineStatus.ACTIVE && l.saturatedHoursLastYear > GridBalance.OP_REINFORCE_SATURATION * Balance.HOURS_PER_YEAR) hot.add(l);
        hot.sort(Comparator.comparingInt((PowerLine l) -> -l.saturatedHoursLastYear));
        for (PowerLine l : hot) {
            if (budget <= 0) break;
            int dup = 0; boolean pending = false;
            for (PowerLine o : l.a.lines) if (o.other(l.a) == l.b) { dup++; pending |= o.status != LineStatus.ACTIVE; }
            if (dup >= 3 || pending) continue;
            announce(l.a, l.b, l.tier);
            budget--;
        }
    }

    private static void merge(Map<City, Integer> part, City a, City b) {
        Integer from = part.get(b), to = part.get(a);
        for (Map.Entry<City, Integer> e : part.entrySet()) if (e.getValue().equals(from)) e.setValue(to);
    }

    private void announce(GridNode a, GridNode b, LineTier tier) {
        PowerLine l = s.createLine(null, a, b, s.lineCost(a, b, tier), tier);
        int months = GridBalance.OP_PLAN_MIN_MONTHS + s.rng.nextInt(GridBalance.OP_PLAN_MAX_MONTHS - GridBalance.OP_PLAN_MIN_MONTHS + 1);
        l.status = LineStatus.PLANNED; l.operatorPlan = true; l.appliedAtHour = s.hour;
        l.buildHoursLeft = months * HPM;
        long at = s.hour + l.buildHoursLeft;
        long year = Balance.START_YEAR + at / Balance.HOURS_PER_YEAR;
        int quarter = (int) ((at / HPM) % 12) / 3 + 1;
        s.notify(shortName + " planı: " + a.name() + "–" + b.name() + " " + tier.label.substring(tier.label.indexOf(' ') + 1)
            + ", tahmini " + year + " " + quarter + ". çeyrek");
    }

    public static String label(PowerLine l) { return l.a.name() + "–" + l.b.name() + " " + l.tier.shortLabel; }
}
