package com.openrun.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SessionDao {
    @Insert
    long insertSession(SessionEntity session);

    @Update
    void updateSession(SessionEntity session);

    @Insert
    void insertPoint(PointEntity point);

    @Insert
    long insertPause(PauseEntity pause);

    @Query("UPDATE pauses SET ended_elapsed_ms = :endedElapsed, ended_at = :endedAt " +
            "WHERE id = :pauseId")
    void finishPause(long pauseId, long endedElapsed, long endedAt);

    @Query("SELECT * FROM pauses WHERE session_id = :sessionId " +
            "ORDER BY started_elapsed_ms ASC")
    List<PauseEntity> loadPauses(String sessionId);

    @Query("SELECT * FROM pauses WHERE session_id = :sessionId " +
            "AND ended_elapsed_ms IS NULL ORDER BY started_elapsed_ms DESC LIMIT 1")
    PauseEntity findOpenPause(String sessionId);

    @Query("UPDATE pauses SET ended_elapsed_ms = :endedElapsed, ended_at = :endedAt " +
            "WHERE session_id = :sessionId AND ended_elapsed_ms IS NULL")
    void finishOpenPauses(String sessionId, long endedElapsed, long endedAt);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertSplit(SplitEntity split);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertSplits(List<SplitEntity> splits);

    @Query("SELECT * FROM splits WHERE session_id = :sessionId ORDER BY split_index ASC")
    List<SplitEntity> loadSplits(String sessionId);

    @Query("SELECT COUNT(*) FROM splits WHERE session_id = :sessionId")
    int countSplits(String sessionId);

    @Query("SELECT * FROM sessions " +
            "WHERE state IN ('ACQUIRING', 'RECORDING', 'PAUSED', 'INTERRUPTED') " +
            "AND deleted = 0 ORDER BY started_at DESC LIMIT 1")
    SessionEntity findUnfinishedSession();

    @Query("SELECT * FROM sessions " +
            "WHERE state IN ('ACQUIRING', 'RECORDING', 'PAUSED') " +
            "ORDER BY started_at DESC LIMIT 1")
    SessionEntity findActiveSession();

    @Query("SELECT * FROM sessions WHERE deleted = 0 ORDER BY started_at ASC")
    List<SessionEntity> loadAllSessions();

    @Query("SELECT * FROM points ORDER BY session_id ASC, seq ASC")
    List<PointEntity> loadAllPoints();

    @Query("SELECT * FROM pauses ORDER BY session_id ASC, started_elapsed_ms ASC")
    List<PauseEntity> loadAllPauses();

    @Query("SELECT * FROM splits ORDER BY session_id ASC, split_index ASC")
    List<SplitEntity> loadAllSplits();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertSession(SessionEntity session);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertPoints(List<PointEntity> points);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertPauses(List<PauseEntity> pauses);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertSplits(List<SplitEntity> splits);

    @Query("SELECT * FROM sessions " +
            "WHERE state = 'FINISHED' AND deleted = 0 " +
            "ORDER BY started_at DESC")
    LiveData<List<SessionEntity>> observeFinishedSessions();

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    SessionEntity getSession(String id);

    @Query("SELECT * FROM points WHERE session_id = :sessionId ORDER BY seq ASC")
    List<PointEntity> loadPoints(String sessionId);

    @Query("DELETE FROM sessions WHERE id = :id")
    void deleteSession(String id);

    @Query("UPDATE sessions SET state = 'INTERRUPTED', updated_at = :now " +
            "WHERE state IN ('ACQUIRING', 'RECORDING', 'PAUSED')")
    void markActiveSessionsInterrupted(long now);
}
