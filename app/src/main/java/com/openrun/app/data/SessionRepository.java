package com.openrun.app.data;

import androidx.lifecycle.LiveData;

import java.util.List;

public final class SessionRepository {
    private final SessionDao dao;

    public SessionRepository(AppDatabase database) {
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

    public LiveData<List<SessionEntity>> observeFinishedSessions() {
        return dao.observeFinishedSessions();
    }

    public void markActiveSessionsInterrupted() {
        dao.markActiveSessionsInterrupted(System.currentTimeMillis());
    }
}
