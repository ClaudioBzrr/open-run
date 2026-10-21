package com.openrun.core.stats;

import com.openrun.core.model.TrackPoint;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RouteSmootherTest {
    @Test
    public void smoothingPreservesEndpointsAndPointCount() {
        List<TrackPoint> points = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            points.add(new TrackPoint(
                    i,
                    -23.55 + i * 0.0001,
                    -46.63 + i * 0.0001,
                    760,
                    4,
                    3,
                    45,
                    1_000 + i * 1_000L,
                    i * 1_000L,
                    i * 1_000L
            ));
        }
        List<TrackPoint> smoothed = RouteSmoother.smooth(points);
        assertEquals(points.size(), smoothed.size());
        assertEquals(
                points.get(0).getLatitude(),
                smoothed.get(0).getLatitude(),
                0.0000001
        );
        assertEquals(
                points.get(points.size() - 1).getLongitude(),
                smoothed.get(smoothed.size() - 1).getLongitude(),
                0.0000001
        );
    }
}
