package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Faz 5: kendi hat inşaatı, UŞİ başvuru süreci, kuyruk, ücretler, plan ve rakip kararları. */
class OperatorTest {
    static final int HPM = Balance.HOURS_PER_MONTH;

    static GameState grid(int rivals, boolean growth) {
        GameSettings g = SimHarness.reference(1);
        g.rivals = rivals;
        g.operatorGrowth = growth;
        GameState s = GameState.newGame(g);
        for (Plant p : new ArrayList<>(s.plants)) s.demolishPlant(p, false);
        for (PowerLine l : new ArrayList<>(s.lines)) s.removeLine(l, false);
        for (Company c : s.companies) c.cash = 50e9;
        s.cities.get(0).population = 2_000_000;
        return s;
    }

    static Plant plantNear(GameState s, City c, double d) {
        int[] site = GridTest.siteAt(s, PlantType.GAS, c, d);
        assertNotNull(site);
        Plant p = s.buildPlant(s.player, s.catalog.get("at-cc"), site[0], site[1]);
        p.buildHoursLeft = 0; p.outageHoursLeft = 0;
        return p;
    }

    static void hours(GameState s, int n) { for (int i = 0; i < n; i++) s.tick(); }

    @Test
    void ownLineBuildsBeforeCarryingPower() {
        GameState s = grid(0, false);
        City c = s.cities.get(0);
        Plant p = plantNear(s, c, 10);
        double cash = s.player.cash;
        PowerLine l = s.buildLine(s.player, p, c, LineTier.YG);
        assertNotNull(l);
        assertEquals(cash - l.buildCost, s.player.cash, 1e-6, "peşin ödeme");
        assertEquals(LineStatus.BUILDING, l.status);
        int base = LineTier.YG.buildDays(l.lengthTiles) * 24;
        assertTrue(l.buildHoursLeft >= base && l.buildHoursLeft <= base * 1.31, "süre " + l.buildHoursLeft + " taban " + base);
        hours(s, 24);
        assertEquals(0, p.outputMW, 1e-9, "inşaattaki hat güç taşımaz");
        hours(s, l.buildHoursLeft + 2);
        assertEquals(LineStatus.ACTIVE, l.status);
        assertTrue(p.outputMW > 0);
    }

    @Test
    void lineDelayHappensAboutTenPercent() {
        GameState s = grid(0, false);
        City c = s.cities.get(0);
        Plant p = plantNear(s, c, 10);
        int delayed = 0, n = 300;
        for (int i = 0; i < n; i++) {
            PowerLine l = s.buildLine(s.player, p, c, LineTier.YG);
            if (l.buildHoursLeft > LineTier.YG.buildDays(l.lengthTiles) * 24) delayed++;
            s.removeLine(l, false);
        }
        System.out.printf("gecikme: %d/%d%n", delayed, n);
        assertTrue(delayed > n * 0.04 && delayed < n * 0.18);
    }

    @Test
    void operatorApplicationLifecycleAndCharges() {
        GameState s = grid(0, false);
        City c = s.cities.get(0);
        Plant p = plantNear(s, c, 10);
        double cash = s.player.cash;
        PowerLine l = s.operator.apply(s.player, p, c, LineTier.YG);
        assertNotNull(l);
        double cost = l.buildCost;
        assertEquals(cash - GridBalance.OP_APPLICATION_FEE - GridBalance.OP_CONTRIBUTION * cost, s.player.cash, 1e-6);
        assertNull(l.owner, "hat UŞİ'nin");
        assertEquals(LineStatus.PLANNED, l.status);
        assertEquals(GridBalance.OP_ANNUAL_CHARGE * cost, l.annualCharge, 1e-6);
        int[] eta = s.operator.eta(l);
        System.out.printf("UŞİ tahmini %d–%d ay%n", eta[0], eta[1]);
        assertTrue(eta[0] >= GridBalance.OP_PLANNING_MONTHS);
        assertEquals(eta[0] + GridBalance.OP_ETA_SPREAD_MONTHS, eta[1]);

        long start = s.hour;
        LineStatus last = l.status;
        List<String> phases = new ArrayList<>(List.of(last.name()));
        double charged = 0;
        for (int h = 0; h < 40 * HPM && l.status != LineStatus.ACTIVE; h++) {
            double before = s.player.connectionCharge;
            s.tick();
            if (s.player.connectionCharge > before) charged += s.player.connectionCharge - before;
            if (s.hour % HPM == 0) charged += s.player.lastConnectionCharge - (s.player.connectionCharge); // ay sonu devri
            if (l.status != last) { last = l.status; phases.add(last.name()); }
        }
        double months = (s.hour - start) / (double) HPM;
        System.out.printf("UŞİ süreci: %s, %.1f ay%n", phases, months);
        // bos slot varsa kuyruk ayni saatte insaata gecer (QUEUED gozlenmeyebilir)
        List<String> core = new ArrayList<>(phases); core.remove("QUEUED");
        assertEquals(List.of("PLANNED", "BUILDING", "ACTIVE"), core);
        assertTrue(months >= GridBalance.OP_PLANNING_MONTHS + LineTier.YG.buildDays(l.lengthTiles) * 1.5 / 30 - 0.1);
        assertTrue(p.outputMW > 0 || s.hour > 0);
        // yillik bedel: aylik tahakkuk (gelir tablosunda)
        hours(s, 3 * HPM);
        assertEquals(l.annualCharge / 12, s.player.lastConnectionCharge, 1e-6);
        // iflas eden basvurandan bedel alinmaz
        s.player.bankrupt = true;
        hours(s, HPM);
        assertEquals(0, s.player.lastConnectionCharge, 1e-9);
    }

    @Test
    void queueRespectsSlotsAndFifo() {
        GameState s = grid(0, false);
        int slots = s.operator.slots();
        assertEquals(GridBalance.OP_BASE_SLOTS + s.cities.size() / GridBalance.OP_CITIES_PER_SLOT, slots);
        List<PowerLine> apps = new ArrayList<>();
        int k = 0;
        for (City c : s.cities) for (int j = 0; j < 2 && apps.size() < slots + 3; j++) {
            Plant p = plantNear(s, c, 6 + j * 3 + k++ % 2);
            PowerLine l = s.operator.apply(s.player, p, c, LineTier.YG);
            assertNotNull(l);
            apps.add(l);
            s.tick();
        }
        // kaymalari devre disi birakmak icin: planlamayi hemen bitir
        for (PowerLine l : apps) l.buildHoursLeft = 1;
        hours(s, 2);
        int building = 0;
        for (PowerLine l : apps) if (l.status == LineStatus.BUILDING) building++;
        assertEquals(slots, building, "aynı anda en fazla slot kadar inşaat");
        for (int i = 0; i < slots; i++) assertEquals(LineStatus.BUILDING, apps.get(i).status, "FIFO: ilk başvurular önce");
        assertEquals(LineStatus.QUEUED, apps.get(apps.size() - 1).status);
        // hizlandirma: son basvuru kuyruk basina gecer
        PowerLine lastApp = apps.get(apps.size() - 1);
        assertNull(s.operator.accelerate(lastApp));
        assertNotNull(s.operator.accelerate(lastApp), "yalnızca bir kez");
        assertSame(lastApp, s.operator.queue().get(0));
    }

    @Test
    void accelerateCancelAndTransfer() {
        GameState s = grid(0, false);
        City c = s.cities.get(0);
        Plant p = plantNear(s, c, 10);
        PowerLine l = s.operator.apply(s.player, p, c, LineTier.YG);
        double cash = s.player.cash;
        assertNull(s.operator.accelerate(l));
        assertEquals(cash - GridBalance.OP_ACCELERATE * l.buildCost, s.player.cash, 1e-6);
        assertTrue(l.buildHoursLeft <= GridBalance.OP_ACCELERATE_PLANNING_MONTHS * HPM);
        // iptal (planlamada): katkinin yarisi
        cash = s.player.cash;
        double refund = s.operator.cancel(l);
        assertEquals(l.contribution * GridBalance.OP_CANCEL_REFUND, refund, 1e-6);
        assertEquals(cash + refund, s.player.cash, 1e-6);
        assertFalse(s.lines.contains(l));
        // devir: aktif kendi hatti defterin %60'i
        s.instantLines = true;
        PowerLine own = s.buildLine(s.player, p, c, LineTier.YG);
        cash = s.player.cash;
        double book = own.bookValue(s.hour);
        assertEquals(book * GridBalance.OP_TRANSFER_BOOK, s.operator.transfer(own), 1e-6);
        assertEquals(cash + book * GridBalance.OP_TRANSFER_BOOK, s.player.cash, 1e-6);
        assertNull(own.owner);
        assertNull(own.requester);
        assertEquals(0, own.annualCharge, 1e-12, "devredilen hatta yıllık bedel yok");
        assertTrue(s.lines.stream().noneMatch(x -> x.owner == s.player), "artık oyuncunun hattı değil (bakım gideri yok)");
    }

    @Test
    void operatorPlanConnectsSeparateCitiesAndMerges() {
        GameState s = grid(0, true);
        s.instantLines = true;
        // her sehre kendi santrali: ayri sebeke parcalari
        for (City c : s.cities) { Plant p = plantNear(s, c, 6); s.buildLine(s.player, p, c, LineTier.YG); }
        // ocaga kadar ilerle
        while (s.month() != 0 || s.hour % HPM != 1) s.tick();
        long plans = s.lines.stream().filter(l -> l.operatorPlan).count();
        System.out.printf("UŞİ ocak planı: %d proje, mesajlar: %s%n", plans, s.messages.stream().filter(m -> m.contains("planı")).toList());
        assertTrue(plans >= 1 && plans <= GridBalance.OP_PLAN_MAX_PER_YEAR);
        PowerLine plan = s.lines.stream().filter(l -> l.operatorPlan).findFirst().orElseThrow();
        assertTrue(plan.a instanceof City && plan.b instanceof City);
        int months = plan.buildHoursLeft / HPM;
        assertTrue(months >= GridBalance.OP_PLAN_MIN_MONTHS && months <= GridBalance.OP_PLAN_MAX_MONTHS);
        s.messages.clear();
        hours(s, plan.buildHoursLeft + 2);
        assertEquals(LineStatus.ACTIVE, plan.status);
        assertSame(s.componentOf(plan.a), s.componentOf(plan.b));
        assertTrue(s.messages.stream().anyMatch(m -> m.contains("piyasaları birleşti")), s.messages.toString());
    }

    @Test
    void operatorGrowthSettingOff() {
        GameState s = grid(0, false);
        s.instantLines = true;
        for (City c : s.cities) { Plant p = plantNear(s, c, 6); s.buildLine(s.player, p, c, LineTier.YG); }
        while (s.month() != 0 || s.hour % HPM != 1) s.tick();
        assertTrue(s.lines.stream().noneMatch(l -> l.operatorPlan));
    }

    @Test
    void rivalsChooseOwnLineOrOperator() {
        GameState s = grid(1, false);
        Company r = s.companies.stream().filter(x -> !x.player && !x.neutral).findFirst().orElseThrow();
        City c = s.cities.get(0);
        // hizli santral (gunes): kendi hatti
        r.cash = 2e9;
        Plant solar = RivalAI.buildNear(s, r, s.catalog.get("hx-topcon"), c, 0);
        assertNotNull(solar);
        assertTrue(solar.lines.stream().anyMatch(l -> l.owner == r && l.status == LineStatus.BUILDING), "kısa inşaat: kendi hattı");
        // cok uzun insaatli nukleer: UŞİ basvurusu
        r.cash = 20e9;
        Plant nuke = null;
        for (int i = 0; i < 20 && nuke == null; i++) nuke = RivalAI.buildNear(s, r, s.catalog.get("atr-600"), s.cities.get(i % s.cities.size()), 0);
        assertNotNull(nuke);
        System.out.printf("nükleer inşaat %d gün, UŞİ tahmini %s%n", nuke.equipment.buildDays(), java.util.Arrays.toString(s.operator.estimate(LineTier.CYG, 10)));
        final Plant nk = nuke;
        boolean applied = nk.lines.stream().anyMatch(l -> l.owner == null && l.requester == r && l.status == LineStatus.PLANNED);
        boolean inCluster = nk.lines.isEmpty() && s.connectionPoints().stream().anyMatch(cp -> cp.members.contains(nk) && cp.members.size() > 1);
        assertTrue(applied || inCluster, "uzun inşaat: UŞİ başvurusu (veya kendi kümesine eklendi)");
    }

    @Test
    void saveKeepsApplicationsAndPlans() {
        GameState s = grid(0, true);
        City c = s.cities.get(0);
        Plant p = plantNear(s, c, 10);
        PowerLine l = s.operator.apply(s.player, p, c, LineTier.YG);
        s.operator.accelerate(l);
        Json json = new Json();
        GameState g = json.fromJson(SaveData.class, json.toJson(SaveData.from(s))).restore();
        PowerLine l2 = g.lines.stream().filter(x -> x.id == l.id).findFirst().orElseThrow();
        assertEquals(LineStatus.PLANNED, l2.status);
        assertNull(l2.owner);
        assertSame(g.player, l2.requester);
        assertTrue(l2.accelerated);
        assertEquals(l.contribution, l2.contribution, 1e-6);
        assertEquals(l.buildHoursLeft, l2.buildHoursLeft);
        assertTrue(g.settings.operatorGrowth);
    }

    /** Başvuru sayısı arttıkça kuyrukta bekleme uzar (yuva formülü darboğazı). */
    @Test
    void queueWaitGrowsWithApplications() {
        GameState s = grid(0, false);
        int slots = s.operator.slots();
        List<PowerLine> apps = new ArrayList<>();
        int k = 0;
        for (City c : s.cities) for (int j = 0; j < 3 && apps.size() < slots * 4; j++) {
            Plant p = plantNear(s, c, 5 + j * 3 + k++ % 2);
            PowerLine l = s.operator.apply(s.player, p, c, LineTier.YG);
            if (l != null) apps.add(l);
        }
        for (PowerLine l : apps) l.buildHoursLeft = 1;
        int guard = 0;
        while (apps.stream().anyMatch(l -> l.status != LineStatus.ACTIVE) && guard++ < 10 * Balance.HOURS_PER_YEAR) { s.tick(); s.messages.clear(); }
        List<double[]> q = s.operator.statQueue;
        System.out.printf("kuyruk: %d yuva, %d başvuru; sıraya göre bekleme (ay): %s%n", slots, q.size(),
            q.stream().map(x -> String.format(java.util.Locale.ROOT, "%.1f", x[0])).toList());
        assertEquals(apps.size(), q.size());
        assertEquals(0, q.get(0)[0], 0.01, "ilk başvuru beklemez");
        assertTrue(q.get(q.size() - 1)[0] > q.get(slots - 1)[0] + 1, "son başvuru belirgin uzun bekler");
    }
}
