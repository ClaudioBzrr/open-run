package com.openrun.app.util;

import com.garmin.fit.Activity;
import com.garmin.fit.ActivityMesg;
import com.garmin.fit.DateTime;
import com.garmin.fit.Event;
import com.garmin.fit.EventType;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.LapMesg;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.Sport;
import com.garmin.fit.SubSport;

import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SplitEntity;
import com.openrun.core.model.ActivityType;
import com.openrun.core.stats.GeoMath;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * FIT activity export using the official Garmin FIT SDK.
 */
public final class SessionFitWriter {
    private static final double SEMICIRCLES_PER_DEGREE =
            2147483648.0d / 180.0d;

    private SessionFitWriter() {
    }

    public static void write(File output,
                             SessionEntity session,
                             List<PointEntity> points,
                             List<SplitEntity> splits) throws IOException {
        FileEncoder encoder = new FileEncoder(output);
        try {
            long startedAt = session.startedAt > 0
                    ? session.startedAt
                    : System.currentTimeMillis();
            long endedAt = session.endedAt != null && session.endedAt > 0
                    ? session.endedAt
                    : lastTimestamp(points, startedAt);
            boolean walking = ActivityType.fromKey(session.type) == ActivityType.WALK;

            FileIdMesg fileId = new FileIdMesg();
            fileId.setType(com.garmin.fit.File.ACTIVITY);
            fileId.setManufacturer(Manufacturer.DEVELOPMENT);
            fileId.setProduct(1);
            fileId.setSerialNumber((long) session.id.hashCode() & 0xffffffffL);
            fileId.setTimeCreated(new DateTime(new Date(startedAt)));
            encoder.write(fileId);

            double cumulativeDistance = 0;
            double maxSpeed = 0;
            PointEntity previous = null;
            for (PointEntity point : points) {
                if (point == null || point.timestamp <= 0) {
                    continue;
                }
                if (previous != null) {
                    cumulativeDistance += GeoMath.distanceMeters(
                            previous.lat,
                            previous.lon,
                            point.lat,
                            point.lon
                    );
                }
                RecordMesg record = new RecordMesg();
                record.setTimestamp(new DateTime(new Date(point.timestamp)));
                record.setPositionLat(semicircles(point.lat));
                record.setPositionLong(semicircles(point.lon));
                record.setDistance((float) cumulativeDistance);
                if (point.altitude != null && isFinite(point.altitude)) {
                    record.setAltitude(point.altitude.floatValue());
                }
                if (point.speed != null && isFinite(point.speed)) {
                    record.setSpeed(point.speed.floatValue());
                    maxSpeed = Math.max(maxSpeed, point.speed);
                }
                encoder.write(record);
                previous = point;
            }

            long movingMs = Math.max(0, session.movingMs);
            long totalMs = Math.max(0, endedAt - startedAt);
            double avgSpeed = movingMs > 0
                    ? session.distanceM / (movingMs / 1000.0d)
                    : 0;

            if (splits != null) {
                long splitStartEpoch = startedAt;
                for (SplitEntity split : splits) {
                    long splitEndEpoch = Math.min(
                            endedAt,
                            splitStartEpoch + Math.max(0, split.durationMs)
                    );
                    LapMesg lap = new LapMesg();
                    lap.setTimestamp(new DateTime(new Date(splitEndEpoch)));
                    lap.setStartTime(new DateTime(new Date(splitStartEpoch)));
                    lap.setTotalElapsedTime(split.durationMs / 1000.0f);
                    lap.setTotalTimerTime(split.durationMs / 1000.0f);
                    lap.setTotalDistance((float) split.distanceM);
                    if (split.durationMs > 0) {
                        lap.setAvgSpeed((float) (
                                split.distanceM / (split.durationMs / 1000.0d)
                        ));
                    }
                    encoder.write(lap);
                    splitStartEpoch = splitEndEpoch;
                }
            }

            SessionMesg fitSession = new SessionMesg();
            fitSession.setTimestamp(new DateTime(new Date(endedAt)));
            fitSession.setStartTime(new DateTime(new Date(startedAt)));
            fitSession.setSport(walking ? Sport.WALKING : Sport.RUNNING);
            fitSession.setSubSport(SubSport.GENERIC);
            fitSession.setTotalElapsedTime(totalMs / 1000.0f);
            fitSession.setTotalTimerTime(movingMs / 1000.0f);
            fitSession.setTotalMovingTime(movingMs / 1000.0f);
            fitSession.setTotalDistance((float) session.distanceM);
            fitSession.setAvgSpeed((float) avgSpeed);
            fitSession.setMaxSpeed((float) maxSpeed);
            fitSession.setTotalAscent((int) Math.round(session.elevationGainM));
            fitSession.setTotalDescent((int) Math.round(session.elevationLossM));
            encoder.write(fitSession);

            ActivityMesg activity = new ActivityMesg();
            activity.setTimestamp(new DateTime(new Date(endedAt)));
            activity.setTotalTimerTime(movingMs / 1000.0f);
            activity.setNumSessions(1);
            activity.setType(Activity.MANUAL);
            activity.setEvent(Event.ACTIVITY);
            activity.setEventType(EventType.STOP);
            encoder.write(activity);
        } finally {
            encoder.close();
        }
    }

    private static long lastTimestamp(List<PointEntity> points, long fallback) {
        if (points != null) {
            for (int i = points.size() - 1; i >= 0; i--) {
                PointEntity point = points.get(i);
                if (point != null && point.timestamp > 0) {
                    return point.timestamp;
                }
            }
        }
        return fallback;
    }

    private static int semicircles(double degrees) {
        long value = Math.round(degrees * SEMICIRCLES_PER_DEGREE);
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
