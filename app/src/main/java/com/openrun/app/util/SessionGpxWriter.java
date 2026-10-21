package com.openrun.app.util;

import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.core.model.ActivityType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * GPX 1.1 export for interoperability with Strava, Garmin, Komoot and others.
 */
public final class SessionGpxWriter {
    private SessionGpxWriter() {
    }

    public static void write(OutputStream output,
                             SessionEntity session,
                             List<PointEntity> points) throws IOException {
        String type = ActivityType.fromKey(session.type) == ActivityType.WALK
                ? "walking"
                : "running";
        String name = "Open Run " + type;
        String time = session.startedAt > 0
                ? Instant.ofEpochMilli(session.startedAt).toString()
                : "";

        StringBuilder xml = new StringBuilder(4096 + points.size() * 120);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<gpx version=\"1.1\" creator=\"Open Run\" ")
                .append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
                .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                .append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 ")
                .append("http://www.topografix.com/GPX/1/1/gpx.xsd\">\n");
        xml.append("  <metadata><name>").append(escape(name)).append("</name>");
        if (!time.isEmpty()) {
            xml.append("<time>").append(time).append("</time>");
        }
        xml.append("</metadata>\n");
        xml.append("  <trk><name>").append(escape(name)).append("</name><type>")
                .append(type).append("</type><trkseg>\n");

        for (PointEntity point : points) {
            if (point == null
                    || Double.isNaN(point.lat)
                    || Double.isInfinite(point.lat)
                    || Double.isNaN(point.lon)
                    || Double.isInfinite(point.lon)) {
                continue;
            }
            xml.append("    <trkpt lat=\"")
                    .append(String.format(Locale.US, "%.7f", point.lat))
                    .append("\" lon=\"")
                    .append(String.format(Locale.US, "%.7f", point.lon))
                    .append("\">");
            if (point.altitude != null
                    && !Double.isNaN(point.altitude)
                    && !Double.isInfinite(point.altitude)) {
                xml.append("<ele>")
                        .append(String.format(Locale.US, "%.1f", point.altitude))
                        .append("</ele>");
            }
            if (point.timestamp > 0) {
                xml.append("<time>")
                        .append(Instant.ofEpochMilli(point.timestamp))
                        .append("</time>");
            }
            xml.append("</trkpt>\n");
        }

        xml.append("  </trkseg></trk>\n</gpx>\n");
        output.write(xml.toString().getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
