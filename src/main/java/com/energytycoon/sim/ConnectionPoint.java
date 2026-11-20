package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Santrallerin sebekeye ciktigi nokta. Faz 2'de her santral kendi noktasidir; ileride kume ("bara")
 * ve trafo merkezi birden fazla santrali ayni noktada toplar ve ihracat kapasitesini paylastirir.
 */
public final class ConnectionPoint {
    public final List<Plant> members = new ArrayList<>();
    /** Noktadan disari cikan hatlar (iki ucu da noktada olan ic hatlar haric). */
    public final List<PowerLine> exportLines = new ArrayList<>();
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

    public boolean contains(GridNode n) { return n instanceof Plant p && members.contains(p); }
}
