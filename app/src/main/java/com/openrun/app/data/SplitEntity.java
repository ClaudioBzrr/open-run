package com.openrun.app.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "splits",
        foreignKeys = @ForeignKey(
                entity = SessionEntity.class,
                parentColumns = "id",
                childColumns = "session_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index(value = {"session_id", "split_index"}, unique = true)
)
public class SplitEntity {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    @ColumnInfo(name = "split_index")
    public int splitIndex;

    @ColumnInfo(name = "distance_m")
    public double distanceM;

    @ColumnInfo(name = "duration_ms")
    public long durationMs;

    @ColumnInfo(name = "pace_sec_per_km")
    public double paceSecPerKm;

    public SplitEntity() {
    }
}
