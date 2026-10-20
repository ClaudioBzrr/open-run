package com.openrun.app.util;

import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.core.model.ActivityType;
import com.openrun.core.stats.GeoMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds pace and elevation series for the performance charts.
 *
 * <p>Pace is calculated over a rolling 30-second window, then downsampled for
 * drawing. Point elapsed times are scaled to the session's moving time so
 * paused periods do not make the pace look artificially slow.</p>
 */
public final class PerformanceSeriesBuilder {
    private static final long PACE_WINDOW_MS = 30_000L;
    private static final long MIN_PACE_WINDOW_MS = 10_000L;
    private static final double MIN_PACE_DISTANCE_METERS = 15.0d;
    private static final int MAX_POINTS = 160;

    private PerformanceSeriesBuilder() {
    }

    public static PerformanceSeries build(SessionEntity session, List<PointEntity> points) {
        if (session == null || points == null || points.size() < 2) {
            return empty();
        }

        List<PointEntity> valid = new ArrayList<>(points.size());
        for (PointEntity point : points) {
            if (point != null
                    && !Double.isNaN(point.lat)
                    && !Double.isInfinite(point.lat)
                    && !Double.isNaN(point.lon)
                    && !Double.isInfinite(point.lon)) {
                valid.add(point);
            }
        }
        if (valid.size() < 2) {
            return empty();
        }

        int count = valid.size();
        double[] distance = new double[count];
        long[] elapsed = new long[count];
        double[] altitude = new double[count];
        double smoothedAltitude = Double.NaN;

        elapsed[0] = Math.max(0, valid.get(0).elapsedMs);
        distance[0] = 0;
        for (int i = 1; i < count; i++) {
            PointEntity previous = valid.get(i - 1);
            PointEntity current = valid.get(i);
            distance[i] = distance[i - 1] + GeoMath.distanceMeters(
                    previous.lat,
                    previous.lon,
                    current.lat,
                    current.lon
            );
            elapsed[i] = Math.max(elapsed[i - 1], Math.max(0, current.elapsedMs));
        }

        for (int i = 0; i < count; i++) {
            Double value = valid.get(i).altitude;
            altitude[i] = Double.NaN;
            if (value != null && !Double.isNaN(value) && !Double.isInfinite(value)) {
                if (Double.isNaN(smoothedAltitude)) {
                    smoothedAltitude = value;
                } else {
                    smoothedAltitude = smoothedAltitude + 0.25d * (value - smoothedAltitude);
                }
                altitude[i] = smoothedAltitude;
            }
        }

        long finalElapsed = elapsed[count - 1];
        double movingScale = 1.0d;
        if (session.movingMs > 0 && finalElapsed > 0) {
            movingScale = session.movingMs / (double) finalElapsed;
        }
        for (int i = 0; i < count; i++) {
            elapsed[i] = Math.round(elapsed[i] * movingScale);
        }

        ActivityType type = ActivityType.fromKey(session.type);
        float minPace = type == ActivityType.WALK ? 240f : 150f;
        float maxPace = type == ActivityType.WALK ? 1800f : 900f;

        List<Float> paceX = new ArrayList<>();
        List<Float> paceY = new ArrayList<>();
        int windowStart = 0;
        for (int i = 2; i < count; i++) {
            while (windowStart < i
                    && elapsed[i] - elapsed[windowStart] > PACE_WINDOW_MS) {
                windowStart++;
            }
            long windowMs = elapsed[i] - elapsed[windowStart];
            if (windowMs < MIN_PACE_WINDOW_MS) {
                continue;
            }
            double windowMeters = distance[i] - distance[windowStart];
            if (windowMeters < MIN_PACE_DISTANCE_METERS) {
                continue;
            }
            double pace = windowMs / (windowMeters / 1000.0d);
            if (pace < minPace || pace > maxPace) {
                continue;
            }
            paceX.add((float) (distance[i] / 1000.0d));
            paceY.add((float) pace);
        }

        List<Float> elevationX = new ArrayList<>();
        List<Float> elevationY = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (!Double.isNaN(altitude[i])) {
                elevationX.add((float) (distance[i] / 1000.0d));
                elevationY.add((float) altitude[i]);
            }
        }

        downsample(paceX, paceY);
        downsample(elevationX, elevationY);

        return new PerformanceSeries(
                toArray(paceX),
                toArray(paceY),
                toArray(elevationX),
                toArray(elevationY)
        );
    }

    private static void downsample(List<Float> x, List<Float> y) {
        if (x.size() <= MAX_POINTS) {
            return;
        }
        int step = (int) Math.ceil(x.size() / (double) MAX_POINTS);
        List<Float> downX = new ArrayList<>();
        List<Float> downY = new ArrayList<>();
        for (int i = 0; i < x.size(); i += step) {
            downX.add(x.get(i));
            downY.add(y.get(i));
        }
        int last = x.size() - 1;
        if (downX.isEmpty()
                || Float.compare(downX.get(downX.size() - 1), x.get(last)) != 0) {
            downX.add(x.get(last));
            downY.add(y.get(last));
        }
        x.clear();
        y.clear();
        x.addAll(downX);
        y.addAll(downY);
    }

    private static float[] toArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static PerformanceSeries empty() {
        return new PerformanceSeries(
                new float[0],
                new float[0],
                new float[0],
                new float[0]
        );
    }
}
