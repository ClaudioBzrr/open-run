package com.openrun.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Final metrics of a finished session.
 */
public final class SessionSummary {
    private final ActivityType type;
    private final long startedAtEpochMs;
    private final long endedAtEpochMs;
    private final long movingTimeMs;
    private final long pausedTimeMs;
    private final double distanceMeters;
    private final double elevationGainMeters;
    private final double elevationLossMeters;
    private final double averagePaceSecPerKm;
    private final double bestKmPaceSecPerKm;
    private final int pointCount;
    private final List<Split> splits;

    public SessionSummary(ActivityType type,
                          long startedAtEpochMs,
                          long endedAtEpochMs,
                          long movingTimeMs,
                          long pausedTimeMs,
                          double distanceMeters,
                          double elevationGainMeters,
                          double elevationLossMeters,
                          double averagePaceSecPerKm,
                          double bestKmPaceSecPerKm,
                          int pointCount,
                          List<Split> splits) {
        this.type = type;
        this.startedAtEpochMs = startedAtEpochMs;
        this.endedAtEpochMs = endedAtEpochMs;
        this.movingTimeMs = movingTimeMs;
        this.pausedTimeMs = pausedTimeMs;
        this.distanceMeters = distanceMeters;
        this.elevationGainMeters = elevationGainMeters;
        this.elevationLossMeters = elevationLossMeters;
        this.averagePaceSecPerKm = averagePaceSecPerKm;
        this.bestKmPaceSecPerKm = bestKmPaceSecPerKm;
        this.pointCount = pointCount;
        this.splits = Collections.unmodifiableList(new ArrayList<>(splits));
    }

    public ActivityType getType() {
        return type;
    }

    public long getStartedAtEpochMs() {
        return startedAtEpochMs;
    }

    public long getEndedAtEpochMs() {
        return endedAtEpochMs;
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

    public double getElevationGainMeters() {
        return elevationGainMeters;
    }

    public double getElevationLossMeters() {
        return elevationLossMeters;
    }

    public double getAveragePaceSecPerKm() {
        return averagePaceSecPerKm;
    }

    public double getBestKmPaceSecPerKm() {
        return bestKmPaceSecPerKm;
    }

    public int getPointCount() {
        return pointCount;
    }

    public List<Split> getSplits() {
        return splits;
    }
}
