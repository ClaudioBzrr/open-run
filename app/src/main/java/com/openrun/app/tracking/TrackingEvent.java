package com.openrun.app.tracking;

import com.openrun.core.model.SessionSummary;

/**
 * One-shot event delivered by the tracking service.
 */
public final class TrackingEvent {
    public enum Type {
        FINISHED,
        ERROR_LOCATION,
        ERROR_PERMISSION
    }

    private final Type type;
    private final String sessionId;
    private final SessionSummary summary;

    private TrackingEvent(Type type, String sessionId, SessionSummary summary) {
        this.type = type;
        this.sessionId = sessionId;
        this.summary = summary;
    }

    public static TrackingEvent finished(String sessionId, SessionSummary summary) {
        return new TrackingEvent(Type.FINISHED, sessionId, summary);
    }

    public static TrackingEvent error(Type type) {
        return new TrackingEvent(type, null, null);
    }

    public Type getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public SessionSummary getSummary() {
        return summary;
    }
}
