package com.openrun.app.data;

import androidx.lifecycle.LiveData;

import java.util.List;

public final class SessionRepository {
    private final AppDatabase database;
    private final SessionDao dao;

    public SessionRepository(AppDatabase database) {
        this.database = database;
        this.dao = database.sessionDao();
    }

    public void insertSession(SessionEntity session) {
        dao.insertSession(session);
    }

    public void updateSession(SessionEntity session) {
        dao.updateSession(session);
    }

    public void insertPoint(PointEntity point) {
        dao.insertPoint(point);
    }

    public long insertPause(PauseEntity pause) {
        return dao.insertPause(pause);
    }

    public void finishPause(long pauseId, long endedElapsed, long endedAt) {
        dao.finishPause(pauseId, endedElapsed, endedAt);
    }

    public List<PauseEntity> loadPauses(String sessionId) {
        return dao.loadPauses(sessionId);
    }

    public PauseEntity findOpenPause(String sessionId) {
        return dao.findOpenPause(sessionId);
    }

    public void finishOpenPauses(String sessionId, long endedElapsed, long endedAt) {
        dao.finishOpenPauses(sessionId, endedElapsed, endedAt);
    }

    public long insertSplit(SplitEntity split) {
        return dao.insertSplit(split);
    }

    public void insertSplits(List<SplitEntity> splits) {
        dao.insertSplits(splits);
    }

    public List<SplitEntity> loadSplits(String sessionId) {
        return dao.loadSplits(sessionId);
    }

    public int countSplits(String sessionId) {
        return dao.countSplits(sessionId);
    }

    public SessionEntity getSession(String id) {
        return dao.getSession(id);
    }

    public List<PointEntity> loadPoints(String sessionId) {
        return dao.loadPoints(sessionId);
    }

    public void deleteSession(String id) {
        dao.deleteSession(id);
    }

    public SessionEntity findActiveSession() {
        return dao.findActiveSession();
    }

    public SessionEntity findUnfinishedSession() {
        return dao.findUnfinishedSession();
    }

    public List<SessionEntity> loadAllSessions() {
        return dao.loadAllSessions();
    }

    public List<PointEntity> loadAllPoints() {
        return dao.loadAllPoints();
    }

    public List<PauseEntity> loadAllPauses() {
        return dao.loadAllPauses();
    }

    public List<SplitEntity> loadAllSplits() {
        return dao.loadAllSplits();
    }

    /**
     * Imports one session in a transaction. A newer local session wins; active
     * sessions are never replaced by a restore.
     */
    public boolean importSession(SessionEntity session,
                                 List<PointEntity> points,
                                 List<PauseEntity> pauses,
                                 List<SplitEntity> splits) {
        final boolean[] imported = {false};
        database.runInTransaction(() -> {
            SessionEntity existing = dao.getSession(session.id);
            if (existing != null) {
                if ("ACQUIRING".equals(existing.state)
                        || "RECORDING".equals(existing.state)
                        || "PAUSED".equals(existing.state)) {
                    return;
                }
                if (existing.updatedAt > session.updatedAt) {
                    return;
                }
                dao.deleteSession(session.id);
            }
            dao.upsertSession(session);
            if (points != null && !points.isEmpty()) {
                dao.upsertPoints(points);
            }
            if (pauses != null && !pauses.isEmpty()) {
                dao.upsertPauses(pauses);
            }
            if (splits != null && !splits.isEmpty()) {
                dao.upsertSplits(splits);
            }
            imported[0] = true;
        });
        return imported[0];
    }

    public LiveData<List<SessionEntity>> observeFinishedSessions() {
        return dao.observeFinishedSessions();
    }

    public void markActiveSessionsInterrupted() {
        dao.markActiveSessionsInterrupted(System.currentTimeMillis());
    }
}
