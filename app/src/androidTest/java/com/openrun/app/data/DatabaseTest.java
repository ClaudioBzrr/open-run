package com.openrun.app.data;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DatabaseTest {
    @Test
    public void sessionRelationsPersistAndCascade() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase database = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase.class
        ).allowMainThreadQueries().build();
        try {
            SessionRepository repository = new SessionRepository(database);
            SessionEntity session = new SessionEntity();
            session.id = "db-test";
            session.type = "run";
            session.state = "FINISHED";
            session.startedAt = 1_000L;
            session.updatedAt = 1_000L;
            session.pointCount = 1;
            repository.insertSession(session);

            PointEntity point = new PointEntity();
            point.sessionId = session.id;
            point.seq = 0;
            point.lat = -23.55;
            point.lon = -46.63;
            point.timestamp = 1_100L;
            point.elapsedMs = 100L;
            point.movingElapsedMs = 90L;
            repository.insertPoint(point);

            PauseEntity pause = new PauseEntity();
            pause.sessionId = session.id;
            pause.startedElapsedMs = 5_000L;
            pause.startedAtEpochMs = 1_500L;
            repository.insertPause(pause);
            repository.finishOpenPauses(session.id, 7_000L, 1_800L);

            SplitEntity split = new SplitEntity();
            split.sessionId = session.id;
            split.splitIndex = 1;
            split.distanceM = 1000L;
            split.durationMs = 240_000L;
            split.paceSecPerKm = 240L;
            repository.insertSplit(split);

            List<PointEntity> points = repository.loadPoints(session.id);
            List<PauseEntity> pauses = repository.loadPauses(session.id);
            List<SplitEntity> splits = repository.loadSplits(session.id);
            assertEquals(1, points.size());
            assertEquals(90L, points.get(0).movingElapsedMs);
            assertEquals(1, pauses.size());
            assertNotNull(pauses.get(0).endedElapsedMs);
            assertEquals(1, splits.size());
            assertNull(repository.findUnfinishedSession());

            repository.deleteSession(session.id);
            assertNull(repository.getSession(session.id));
            assertTrue(repository.loadPoints(session.id).isEmpty());
            assertTrue(repository.loadPauses(session.id).isEmpty());
            assertTrue(repository.loadSplits(session.id).isEmpty());
        } finally {
            database.close();
        }
    }

    @Test
    public void unfinishedSessionIncludesInterruptedRows() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase database = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase.class
        ).allowMainThreadQueries().build();
        try {
            SessionRepository repository = new SessionRepository(database);
            SessionEntity session = new SessionEntity();
            session.id = "interrupted-test";
            session.type = "walk";
            session.state = "INTERRUPTED";
            session.startedAt = 2_000L;
            session.startedElapsed = 500L;
            session.bootCount = 7;
            session.updatedAt = 2_000L;
            repository.insertSession(session);

            SessionEntity unfinished = repository.findUnfinishedSession();
            assertNotNull(unfinished);
            assertEquals("INTERRUPTED", unfinished.state);
            assertNull(repository.findActiveSession());
        } finally {
            database.close();
        }
    }
}
