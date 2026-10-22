package com.openrun.core.stats;

import com.openrun.core.model.RawFix;
import com.openrun.core.model.RecordingConfig;

/**
 * Rejects location samples that would make the track noisy or physically
 * impossible. Distances are always measured from the last accepted point, so a
 * rejected sample never creates a gap in the accumulated distance.
 */
public final class TrackFilter {
    private static final double MIN_SEGMENT_METERS = 1.5d;
    private static final double MAX_DYNAMIC_THRESHOLD_METERS = 12.0d;
    private static final double ACCURACY_THRESHOLD_FACTOR = 0.35d;
    private static final double SPEED_CONFIRMS_MOVEMENT_MPS = 0.8d;
    private static final double STATIONARY_ACCURACY_FACTOR = 0.8d;
    private static final double MIN_STATIONARY_RADIUS_METERS = 10.0d;
    private static final double MAX_STATIONARY_RADIUS_METERS = 20.0d;

    private final RecordingConfig config;
    private RawFix lastAccepted;
    private RawFix pending;
    private int pendingConfirmationCount;

    public TrackFilter(RecordingConfig config) {
        this.config = config;
    }

    public boolean accept(RawFix fix, boolean ignoreAccuracy) {
        if (fix == null || !fix.hasValidCoordinate()) {
            clearPending();
            return false;
        }
        if (!ignoreAccuracy) {
            if (!fix.hasAccuracy() || fix.getAccuracyMeters() > config.getMaxAccuracyMeters()) {
                clearPending();
                return false;
            }
        }
        if (fix.hasSpeed() && fix.getSpeedMps() > config.getMaxSpeedMps()) {
            clearPending();
            return false;
        }
        if (lastAccepted == null) {
            lastAccepted = fix;
            clearPending();
            return true;
        }

        long deltaMs = fix.getTimestampEpochMs() - lastAccepted.getTimestampEpochMs();
        if (deltaMs <= 0) {
            clearPending();
            return false;
        }

        double distance = GeoMath.distanceMeters(
                lastAccepted.getLatitude(),
                lastAccepted.getLongitude(),
                fix.getLatitude(),
                fix.getLongitude()
        );
        double requiredDistance = dynamicThresholdMeters(fix);
        if (distance < requiredDistance) {
            clearPending();
            return false;
        }

        // Doppler speed is usually much better than position-derived speed for
        // distinguishing a stationary phone from slow walking. Without a
        // confirming speed, do not move the anchor until the fix has clearly
        // left the current accuracy radius. This is the deadband that prevents
        // multipath jitter from becoming a wandering route.
        double impliedSpeed = distance / (deltaMs / 1000.0d);
        if (impliedSpeed > config.getMaxSpeedMps()) {
            clearPending();
            return false;
        }

        boolean speedConfirmsMovement = fix.hasSpeed()
                && fix.getSpeedMps() >= SPEED_CONFIRMS_MOVEMENT_MPS;
        if (!speedConfirmsMovement
                && distance < stationaryRadiusMeters(fix)) {
            clearPending();
            return false;
        }

        if (speedConfirmsMovement) {
            clearPending();
            lastAccepted = fix;
            return true;
        }

        // With no reliable speed, require three consistent fixes beyond the
        // deadband. Isolated multipath jumps are therefore ignored instead of
        // becoming fake segments.
        if (pending == null) {
            pending = fix;
            pendingConfirmationCount = 1;
            return false;
        }
        double pendingDistance = GeoMath.distanceMeters(
                pending.getLatitude(),
                pending.getLongitude(),
                fix.getLatitude(),
                fix.getLongitude()
        );
        if (pendingDistance > Math.max(5.0d, requiredDistance * 2.0d)) {
            pending = fix;
            pendingConfirmationCount = 1;
            return false;
        }

        pendingConfirmationCount++;
        pending = fix;
        if (pendingConfirmationCount < 3) {
            return false;
        }

        clearPending();
        lastAccepted = fix;
        return true;
    }

    private void clearPending() {
        pending = null;
        pendingConfirmationCount = 0;
    }

    private double dynamicThresholdMeters(RawFix fix) {
        double accuracy = fix.hasAccuracy()
                ? fix.getAccuracyMeters()
                : lastAccepted.getAccuracyMeters();
        if (!RawFix.isFinite(accuracy) || accuracy <= 0) {
            return MIN_SEGMENT_METERS;
        }
        return Math.min(
                MAX_DYNAMIC_THRESHOLD_METERS,
                Math.max(MIN_SEGMENT_METERS, accuracy * ACCURACY_THRESHOLD_FACTOR)
        );
    }

    private double stationaryRadiusMeters(RawFix fix) {
        double accuracy = fix.hasAccuracy()
                ? fix.getAccuracyMeters()
                : lastAccepted.getAccuracyMeters();
        if (!RawFix.isFinite(accuracy) || accuracy <= 0) {
            return MIN_SEGMENT_METERS;
        }
        return Math.min(
                MAX_STATIONARY_RADIUS_METERS,
                Math.max(MIN_STATIONARY_RADIUS_METERS, accuracy * STATIONARY_ACCURACY_FACTOR)
        );
    }

    public void reset() {
        lastAccepted = null;
        clearPending();
    }

    /**
     * Restores the filter anchor when resuming a persisted session.
     */
    public void restoreLast(RawFix fix) {
        lastAccepted = fix;
        clearPending();
    }

    public RawFix getLastAccepted() {
        return lastAccepted;
    }
}
