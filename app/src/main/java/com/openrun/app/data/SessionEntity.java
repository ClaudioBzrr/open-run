package com.openrun.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sessions")
public class SessionEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    @NonNull
    @ColumnInfo(name = "type")
    public String type = "run";

    @NonNull
    @ColumnInfo(name = "state")
    public String state = "IDLE";

    @ColumnInfo(name = "started_at")
    public long startedAt;

    @ColumnInfo(name = "started_elapsed")
    public long startedElapsed;

    @ColumnInfo(name = "recording_started_elapsed")
    public long recordingStartedElapsed;

    @ColumnInfo(name = "recording_started_at")
    public long recordingStartedAt;

    @ColumnInfo(name = "boot_count")
    public int bootCount;

    @Nullable
    @ColumnInfo(name = "ended_at")
    public Long endedAt;

    @ColumnInfo(name = "moving_ms")
    public long movingMs;

    @ColumnInfo(name = "paused_ms")
    public long pausedMs;

    @ColumnInfo(name = "distance_m")
    public double distanceM;

    @ColumnInfo(name = "elevation_gain_m")
    public double elevationGainM;

    @ColumnInfo(name = "elevation_loss_m")
    public double elevationLossM;

    @ColumnInfo(name = "point_count")
    public int pointCount;

    @Nullable
    @ColumnInfo(name = "last_lat")
    public Double lastLat;

    @Nullable
    @ColumnInfo(name = "last_lon")
    public Double lastLon;

    @Nullable
    @ColumnInfo(name = "last_accuracy")
    public Double lastAccuracy;

    @Nullable
    @ColumnInfo(name = "last_point_at")
    public Long lastPointAt;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    @ColumnInfo(name = "deleted")
    public boolean deleted;

    public SessionEntity() {
    }
}
