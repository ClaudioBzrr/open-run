package com.openrun.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.openrun.core.model.TrackPoint;

@Entity(
        tableName = "points",
        foreignKeys = @ForeignKey(
                entity = SessionEntity.class,
                parentColumns = "id",
                childColumns = "session_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index(value = {"session_id", "seq"}, unique = true)
)
public class PointEntity {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    @ColumnInfo(name = "seq")
    public int seq;

    @ColumnInfo(name = "lat")
    public double lat;

    @ColumnInfo(name = "lon")
    public double lon;

    @Nullable
    @ColumnInfo(name = "altitude")
    public Double altitude;

    @Nullable
    @ColumnInfo(name = "accuracy")
    public Double accuracy;

    @Nullable
    @ColumnInfo(name = "speed")
    public Double speed;

    @Nullable
    @ColumnInfo(name = "bearing")
    public Double bearing;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "elapsed_ms")
    public long elapsedMs;

    @ColumnInfo(name = "moving_elapsed_ms")
    public long movingElapsedMs;

    public PointEntity() {
    }

    public static PointEntity from(String sessionId, TrackPoint point) {
        PointEntity entity = new PointEntity();
        entity.sessionId = sessionId;
        entity.seq = point.getSeq();
        entity.lat = point.getLatitude();
        entity.lon = point.getLongitude();
        entity.altitude = finiteOrNull(point.getAltitudeMeters());
        entity.accuracy = finiteOrNull(point.getAccuracyMeters());
        entity.speed = finiteOrNull(point.getSpeedMps());
        entity.bearing = finiteOrNull(point.getBearingDegrees());
        entity.timestamp = point.getTimestampEpochMs();
        entity.elapsedMs = point.getElapsedMs();
        entity.movingElapsedMs = point.getMovingElapsedMs();
        return entity;
    }

    @Nullable
    private static Double finiteOrNull(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return value;
    }
}
