package com.openrun.core.stats;

import com.openrun.core.model.ActivityType;
import com.openrun.core.model.RawFix;
import com.openrun.core.model.RecordingConfig;
import com.openrun.core.model.SessionSummary;
import com.openrun.core.model.Split;
import com.openrun.core.model.TrackPoint;
import com.openrun.core.model.TrackingSnapshot;
import com.openrun.core.model.TrackingState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Platform-independent recording engine.
 *
 * <p>The Android service feeds location fixes and persists the accepted points.
 * Keeping this class free of Android types makes the whole tracking behaviour
 * testable on the JVM.</p>
 */
public final class TrackingEngine {
    private static final long PACE_WINDOW_MS = 30_000L;
    private static final long SIGNAL_LOST_MS = 15_000L;
    private static final double AUTO_PAUSE_SPEED_MPS = 0.5d;
    private static final double AUTO_RESUME_SPEED_MPS = 1.0d;
    private static final long AUTO_PAUSE_AFTER_MS = 15_000L;
    private static final long AUTO_RESUME_AFTER_MS = 5_000L;
    private static final double SPLIT_DISTANCE_METERS = 1000.0d;

    private final List<TrackPoint> points = new ArrayList<>();
    private final Deque<TrackPoint> recentPoints = new ArrayDeque<>();
    private final List<Split> splits = new ArrayList<>();

    private RecordingConfig config;
    private TrackFilter filter;

    private TrackingState state = TrackingState.IDLE;
    private long sessionStartElapsedMs;
    private long sessionStartEpochMs;
    private long recordingStartElapsedMs = -1;
    private long recordingStartEpochMs;
    private long pausedAtElapsedMs = -1;
    private long totalPausedMs;
    private long lastFixEpochMs;
    private double lastAccuracyMeters = Double.NaN;

    private TrackPoint lastPoint;
    private double distanceMeters;
    private double elevationGainMeters;
    private double elevationLossMeters;
    private double smoothedAltitude = Double.NaN;
    private double lastElevationAnchor = Double.NaN;
    private double lastSegmentSpeedMps;
    private double smoothedSpeedMps = Double.NaN;
    private int nextSplitIndex = 1;
    private double nextSplitAtMeters = SPLIT_DISTANCE_METERS;
    private long lastSplitMovingMs;
    private boolean autoPaused;
    private long slowSinceElapsedMs = -1;
    private long fastSinceElapsedMs = -1;
    private SessionSummary summary;

    public synchronized void start(RecordingConfig config, long nowElapsedMs, long nowEpochMs) {
        reset();
        this.config = config;
        this.filter = new TrackFilter(config);
        this.state = TrackingState.ACQUIRING;
        this.sessionStartElapsedMs = nowElapsedMs;
        this.sessionStartEpochMs = nowEpochMs;
    }

    /**
     * Rehydrates the engine after process death. Points and splits come from
     * Room; accumulated distance and elevation come from the persisted session.
     */
    public synchronized void restore(RecordingConfig config,
                                     List<TrackPoint> restoredPoints,
                                     List<Split> restoredSplits,
                                     TrackingState restoredState,
                                     long sessionStartElapsedMs,
                                     long sessionStartEpochMs,
                                     long recordingStartElapsedMs,
                                     long recordingStartEpochMs,
                                     long totalPausedMs,
                                     double distanceMeters,
                                     double elevationGainMeters,
                                     double elevationLossMeters,
                                     long nowElapsedMs,
                                     boolean autoPaused) {
        reset();
        this.config = config;
        this.filter = new TrackFilter(config);
        this.state = restoredState;
        this.sessionStartElapsedMs = sessionStartElapsedMs;
        this.sessionStartEpochMs = sessionStartEpochMs;
        this.recordingStartElapsedMs = recordingStartElapsedMs;
        this.recordingStartEpochMs = recordingStartEpochMs;
        this.totalPausedMs = Math.max(0, totalPausedMs);
        this.distanceMeters = Math.max(0, distanceMeters);
        this.elevationGainMeters = Math.max(0, elevationGainMeters);
        this.elevationLossMeters = Math.max(0, elevationLossMeters);
        this.autoPaused = autoPaused && restoredState == TrackingState.PAUSED;
        this.pausedAtElapsedMs = restoredState == TrackingState.PAUSED
                ? nowElapsedMs
                : -1;

        if (restoredPoints != null) {
            points.addAll(restoredPoints);
        }
        if (restoredSplits != null) {
            splits.addAll(restoredSplits);
        }

        if (!points.isEmpty()) {
            TrackPoint last = points.get(points.size() - 1);
            lastPoint = last;
            lastFixEpochMs = last.getTimestampEpochMs();
            lastAccuracyMeters = last.getAccuracyMeters();
            lastSegmentSpeedMps = finite(last.getSpeedMps()) ? last.getSpeedMps() : 0;
            if (finite(last.getSpeedMps())) {
                smoothedSpeedMps = last.getSpeedMps();
            }
            filter.restoreLast(new RawFix(
                    last.getLatitude(),
                    last.getLongitude(),
                    last.getAltitudeMeters(),
                    last.getAccuracyMeters(),
                    last.getSpeedMps(),
                    last.getBearingDegrees(),
                    last.getTimestampEpochMs()
            ));
            if (finite(last.getAltitudeMeters())) {
                smoothedAltitude = last.getAltitudeMeters();
                lastElevationAnchor = smoothedAltitude;
            }
            for (int i = points.size() - 1; i >= 0; i--) {
                TrackPoint point = points.get(i);
                if (point.getElapsedMs() < last.getElapsedMs() - PACE_WINDOW_MS) {
                    break;
                }
                recentPoints.addFirst(point);
            }

            if (recordingStartElapsedMs <= 0) {
                this.recordingStartElapsedMs = Math.max(
                        0,
                        nowElapsedMs - last.getElapsedMs()
                );
            }
            if (recordingStartEpochMs <= 0) {
                this.recordingStartEpochMs = last.getTimestampEpochMs()
                        - last.getElapsedMs();
            }
        } else if (recordingStartElapsedMs <= 0) {
            this.recordingStartElapsedMs = -1;
        }

        long splitMoving = 0;
        for (Split split : splits) {
            splitMoving += Math.max(0, split.getDurationMs());
        }
        lastSplitMovingMs = splitMoving;
        nextSplitIndex = splits.size() + 1;
        nextSplitAtMeters = nextSplitIndex * SPLIT_DISTANCE_METERS;
        summary = null;
    }

    /**
     * Starts the clock even if the GPS has not produced a good fix yet.
     * The next valid fix is accepted regardless of its accuracy.
     */
    public synchronized void forceStart(long nowElapsedMs, long nowEpochMs) {
        if (state != TrackingState.ACQUIRING) {
            return;
        }
        state = TrackingState.RECORDING;
        recordingStartElapsedMs = nowElapsedMs;
        recordingStartEpochMs = nowEpochMs;
    }

    public synchronized TrackingSnapshot onFix(RawFix fix, long nowElapsedMs) {
        if (config == null || !state.isActive() || fix == null) {
            return snapshot(nowElapsedMs, System.currentTimeMillis());
        }

        lastFixEpochMs = fix.getTimestampEpochMs();
        lastAccuracyMeters = fix.getAccuracyMeters();
        updateSmoothedSpeed(fix);

        if (state == TrackingState.PAUSED) {
            maybeAutoResume(fix, nowElapsedMs);
            return snapshot(nowElapsedMs, fix.getTimestampEpochMs());
        }

        if (state == TrackingState.ACQUIRING) {
            boolean accurateEnough = fix.hasAccuracy()
                    && fix.getAccuracyMeters() <= config.getWarmupAccuracyMeters();
            if (!accurateEnough) {
                return snapshot(nowElapsedMs, fix.getTimestampEpochMs());
            }
            state = TrackingState.RECORDING;
            recordingStartElapsedMs = nowElapsedMs;
            recordingStartEpochMs = fix.getTimestampEpochMs();
        }

        if (state == TrackingState.RECORDING) {
            if (!filter.accept(fix, false)) {
                return snapshot(nowElapsedMs, fix.getTimestampEpochMs());
            }
            appendPoint(fix, nowElapsedMs);
            maybeAutoPause(fix, nowElapsedMs);
        }

        return snapshot(nowElapsedMs, fix.getTimestampEpochMs());
    }

    public synchronized void pause(long nowElapsedMs) {
        pauseInternal(nowElapsedMs, false);
    }

    public synchronized void resume(long nowElapsedMs) {
        if (state != TrackingState.PAUSED) {
            return;
        }
        if (pausedAtElapsedMs >= 0) {
            totalPausedMs += Math.max(0, nowElapsedMs - pausedAtElapsedMs);
        }
        pausedAtElapsedMs = -1;
        state = TrackingState.RECORDING;
        autoPaused = false;
        slowSinceElapsedMs = -1;
        fastSinceElapsedMs = -1;
        filter.reset();
        lastPoint = null;
        lastSegmentSpeedMps = 0;
        smoothedSpeedMps = Double.NaN;
        recentPoints.clear();
    }

    public synchronized SessionSummary finish(long nowElapsedMs, long nowEpochMs) {
        if (summary != null) {
            return summary;
        }
        if (!state.isActive() || config == null) {
            return null;
        }

        long pausedMs = totalPausedMs;
        if (state == TrackingState.PAUSED && pausedAtElapsedMs >= 0) {
            pausedMs += Math.max(0, nowElapsedMs - pausedAtElapsedMs);
        }
        long movingMs = computeMovingMs(nowElapsedMs);
        double avgPace = GeoMath.paceSecPerKm(distanceMeters, movingMs);
        summary = new SessionSummary(
                config.getType(),
                startedAtEpochMs(),
                nowEpochMs,
                movingMs,
                pausedMs,
                distanceMeters,
                elevationGainMeters,
                elevationLossMeters,
                avgPace,
                bestSplitPace(),
                points.size(),
                splits
        );
        state = TrackingState.FINISHED;
        pausedAtElapsedMs = -1;
        return summary;
    }

    public synchronized void discard() {
        reset();
        state = TrackingState.DISCARDED;
        sessionStartEpochMs = 0;
        sessionStartElapsedMs = 0;
    }

    public synchronized TrackingSnapshot snapshot(long nowElapsedMs, long nowEpochMs) {
        if (config == null || state == TrackingState.IDLE || state == TrackingState.DISCARDED) {
            return TrackingSnapshot.idle(config == null ? ActivityType.RUN : config.getType());
        }

        long movingMs = state == TrackingState.FINISHED && summary != null
                ? summary.getMovingTimeMs()
                : computeMovingMs(nowElapsedMs);
        long pausedMs = state == TrackingState.FINISHED && summary != null
                ? summary.getPausedTimeMs()
                : computePausedMs(nowElapsedMs);
        double currentPace = state == TrackingState.RECORDING ? computeCurrentPace() : 0;
        if (state == TrackingState.RECORDING && !Double.isNaN(smoothedSpeedMps)) {
            double dopplerPace = 1000.0d / smoothedSpeedMps;
            if (dopplerPace >= 120.0d && dopplerPace <= 1800.0d) {
                currentPace = dopplerPace;
            }
        }
        double averagePace = GeoMath.paceSecPerKm(distanceMeters, movingMs);
        boolean signalLost = state == TrackingState.RECORDING
                && lastFixEpochMs > 0
                && nowEpochMs - lastFixEpochMs > SIGNAL_LOST_MS;

        return new TrackingSnapshot(
                state,
                config.getType(),
                movingMs,
                pausedMs,
                distanceMeters,
                currentPace,
                averagePace,
                elevationGainMeters,
                elevationLossMeters,
                lastAccuracyMeters,
                signalLost,
                points.size(),
                lastPoint == null ? Double.NaN : lastPoint.getLatitude(),
                lastPoint == null ? Double.NaN : lastPoint.getLongitude(),
                lastFixEpochMs,
                autoPaused,
                splits,
                startedAtEpochMs()
        );
    }

    public synchronized TrackingState getState() {
        return state;
    }

    public synchronized boolean isActive() {
        return state.isActive();
    }

    public synchronized ActivityType getType() {
        return config == null ? ActivityType.RUN : config.getType();
    }

    public synchronized RecordingConfig getConfig() {
        return config;
    }

    public synchronized long getSessionStartElapsedMs() {
        return sessionStartElapsedMs;
    }

    public synchronized List<TrackPoint> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public synchronized List<Split> getSplits() {
        return Collections.unmodifiableList(splits);
    }

    public synchronized long getRecordingStartElapsedMs() {
        return recordingStartElapsedMs;
    }

    public synchronized long getRecordingStartEpochMs() {
        return recordingStartEpochMs;
    }

    public synchronized long getTotalPausedMs() {
        return totalPausedMs;
    }

    public synchronized long getPausedAtElapsedMs() {
        return pausedAtElapsedMs;
    }

    public synchronized TrackPoint getLastAcceptedPoint() {
        return lastPoint;
    }

    public synchronized SessionSummary getSummary() {
        return summary;
    }

    private void appendPoint(RawFix fix, long nowElapsedMs) {
        long elapsedMs = Math.max(0, nowElapsedMs - recordingStartElapsedMs);
        double segmentMeters;
        long segmentMs;

        if (lastPoint != null) {
            segmentMeters = GeoMath.distanceMeters(
                    lastPoint.getLatitude(),
                    lastPoint.getLongitude(),
                    fix.getLatitude(),
                    fix.getLongitude()
            );
            segmentMs = Math.max(1, elapsedMs - lastPoint.getElapsedMs());
            distanceMeters += segmentMeters;
            lastSegmentSpeedMps = segmentMeters / (segmentMs / 1000.0d);
        }

        updateElevation(fix);

        long movingMs = computeMovingMs(nowElapsedMs);
        TrackPoint point = new TrackPoint(
                points.size(),
                fix.getLatitude(),
                fix.getLongitude(),
                fix.getAltitudeMeters(),
                fix.getAccuracyMeters(),
                fix.getSpeedMps(),
                fix.getBearingDegrees(),
                fix.getTimestampEpochMs(),
                elapsedMs,
                movingMs
        );
        points.add(point);
        recentPoints.addLast(point);
        pruneRecentPoints(point.getElapsedMs());

        while (distanceMeters >= nextSplitAtMeters) {
            long splitDuration = Math.max(0, movingMs - lastSplitMovingMs);
            splits.add(new Split(nextSplitIndex, SPLIT_DISTANCE_METERS, splitDuration));
            lastSplitMovingMs = movingMs;
            nextSplitIndex++;
            nextSplitAtMeters += SPLIT_DISTANCE_METERS;
        }

        lastPoint = point;
    }

    private void updateSmoothedSpeed(RawFix fix) {
        if (!fix.hasSpeed() || config == null) {
            return;
        }
        double speed = fix.getSpeedMps();
        if (speed < 0 || speed > config.getMaxSpeedMps()) {
            return;
        }
        if (Double.isNaN(smoothedSpeedMps)) {
            smoothedSpeedMps = speed;
        } else {
            smoothedSpeedMps = smoothedSpeedMps + 0.25d * (speed - smoothedSpeedMps);
        }
    }

    private void pruneRecentPoints(long currentElapsedMs) {
        while (recentPoints.size() > 2
                && recentPoints.peekFirst() != null
                && recentPoints.peekFirst().getElapsedMs() < currentElapsedMs - PACE_WINDOW_MS) {
            recentPoints.removeFirst();
        }
    }

    private void updateElevation(RawFix fix) {
        if (!fix.hasAltitude()) {
            return;
        }
        double altitude = fix.getAltitudeMeters();
        if (Double.isNaN(smoothedAltitude)) {
            smoothedAltitude = altitude;
            lastElevationAnchor = altitude;
            return;
        }

        smoothedAltitude = smoothedAltitude + 0.2d * (altitude - smoothedAltitude);
        double delta = smoothedAltitude - lastElevationAnchor;
        if (delta > 10.0d || delta < -10.0d) {
            // A jump this large is almost certainly GPS noise.
            lastElevationAnchor = smoothedAltitude;
            return;
        }
        if (delta >= 1.0d) {
            elevationGainMeters += delta;
            lastElevationAnchor = smoothedAltitude;
        } else if (delta <= -1.0d) {
            elevationLossMeters += -delta;
            lastElevationAnchor = smoothedAltitude;
        }
    }

    private void maybeAutoPause(RawFix fix, long nowElapsedMs) {
        if (!config.isAutoPauseEnabled()) {
            return;
        }
        double speed = fix.hasSpeed() ? fix.getSpeedMps() : lastSegmentSpeedMps;
        if (speed < AUTO_PAUSE_SPEED_MPS) {
            if (slowSinceElapsedMs < 0) {
                slowSinceElapsedMs = nowElapsedMs;
            } else if (nowElapsedMs - slowSinceElapsedMs >= AUTO_PAUSE_AFTER_MS) {
                pauseInternal(nowElapsedMs, true);
            }
        } else {
            slowSinceElapsedMs = -1;
        }
    }

    private void maybeAutoResume(RawFix fix, long nowElapsedMs) {
        if (!autoPaused || !config.isAutoPauseEnabled()) {
            return;
        }
        double speed = fix.hasSpeed() ? fix.getSpeedMps() : 0;
        if (speed > AUTO_RESUME_SPEED_MPS) {
            if (fastSinceElapsedMs < 0) {
                fastSinceElapsedMs = nowElapsedMs;
            } else if (nowElapsedMs - fastSinceElapsedMs >= AUTO_RESUME_AFTER_MS) {
                resume(nowElapsedMs);
            }
        } else {
            fastSinceElapsedMs = -1;
        }
    }

    private void pauseInternal(long nowElapsedMs, boolean automatic) {
        if (state != TrackingState.RECORDING) {
            return;
        }
        state = TrackingState.PAUSED;
        pausedAtElapsedMs = nowElapsedMs;
        autoPaused = automatic;
        slowSinceElapsedMs = -1;
        fastSinceElapsedMs = -1;
    }

    private long computeMovingMs(long nowElapsedMs) {
        if (state == TrackingState.FINISHED && summary != null) {
            return summary.getMovingTimeMs();
        }
        if (state == TrackingState.ACQUIRING || recordingStartElapsedMs < 0) {
            return 0;
        }
        long reference = state == TrackingState.PAUSED && pausedAtElapsedMs >= 0
                ? pausedAtElapsedMs
                : nowElapsedMs;
        return Math.max(0, reference - recordingStartElapsedMs - totalPausedMs);
    }

    private long computePausedMs(long nowElapsedMs) {
        if (state == TrackingState.FINISHED && summary != null) {
            return summary.getPausedTimeMs();
        }
        if (state == TrackingState.PAUSED && pausedAtElapsedMs >= 0) {
            return totalPausedMs + Math.max(0, nowElapsedMs - pausedAtElapsedMs);
        }
        return totalPausedMs;
    }

    private double computeCurrentPace() {
        if (recentPoints.size() < 2) {
            return 0;
        }
        TrackPoint first = recentPoints.peekFirst();
        TrackPoint last = recentPoints.peekLast();
        if (first == null || last == null) {
            return 0;
        }
        long elapsedMs = last.getElapsedMs() - first.getElapsedMs();
        if (elapsedMs <= 0) {
            return 0;
        }
        double distance = 0;
        TrackPoint previous = null;
        for (TrackPoint point : recentPoints) {
            if (previous != null) {
                distance += GeoMath.distanceMeters(
                        previous.getLatitude(),
                        previous.getLongitude(),
                        point.getLatitude(),
                        point.getLongitude()
                );
            }
            previous = point;
        }
        if (distance < 5.0d) {
            return 0;
        }
        double speed = distance / (elapsedMs / 1000.0d);
        if (speed < 0.3d) {
            return 0;
        }
        double pace = 1000.0d / speed;
        if (pace < 120.0d || pace > 1800.0d) {
            return 0;
        }
        return pace;
    }

    private long startedAtEpochMs() {
        return recordingStartEpochMs > 0 ? recordingStartEpochMs : sessionStartEpochMs;
    }

    private boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private double bestSplitPace() {
        double best = 0;
        for (Split split : splits) {
            double pace = split.getPaceSecPerKm();
            if (pace > 0 && (best == 0 || pace < best)) {
                best = pace;
            }
        }
        return best;
    }

    private void reset() {
        points.clear();
        recentPoints.clear();
        splits.clear();
        config = null;
        filter = null;
        state = TrackingState.IDLE;
        sessionStartElapsedMs = 0;
        sessionStartEpochMs = 0;
        recordingStartElapsedMs = -1;
        recordingStartEpochMs = 0;
        pausedAtElapsedMs = -1;
        totalPausedMs = 0;
        lastFixEpochMs = 0;
        lastAccuracyMeters = Double.NaN;
        lastPoint = null;
        distanceMeters = 0;
        elevationGainMeters = 0;
        elevationLossMeters = 0;
        smoothedAltitude = Double.NaN;
        lastElevationAnchor = Double.NaN;
        lastSegmentSpeedMps = 0;
        smoothedSpeedMps = Double.NaN;
        nextSplitIndex = 1;
        nextSplitAtMeters = SPLIT_DISTANCE_METERS;
        lastSplitMovingMs = 0;
        autoPaused = false;
        slowSinceElapsedMs = -1;
        fastSinceElapsedMs = -1;
        summary = null;
    }
}
