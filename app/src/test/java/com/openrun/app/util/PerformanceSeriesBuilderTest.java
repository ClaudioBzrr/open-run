package com.openrun.app.util;

import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class PerformanceSeriesBuilderTest {
    @Test
    public void paceIsStoredInSecondsPerKilometre() {
        SessionEntity session = new SessionEntity();
        session.id = "pace-test";
        session.type = "run";
        session.state = "FINISHED";
        session.movingMs = 89_000L;
        session.distanceM = 445.0d;

        List<PointEntity> points = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            PointEntity point = new PointEntity();
            point.sessionId = session.id;
            point.seq = i;
            // About five metres per second, which is roughly 3:20 min/km.
            point.lat = -23.5505d + i * 0.000045d;
            point.lon = -46.6333d;
            point.altitude = 760.0d + i * 0.5d;
            point.accuracy = 4.0d;
            point.speed = 5.0d;
            point.timestamp = 1_750_000_000_000L + i * 1_000L;
            point.elapsedMs = i * 1_000L;
            point.movingElapsedMs = i * 1_000L;
            points.add(point);
        }

        PerformanceSeries series = PerformanceSeriesBuilder.build(session, points);

        assertTrue("pace chart must contain samples", series.hasPace());
        float firstPace = series.paceY[0];
        assertTrue(
                "pace must be seconds per kilometre, not milliseconds: " + firstPace,
                firstPace > 150f && firstPace < 260f
        );
        assertTrue("elevation chart must contain samples", series.hasElevation());
    }

    @Test
    public void pausedTimeDoesNotInflatePace() {
        SessionEntity session = new SessionEntity();
        session.id = "pause-test";
        session.type = "run";
        session.state = "FINISHED";
        session.movingMs = 89_000L;
        session.pausedMs = 300_000L;
        session.distanceM = 445.0d;

        List<PointEntity> points = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            PointEntity point = new PointEntity();
            point.sessionId = session.id;
            point.seq = i;
            point.lat = -23.5505d + i * 0.000045d;
            point.lon = -46.6333d;
            point.altitude = 760.0d;
            point.accuracy = 4.0d;
            point.speed = 5.0d;
            point.timestamp = 1_750_000_000_000L + i * 1_000L;
            // A five-minute pause between the two halves of the track.
            point.elapsedMs = i * 1_000L + (i >= 45 ? 300_000L : 0L);
            point.movingElapsedMs = i * 1_000L;
            points.add(point);
        }

        PerformanceSeries series = PerformanceSeriesBuilder.build(session, points);

        assertTrue(series.hasPace());
        float firstPace = series.paceY[0];
        assertTrue(
                "moving clock must exclude the pause: " + firstPace,
                firstPace > 150f && firstPace < 260f
        );
    }

    @Test
    public void legacyPointsWithoutMovingElapsedStillRender() {
        SessionEntity session = new SessionEntity();
        session.id = "legacy-test";
        session.type = "run";
        session.state = "FINISHED";
        session.movingMs = 60_000L;
        session.distanceM = 300.0d;

        List<PointEntity> points = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            PointEntity point = new PointEntity();
            point.sessionId = session.id;
            point.seq = i;
            point.lat = -23.5505d + i * 0.000045d;
            point.lon = -46.6333d;
            point.altitude = 760.0d;
            point.accuracy = 4.0d;
            point.speed = 5.0d;
            point.timestamp = 1_750_000_000_000L + i * 1_000L;
            point.elapsedMs = i * 1_000L;
            point.movingElapsedMs = 0L;
            points.add(point);
        }

        PerformanceSeries series = PerformanceSeriesBuilder.build(session, points);

        assertTrue(series.hasPace());
        assertTrue(series.hasElevation());
    }
}
