package com.energytycoon.sim;

import java.util.function.DoubleFunction;
import java.util.function.ToDoubleFunction;

/** Senaryo hedefi: bir olcu ve hedef deger. */
public final class Objective {
    public final String label;
    private final ToDoubleFunction<GameState> metric;
    public final double target;
    /** true ise deger hedefin altinda olmali (ör. kömür santrali sayısı). */
    public final boolean atMost;
    private final DoubleFunction<String> fmt;

    public Objective(String label, ToDoubleFunction<GameState> metric, double target, boolean atMost, DoubleFunction<String> fmt) {
        this.label = label; this.metric = metric; this.target = target; this.atMost = atMost; this.fmt = fmt;
    }

    public double value(GameState s) { return metric.applyAsDouble(s); }
    public boolean met(GameState s) { double v = value(s); return atMost ? v <= target + 1e-9 : v >= target - 1e-9; }

    public double progress(GameState s) {
        double v = value(s);
        if (atMost) return met(s) ? 1 : Math.max(0, Math.min(0.99, 1.0 / (1 + v - target)));
        return target <= 0 ? 1 : Math.max(0, Math.min(1, v / target));
    }

    public String status(GameState s) { return fmt.apply(value(s)) + " / " + fmt.apply(target); }
}
