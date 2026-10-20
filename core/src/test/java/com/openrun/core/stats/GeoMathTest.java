package com.openrun.core.stats;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GeoMathTest {

    @Test
    public void distanceForOneThousandthOfLongitudeAtEquator() {
        double meters = GeoMath.distanceMeters(0, 0, 0, 0.001);
        assertEquals(111.19d, meters, 0.5d);
    }

    @Test
    public void paceIsSecondsPerKilometre() {
        assertEquals(300.0d, GeoMath.paceSecPerKm(1000, 300_000), 0.001d);
        assertEquals(0.0d, GeoMath.paceSecPerKm(0, 300_000), 0.001d);
    }
}
