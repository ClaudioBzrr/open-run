package com.openrun.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "pauses",
        foreignKeys = @ForeignKey(
                entity = SessionEntity.class,
                parentColumns = "id",
                childColumns = "session_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("session_id")
)
public class PauseEntity {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    @ColumnInfo(name = "started_elapsed_ms")
    public long startedElapsedMs;

    @Nullable
    @ColumnInfo(name = "ended_elapsed_ms")
    public Long endedElapsedMs;

    @ColumnInfo(name = "started_at")
    public long startedAtEpochMs;

    @Nullable
    @ColumnInfo(name = "ended_at")
    public Long endedAtEpochMs;

    @ColumnInfo(name = "auto_pause")
    public boolean autoPause;

    public PauseEntity() {
    }
}
