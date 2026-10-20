package com.openrun.core.model;

/**
 * A completed kilometre split.
 */
public final class Split {
    private final int index;
    private final double distanceMeters;
    private final long durationMs;

    public Split(int index, double distanceMeters, long durationMs) {
        this.index = index;
        this.distanceMeters = distanceMeters;
        this.durationMs = durationMs;
    }

    public int getIndex() {
        return index;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public double getPaceSecPerKm() {
        if (distanceMeters <= 0) {
            return 0;
        }
        return (durationMs / 1000.0d) / (distanceMeters / 1000.0d);
    }
}
