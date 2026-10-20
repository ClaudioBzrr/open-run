package com.openrun.app.util;

/**
 * Chart-ready series derived from a recorded track.
 */
public final class PerformanceSeries {
    public final float[] paceX;
    public final float[] paceY;
    public final float[] elevationX;
    public final float[] elevationY;

    public PerformanceSeries(float[] paceX,
                             float[] paceY,
                             float[] elevationX,
                             float[] elevationY) {
        this.paceX = paceX;
        this.paceY = paceY;
        this.elevationX = elevationX;
        this.elevationY = elevationY;
    }

    public boolean hasPace() {
        return paceX.length >= 2;
    }

    public boolean hasElevation() {
        return elevationX.length >= 2;
    }
}
