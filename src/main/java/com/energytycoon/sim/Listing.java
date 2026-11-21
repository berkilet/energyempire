package com.energytycoon.sim;

/** Satisa cikarilan varlik (santral veya trafo merkezi; sahibinin bagli hatlariyla birlikte). */
public final class Listing {
    public enum Status { OPEN("Satışta"), SOLD("Satıldı"), WITHDRAWN("Satıştan çekildi"), EXPIRED("Alıcı çıkmadı");
        public final String label; Status(String l) { label = l; } }

    public int id;
    public GridNode asset;
    public Company seller, buyer;
    public double price;
    public long listedHour, expiresHour;
    public Status status = Status.OPEN;

    public String assetName() {
        return asset instanceof Plant p ? p.name() + " (" + Math.round(p.capacityMW()) + " MW " + p.type.label + ")"
             : asset instanceof Substation t ? t.name() + " (" + Math.round(t.transformerMW) + " MW trafo)" : asset.name();
    }
}
