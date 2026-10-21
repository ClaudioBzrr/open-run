package com.openrun.app.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.LocaleList;

import com.openrun.app.R;
import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.core.model.ActivityType;
import com.openrun.core.stats.GeoMath;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Renders a one-page A4 activity report with a route sketch, stats and the
 * pace/elevation charts. The page uses fixed light colors so the exported
 * document stays readable regardless of the app theme.
 */
public final class SessionPdfWriter {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN = 44f;

    private static final int BRAND = Color.rgb(11, 110, 79);
    private static final int ACCENT = Color.rgb(255, 122, 69);
    private static final int INK = Color.rgb(27, 28, 26);
    private static final int MUTED = Color.rgb(95, 107, 100);
    private static final int LINE = Color.rgb(221, 229, 223);
    private static final int SOFT_BG = Color.rgb(245, 250, 246);
    private static final int WHITE = Color.WHITE;

    private static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);

    private SessionPdfWriter() {
    }

    public static void write(Context context,
                             OutputStream output,
                             SessionEntity session,
                             List<PointEntity> points) throws IOException {
        if (context == null || output == null || session == null) {
            throw new IllegalArgumentException("Context, output and session are required");
        }
        List<PointEntity> safePoints = points == null
                ? Collections.emptyList()
                : points;

        PdfDocument document = new PdfDocument();
        try {
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH,
                    PAGE_HEIGHT,
                    1
            ).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            draw(page.getCanvas(), context, session, safePoints);
            document.finishPage(page);
            document.writeTo(output);
        } finally {
            document.close();
        }
    }

    private static void draw(Canvas canvas,
                             Context context,
                             SessionEntity session,
                             List<PointEntity> points) {
        Locale locale = appLocale(context);
        ActivityType type = ActivityType.fromKey(session.type);
        String typeLabel = context.getString(
                type == ActivityType.WALK ? R.string.activity_walk : R.string.activity_run
        );
        long start = session.startedAt;
        long end = session.endedAt != null
                ? session.endedAt
                : start + session.movingMs + session.pausedMs;
        long total = Math.max(0, end - start);
        double pace = GeoMath.paceSecPerKm(session.distanceM, session.movingMs);
        float contentWidth = PAGE_WIDTH - (MARGIN * 2f);

        canvas.drawColor(WHITE);

        text(
                canvas,
                context.getString(R.string.app_name).toUpperCase(locale),
                MARGIN,
                50f,
                10.5f,
                BRAND,
                BOLD,
                Paint.Align.LEFT
        );
        text(
                canvas,
                context.getString(R.string.export_report_title).toUpperCase(locale),
                PAGE_WIDTH - MARGIN,
                50f,
                8.5f,
                MUTED,
                NORMAL,
                Paint.Align.RIGHT
        );
        text(
                canvas,
                typeLabel.toUpperCase(locale),
                MARGIN,
                90f,
                30f,
                INK,
                BOLD,
                Paint.Align.LEFT
        );
        text(
                canvas,
                Formatters.dateTime(context, start),
                MARGIN,
                112f,
                10.5f,
                MUTED,
                NORMAL,
                Paint.Align.LEFT
        );

        text(
                canvas,
                context.getString(R.string.label_distance).toUpperCase(locale),
                MARGIN,
                154f,
                8.5f,
                MUTED,
                NORMAL,
                Paint.Align.LEFT
        );
        text(
                canvas,
                Formatters.distance(context, session.distanceM),
                MARGIN,
                196f,
                34f,
                INK,
                BOLD,
                Paint.Align.LEFT
        );
        text(
                canvas,
                context.getString(R.string.label_pace).toUpperCase(locale),
                315f,
                154f,
                8.5f,
                MUTED,
                NORMAL,
                Paint.Align.LEFT
        );
        text(
                canvas,
                Formatters.pace(context, pace),
                315f,
                180f,
                20f,
                INK,
                BOLD,
                Paint.Align.LEFT
        );
        text(
                canvas,
                context.getString(R.string.label_elevation_gain).toUpperCase(locale),
                315f,
                210f,
                8.5f,
                MUTED,
                NORMAL,
                Paint.Align.LEFT
        );
        text(
                canvas,
                Formatters.elevation(context, session.elevationGainM),
                315f,
                236f,
                20f,
                INK,
                BOLD,
                Paint.Align.LEFT
        );

        drawLine(canvas, 254f);

        text(
                canvas,
                context.getString(R.string.detail_info_title),
                MARGIN,
                280f,
                13f,
                INK,
                BOLD,
                Paint.Align.LEFT
        );
        drawDetailRow(
                canvas,
                context.getString(R.string.label_start_time),
                Formatters.dateTime(context, start),
                308f
        );
        drawDetailRow(
                canvas,
                context.getString(R.string.label_end_time),
                Formatters.dateTime(context, end),
                334f
        );
        drawDetailRow(
                canvas,
                context.getString(R.string.label_total_duration),
                Formatters.duration(total),
                360f
        );
        drawDetailRow(
                canvas,
                context.getString(R.string.label_moving_time),
                Formatters.duration(session.movingMs),
                386f
        );

        drawLine(canvas, 404f);

        text(
                canvas,
                context.getString(R.string.performance_title),
                MARGIN,
                430f,
                13f,
                INK,
                BOLD,
                Paint.Align.LEFT
        );
        PerformanceSeries series = PerformanceSeriesBuilder.build(session, points);
        drawChart(
                canvas,
                context,
                context.getString(R.string.chart_pace_title),
                context.getString(R.string.chart_unit_pace),
                series.paceX,
                series.paceY,
                true,
                MARGIN,
                444f,
                contentWidth,
                94f,
                BRAND
        );
        drawChart(
                canvas,
                context,
                context.getString(R.string.chart_elevation_title),
                context.getString(R.string.chart_unit_elevation),
                series.elevationX,
                series.elevationY,
                false,
                MARGIN,
                548f,
                contentWidth,
                94f,
                ACCENT
        );

        drawLine(canvas, 654f);

        text(
                canvas,
                context.getString(R.string.export_route_title),
                MARGIN,
                682f,
                13f,
                INK,
                BOLD,
                Paint.Align.LEFT
        );
        text(
                canvas,
                context.getString(R.string.export_track_point_count, points.size()),
                PAGE_WIDTH - MARGIN,
                682f,
                8.5f,
                MUTED,
                NORMAL,
                Paint.Align.RIGHT
        );
        drawRouteSketch(canvas, context, points, MARGIN, 696f, contentWidth, 96f);

        text(
                canvas,
                context.getString(R.string.export_report_generated_by)
                        + " · "
                        + context.getString(R.string.share_footer),
                MARGIN,
                826f,
                8f,
                MUTED,
                NORMAL,
                Paint.Align.LEFT
        );
    }

    private static void drawDetailRow(Canvas canvas,
                                      String label,
                                      String value,
                                      float baseline) {
        text(canvas, label, MARGIN, baseline, 10.5f, MUTED, NORMAL, Paint.Align.LEFT);
        text(
                canvas,
                value,
                PAGE_WIDTH - MARGIN,
                baseline,
                10.5f,
                INK,
                BOLD,
                Paint.Align.RIGHT
        );

        Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);
        divider.setColor(Color.argb(150, 221, 229, 223));
        divider.setStrokeWidth(0.6f);
        canvas.drawLine(
                MARGIN,
                baseline + 11f,
                PAGE_WIDTH - MARGIN,
                baseline + 11f,
                divider
        );
    }

    private static void drawChart(Canvas canvas,
                                  Context context,
                                  String title,
                                  String unit,
                                  float[] xs,
                                  float[] ys,
                                  boolean paceChart,
                                  float left,
                                  float top,
                                  float width,
                                  float height,
                                  int color) {
        text(canvas, title, left + 2f, top + 12f, 9f, INK, BOLD, Paint.Align.LEFT);
        text(
                canvas,
                unit,
                left + width - 2f,
                top + 12f,
                8f,
                MUTED,
                NORMAL,
                Paint.Align.RIGHT
        );

        float chartLeft = left + 40f;
        float chartRight = left + width - 2f;
        float chartTop = top + 24f;
        float chartBottom = top + height - 4f;

        Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        grid.setColor(LINE);
        grid.setStrokeWidth(0.6f);
        for (int i = 0; i < 3; i++) {
            float y = chartTop + (chartBottom - chartTop) * i / 2f;
            canvas.drawLine(chartLeft, y, chartRight, y, grid);
        }

        if (xs == null || ys == null || xs.length < 2 || ys.length < 2) {
            text(
                    canvas,
                    context.getString(R.string.chart_no_data),
                    (chartLeft + chartRight) / 2f,
                    (chartTop + chartBottom) / 2f + 3f,
                    9f,
                    MUTED,
                    NORMAL,
                    Paint.Align.CENTER
            );
            return;
        }

        int count = Math.min(xs.length, ys.length);
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxX = 0f;
        for (int i = 0; i < count; i++) {
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
            maxX = Math.max(maxX, xs[i]);
        }
        if (maxX <= 0f) {
            maxX = 1f;
        }
        if (maxY - minY < 0.01f) {
            float padding = paceChart ? 10f : 2f;
            minY -= padding;
            maxY += padding;
        } else {
            float padding = (maxY - minY) * 0.08f;
            minY -= padding;
            maxY += padding;
        }

        drawChartValue(
                canvas,
                paceChart ? minY : maxY,
                chartLeft - 6f,
                chartTop + 3f,
                paceChart
        );
        drawChartValue(
                canvas,
                (minY + maxY) / 2f,
                chartLeft - 6f,
                (chartTop + chartBottom) / 2f + 3f,
                paceChart
        );
        drawChartValue(
                canvas,
                paceChart ? maxY : minY,
                chartLeft - 6f,
                chartBottom + 3f,
                paceChart
        );

        Path line = new Path();
        float firstX = 0f;
        float lastX = 0f;
        float lastY = 0f;
        for (int i = 0; i < count; i++) {
            float x = chartLeft + (xs[i] / maxX) * (chartRight - chartLeft);
            float normalized = (ys[i] - minY) / (maxY - minY);
            float y = paceChart
                    ? chartTop + normalized * (chartBottom - chartTop)
                    : chartBottom - normalized * (chartBottom - chartTop);
            if (i == 0) {
                line.moveTo(x, y);
                firstX = x;
            } else {
                line.lineTo(x, y);
            }
            lastX = x;
            lastY = y;
        }

        Path fill = new Path(line);
        fill.lineTo(lastX, chartBottom);
        fill.lineTo(firstX, chartBottom);
        fill.close();

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.argb(
                38,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        ));
        canvas.drawPath(fill, fillPaint);

        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1.5f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(color);
        canvas.drawPath(line, linePaint);

        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(color);
        canvas.drawCircle(lastX, lastY, 2.2f, dotPaint);
    }

    private static void drawChartValue(Canvas canvas,
                                       float value,
                                       float x,
                                       float y,
                                       boolean pace) {
        String label;
        if (pace) {
            int totalSeconds = Math.max(0, Math.round(value));
            label = String.format(
                    Locale.getDefault(),
                    "%d:%02d",
                    totalSeconds / 60,
                    totalSeconds % 60
            );
        } else {
            label = String.valueOf(Math.round(value));
        }
        text(canvas, label, x, y, 7.5f, MUTED, NORMAL, Paint.Align.RIGHT);
    }

    private static void drawRouteSketch(Canvas canvas,
                                        Context context,
                                        List<PointEntity> points,
                                        float left,
                                        float top,
                                        float width,
                                        float height) {
        RectF bounds = new RectF(left, top, left + width, top + height);

        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setStyle(Paint.Style.FILL);
        background.setColor(SOFT_BG);
        canvas.drawRoundRect(bounds, 10f, 10f, background);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(0.8f);
        border.setColor(LINE);
        canvas.drawRoundRect(bounds, 10f, 10f, border);

        List<PointEntity> valid = new ArrayList<>();
        for (PointEntity point : points) {
            if (point == null) {
                continue;
            }
            if (Double.isNaN(point.lat)
                    || Double.isInfinite(point.lat)
                    || Double.isNaN(point.lon)
                    || Double.isInfinite(point.lon)) {
                continue;
            }
            valid.add(point);
        }

        if (valid.isEmpty()) {
            text(
                    canvas,
                    context.getString(R.string.map_unavailable),
                    left + width / 2f,
                    top + height / 2f + 3f,
                    9f,
                    MUTED,
                    NORMAL,
                    Paint.Align.CENTER
            );
            return;
        }

        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        for (PointEntity point : valid) {
            minLat = Math.min(minLat, point.lat);
            maxLat = Math.max(maxLat, point.lat);
            minLon = Math.min(minLon, point.lon);
            maxLon = Math.max(maxLon, point.lon);
        }

        double centerLat = (minLat + maxLat) / 2.0d;
        double lonScale = Math.cos(Math.toRadians(centerLat));
        if (lonScale < 0.01d) {
            lonScale = 0.01d;
        }
        double spanX = (maxLon - minLon) * lonScale;
        double spanY = maxLat - minLat;
        double usableWidth = width - 24f;
        double usableHeight = height - 24f;
        double scaleX = spanX > 0 ? usableWidth / spanX : Double.MAX_VALUE;
        double scaleY = spanY > 0 ? usableHeight / spanY : Double.MAX_VALUE;
        double scale = Math.min(scaleX, scaleY);
        if (!Double.isFinite(scale) || scale <= 0) {
            scale = 1.0d;
        }

        double drawWidth = spanX * scale;
        double drawHeight = spanY * scale;
        double originX = left + (width - drawWidth) / 2.0d;
        double originY = top + (height - drawHeight) / 2.0d;

        Path route = new Path();
        float firstX = 0f;
        float firstY = 0f;
        float lastX = 0f;
        float lastY = 0f;
        for (int i = 0; i < valid.size(); i++) {
            PointEntity point = valid.get(i);
            float x = (float) (originX + (point.lon - minLon) * lonScale * scale);
            float y = (float) (originY + (maxLat - point.lat) * scale);
            if (i == 0) {
                route.moveTo(x, y);
                firstX = x;
                firstY = y;
            } else {
                route.lineTo(x, y);
            }
            lastX = x;
            lastY = y;
        }

        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(5f);
        halo.setStrokeCap(Paint.Cap.ROUND);
        halo.setStrokeJoin(Paint.Join.ROUND);
        halo.setColor(WHITE);
        canvas.drawPath(route, halo);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2f);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(BRAND);
        canvas.drawPath(route, line);

        drawMarker(
                canvas,
                firstX,
                firstY,
                BRAND,
                context.getString(R.string.marker_start_short)
        );
        drawMarker(
                canvas,
                lastX,
                lastY,
                ACCENT,
                context.getString(R.string.marker_end_short)
        );
    }

    private static void drawMarker(Canvas canvas,
                                   float x,
                                   float y,
                                   int color,
                                   String label) {
        Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
        outer.setStyle(Paint.Style.FILL);
        outer.setColor(WHITE);
        canvas.drawCircle(x, y, 6.5f, outer);

        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setStyle(Paint.Style.FILL);
        inner.setColor(color);
        canvas.drawCircle(x, y, 5.2f, inner);

        text(canvas, label, x, y + 1.9f, 5.4f, WHITE, BOLD, Paint.Align.CENTER);
    }

    private static void drawLine(Canvas canvas, float y) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(LINE);
        paint.setStrokeWidth(0.8f);
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint);
    }

    private static void text(Canvas canvas,
                             String value,
                             float x,
                             float y,
                             float size,
                             int color,
                             Typeface typeface,
                             Paint.Align align) {
        if (value == null) {
            return;
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(typeface);
        paint.setTextAlign(align);
        canvas.drawText(value, x, y, paint);
    }

    private static Locale appLocale(Context context) {
        LocaleList locales = context.getResources().getConfiguration().getLocales();
        return locales.isEmpty() ? Locale.getDefault() : locales.get(0);
    }
}
