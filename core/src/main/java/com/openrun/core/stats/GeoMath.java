package com.openrun.core.stats;

/**
 * Pure geospatial helpers, kept independent from Android so they can be
 * tested on the JVM and ported to another platform later.
 */
public final class GeoMath {
    private static final double EARTH_RADIUS_METERS = 6371000.0d;

    private GeoMath() {
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double sinPhi = Math.sin(deltaPhi / 2.0d);
        double sinLambda = Math.sin(deltaLambda / 2.0d);
        double a = sinPhi * sinPhi
                + Math.cos(phi1) * Math.cos(phi2) * sinLambda * sinLambda;
        double clamped = Math.max(0.0d, Math.min(1.0d, a));
        double c = 2.0d * Math.atan2(Math.sqrt(clamped), Math.sqrt(1.0d - clamped));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Seconds per kilometre for a distance covered in a duration.
     * Returns 0 when there is not enough data.
     */
    public static double paceSecPerKm(double distanceMeters, long durationMs) {
        if (distanceMeters <= 0 || durationMs <= 0) {
            return 0;
        }
        return durationMs / distanceMeters;
    }
}
