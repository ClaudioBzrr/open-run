package com.openrun.core.stats;

import com.openrun.core.model.RawFix;
import com.openrun.core.model.RecordingConfig;

/**
 * Rejects location samples that would make the track noisy or physically
 * impossible. Distances are always measured from the last accepted point, so a
 * rejected sample never creates a gap in the accumulated distance.
 */
public final class TrackFilter {
    private static final double MIN_SEGMENT_METERS = 0.8d;

    private final RecordingConfig config;
    private RawFix lastAccepted;

    public TrackFilter(RecordingConfig config) {
        this.config = config;
    }

    public boolean accept(RawFix fix, boolean ignoreAccuracy) {
        if (fix == null || !fix.hasValidCoordinate()) {
            return false;
        }
        if (!ignoreAccuracy) {
            if (!fix.hasAccuracy() || fix.getAccuracyMeters() > config.getMaxAccuracyMeters()) {
                return false;
            }
        }
        if (lastAccepted == null) {
            lastAccepted = fix;
            return true;
        }

        long deltaMs = fix.getTimestampEpochMs() - lastAccepted.getTimestampEpochMs();
        if (deltaMs <= 0) {
            return false;
        }

        double distance = GeoMath.distanceMeters(
                lastAccepted.getLatitude(),
                lastAccepted.getLongitude(),
                fix.getLatitude(),
                fix.getLongitude()
        );
        if (distance < MIN_SEGMENT_METERS) {
            return false;
        }

        double impliedSpeed = distance / (deltaMs / 1000.0d);
        if (impliedSpeed > config.getMaxSpeedMps()) {
            return false;
        }

        lastAccepted = fix;
        return true;
    }

    public void reset() {
        lastAccepted = null;
    }

    public RawFix getLastAccepted() {
        return lastAccepted;
    }
}
