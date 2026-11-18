package com.energytycoon.sim;

public final class PowerLine {
    public int id;
    public GridNode a, b;
    public Company owner;
    public double capacityMW = Balance.LINE_CAPACITY;
    public double lengthTiles;
    public double buildCost;

    public GridNode other(GridNode n) { return n == a ? b : a; }
    public double utilization() { return Math.max(a.utilization(), b.utilization()); }
    public double omPerHour() { return lengthTiles * Balance.LINE_OM_PER_TILE_YEAR / Balance.HOURS_PER_YEAR; }
}
