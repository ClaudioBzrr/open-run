package com.openrun.core.stats;

import com.openrun.core.model.TrackPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight display smoothing for GPS tracks. Raw accepted points remain the
 * source of truth for distance and exports; this is used only for drawing the
 * route so stationary jitter and small zig-zags do not dominate the map.
 */
public final class RouteSmoother {
    private static final double[] WEIGHTS = {1.0d, 2.0d, 3.0d, 2.0d, 1.0d};

    private RouteSmoother() {
    }

    public static List<TrackPoint> smooth(List<TrackPoint> points) {
        if (points == null || points.size() < 3) {
            return points == null ? Collections.emptyList() : points;
        }
        List<TrackPoint> result = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            if (i == 0 || i == points.size() - 1) {
                result.add(points.get(i));
                continue;
            }
            double latitude = 0;
            double longitude = 0;
            double altitude = 0;
            double weightSum = 0;
            boolean hasAltitude = false;
            int start = Math.max(0, i - 2);
            int end = Math.min(points.size() - 1, i + 2);
            for (int j = start; j <= end; j++) {
                int weightIndex = j - (i - 2);
                double weight = WEIGHTS[Math.max(0, Math.min(WEIGHTS.length - 1, weightIndex))];
                TrackPoint point = points.get(j);
                latitude += point.getLatitude() * weight;
                longitude += point.getLongitude() * weight;
                weightSum += weight;
                if (!Double.isNaN(point.getAltitudeMeters())
                        && !Double.isInfinite(point.getAltitudeMeters())) {
                    altitude += point.getAltitudeMeters() * weight;
                    hasAltitude = true;
                }
            }
            TrackPoint center = points.get(i);
            result.add(new TrackPoint(
                    center.getSeq(),
                    latitude / weightSum,
                    longitude / weightSum,
                    hasAltitude ? altitude / weightSum : Double.NaN,
                    center.getAccuracyMeters(),
                    center.getSpeedMps(),
                    center.getBearingDegrees(),
                    center.getTimestampEpochMs(),
                    center.getElapsedMs(),
                    center.getMovingElapsedMs()
            ));
        }
        return result;
    }
}
