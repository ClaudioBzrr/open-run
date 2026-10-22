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
    public void accuracyRaisesTheRequiredSegmentLength() {
        TrackFilter filter = new TrackFilter(config);
        double latitude = -23.550520d;
        assertTrue(filter.accept(fix(latitude, 20.0d, 1.4d, 0), false));

        latitude += 3.0d / METERS_PER_DEGREE_LAT;
        assertFalse(filter.accept(fix(latitude, 20.0d, 1.4d, 1), false));

        latitude += 5.0d / METERS_PER_DEGREE_LAT;
        assertTrue(filter.accept(fix(latitude, 20.0d, 1.4d, 2), false));
    }

    @Test
    public void missingSpeedRequiresASecondConsistentFix() {
        TrackFilter filter = new TrackFilter(config);
        double latitude = -23.550520d;
        assertTrue(filter.accept(
                fix(latitude, 5.0d, Double.NaN, 0),
                false
        ));

        latitude += 3.0d / METERS_PER_DEGREE_LAT;
        assertFalse(filter.accept(
                fix(latitude, 5.0d, Double.NaN, 1),
                false
        ));

        latitude += 1.0d / METERS_PER_DEGREE_LAT;
        assertFalse(filter.accept(
                fix(latitude, 5.0d, Double.NaN, 2),
                false
        ));

        latitude += 1.0d / METERS_PER_DEGREE_LAT;
        assertTrue(filter.accept(
                fix(latitude, 5.0d, Double.NaN, 3),
                false
        ));
    }
}
