package com.openrun.core.model;

public enum TrackingState {
    IDLE,
    ACQUIRING,
    RECORDING,
    PAUSED,
    FINISHED,
    DISCARDED;

    public boolean isActive() {
        return this == ACQUIRING || this == RECORDING || this == PAUSED;
    }
}
