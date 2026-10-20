package com.openrun.core.model;

/**
 * A location sample as delivered by the platform, before filtering.
 *
 * <p>Optional values are represented as {@link Double#NaN}.</p>
 */
public final class RawFix {
    private final double latitude;
    private final double longitude;
    private final double altitudeMeters;
    private final double accuracyMeters;
    private final double speedMps;
    private final double bearingDegrees;
    private final long timestampEpochMs;

    public RawFix(double latitude,
                  double longitude,
                  double altitudeMeters,
                  double accuracyMeters,
                  double speedMps,
                  double bearingDegrees,
                  long timestampEpochMs) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitudeMeters = altitudeMeters;
        this.accuracyMeters = accuracyMeters;
        this.speedMps = speedMps;
        this.bearingDegrees = bearingDegrees;
        this.timestampEpochMs = timestampEpochMs;
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

    public boolean hasAltitude() {
        return isFinite(altitudeMeters);
    }

    public boolean hasAccuracy() {
        return isFinite(accuracyMeters) && accuracyMeters > 0;
    }

    public boolean hasSpeed() {
        return isFinite(speedMps) && speedMps >= 0;
    }

    public boolean hasBearing() {
        return isFinite(bearingDegrees);
    }

    public boolean hasValidCoordinate() {
        return isFinite(latitude)
                && isFinite(longitude)
                && latitude >= -90.0d
                && latitude <= 90.0d
                && longitude >= -180.0d
                && longitude <= 180.0d;
    }

    public static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
