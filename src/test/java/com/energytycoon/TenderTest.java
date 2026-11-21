package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Acil kapasite ihalesi: tetiklenme, kazanan, garantili ödeme, teminat yanması. */
class TenderTest {
    static final int HPM = Balance.HOURS_PER_MONTH;

    /** Rakipsiz oyun; ilk şehri besleyen kamu santralleri kapatılır (eski filo kapanışı). */
    static GameState darkCity() {
        GameSettings g = SimHarness.reference(1);
        g.rivals = 0;
        g.operatorGrowth = false;
        GameState s = GameState.newGame(g);
        City c = s.cities.get(0);
        for (Plant p : new ArrayList<>(s.plants))
            if (p.owner.neutral && p.lines.stream().anyMatch(l -> l.other(p) == c)) s.demolishPlant(p, false);
        return s;
    }

    static Tender waitForTender(GameState s, City c) {
        for (int m = 0; m < 20; m++) {
            for (int h = 0; h < HPM; h++) s.tick();
            for (Tender t : s.tenders.all) if (t.city == c) return t;
        }
        return null;
    }

    @Test
    void tenderOpensAfterTwelveDarkMonths() {
        GameState s = darkCity();
        City c = s.cities.get(0);
        long start = s.hour;
        Tender t = waitForTender(s, c);
        assertNotNull(t, "ihale açılmadı; memnuniyet " + c.satisfaction);
        double months = (t.openedHour - start) / (double) HPM;
        System.out.printf("ihale: %s %.0f MW, %.1f ay sonra açıldı (memnuniyet %%%.0f, karşılanan %%%.0f)%n",
            c.name, t.mw, months, c.satisfaction * 100, c.lastServedRatio * 100);
        // oyun ayin 8. saatinde basladigi icin ilk ay ~1 saat eksik sayilir
        assertTrue(months >= Balance.TENDER_LOW_MONTHS - 0.05, "12 ay dolmadan açılmamalı");
        assertEquals(0, t.mw % Balance.TENDER_ROUND_MW, 1e-9);
        assertTrue(t.mw >= Balance.TENDER_ROUND_MW);
        assertTrue(s.messages.stream().anyMatch(m -> m.startsWith("İHALE:") && m.contains(c.name)));
        assertEquals("tender:" + t.id, s.messageActions.get(s.messages.stream().filter(m -> m.startsWith("İHALE:")).findFirst().orElseThrow()));
        // ayni sehir icin 3 yil icinde ikinci ihale yok
        for (int h = 0; h < 30 * HPM; h++) s.tick();
        assertEquals(1, s.tenders.all.stream().filter(x -> x.city == c).count());
    }

    @Test
    void winnerIsPaidBidPrice() {
        GameState s = darkCity();
        s.instantLines = true;
        City c = s.cities.get(0);
        Tender t = waitForTender(s, c);
        assertNotNull(t);
        s.player.cash = 5e9;
        double cash = s.player.cash, dep = s.tenders.depositFor(t);
        assertNull(s.tenders.bid(s.player, t, 95));
        assertEquals(cash - dep, s.player.cash, 1e-6, "teminat");
        while (t.status == Tender.Status.OPEN) s.tick();
        while (s.hour % HPM != 1) s.tick();
        assertEquals(Tender.Status.AWARDED, t.status);
        assertSame(s.player, t.winner);
        // kapasiteyi kur: sehre <= 15 kare gaz santralleri, her birine kendi hatti
        double built = 0;
        for (double d = 5; built < t.mw && d < 15; d += 1) {
            int[] site = GridTest.siteAt(s, PlantType.GAS, c, d);
            if (site == null) continue;
            Plant p = s.buildPlant(s.player, s.catalog.get("at-cc"), site[0], site[1]);
            p.buildHoursLeft = 0; p.outageHoursLeft = 0;
            assertNotNull(s.buildLine(s.player, p, c, LineTier.YG));
            built += p.capacityMW();
        }
        double before = s.player.cash;
        for (int h = 0; h < HPM; h++) s.tick();
        assertEquals(Tender.Status.ACTIVE, t.status, "kurulum ay sonunda tanınmalı");
        assertTrue(s.player.cash > before, "teminat iade edildi ve gelir var");
        double paid0 = t.paidRevenue, mwh0 = t.paidMWh;
        for (int h = 0; h < 48; h++) s.tick();
        double paid = t.paidRevenue - paid0, mwh = t.paidMWh - mwh0;
        System.out.printf("ihale ödemesi: 48 saatte %.0f MWh × $%.0f = %.0f $ (kurulu %.0f MW, ihale %.0f MW)%n", mwh, t.price, paid, built, t.mw);
        assertTrue(mwh > 0);
        assertEquals(t.price, paid / mwh, 1e-6, "kapsamdaki üretim teklif fiyatından");
        assertTrue(mwh <= t.mw * 48 + 1e-6, "ihale MW'ını aşan kısım piyasa fiyatından");
        // kayit/yukleme
        Json json = new Json();
        GameState g = json.fromJson(SaveData.class, json.toJson(SaveData.from(s))).restore();
        Tender t2 = g.tenders.get(t.id);
        assertEquals(Tender.Status.ACTIVE, t2.status);
        assertSame(g.player, t2.winner);
        assertTrue(g.plants.stream().anyMatch(p -> p.tenderId == t.id));
    }

    @Test
    void depositForfeitedIfNotBuilt() {
        GameState s = darkCity();
        City c = s.cities.get(0);
        Tender t = waitForTender(s, c);
        assertNotNull(t);
        s.player.cash = 5e9;
        assertNull(s.tenders.bid(s.player, t, 120));
        while (t.status == Tender.Status.OPEN) s.tick();
        assertEquals(Tender.Status.AWARDED, t.status);
        double cash = s.player.cash;
        for (int m = 0; m < Balance.TENDER_BUILD_MONTHS + 2 && t.status == Tender.Status.AWARDED; m++) for (int h = 0; h < HPM; h++) s.tick();
        assertEquals(Tender.Status.FORFEITED, t.status);
        assertEquals(cash, s.player.cash, 1.0, "teminat iade edilmedi (oyuncunun başka nakit akışı yok)");
        assertTrue(s.messages.stream().anyMatch(m -> m.contains("teminat") && m.contains("yandı")));
    }

    @Test
    void rivalBidsAndLosersAreRefunded() {
        GameSettings g = SimHarness.reference(1);
        g.rivals = 2; g.operatorGrowth = false;
        GameState s = GameState.newGame(g);
        City c = s.cities.get(0);
        for (Plant p : new ArrayList<>(s.plants))
            if (p.owner.neutral && p.lines.stream().anyMatch(l -> l.other(p) == c)) s.demolishPlant(p, false);
        for (Company x : s.companies) if (!x.player && !x.neutral) x.cash = 0; // ihale acilana kadar sehri doldurmasinlar
        Tender t = waitForTender(s, c);
        assertNotNull(t);
        for (Company x : s.companies) if (!x.player && !x.neutral) x.cash = 3e9;
        while (t.status == Tender.Status.OPEN && s.hour < t.closeHour - 24) s.tick();
        System.out.printf("teklifler: %s%n", t.bids.stream().map(b -> b.company.name + " $" + b.price).toList());
        assertFalse(t.bids.isEmpty(), "nakdi olan rakip teklif vermeli");
        s.player.cash = 5e9;
        assertNull(s.tenders.bid(s.player, t, Balance.TENDER_MAX_PRICE)); // pahali teklif: kaybeder
        double cash = s.player.cash;
        while (t.status == Tender.Status.OPEN) s.tick();
        assertNotSame(s.player, t.winner);
        assertEquals(cash + t.bidOf(s.player).deposit, s.player.cash, 1.0, "kaybedene teminat iadesi");
    }
}
