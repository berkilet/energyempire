package com.energytycoon.sim;

import java.util.ArrayList;
import java.util.List;

/** Sebekeye bagli dugum: santral veya sehir. */
public abstract class GridNode {
    public int id;
    public int x, y;
    public final List<PowerLine> lines = new ArrayList<>();
    /** Son saatteki akis (MW) ve kullanim orani. */
    public double flowMW;

    public double lineCapacity() {
        double c = 0;
        for (PowerLine l : lines) if (l.usable()) c += l.capacityMW();
        return c;
    }
    public double utilization() {
        double c = lineCapacity();
        return c <= 0 ? 0 : Math.min(1.5, flowMW / c);
    }
    public abstract String name();
}
