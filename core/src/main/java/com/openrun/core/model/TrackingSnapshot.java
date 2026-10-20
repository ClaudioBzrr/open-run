package com.openrun.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable view of the current recording state, suitable for the UI.
 */
public final class TrackingSnapshot {
    private final TrackingState state;
    private final ActivityType type;
    private final long movingTimeMs;
    private final long pausedTimeMs;
    private final double distanceMeters;
    private final double currentPaceSecPerKm;
    private final double averagePaceSecPerKm;
    private final double elevationGainMeters;
    private final double elevationLossMeters;
    private final double accuracyMeters;
    private final boolean signalLost;
    private final int pointCount;
    private final double lastLatitude;
    private final double lastLongitude;
    private final long lastFixAtEpochMs;
    private final boolean autoPaused;
    private final List<Split> splits;
    private final long startedAtEpochMs;

    public TrackingSnapshot(TrackingState state,
                            ActivityType type,
                            long movingTimeMs,
                            long pausedTimeMs,
                            double distanceMeters,
                            double currentPaceSecPerKm,
                            double averagePaceSecPerKm,
                            double elevationGainMeters,
                            double elevationLossMeters,
                            double accuracyMeters,
                            boolean signalLost,
                            int pointCount,
                            double lastLatitude,
                            double lastLongitude,
                            long lastFixAtEpochMs,
                            boolean autoPaused,
                            List<Split> splits,
                            long startedAtEpochMs) {
        this.state = state;
        this.type = type;
        this.movingTimeMs = movingTimeMs;
        this.pausedTimeMs = pausedTimeMs;
        this.distanceMeters = distanceMeters;
        this.currentPaceSecPerKm = currentPaceSecPerKm;
        this.averagePaceSecPerKm = averagePaceSecPerKm;
        this.elevationGainMeters = elevationGainMeters;
        this.elevationLossMeters = elevationLossMeters;
        this.accuracyMeters = accuracyMeters;
        this.signalLost = signalLost;
        this.pointCount = pointCount;
        this.lastLatitude = lastLatitude;
        this.lastLongitude = lastLongitude;
        this.lastFixAtEpochMs = lastFixAtEpochMs;
        this.autoPaused = autoPaused;
        this.splits = Collections.unmodifiableList(new ArrayList<>(splits));
        this.startedAtEpochMs = startedAtEpochMs;
    }

    public static TrackingSnapshot idle(ActivityType type) {
        return new TrackingSnapshot(
                TrackingState.IDLE,
                type == null ? ActivityType.RUN : type,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Double.NaN,
                false,
                0,
                Double.NaN,
                Double.NaN,
                0,
                false,
                Collections.emptyList(),
                0
        );
    }

    public TrackingState getState() {
        return state;
    }

    public ActivityType getType() {
        return type;
    }

    public long getMovingTimeMs() {
        return movingTimeMs;
    }

    public long getPausedTimeMs() {
        return pausedTimeMs;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public double getCurrentPaceSecPerKm() {
        return currentPaceSecPerKm;
    }

    public double getAveragePaceSecPerKm() {
        return averagePaceSecPerKm;
    }

    public double getElevationGainMeters() {
        return elevationGainMeters;
    }

    public double getElevationLossMeters() {
        return elevationLossMeters;
    }

    public double getAccuracyMeters() {
        return accuracyMeters;
    }

    public boolean isSignalLost() {
        return signalLost;
    }

    public int getPointCount() {
        return pointCount;
    }

    public double getLastLatitude() {
        return lastLatitude;
    }

    public double getLastLongitude() {
        return lastLongitude;
    }

    public long getLastFixAtEpochMs() {
        return lastFixAtEpochMs;
    }

    public boolean isAutoPaused() {
        return autoPaused;
    }

    public List<Split> getSplits() {
        return splits;
    }

    public long getStartedAtEpochMs() {
        return startedAtEpochMs;
    }

    public boolean isActive() {
        return state.isActive();
    }
}
