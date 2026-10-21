package com.openrun.app.util;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.openrun.app.data.AppDatabase;
import com.openrun.app.data.PauseEntity;
import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SessionRepository;
import com.openrun.app.data.SplitEntity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class BackupManagerTest {
    @Test
    public void encryptedRoundTripRestoresPointsPausesAndSplits() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase source = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase.class
        ).allowMainThreadQueries().build();
        AppDatabase target = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase.class
        ).allowMainThreadQueries().build();
        try {
            SessionRepository sourceRepository = new SessionRepository(source);
            SessionEntity session = new SessionEntity();
            session.id = "backup-test";
            session.type = "run";
            session.state = "FINISHED";
            session.startedAt = 10_000L;
            session.endedAt = 20_000L;
            session.movingMs = 9_000L;
            session.pausedMs = 1_000L;
            session.distanceM = 1000L;
            session.pointCount = 2;
            session.updatedAt = 20_000L;
            sourceRepository.insertSession(session);

            for (int i = 0; i < 2; i++) {
                PointEntity point = new PointEntity();
                point.sessionId = session.id;
                point.seq = i;
                point.lat = -23.55 + i * 0.0001;
                point.lon = -46.63 + i * 0.0001;
                point.altitude = 760.0;
                point.accuracy = 4.0;
                point.speed = 4.0;
                point.timestamp = 10_000L + i * 5_000L;
                point.elapsedMs = i * 5_000L;
                point.movingElapsedMs = i * 4_800L;
                sourceRepository.insertPoint(point);
            }

            PauseEntity pause = new PauseEntity();
            pause.sessionId = session.id;
            pause.startedElapsedMs = 5_000L;
            pause.endedElapsedMs = 6_000L;
            pause.startedAtEpochMs = 15_000L;
            pause.endedAtEpochMs = 16_000L;
            sourceRepository.insertPause(pause);

            SplitEntity split = new SplitEntity();
            split.sessionId = session.id;
            split.splitIndex = 1;
            split.distanceM = 1000L;
            split.durationMs = 240_000L;
            split.paceSecPerKm = 240L;
            sourceRepository.insertSplit(split);

            ByteArrayOutputStream backup = new ByteArrayOutputStream();
            char[] passphrase = "correct horse battery".toCharArray();
            BackupManager.exportBackup(sourceRepository, backup, passphrase);

            assertTrue(backup.size() > 64);
            SessionRepository targetRepository = new SessionRepository(target);
            BackupManager.ImportResult result = BackupManager.importBackup(
                    targetRepository,
                    new ByteArrayInputStream(backup.toByteArray()),
                    passphrase
            );
            assertEquals(1, result.sessionsImported);
            assertNotNull(targetRepository.getSession(session.id));
            assertEquals(2, targetRepository.loadPoints(session.id).size());
            assertEquals(1, targetRepository.loadPauses(session.id).size());
            assertEquals(1, targetRepository.loadSplits(session.id).size());

            try {
                BackupManager.importBackup(
                        targetRepository,
                        new ByteArrayInputStream(backup.toByteArray()),
                        "wrong passphrase".toCharArray()
                );
                fail("Wrong passphrase must not decrypt the backup");
            } catch (Exception expected) {
                // Expected: GCM authentication failed.
            }
        } finally {
            source.close();
            target.close();
        }
    }
}
