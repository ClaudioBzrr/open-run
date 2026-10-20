package com.openrun.core.model;

/**
 * A location point accepted by the tracking filter and persisted with the session.
 */
public final class TrackPoint {
    private final int seq;
    private final double latitude;
    private final double longitude;
    private final double altitudeMeters;
    private final double accuracyMeters;
    private final double speedMps;
    private final double bearingDegrees;
    private final long timestampEpochMs;
    private final long elapsedMs;

    public TrackPoint(int seq,
                      double latitude,
                      double longitude,
                      double altitudeMeters,
                      double accuracyMeters,
                      double speedMps,
                      double bearingDegrees,
                      long timestampEpochMs,
                      long elapsedMs) {
        this.seq = seq;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeMeters = altitudeMeters;
        this.accuracyMeters = accuracyMeters;
        this.speedMps = speedMps;
        this.bearingDegrees = bearingDegrees;
        this.timestampEpochMs = timestampEpochMs;
        this.elapsedMs = elapsedMs;
    }

    public int getSeq() {
        return seq;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAltitudeMeters() {
        return altitudeMeters;
    }

    public double getAccuracyMeters() {
        return accuracyMeters;
    }

    public double getSpeedMps() {
        return speedMps;
    }

    public double getBearingDegrees() {
        return bearingDegrees;
    }

    public long getTimestampEpochMs() {
        return timestampEpochMs;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }
}
