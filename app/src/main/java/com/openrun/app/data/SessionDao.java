package com.openrun.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
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

    @Query("SELECT * FROM sessions " +
            "WHERE state IN ('ACQUIRING', 'RECORDING', 'PAUSED') " +
            "ORDER BY started_at DESC LIMIT 1")
    SessionEntity findActiveSession();

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
