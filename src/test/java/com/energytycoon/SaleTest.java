package com.energytycoon;

import com.badlogic.gdx.utils.Json;
import com.energytycoon.harness.SimHarness;
import com.energytycoon.sim.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Varlık satışı ve iflas devri: hiçbir varlık yıkılmaz; şirketler, sonra Kamu/UŞİ alır. */
class SaleTest {
    static final int HPM = Balance.HOURS_PER_MONTH;

    static GameState game(int rivals) {
        GameSettings g = SimHarness.reference(1);
        g.rivals = rivals; g.operatorGrowth = false;
        GameState s = GameState.newGame(g);
        s.instantLines = true;
        return s;
    }

    static Company rival(GameState s) { return s.companies.stream().filter(x -> !x.player && !x.neutral).findFirst().orElseThrow(); }

    static Plant plant(GameState s, Company owner, City c, double d) {
        int[] site = null;
        for (double dd = d; site == null && dd < d + 6; dd += 0.5) site = GridTest.siteAt(s, PlantType.GAS, c, dd);
        owner.cash += 1e9;
        Plant p = s.buildPlant(owner, s.catalog.get("at-cc"), site[0], site[1]);
        p.buildHoursLeft = 0;
        assertNotNull(s.buildLine(owner, p, c, LineTier.YG));
        return p;
    }

    @Test
    void rivalBuysProfitablePlayerListing() {
        GameState s = game(1);
        Company r = rival(s);
        Plant p = plant(s, s.player, s.cities.get(0), 8);
        p.lastMonthProfit = 5e6; // yillik 60M: getiri esigini asar (ay devrinde de korunsun)
        assertNull(s.sales.list(s.player, p));
        Listing l = s.sales.openListingOf(p);
        assertEquals(s.sales.bookValue(p, s.player) * Balance.SALE_ASK, l.price, 1e-6);
        r.cash = l.price + 1e9;
        double cash = s.player.cash;
        while (s.hour % HPM != 0) { p.monthProfit = 5e6; s.tick(); }
        s.tick();
        System.out.printf("ilan: %s, %s, alan %s%n", l.assetName(), l.status.label, l.buyer == null ? "-" : l.buyer.name);
        assertEquals(Listing.Status.SOLD, l.status);
        assertSame(r, p.owner);
        assertTrue(p.lines.stream().allMatch(x -> x.owner == r), "bağlı hatlar da devredilir");
        assertTrue(s.player.cash > cash + l.price * 0.9);
    }

    @Test
    void unsoldPlantGoesToKamuAndTmToOperator() {
        GameState s = game(0);
        City c = s.cities.get(0);
        Plant p = plant(s, s.player, c, 8);
        p.lastMonthProfit = 1; // Kamu zarar etmeyen santrali ister
        Substation t = s.buildSubstation(s.player, 1, p.x, p.y + 1);
        if (t == null) t = s.buildSubstation(s.player, 1, p.x + 1, p.y);
        assertNotNull(t);
        t.buildHoursLeft = 0;
        s.markTopologyDirty(); s.tick();
        assertSame(t.cp, p.cp);
        assertNull(s.sales.list(s.player, p));
        assertNull(s.sales.list(s.player, t));
        double cash = s.player.cash;
        double floorP = s.sales.floorPrice(p, s.player), floorT = s.sales.floorPrice(t, s.player);
        for (int h = 0; h < (Balance.SALE_DAYS + 2) * 24; h++) { if (p.owner == s.player) p.monthProfit = 1e6; s.tick(); }
        assertSame(s.neutral, p.owner, "alıcı çıkmazsa Kamu");
        assertNull(t.owner, "TM UŞİ'ye");
        assertTrue(s.substations.contains(t) && s.plants.contains(p), "hiçbir şey yıkılmadı");
        s.markTopologyDirty(); s.tick();
        assertSame(t.cp, p.cp, "santral UŞİ TM'sine bağlı kalır");
        assertTrue(p.outputMW >= 0);
        System.out.printf("son alıcı: santral Kamu %.1fM, TM UŞİ %.1fM%n", floorP / 1e6, floorT / 1e6);
        assertTrue(s.player.cash >= cash + floorP + floorT - 5e6, "taban fiyatlar ödendi");
    }

    @Test
    void bankruptRivalAssetsAreTransferredNotDemolished() {
        GameState s = game(1);
        Company r = rival(s);
        City c = s.cities.get(0);
        Plant a = plant(s, r, c, 8), b = plant(s, r, c, 12);
        a.lastMonthProfit = 1; b.lastMonthProfit = 1;
        Substation t = s.buildSubstation(r, 1, a.x, a.y + 1);
        if (t == null) t = s.buildSubstation(r, 1, a.x + 1, a.y);
        t.buildHoursLeft = 0;
        int plants = s.plants.size(), lines = s.lines.size(), tms = s.substations.size();
        r.cash = -5e9; // satislar acigi kapatamaz
        for (int m = 0; m < 4 && !r.bankrupt; m++) for (int h = 0; h < HPM; h++) {
            a.monthProfit = 1e6; b.monthProfit = 1e6; // Kamu zarar etmeyen santrali ister
            s.tick(); r.cash = Math.min(r.cash, -5e9);
        }
        assertTrue(r.bankrupt);
        System.out.printf("iflas: santral %d→%d, hat %d→%d, TM %d→%d; mesaj %s%n", plants, s.plants.size(), lines, s.lines.size(), tms, s.substations.size(),
            s.messages.stream().filter(m -> m.contains("iflas")).toList());
        assertEquals(tms, s.substations.size(), "TM yıkılmaz");
        assertEquals(lines, s.lines.size(), "hatlar yıkılmaz");
        assertTrue(s.plants.stream().noneMatch(p -> p.owner == r));
        assertTrue(s.lines.stream().noneMatch(l -> l.owner == r));
        assertTrue(s.substations.stream().noneMatch(x -> x.owner == r));
    }

    @Test
    void distressedRivalListsAssets() {
        GameState s = game(1);
        Company r = rival(s);
        Plant p = plant(s, r, s.cities.get(0), 8);
        r.cash = -20e6;
        while (s.hour % HPM != 0) s.tick();
        s.tick();
        assertNotNull(s.sales.openListingOf(p), "nakit eksiye düşünce satışa çıkar");
        // kayit/yukleme
        Json json = new Json();
        GameState g = json.fromJson(SaveData.class, json.toJson(SaveData.from(s))).restore();
        assertEquals(1, g.sales.open().size());
        assertEquals(p.id, g.sales.open().get(0).asset.id);
    }
}
