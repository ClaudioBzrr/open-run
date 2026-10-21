package com.openrun.app.util;

import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SplitEntity;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class ExportWriterTest {
    @Test
    public void writesGpxAndFitFiles() throws Exception {
        long startedAt = 1750000000000L;
        SessionEntity session = new SessionEntity();
        session.id = "export-test";
        session.type = "run";
        session.state = "FINISHED";
        session.startedAt = startedAt;
        session.endedAt = startedAt + 60_000L;
        session.movingMs = 58_000L;
        session.pausedMs = 2_000L;
        session.distanceM = 240.0d;
        session.elevationGainM = 8.0d;
        session.elevationLossM = 3.0d;
        session.pointCount = 12;

        List<PointEntity> points = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            PointEntity point = new PointEntity();
            point.sessionId = session.id;
            point.seq = i;
            point.lat = -23.5505d + i * 0.00002d;
            point.lon = -46.6333d + i * 0.00003d;
            point.altitude = 760.0d + i;
            point.accuracy = 4.0d;
            point.speed = 4.0d;
            point.bearing = 45.0d;
            point.timestamp = startedAt + i * 5_000L;
            point.elapsedMs = i * 5_000L;
            point.movingElapsedMs = i * 4_800L;
            points.add(point);
        }

        SplitEntity split = new SplitEntity();
        split.sessionId = session.id;
        split.splitIndex = 1;
        split.distanceM = 1000.0d;
        split.durationMs = 240_000L;
        split.paceSecPerKm = 240.0d;

        File directory = new File("build/tmp/export-test");
        assertTrue(directory.exists() || directory.mkdirs());
        File fit = new File(directory, "sample.fit");
        File gpx = new File(directory, "sample.gpx");

        SessionFitWriter.write(fit, session, points, List.of(split));
        try (FileOutputStream output = new FileOutputStream(gpx)) {
            SessionGpxWriter.write(output, session, points);
        }

        assertTrue(fit.length() > 100);
        assertTrue(gpx.length() > 500);
        System.out.println("FIT=" + fit.getAbsolutePath());
        System.out.println("GPX=" + gpx.getAbsolutePath());
    }
}
