package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Santrallerin sebekeye ciktigi nokta: tek santral, kume ("bara": ayni sirketin bitisik santralleri) ya da
 * trafo merkezi. Uyeler ihracat kapasitesini paylasir; uyeler arasi baglanti kayipsizdir.
 */
public final class ConnectionPoint {
    public final List<Plant> members = new ArrayList<>();
    /** Noktadan disari cikan hatlar (iki ucu da noktada olan ic hatlar haric). */
    public final List<PowerLine> exportLines = new ArrayList<>();
    /** Trafo merkezi (yoksa null: tek santral veya bara). */
    public Substation tm;
    /** En yakin sehre giden en kisa yolun kaybi (en fazla GridBalance.MAX_PATH_LOSS). */
    public double lossFraction = GridBalance.MAX_PATH_LOSS;
    public double exportedMWThisHour;
    /** Ust sinir (ör. trafo MW); sinirsizsa Double.MAX_VALUE. */
    public double transformerMW = Double.MAX_VALUE;
    /** Dagitim sirasinda kalan ihracat kapasitesi. */
    double remainingMW;

    public double lineCapacityMW() {
        double c = 0;
        for (PowerLine l : exportLines) if (l.usable()) c += l.capacityMW();
        return c;
    }

    public double exportCapacityMW() { return Math.min(transformerMW, lineCapacityMW()); }

    public double utilization() {
        double c = exportCapacityMW();
        return c <= 0 ? 0 : Math.min(1.5, exportedMWThisHour / c);
    }

    public double installedMW() {
        double c = 0;
        for (Plant p : members) c += p.capacityMW();
        return c;
    }

    public boolean contains(GridNode n) { return n == tm && n != null || n instanceof Plant p && members.contains(p); }

    /** Bara: TM olmadan birden fazla santral. */
    public boolean isBara() { return tm == null && members.size() > 1; }

    public String kindLabel() {
        if (tm != null) return tm.levelLabel() + " (" + members.size() + " santral, " + Math.round(installedMW()) + " MW)";
        if (members.size() > 1) return "Küme (" + members.size() + " santral, " + Math.round(installedMW()) + " MW)";
        return "Doğrudan bağlantı";
    }
}
