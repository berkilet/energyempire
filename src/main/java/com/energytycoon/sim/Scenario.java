package com.energytycoon.sim;

import com.energytycoon.world.MapPreset;

import java.util.List;
import java.util.function.Consumer;

public final class Scenario {
    public final String id, title, desc;
    public final MapPreset forcedPreset;
    /** 0 = sure siniri yok; aksi halde bu yilin sonuna kadar hedefler tutturulmali. */
    public final int deadlineYear;
    public final List<Objective> objectives;
    final Consumer<GameState> setup;

    Scenario(String id, String title, String desc, MapPreset forced, int deadline, List<Objective> obj, Consumer<GameState> setup) {
        this.id = id; this.title = title; this.desc = desc; this.forcedPreset = forced; this.deadlineYear = deadline;
        this.objectives = obj; this.setup = setup;
    }

    public boolean allMet(GameState s) {
        for (Objective o : objectives) if (!o.met(s)) return false;
        return true;
    }
}
