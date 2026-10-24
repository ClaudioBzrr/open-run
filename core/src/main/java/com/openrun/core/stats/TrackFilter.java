package com.openrun.core.stats;

import com.openrun.core.model.RawFix;
import com.openrun.core.model.RecordingConfig;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Rejects location samples that would make the track noisy or physically
 * impossible.
 *
 * <p>Once movement is confirmed, samples are kept dense: the filter only
 * requires a short segment between accepted fixes. Keeping every fix is what
 * preserves distance on curved routes. Previously the anchor was held for
 * 10-20 m and the whole path between anchors was collapsed into a straight
 * chord, which made winding runs and walks come out much shorter than the
 * distance actually covered.</p>
 *
 * <p>Stationary drift is still rejected. Movement is confirmed with Doppler
 * speed when the platform provides it, or by a consistent displacement over a
 * short time window when it does not. A stationary phone must accumulate a
 * clear displacement before the filter starts accepting segments again.</p>
 */
public final class TrackFilter {
    private static final double MIN_SEGMENT_METERS = 1.0d;
    private static final double MAX_SEGMENT_METERS = 3.0d;
    private static final double SEGMENT_ACCURACY_FACTOR = 0.1d;

    private static final double SPEED_MOVING_MPS = 0.8d;
    private static final double SPEED_STATIONARY_MPS = 0.3d;

    // Radius used to confirm that a stationary phone has really started to
    // move. It is intentionally larger than the normal segment threshold.
    private static final double MIN_CONFIRM_RADIUS_METERS = 10.0d;
    private static final double MAX_CONFIRM_RADIUS_METERS = 20.0d;
    private static final double CONFIRM_ACCURACY_FACTOR = 0.8d;

    // Window used to tell a slow walk (consistent direction) from GPS jitter
    // (wandering). The radius is the net displacement expected while moving.
    private static final double MIN_MOTION_RADIUS_METERS = 3.0d;
    private static final double MAX_MOTION_RADIUS_METERS = 6.0d;
    private static final double MOTION_ACCURACY_FACTOR = 0.25d;
    private static final double MIN_STRAIGHTNESS = 0.5d;
    private static final long WINDOW_MS = 5_000L;
    private static final long WINDOW_READY_MS = 3_500L;

    private static final long STATIONARY_CONFIRM_MS = 2_500L;

    private final RecordingConfig config;
    private final Deque<RawFix> window = new ArrayDeque<>();

    private RawFix lastAccepted;
    private RawFix pending;
    private int pendingConfirmationCount;
    private boolean moving;
    private long stationarySinceMs = -1;

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
            remember(fix);
            moving = fix.hasSpeed() && fix.getSpeedMps() >= SPEED_MOVING_MPS;
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
        double impliedSpeed = distance / (deltaMs / 1000.0d);
        if (impliedSpeed > config.getMaxSpeedMps()) {
            clearPending();
            return false;
        }

        remember(fix);
        boolean speedKnown = fix.hasSpeed();
        boolean speedMoving = speedKnown && fix.getSpeedMps() >= SPEED_MOVING_MPS;
        boolean speedStationary = speedKnown && fix.getSpeedMps() < SPEED_STATIONARY_MPS;
        Window windowStats = windowStats(fix);
        double requiredDistance = requiredDistance(fix);

        if (speedMoving) {
            moving = true;
            stationarySinceMs = -1;
            clearPending();
            return acceptIfFar(fix, distance, requiredDistance);
        }

        if (moving) {
            boolean stationaryNow = false;
            if (speedStationary) {
                // Doppler says still. Give a clearly consistent displacement
                // the benefit of the doubt, because some devices report zero
                // speed while the user is actually moving.
                boolean displacementSaysMoving = windowStats.ready
                        && windowStats.net >= Math.max(2.5d, requiredDistance * 1.5d)
                        && windowStats.straightness >= 0.65d;
                stationaryNow = !displacementSaysMoving;
            } else if (!speedKnown || fix.getSpeedMps() < SPEED_MOVING_MPS) {
                // Ambiguous or missing speed: only keep moving while the
                // window shows a consistent displacement.
                stationaryNow = windowStats.ready
                        && (windowStats.net < motionRadius(fix)
                        || windowStats.straightness < MIN_STRAIGHTNESS);
            }

            if (stationaryNow) {
                if (stationarySinceMs < 0) {
                    stationarySinceMs = fix.getTimestampEpochMs();
                }
                if (fix.getTimestampEpochMs() - stationarySinceMs >= STATIONARY_CONFIRM_MS) {
                    moving = false;
                    clearPending();
                }
                // Do not move the anchor while the phone looks stationary:
                // this is what stops jitter from becoming distance.
                return false;
            }

            stationarySinceMs = -1;
            return acceptIfFar(fix, distance, requiredDistance);
        }

        // Not moving yet. A full window of consistent displacement is enough
        // evidence; otherwise require a few fixes beyond the confirmation
        // radius so a single multipath jump cannot start a track.
        boolean windowConfirmsMovement = windowStats.ready
                && windowStats.net >= confirmRadius(fix)
                && windowStats.straightness >= 0.6d;
        if (windowConfirmsMovement) {
            moving = true;
            stationarySinceMs = -1;
            clearPending();
            return acceptIfFar(fix, distance, requiredDistance);
        }

        if (distance >= confirmRadius(fix)) {
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
            if (pendingDistance > Math.max(4.0d, requiredDistance * 2.0d)) {
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
            moving = true;
            stationarySinceMs = -1;
            return acceptIfFar(fix, distance, requiredDistance);
        }

        clearPending();
        return false;
    }

    private boolean acceptIfFar(RawFix fix, double distance, double requiredDistance) {
        if (distance < requiredDistance) {
            return false;
        }
        lastAccepted = fix;
        return true;
    }

    private void remember(RawFix fix) {
        window.addLast(fix);
        while (!window.isEmpty()
                && window.peekFirst().getTimestampEpochMs()
                < fix.getTimestampEpochMs() - WINDOW_MS - 1_000L) {
            window.removeFirst();
        }
    }

    private Window windowStats(RawFix fix) {
        Window stats = new Window();
        if (window.isEmpty()
                || window.peekFirst().getTimestampEpochMs()
                > fix.getTimestampEpochMs() - WINDOW_READY_MS) {
            return stats;
        }

        long cutoff = fix.getTimestampEpochMs() - WINDOW_MS;
        RawFix first = null;
        RawFix previous = null;
        double pathMeters = 0;
        for (RawFix point : window) {
            if (point.getTimestampEpochMs() < cutoff) {
                continue;
            }
            if (first == null) {
                first = point;
            }
            if (previous != null) {
                pathMeters += GeoMath.distanceMeters(
                        previous.getLatitude(),
                        previous.getLongitude(),
                        point.getLatitude(),
                        point.getLongitude()
                );
            }
            previous = point;
        }
        if (first == null || previous == null) {
            return stats;
        }

        stats.ready = true;
        stats.net = GeoMath.distanceMeters(
                first.getLatitude(),
                first.getLongitude(),
                previous.getLatitude(),
                previous.getLongitude()
        );
        stats.straightness = pathMeters > 0 ? stats.net / pathMeters : 0;
        return stats;
    }

    private double requiredDistance(RawFix fix) {
        double accuracy = currentAccuracy(fix);
        if (!RawFix.isFinite(accuracy) || accuracy <= 0) {
            return MIN_SEGMENT_METERS;
        }
        return Math.min(
                MAX_SEGMENT_METERS,
                Math.max(MIN_SEGMENT_METERS, accuracy * SEGMENT_ACCURACY_FACTOR)
        );
    }

    private double confirmRadius(RawFix fix) {
        double accuracy = currentAccuracy(fix);
        if (!RawFix.isFinite(accuracy) || accuracy <= 0) {
            return MIN_CONFIRM_RADIUS_METERS;
        }
        return Math.min(
                MAX_CONFIRM_RADIUS_METERS,
                Math.max(MIN_CONFIRM_RADIUS_METERS, accuracy * CONFIRM_ACCURACY_FACTOR)
        );
    }

    private double motionRadius(RawFix fix) {
        double accuracy = currentAccuracy(fix);
        if (!RawFix.isFinite(accuracy) || accuracy <= 0) {
            return MIN_MOTION_RADIUS_METERS;
        }
        return Math.min(
                MAX_MOTION_RADIUS_METERS,
                Math.max(MIN_MOTION_RADIUS_METERS, accuracy * MOTION_ACCURACY_FACTOR)
        );
    }

    private double currentAccuracy(RawFix fix) {
        return fix.hasAccuracy()
                ? fix.getAccuracyMeters()
                : lastAccepted.getAccuracyMeters();
    }

    private void clearPending() {
        pending = null;
        pendingConfirmationCount = 0;
    }

    public void reset() {
        lastAccepted = null;
        clearPending();
        window.clear();
        moving = false;
        stationarySinceMs = -1;
    }

    /**
     * Restores the filter anchor when resuming a persisted session.
     */
    public void restoreLast(RawFix fix) {
        lastAccepted = fix;
        clearPending();
        window.clear();
        window.addLast(fix);
        moving = fix.hasSpeed() && fix.getSpeedMps() >= SPEED_MOVING_MPS;
        stationarySinceMs = -1;
    }

    public RawFix getLastAccepted() {
        return lastAccepted;
    }

    private static final class Window {
        boolean ready;
        double net;
        double straightness;
    }
}
