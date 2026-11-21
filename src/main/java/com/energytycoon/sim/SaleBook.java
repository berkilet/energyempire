package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Varlik satislari. Her sirket (oyuncu dahil) santralini veya trafo merkezini SALE_DAYS gunluguna satisa cikarabilir;
 * once sirketler (oyuncu, rakipler) istenen fiyattan alabilir. Alici cikmazsa son alici: santrali Kamu (isterse),
 * trafo merkezini UŞİ, taban fiyattan alir. Iflasta kalan varliklar yikilmaz: santraller Kamu'ya (istemezse kapatilir),
 * trafo merkezleri ve hatlar UŞİ'ye gecer.
 */
public final class SaleBook {
    private final GameState s;
    public final List<Listing> all = new ArrayList<>();
    final Map<Integer, Listing> byId = new HashMap<>();

    SaleBook(GameState s) { this.s = s; }

    public Listing get(int id) { return byId.get(id); }
    void add(Listing l) { all.add(l); byId.put(l.id, l); }

    public List<Listing> open() {
        List<Listing> r = new ArrayList<>();
        for (Listing l : all) if (l.status == Listing.Status.OPEN) r.add(l);
        return r;
    }

    public Listing openListingOf(GridNode asset) {
        for (Listing l : all) if (l.status == Listing.Status.OPEN && l.asset == asset) return l;
        return null;
    }

    /** Varligin defter degeri: santral/TM + saticinin ona bagli hatlari. */
    public double bookValue(GridNode asset, Company owner) {
        double v = asset instanceof Plant p ? p.bookValue(s.hour) : asset instanceof Substation t ? t.bookValue(s.hour) : 0;
        for (PowerLine l : asset.lines) if (l.owner == owner && l.owner != null) v += l.bookValue(s.hour);
        return v;
    }

    public double askPrice(GridNode asset, Company owner) { return bookValue(asset, owner) * Balance.SALE_ASK; }
    public double floorPrice(GridNode asset, Company owner) { return bookValue(asset, owner) * Balance.SALE_FLOOR; }

    static Company ownerOf(GridNode n) { return n instanceof Plant p ? p.owner : n instanceof Substation t ? t.owner : null; }

    /** Satisa cikar. Null: tamam; aksi halde sebep. */
    public String list(Company seller, GridNode asset) {
        if (ownerOf(asset) != seller || seller == null) return "Yalnızca kendi varlığınızı satabilirsiniz";
        if (seller.neutral) return "Kamu varlık satmaz";
        if (openListingOf(asset) != null) return "Zaten satışta";
        Listing l = new Listing();
        l.id = s.nextId++; l.asset = asset; l.seller = seller; l.price = askPrice(asset, seller);
        l.listedHour = s.hour; l.expiresHour = s.hour + Balance.SALE_DAYS * 24L;
        add(l);
        s.notify("SATIŞ: " + seller.name + ", " + l.assetName() + " varlığını " + Math.round(l.price / 1e5) / 10.0 + "M $'a satışa çıkardı ("
            + Balance.SALE_DAYS + " gün)", "sale:" + l.id);
        return null;
    }

    public String withdraw(Company c, Listing l) {
        if (l.seller != c || l.status != Listing.Status.OPEN) return "Bu ilan geri çekilemez";
        l.status = Listing.Status.WITHDRAWN;
        return null;
    }

    /** Sirketin satin almasi (oyuncu veya rakip). Null: tamam. */
    public String buy(Company buyer, Listing l) {
        if (l.status != Listing.Status.OPEN) return "İlan kapalı";
        if (buyer == l.seller) return "Kendi ilanınız";
        if (buyer.cash < l.price) return "Yetersiz nakit (" + Math.round(l.price / 1e5) / 10.0 + "M $)";
        buyer.cash -= l.price; buyer.capexSpent += l.price;
        l.seller.cash += l.price;
        transfer(l.asset, l.seller, buyer);
        l.buyer = buyer; l.status = Listing.Status.SOLD;
        s.notify("SATIŞ: " + l.assetName() + " → " + buyer.name + " (" + Math.round(l.price / 1e5) / 10.0 + "M $)");
        return null;
    }

    /** Varligi ve saticinin ona bagli hatlarini yeni sahibe devreder. newOwner == null: UŞİ (yalnizca TM). Kamu'ya gecen santralin hatlari UŞİ'ye gecer. */
    void transfer(GridNode asset, Company from, Company to) {
        boolean toNeutral = to != null && to.neutral;
        for (PowerLine ln : asset.lines) if (ln.owner == from && from != null) {
            ln.owner = (to == null || toNeutral) ? null : to;
            if (ln.owner == null) { ln.requester = null; ln.annualCharge = 0; }
        }
        if (asset instanceof Plant p) {
            if (from != null) s.finance.settleLinkedEca(from, p); // satistan once santrale bagli ECA kapatilir
            p.owner = to; p.preferredTm = 0;
            if (toNeutral) { p.legacy = false; p.retireYear = 0; }
        } else if (asset instanceof Substation t) {
            // uye santraller ayni kalsin: otomatik uyelik sahibe bagli oldugu icin acik tercih yazilir
            if (t.cp != null) for (Plant m : t.cp.members) if (m.preferredTm == 0) m.preferredTm = t.id;
            t.owner = to;
            if (to == null) { t.openAccess = true; t.accessFeePerMWh = GridBalance.ACCESS_FEE_DEFAULT; t.pendingUpgrade = 0; t.upgradeHoursLeft = 0; }
        }
        for (Listing l : all) if (l.asset == asset && l.status == Listing.Status.OPEN) l.status = Listing.Status.WITHDRAWN;
        s.markTopologyDirty();
    }

    /** Kamu bu santrali ister mi: zarar etmiyorsa veya bulundugu sebeke parcasinin arzi yetersizse. */
    public boolean kamuWants(Plant p) {
        if (s.neutral == null) return false;
        GameState.Component comp = s.componentOf(p);
        boolean supplyNeed = comp != null && comp.peakMW > 0 && comp.nominalMW / comp.peakMW < Balance.SALE_KAMU_SUPPLY;
        return supplyNeed || (p.operational() && p.lastMonthProfit >= 0);
    }

    /** Gunluk: suresi dolan ilanlar; alici cikmazsa son alici (Kamu santral / UŞİ TM) taban fiyattan alir. */
    void daily() {
        for (Listing l : all) {
            if (l.status != Listing.Status.OPEN || s.hour < l.expiresHour) continue;
            double floor = floorPrice(l.asset, l.seller);
            if (l.asset instanceof Plant p && kamuWants(p)) {
                s.neutral.cash -= floor; l.seller.cash += floor;
                transfer(l.asset, l.seller, s.neutral);
                l.buyer = s.neutral; l.price = floor; l.status = Listing.Status.SOLD;
                s.notify("SATIŞ: alıcı çıkmadı, " + l.assetName() + " Kamu Üretim'e geçti (" + Math.round(floor / 1e5) / 10.0 + "M $)");
            } else if (l.asset instanceof Substation) {
                l.seller.cash += floor;
                transfer(l.asset, l.seller, null);
                l.price = floor; l.status = Listing.Status.SOLD;
                s.notify("SATIŞ: alıcı çıkmadı, " + l.assetName() + " UŞİ'ye geçti (" + Math.round(floor / 1e5) / 10.0 + "M $)");
            } else {
                l.status = Listing.Status.EXPIRED;
                if (l.seller.player) s.notify("SATIŞ: " + l.assetName() + " için alıcı çıkmadı; Kamu da almak istemedi");
            }
        }
    }

    /** Iflas: kalan varliklar yikilmaz. Santraller Kamu'ya (istemezse kapatilir), TM ve hatlar UŞİ'ye. */
    void bankruptcy(Company c) {
        for (Listing l : all) if (l.seller == c && l.status == Listing.Status.OPEN) l.status = Listing.Status.WITHDRAWN;
        int toKamu = 0, closed = 0, toOp = 0;
        for (Plant p : new ArrayList<>(s.plants)) {
            if (p.owner != c) continue;
            if (kamuWants(p)) { transfer(p, c, s.neutral); toKamu++; }
            else { s.demolishPlant(p, false); closed++; }
        }
        for (Substation t : s.substations) if (t.owner == c) { transfer(t, c, null); toOp++; }
        for (PowerLine l : s.lines) if (l.owner == c) { l.owner = null; l.requester = null; l.annualCharge = 0; }
        s.markTopologyDirty();
        s.notify(c.name + " iflas etti: " + toKamu + " santral Kamu'ya, " + toOp + " trafo merkezi ve hatları UŞİ'ye geçti"
            + (closed > 0 ? ", " + closed + " santral kapatıldı" : ""));
    }
}
