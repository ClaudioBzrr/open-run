package com.openrun.app.tracking;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.openrun.core.model.ActivityType;
import com.openrun.core.model.TrackingSnapshot;

/**
 * In-process bridge between the foreground service and the UI. The service is
 * the source of truth; activities only observe this holder.
 */
public final class TrackingStateHolder {
    private final MutableLiveData<TrackingSnapshot> snapshot =
            new MutableLiveData<>(TrackingSnapshot.idle(ActivityType.RUN));
    private final MutableLiveData<TrackingEvent> event = new MutableLiveData<>();

    public LiveData<TrackingSnapshot> getSnapshot() {
        return snapshot;
    }

    public LiveData<TrackingEvent> getEvent() {
        return event;
    }

    public TrackingSnapshot getCurrentSnapshot() {
        return snapshot.getValue();
    }

    public void postSnapshot(TrackingSnapshot value) {
        snapshot.postValue(value);
    }

    public void postEvent(TrackingEvent value) {
        event.postValue(value);
    }

    public void clearEvent() {
        event.setValue(null);
    }
}
