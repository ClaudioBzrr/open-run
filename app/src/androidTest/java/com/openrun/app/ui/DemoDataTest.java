package com.openrun.app.ui;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.openrun.app.data.AppDatabase;
import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SessionRepository;
import com.openrun.app.data.SplitEntity;
import com.openrun.core.stats.GeoMath;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Seeds deterministic local data for the marketing screenshot workflow. This
 * test never runs against user data outside the CI emulator.
 */
@RunWith(AndroidJUnit4.class)
public class DemoDataTest {
    @Test
    public void seedDemoSessions() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(context);
        database.clearAllTables();
        SessionRepository repository = new SessionRepository(database);
        long now = System.currentTimeMillis();

        SessionEntity run = baseSession(
                "demo-run-1",
                "run",
                now - 2L * 60L * 60L * 1000L,
                0
        );
        List<PointEntity> points = buildParkLoop(run);
        double distance = 0;
        PointEntity previous = null;
        for (PointEntity point : points) {
            if (previous != null) {
                distance += GeoMath.distanceMeters(
                        previous.lat,
                        previous.lon,
                        point.lat,
                        point.lon
                );
            }
            previous = point;
        }
        run.distanceM = distance;
        run.movingMs = points.get(points.size() - 1).elapsedMs;
        run.pausedMs = 0;
        run.pointCount = points.size();
        run.elevationGainM = 38.4;
        run.elevationLossM = 36.7;
        run.endedAt = run.startedAt + run.movingMs;
        run.lastLat = previous.lat;
        run.lastLon = previous.lon;
        run.lastAccuracy = previous.accuracy;
        run.lastPointAt = previous.timestamp;
        repository.insertSession(run);
        for (PointEntity point : points) {
            repository.insertPoint(point);
        }
        repository.insertSplits(buildSplits(run, points));

        SessionEntity walk = baseSession(
                "demo-walk-1",
                "walk",
                now - 26L * 60L * 60L * 1000L,
                47L * 60L * 1000L + 12_000L
        );
        walk.distanceM = 3_840;
        walk.elevationGainM = 22.0;
        walk.elevationLossM = 21.0;
        walk.lastLat = -23.5885;
        walk.lastLon = -46.6570;
        walk.lastAccuracy = 6.0;
        walk.lastPointAt = walk.endedAt;
        repository.insertSession(walk);

        SessionEntity longRun = baseSession(
                "demo-run-2",
                "run",
                now - 2L * 24L * 60L * 60L * 1000L,
                51L * 60L * 1000L + 42_000L
        );
        longRun.distanceM = 10_240;
        longRun.elevationGainM = 71.0;
        longRun.elevationLossM = 69.0;
        longRun.lastLat = -23.5860;
        longRun.lastLon = -46.6600;
        longRun.lastAccuracy = 5.0;
        longRun.lastPointAt = longRun.endedAt;
        repository.insertSession(longRun);

        SessionEntity oldRun = baseSession(
                "demo-run-3",
                "run",
                now - 4L * 24L * 60L * 60L * 1000L,
                44L * 60L * 1000L + 29_000L
        );
        oldRun.distanceM = 8_060;
        oldRun.elevationGainM = 54.0;
        oldRun.elevationLossM = 52.0;
        oldRun.lastLat = -23.5900;
        oldRun.lastLon = -46.6550;
        oldRun.lastAccuracy = 4.0;
        oldRun.lastPointAt = oldRun.endedAt;
        repository.insertSession(oldRun);

        assertTrue(run.pointCount > 100);
    }

    private SessionEntity baseSession(String id, String type, long startedAt, long movingMs) {
        SessionEntity session = new SessionEntity();
        session.id = id;
        session.type = type;
        session.state = "FINISHED";
        session.startedAt = startedAt;
        session.startedElapsed = 0;
        session.recordingStartedElapsed = 0;
        session.recordingStartedAt = startedAt;
        session.bootCount = 0;
        session.movingMs = movingMs;
        session.pausedMs = 0;
        session.pointCount = 0;
        session.createdAt = startedAt;
        session.updatedAt = startedAt + movingMs;
        session.deleted = false;
        session.endedAt = startedAt + movingMs;
        session.lastPointAt = session.endedAt;
        return session;
    }

    private List<PointEntity> buildParkLoop(SessionEntity session) {
        List<PointEntity> points = new ArrayList<>();
        long start = session.startedAt;
        double centerLat = -23.5874;
        double centerLon = -46.6576;
        for (int i = 0; i < 460; i++) {
            double angle = (Math.PI * 2.0d) * i / 459.0d;
            double latitude = centerLat
                    + 0.0080d * Math.sin(angle)
                    + 0.00035d * Math.sin(angle * 7.0d);
            double longitude = centerLon
                    + 0.0110d * Math.cos(angle)
                    + 0.00030d * Math.cos(angle * 5.0d);
            PointEntity point = new PointEntity();
            point.sessionId = session.id;
            point.seq = i;
            point.lat = latitude;
            point.lon = longitude;
            point.altitude = 758.0d + 7.0d * Math.sin(angle * 3.0d);
            point.accuracy = 4.2d + 1.8d * (i % 4) / 4.0d;
            point.speed = 3.2d;
            point.bearing = (Math.toDegrees(angle) + 90.0d) % 360.0d;
            point.timestamp = start + i * 3_000L;
            point.elapsedMs = i * 3_000L;
            point.movingElapsedMs = i * 3_000L;
            points.add(point);
        }
        return points;
    }

    private List<SplitEntity> buildSplits(SessionEntity session, List<PointEntity> points) {
        List<SplitEntity> splits = new ArrayList<>();
        double cumulative = 0;
        double nextSplit = 1_000;
        long lastSplitTime = 0;
        int index = 1;
        PointEntity previous = null;
        for (PointEntity point : points) {
            if (previous != null) {
                cumulative += GeoMath.distanceMeters(
                        previous.lat,
                        previous.lon,
                        point.lat,
                        point.lon
                );
                while (cumulative >= nextSplit) {
                    SplitEntity split = new SplitEntity();
                    split.sessionId = session.id;
                    split.splitIndex = index;
                    split.distanceM = 1_000;
                    split.durationMs = point.movingElapsedMs - lastSplitTime;
                    split.paceSecPerKm = split.durationMs / 1000.0d;
                    splits.add(split);
                    lastSplitTime = point.movingElapsedMs;
                    nextSplit += 1_000;
                    index++;
                }
            }
            previous = point;
        }
        return splits;
    }
}
