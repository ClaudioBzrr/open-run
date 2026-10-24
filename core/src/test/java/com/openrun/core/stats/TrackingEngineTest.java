package com.openrun.core.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.openrun.core.model.ActivityType;
import com.openrun.core.model.RawFix;
import com.openrun.core.model.RecordingConfig;
import com.openrun.core.model.SessionSummary;
import com.openrun.core.model.Split;
import com.openrun.core.model.TrackPoint;
import com.openrun.core.model.TrackingSnapshot;
import com.openrun.core.model.TrackingState;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TrackingEngineTest {
    private static final long START_ELAPSED = 10_000L;
    private static final long START_EPOCH = 1_700_000_000_000L;
    private static final double METERS_PER_DEGREE_LAT = 111_195.0d;

    private TrackingEngine newEngine(RecordingConfig config) {
        TrackingEngine engine = new TrackingEngine();
        engine.start(config, START_ELAPSED, START_EPOCH);
        return engine;
    }

    private RawFix fix(double latitude, double longitude, double accuracy, long epochMs) {
        return new RawFix(latitude, longitude, 760.0d, accuracy, 3.0d, 0.0d, epochMs);
    }

    @Test
    public void accumulatesDistanceAndMovingTime() {
        RecordingConfig config = new RecordingConfig(ActivityType.RUN, 30, 20, 15.0d, false);
        TrackingEngine engine = newEngine(config);

        double latitude = -23.550520d;
        double longitude = -46.633308d;
        engine.onFix(fix(latitude, longitude, 5, START_EPOCH), START_ELAPSED);

        for (int second = 1; second <= 10; second++) {
            latitude += 3.0d / METERS_PER_DEGREE_LAT;
            engine.onFix(
                    fix(latitude, longitude, 5, START_EPOCH + second * 1000L),
                    START_ELAPSED + second * 1000L
            );
        }

        TrackingSnapshot snapshot = engine.snapshot(START_ELAPSED + 10_000L, START_EPOCH + 10_000L);
        assertEquals(TrackingState.RECORDING, snapshot.getState());
        assertEquals(10_000L, snapshot.getMovingTimeMs());
        assertEquals(30.0d, snapshot.getDistanceMeters(), 1.0d);
        assertEquals(11, snapshot.getPointCount());
        assertEquals(ActivityType.RUN, snapshot.getType());
    }

    @Test
    public void ignoresBadAccuracyUntilAGoodFixArrives() {
        RecordingConfig config = new RecordingConfig(ActivityType.RUN, 30, 20, 15.0d, false);
        TrackingEngine engine = newEngine(config);

        engine.onFix(fix(-23.55d, -46.63d, 80, START_EPOCH), START_ELAPSED);
        TrackingSnapshot acquiring = engine.snapshot(START_ELAPSED + 1000L, START_EPOCH + 1000L);
        assertEquals(TrackingState.ACQUIRING, acquiring.getState());
        assertEquals(0, acquiring.getPointCount());

        engine.onFix(fix(-23.55d, -46.63d, 8, START_EPOCH + 2000L), START_ELAPSED + 2000L);
        TrackingSnapshot recording = engine.snapshot(START_ELAPSED + 2000L, START_EPOCH + 2000L);
        assertEquals(TrackingState.RECORDING, recording.getState());
        assertEquals(1, recording.getPointCount());
    }

    @Test
    public void startsRecordingOnAFixWithinTheDefaultAccuracyLimit() {
        TrackingEngine engine = newEngine(RecordingConfig.defaults(ActivityType.RUN));

        engine.onFix(fix(-23.55d, -46.63d, 20.0d, START_EPOCH), START_ELAPSED);

        TrackingSnapshot snapshot = engine.snapshot(
                START_ELAPSED + 1_000L,
                START_EPOCH + 1_000L
        );
        assertEquals(TrackingState.RECORDING, snapshot.getState());
        assertEquals(1, snapshot.getPointCount());
    }

    @Test
    public void curvedRouteWithMissingDopplerSpeedKeepsItsDistance() {
        RecordingConfig config = new RecordingConfig(
                ActivityType.RUN,
                30,
                20,
                15.0d,
                false
        );
        TrackingEngine engine = newEngine(config);

        double radiusMeters = 50.0d;
        double stepMeters = 2.0d;
        double stepRadians = stepMeters / radiusMeters;
        double metersPerDegreeLon = METERS_PER_DEGREE_LAT
                * Math.cos(Math.toRadians(-23.55d));
        double previousLatitude = 0;
        double previousLongitude = 0;
        double trueMeters = 0;
        int steps = 120;

        for (int step = 0; step <= steps; step++) {
            double angle = step * stepRadians;
            double latitude = -23.55d
                    + radiusMeters * Math.cos(angle) / METERS_PER_DEGREE_LAT;
            double longitude = -46.63d
                    + radiusMeters * Math.sin(angle) / metersPerDegreeLon;

            if (step > 0) {
                trueMeters += GeoMath.distanceMeters(
                        previousLatitude,
                        previousLongitude,
                        latitude,
                        longitude
                );
            }
            previousLatitude = latitude;
            previousLongitude = longitude;

            engine.onFix(
                    new RawFix(
                            latitude,
                            longitude,
                            760.0d,
                            5.0d,
                            Double.NaN,
                            0.0d,
                            START_EPOCH + step * 1_000L
                    ),
                    START_ELAPSED + step * 1_000L
            );
        }

        TrackingSnapshot snapshot = engine.snapshot(
                START_ELAPSED + steps * 1_000L,
                START_EPOCH + steps * 1_000L
        );
        assertTrue(trueMeters > 200.0d);
        assertTrue(
                "curved track lost too much distance: "
                        + snapshot.getDistanceMeters() + " of " + trueMeters,
                snapshot.getDistanceMeters() > trueMeters * 0.95d
        );
    }

    @Test
    public void rejectsImpossibleSpeedJumps() {
        RecordingConfig config = new RecordingConfig(ActivityType.RUN, 30, 20, 15.0d, false);
        TrackingEngine engine = newEngine(config);

        engine.onFix(fix(-23.55d, -46.63d, 5, START_EPOCH), START_ELAPSED);
        // ~111 metres in one second is not running.
        engine.onFix(fix(-23.549d, -46.63d, 5, START_EPOCH + 1000L), START_ELAPSED + 1000L);

        TrackingSnapshot snapshot = engine.snapshot(START_ELAPSED + 1000L, START_EPOCH + 1000L);
        assertEquals(1, snapshot.getPointCount());
        assertEquals(0.0d, snapshot.getDistanceMeters(), 0.01d);
    }

    @Test
    public void pauseExcludesPausedTimeFromMovingTime() {
        RecordingConfig config = new RecordingConfig(ActivityType.RUN, 30, 20, 15.0d, false);
        TrackingEngine engine = newEngine(config);

        double latitude = -23.55d;
        engine.onFix(fix(latitude, -46.63d, 5, START_EPOCH), START_ELAPSED);
        latitude += 3.0d / METERS_PER_DEGREE_LAT;
        engine.onFix(fix(latitude, -46.63d, 5, START_EPOCH + 1000L), START_ELAPSED + 1000L);
        latitude += 3.0d / METERS_PER_DEGREE_LAT;
        engine.onFix(fix(latitude, -46.63d, 5, START_EPOCH + 2000L), START_ELAPSED + 2000L);

        engine.pause(START_ELAPSED + 3000L);
        engine.resume(START_ELAPSED + 10_000L);
        engine.onFix(fix(latitude, -46.63d, 5, START_EPOCH + 11_000L), START_ELAPSED + 11_000L);

        TrackingSnapshot snapshot = engine.snapshot(START_ELAPSED + 12_000L, START_EPOCH + 12_000L);
        assertEquals(5_000L, snapshot.getMovingTimeMs());
        assertEquals(7_000L, snapshot.getPausedTimeMs());
        assertEquals(6.0d, snapshot.getDistanceMeters(), 0.5d);
    }

    @Test
    public void forceStartStillWaitsForAGoodFix() {
        RecordingConfig config = new RecordingConfig(ActivityType.RUN, 30, 20, 15.0d, false);
        TrackingEngine engine = newEngine(config);

        engine.forceStart(START_ELAPSED + 30_000L, START_EPOCH + 30_000L);
        engine.onFix(fix(-23.55d, -46.63d, 80, START_EPOCH + 31_000L), START_ELAPSED + 31_000L);

        TrackingSnapshot poorFix = engine.snapshot(
                START_ELAPSED + 31_000L,
                START_EPOCH + 31_000L
        );
        assertEquals(TrackingState.RECORDING, poorFix.getState());
        assertEquals(0, poorFix.getPointCount());

        engine.onFix(fix(-23.55d, -46.63d, 10, START_EPOCH + 32_000L), START_ELAPSED + 32_000L);
        TrackingSnapshot goodFix = engine.snapshot(
                START_ELAPSED + 32_000L,
                START_EPOCH + 32_000L
        );
        assertEquals(1, goodFix.getPointCount());
    }

    @Test
    public void createsKilometreSplits() {
        RecordingConfig config = new RecordingConfig(ActivityType.RUN, 30, 20, 20.0d, false);
        TrackingEngine engine = newEngine(config);

        double latitude = -23.55d;
        engine.onFix(fix(latitude, -46.63d, 5, START_EPOCH), START_ELAPSED);
        for (int second = 1; second <= 105; second++) {
            latitude += 10.0d / METERS_PER_DEGREE_LAT;
            engine.onFix(
                    fix(latitude, -46.63d, 5, START_EPOCH + second * 1000L),
                    START_ELAPSED + second * 1000L
            );
        }

        TrackingSnapshot snapshot = engine.snapshot(START_ELAPSED + 105_000L, START_EPOCH + 105_000L);
        assertEquals(1, snapshot.getSplits().size());
        assertEquals(1, snapshot.getSplits().get(0).getIndex());
    }

    @Test
    public void finishProducesSummaryAndFreezesState() {
        RecordingConfig config = new RecordingConfig(ActivityType.WALK, 30, 20, 8.0d, false);
        TrackingEngine engine = newEngine(config);

        double latitude = -23.55d;
        engine.onFix(fix(latitude, -46.63d, 5, START_EPOCH), START_ELAPSED);
        latitude += 2.0d / METERS_PER_DEGREE_LAT;
        engine.onFix(fix(latitude, -46.63d, 5, START_EPOCH + 1000L), START_ELAPSED + 1000L);

        SessionSummary summary = engine.finish(START_ELAPSED + 2000L, START_EPOCH + 2000L);

        assertNotNull(summary);
        assertEquals(ActivityType.WALK, summary.getType());
        assertEquals(2_000L, summary.getMovingTimeMs());
        assertEquals(2.0d, summary.getDistanceMeters(), 0.5d);
        assertEquals(TrackingState.FINISHED, engine.getState());
    }

    @Test
    public void stationaryJitterDoesNotAccumulateDistance() {
        RecordingConfig config = new RecordingConfig(
                ActivityType.RUN,
                30,
                20,
                15.0d,
                false
        );
        TrackingEngine engine = newEngine(config);
        double latitude = -23.55d;
        engine.onFix(stationaryFix(latitude, 5.0d, 0.05d, 0), START_ELAPSED);

        for (int second = 1; second <= 12; second++) {
            double jitter = ((second % 3) - 1) * 3.0d / METERS_PER_DEGREE_LAT;
            engine.onFix(
                    stationaryFix(latitude + jitter, 5.0d, 0.05d, second),
                    START_ELAPSED + second * 1000L
            );
        }

        TrackingSnapshot stationary = engine.snapshot(
                START_ELAPSED + 12_000L,
                START_EPOCH + 12_000L
        );
        assertEquals(1, stationary.getPointCount());
        assertEquals(0.0d, stationary.getDistanceMeters(), 0.01d);

        engine.onFix(
                stationaryFix(
                        latitude + 10.0d / METERS_PER_DEGREE_LAT,
                        5.0d,
                        2.0d,
                        13
                ),
                START_ELAPSED + 13_000L
        );
        TrackingSnapshot moving = engine.snapshot(
                START_ELAPSED + 13_000L,
                START_EPOCH + 13_000L
        );
        assertEquals(2, moving.getPointCount());
        assertEquals(10.0d, moving.getDistanceMeters(), 0.5d);
    }

    private RawFix stationaryFix(double latitude,
                                 double accuracy,
                                 double speed,
                                 long second) {
        return new RawFix(
                latitude,
                -46.63d,
                760.0d,
                accuracy,
                speed,
                0.0d,
                START_EPOCH + second * 1000L
        );
    }

    @Test
    public void movingElapsedIsStoredPerPointAndEngineCanBeRestored() {
        RecordingConfig config = new RecordingConfig(
                ActivityType.RUN,
                30,
                20,
                15.0d,
                false
        );
        TrackingEngine engine = newEngine(config);
        double latitude = -23.55d;
        engine.onFix(fix(latitude, -46.63d, 5, START_EPOCH), START_ELAPSED);
        latitude += 3.0d / METERS_PER_DEGREE_LAT;
        engine.onFix(
                fix(latitude, -46.63d, 5, START_EPOCH + 1000L),
                START_ELAPSED + 1000L
        );
        latitude += 3.0d / METERS_PER_DEGREE_LAT;
        engine.onFix(
                fix(latitude, -46.63d, 5, START_EPOCH + 2000L),
                START_ELAPSED + 2000L
        );
        engine.pause(START_ELAPSED + 3000L);
        engine.resume(START_ELAPSED + 10_000L);
        latitude += 3.0d / METERS_PER_DEGREE_LAT;
        engine.onFix(
                fix(latitude, -46.63d, 5, START_EPOCH + 11_000L),
                START_ELAPSED + 11_000L
        );
        SessionSummary summary = engine.finish(
                START_ELAPSED + 12_000L,
                START_EPOCH + 12_000L
        );
        List<TrackPoint> points = new ArrayList<>(engine.getPoints());
        List<Split> splits = new ArrayList<>(engine.getSplits());
        assertTrue(points.get(points.size() - 1).getMovingElapsedMs()
                < points.get(points.size() - 1).getElapsedMs());

        TrackingEngine restored = new TrackingEngine();
        restored.restore(
                config,
                points,
                splits,
                TrackingState.RECORDING,
                START_ELAPSED,
                START_EPOCH,
                START_ELAPSED,
                START_EPOCH,
                summary.getPausedTimeMs(),
                summary.getDistanceMeters(),
                summary.getElevationGainMeters(),
                summary.getElevationLossMeters(),
                START_ELAPSED + 12_000L,
                false
        );
        TrackingSnapshot snapshot = restored.snapshot(
                START_ELAPSED + 12_000L,
                START_EPOCH + 12_000L
        );
        assertEquals(5_000L, snapshot.getMovingTimeMs());
        assertEquals(points.size(), snapshot.getPointCount());
        assertEquals(summary.getDistanceMeters(), snapshot.getDistanceMeters(), 0.01d);
    }
}
