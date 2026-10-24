package com.openrun.core.stats;

import com.openrun.core.model.ActivityType;
import com.openrun.core.model.RawFix;
import com.openrun.core.model.RecordingConfig;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TrackFilterTest {
    private static final double METERS_PER_DEGREE_LAT = 111_195.0d;
    private static final long EPOCH = 1_700_000_000_000L;

    private final RecordingConfig config = new RecordingConfig(
            ActivityType.RUN,
            25,
            15,
            15.0d,
            false
    );

    private RawFix fix(double latitude, double accuracy, double speed, long second) {
        return new RawFix(
                latitude,
                -46.633308d,
                760.0d,
                accuracy,
                speed,
                0.0d,
                EPOCH + second * 1000L
        );
    }

    @Test
    public void rejectsStationaryMultipathJitterWhenDopplerSpeedIsLow() {
        TrackFilter filter = new TrackFilter(config);
        double baseLatitude = -23.550520d;
        assertTrue(filter.accept(fix(baseLatitude, 5.0d, 0.05d, 0), false));

        for (int second = 1; second <= 12; second++) {
            double latitude = baseLatitude
                    + ((second % 3) - 1) * 3.0d / METERS_PER_DEGREE_LAT;
            assertFalse(filter.accept(
                    fix(latitude, 5.0d, 0.05d, second),
                    false
            ));
        }

        assertNotNull(filter.getLastAccepted());
        assertEquals(
                baseLatitude,
                filter.getLastAccepted().getLatitude(),
                0.0000001d
        );
    }

    @Test
    public void acceptsRealMovementAtWalkingSpeed() {
        TrackFilter filter = new TrackFilter(config);
        double latitude = -23.550520d;
        assertTrue(filter.accept(fix(latitude, 5.0d, 1.4d, 0), false));

        latitude += 3.0d / METERS_PER_DEGREE_LAT;
        assertTrue(filter.accept(fix(latitude, 5.0d, 1.4d, 1), false));
    }

    @Test
    public void accuracyScalesTheSegmentWithoutHoldingTheAnchorForMetres() {
        TrackFilter filter = new TrackFilter(config);
        double latitude = -23.550520d;
        assertTrue(filter.accept(fix(latitude, 20.0d, 1.4d, 0), false));

        // 1 m is still within the accuracy-scaled noise band.
        latitude += 1.0d / METERS_PER_DEGREE_LAT;
        assertFalse(filter.accept(fix(latitude, 20.0d, 1.4d, 1), false));

        // 3 m is real movement. The old filter rejected it and only accepted
        // after 8.75 m, collapsing the path into a straight chord.
        latitude += 2.0d / METERS_PER_DEGREE_LAT;
        assertTrue(filter.accept(fix(latitude, 20.0d, 1.4d, 2), false));
    }

    @Test
    public void keepsDenseSamplingOnCurvesWithoutDopplerSpeed() {
        TrackFilter filter = new TrackFilter(config);
        double radiusMeters = 20.0d;
        double stepMeters = 2.0d;
        double stepRadians = stepMeters / radiusMeters;
        double centerLatitude = -23.550520d;
        double metersPerDegreeLon = METERS_PER_DEGREE_LAT
                * Math.cos(Math.toRadians(centerLatitude));

        double[] previous = null;
        double[] previousAccepted = null;
        double acceptedMeters = 0;
        double trueMeters = 0;
        int second = 0;

        for (int step = 0; step <= 80; step++) {
            double angle = step * stepRadians;
            double latitude = centerLatitude + radiusMeters
                    * Math.cos(angle) / METERS_PER_DEGREE_LAT;
            double longitude = -46.633308d + radiusMeters
                    * Math.sin(angle) / metersPerDegreeLon;

            if (previous != null) {
                trueMeters += GeoMath.distanceMeters(
                        previous[0], previous[1], latitude, longitude);
            }
            previous = new double[]{latitude, longitude};

            if (filter.accept(
                    new RawFix(
                            latitude,
                            longitude,
                            760.0d,
                            5.0d,
                            Double.NaN,
                            0.0d,
                            EPOCH + second * 1000L
                    ),
                    false
            )) {
                if (previousAccepted != null) {
                    acceptedMeters += GeoMath.distanceMeters(
                            previousAccepted[0],
                            previousAccepted[1],
                            latitude,
                            longitude
                    );
                }
                previousAccepted = new double[]{latitude, longitude};
            }
            second++;
        }

        assertTrue(trueMeters > 150.0d);
        assertTrue(
                "curved track lost too much distance: " + acceptedMeters
                        + " of " + trueMeters,
                acceptedMeters > trueMeters * 0.95d
        );
    }

    @Test
    public void stopsAddingDistanceWhenMovementEnds() {
        TrackFilter filter = new TrackFilter(config);
        double latitude = -23.550520d;
        assertTrue(filter.accept(fix(latitude, 5.0d, Double.NaN, 0), false));

        for (int second = 1; second <= 20; second++) {
            latitude += 3.0d / METERS_PER_DEGREE_LAT;
            filter.accept(fix(latitude, 5.0d, Double.NaN, second), false);
        }
        assertNotNull(filter.getLastAccepted());

        double stoppedLatitude = latitude;
        double previousAcceptedLatitude = latitude;
        double addedMeters = 0;
        for (int second = 21; second <= 80; second++) {
            double jitter = ((second % 3) - 1) * 3.0d / METERS_PER_DEGREE_LAT;
            if (filter.accept(fix(stoppedLatitude + jitter, 5.0d, 0.05d, second), false)) {
                RawFix accepted = filter.getLastAccepted();
                addedMeters += GeoMath.distanceMeters(
                        previousAcceptedLatitude,
                        -46.633308d,
                        accepted.getLatitude(),
                        accepted.getLongitude()
                );
                previousAcceptedLatitude = accepted.getLatitude();
            }
        }

        assertTrue(
                "stationary drift added " + addedMeters + " m after stopping",
                addedMeters < 20.0d
        );
    }

    @Test
    public void rejectsStationaryJitterWhenSpeedIsMissing() {
        TrackFilter filter = new TrackFilter(config);
        double baseLatitude = -23.550520d;
        assertTrue(filter.accept(fix(baseLatitude, 5.0d, Double.NaN, 0), false));

        for (int second = 1; second <= 60; second++) {
            double latitude = baseLatitude
                    + ((second % 3) - 1) * 3.0d / METERS_PER_DEGREE_LAT;
            assertFalse(filter.accept(
                    fix(latitude, 5.0d, Double.NaN, second),
                    false
            ));
        }

        assertNotNull(filter.getLastAccepted());
        assertEquals(
                baseLatitude,
                filter.getLastAccepted().getLatitude(),
                0.0000001d
        );
    }

    @Test
    public void missingSpeedRequiresASecondConsistentFix() {
        TrackFilter filter = new TrackFilter(config);
        double latitude = -23.550520d;
        assertTrue(filter.accept(
                fix(latitude, 5.0d, Double.NaN, 0),
                false
        ));

        latitude += 4.0d / METERS_PER_DEGREE_LAT;
        assertFalse(filter.accept(
                fix(latitude, 5.0d, Double.NaN, 1),
                false
        ));

        latitude += 8.0d / METERS_PER_DEGREE_LAT;
        assertFalse(filter.accept(
                fix(latitude, 5.0d, Double.NaN, 2),
                false
        ));

        latitude += 1.0d / METERS_PER_DEGREE_LAT;
        assertFalse(filter.accept(
                fix(latitude, 5.0d, Double.NaN, 3),
                false
        ));

        latitude += 1.0d / METERS_PER_DEGREE_LAT;
        assertTrue(filter.accept(
                fix(latitude, 5.0d, Double.NaN, 4),
                false
        ));
    }
}
