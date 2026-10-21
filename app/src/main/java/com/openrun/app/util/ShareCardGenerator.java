package com.openrun.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.openrun.app.R;
import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.core.model.ActivityType;
import com.openrun.core.model.TrackPoint;
import com.openrun.core.stats.GeoMath;
import com.openrun.core.stats.RouteSmoother;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Renders a shareable PNG card with the route map and activity stats.
 *
 * <p>Map tiles are downloaded on the calling background thread. If tiles are
 * unavailable, the route is drawn on a clean map-like background instead of
 * failing the share action.</p>
 */
public final class ShareCardGenerator {
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1350;

    private static final int MAP_LEFT = 48;
    private static final int MAP_TOP = 220;
    private static final int MAP_RIGHT = 1032;
    private static final int MAP_BOTTOM = 870;
    private static final int MAP_WIDTH = MAP_RIGHT - MAP_LEFT;
    private static final int MAP_HEIGHT = MAP_BOTTOM - MAP_TOP;

    private static final int TILE_SIZE = 256;
    private static final int MIN_ZOOM = 6;
    private static final int MAX_ZOOM = 16;
    private static final int MAX_TILES = 24;
    private static final String TILE_URL = "https://tile.openstreetmap.org/";

    private static final int CARD_BG = Color.rgb(255, 251, 248);
    private static final int INK = Color.rgb(27, 28, 26);
    private static final int MUTED = Color.rgb(95, 107, 100);
    private static final int BRAND = Color.rgb(11, 110, 79);
    private static final int ACCENT = Color.rgb(255, 122, 69);
    private static final int LINE = Color.rgb(221, 229, 223);
    private static final int WHITE = Color.WHITE;

    private static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);

    private ShareCardGenerator() {
    }

    public static Bitmap generate(Context context,
                                  SessionEntity session,
                                  List<PointEntity> points) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(CARD_BG);

        Locale locale = appLocale(context);
        ActivityType type = ActivityType.fromKey(session.type);
        String typeLabel = context.getString(
                type == ActivityType.WALK ? R.string.activity_walk : R.string.activity_run
        ).toUpperCase(locale);
        String appName = context.getString(R.string.app_name).toUpperCase(locale);

        long start = session.startedAt;
        long end = session.endedAt != null
                ? session.endedAt
                : start + session.movingMs + session.pausedMs;
        long total = Math.max(0, end - start);

        String distance = Formatters.distance(context, session.distanceM);
        String duration = Formatters.duration(total);
        String pace = Formatters.pace(
                context,
                GeoMath.paceSecPerKm(session.distanceM, session.movingMs)
        );
        String startTime = Formatters.time(context, start);
        String endTime = Formatters.time(context, end);
        String elevation = Formatters.elevation(context, session.elevationGainM);

        drawText(canvas, appName, 56, 92, 34, BRAND, BOLD, Paint.Align.LEFT);
        drawText(
                canvas,
                Formatters.date(context, start),
                1024,
                92,
                27,
                MUTED,
                NORMAL,
                Paint.Align.RIGHT
        );
        drawText(canvas, typeLabel, 56, 186, 56, INK, BOLD, Paint.Align.LEFT);

        drawMap(canvas, context, points);

        drawStat(
                canvas,
                56,
                context.getString(R.string.label_distance).toUpperCase(locale),
                distance
        );
        drawStat(
                canvas,
                396,
                context.getString(R.string.label_duration).toUpperCase(locale),
                duration
        );
        drawStat(
                canvas,
                736,
                context.getString(R.string.label_pace).toUpperCase(locale),
                pace
        );

        Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);
        divider.setColor(LINE);
        divider.setStrokeWidth(2f);
        canvas.drawLine(56, 1054, 1024, 1054, divider);

        drawDetail(
                canvas,
                56,
                context.getString(R.string.label_start_time).toUpperCase(locale),
                startTime
        );
        drawDetail(
                canvas,
                396,
                context.getString(R.string.label_end_time).toUpperCase(locale),
                endTime
        );
        drawDetail(
                canvas,
                736,
                context.getString(R.string.label_elevation_gain).toUpperCase(locale),
                elevation
        );

        drawText(canvas, appName, 56, 1286, 26, BRAND, BOLD, Paint.Align.LEFT);
        drawText(
                canvas,
                context.getString(R.string.share_footer).toUpperCase(locale),
                1024,
                1286,
                20,
                MUTED,
                NORMAL,
                Paint.Align.RIGHT
        );

        return bitmap;
    }

    private static void drawMap(Canvas canvas, Context context, List<PointEntity> points) {
        RectF mapRect = new RectF(MAP_LEFT, MAP_TOP, MAP_RIGHT, MAP_BOTTOM);
        Path clip = new Path();
        clip.addRoundRect(mapRect, 36, 36, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clip);
        drawMapBackground(canvas);

        if (points == null || points.isEmpty()) {
            drawText(
                    canvas,
                    context.getString(R.string.map_unavailable),
                    MAP_LEFT + MAP_WIDTH / 2f,
                    MAP_TOP + MAP_HEIGHT / 2f,
                    28,
                    MUTED,
                    NORMAL,
                    Paint.Align.CENTER
            );
            canvas.restore();
            drawMapBorder(canvas, mapRect);
            return;
        }

        points = smoothForDisplay(points);
        Projection projection = chooseProjection(points);
        if (projection == null) {
            canvas.restore();
            drawMapBorder(canvas, mapRect);
            return;
        }

        double offsetX = MAP_LEFT
                + (MAP_WIDTH - projection.width()) / 2.0d
                - projection.minX;
        double offsetY = MAP_TOP
                + (MAP_HEIGHT - projection.height()) / 2.0d
                - projection.minY;

        int tileCount = 1 << projection.zoom;
        int tileXMin = clamp((int) Math.floor((MAP_LEFT - offsetX) / TILE_SIZE), 0, tileCount - 1);
        int tileXMax = clamp((int) Math.floor((MAP_RIGHT - offsetX) / TILE_SIZE), 0, tileCount - 1);
        int tileYMin = clamp((int) Math.floor((MAP_TOP - offsetY) / TILE_SIZE), 0, tileCount - 1);
        int tileYMax = clamp((int) Math.floor((MAP_BOTTOM - offsetY) / TILE_SIZE), 0, tileCount - 1);

        boolean tilesDrawn = false;
        List<int[]> tileRequests = new ArrayList<>();
        for (int tileX = tileXMin; tileX <= tileXMax; tileX++) {
            for (int tileY = tileYMin; tileY <= tileYMax; tileY++) {
                if (tileRequests.size() >= MAX_TILES) {
                    break;
                }
                tileRequests.add(new int[]{tileX, tileY});
            }
        }

        ExecutorService tileExecutor = Executors.newFixedThreadPool(4);
        List<Future<Bitmap>> tileFutures = new ArrayList<>(tileRequests.size());
        for (int[] request : tileRequests) {
            tileFutures.add(tileExecutor.submit(
                    () -> loadTile(context, projection.zoom, request[0], request[1])
            ));
        }

        for (int i = 0; i < tileRequests.size(); i++) {
            try {
                Bitmap tile = tileFutures.get(i).get(4, TimeUnit.SECONDS);
                if (tile != null) {
                    int[] request = tileRequests.get(i);
                    float left = (float) (offsetX + request[0] * TILE_SIZE);
                    float top = (float) (offsetY + request[1] * TILE_SIZE);
                    canvas.drawBitmap(tile, left, top, null);
                    tile.recycle();
                    tilesDrawn = true;
                }
            } catch (Exception ignored) {
                // Missing tiles fall back to the map-style background.
            }
        }
        tileExecutor.shutdownNow();

        Path route = new Path();
        for (int i = 0; i < projection.xs.length; i++) {
            float x = (float) (offsetX + projection.xs[i]);
            float y = (float) (offsetY + projection.ys[i]);
            if (i == 0) {
                route.moveTo(x, y);
            } else {
                route.lineTo(x, y);
            }
        }

        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(28f);
        halo.setStrokeCap(Paint.Cap.ROUND);
        halo.setStrokeJoin(Paint.Join.ROUND);
        halo.setColor(Color.argb(225, 255, 255, 255));
        canvas.drawPath(route, halo);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(14f);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(BRAND);
        canvas.drawPath(route, line);

        drawMarker(
                canvas,
                (float) (offsetX + projection.xs[0]),
                (float) (offsetY + projection.ys[0]),
                BRAND,
                context.getString(R.string.marker_start_short)
        );
        int last = projection.xs.length - 1;
        drawMarker(
                canvas,
                (float) (offsetX + projection.xs[last]),
                (float) (offsetY + projection.ys[last]),
                ACCENT,
                context.getString(R.string.marker_end_short)
        );

        if (tilesDrawn) {
            drawAttribution(canvas, context.getString(R.string.map_attribution));
        }

        canvas.restore();
        drawMapBorder(canvas, mapRect);
    }

    private static void drawMapBackground(Canvas canvas) {
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setShader(new LinearGradient(
                0,
                MAP_TOP,
                0,
                MAP_BOTTOM,
                Color.rgb(245, 250, 246),
                Color.rgb(228, 240, 232),
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(MAP_LEFT, MAP_TOP, MAP_RIGHT, MAP_BOTTOM, background);

        // A soft, map-like fallback. The old checkerboard grid looked like
        // missing tiles when a long route was rendered without connectivity.
        Paint park = new Paint(Paint.ANTI_ALIAS_FLAG);
        park.setColor(Color.argb(80, 194, 224, 203));
        canvas.drawCircle(MAP_LEFT + 180, MAP_TOP + 190, 190, park);
        canvas.drawCircle(MAP_RIGHT - 150, MAP_BOTTOM - 110, 150, park);

        Path roads = new Path();
        roads.moveTo(MAP_LEFT - 40, MAP_TOP + 150);
        roads.cubicTo(
                MAP_LEFT + 280,
                MAP_TOP + 50,
                MAP_RIGHT - 330,
                MAP_TOP + 310,
                MAP_RIGHT + 40,
                MAP_TOP + 220
        );
        roads.moveTo(MAP_LEFT + 130, MAP_BOTTOM + 40);
        roads.cubicTo(
                MAP_LEFT + 260,
                MAP_BOTTOM - 230,
                MAP_RIGHT - 190,
                MAP_BOTTOM - 270,
                MAP_RIGHT - 60,
                MAP_TOP - 40
        );

        Paint road = new Paint(Paint.ANTI_ALIAS_FLAG);
        road.setStyle(Paint.Style.STROKE);
        road.setStrokeWidth(24f);
        road.setStrokeCap(Paint.Cap.ROUND);
        road.setStrokeJoin(Paint.Join.ROUND);
        road.setColor(Color.argb(165, 255, 255, 255));
        canvas.drawPath(roads, road);

        Paint roadEdge = new Paint(road);
        roadEdge.setStrokeWidth(2f);
        roadEdge.setColor(Color.argb(55, 150, 174, 160));
        canvas.drawPath(roads, roadEdge);
    }

    private static void drawMapBorder(Canvas canvas, RectF mapRect) {
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2f);
        border.setColor(LINE);
        canvas.drawRoundRect(mapRect, 36, 36, border);
    }

    private static void drawAttribution(Canvas canvas, String text) {
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(17f);
        textPaint.setTypeface(NORMAL);
        textPaint.setColor(WHITE);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        float textWidth = textPaint.measureText(text);

        RectF background = new RectF(
                MAP_RIGHT - textWidth - 34,
                MAP_BOTTOM - 44,
                MAP_RIGHT - 10,
                MAP_BOTTOM - 10
        );
        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Color.argb(155, 0, 0, 0));
        canvas.drawRoundRect(background, 12, 12, backgroundPaint);
        canvas.drawText(text, MAP_RIGHT - 22, MAP_BOTTOM - 21, textPaint);
    }

    private static void drawMarker(Canvas canvas, float x, float y, int color, String label) {
        Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
        outer.setColor(WHITE);
        canvas.drawCircle(x, y, 27f, outer);

        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setColor(color);
        canvas.drawCircle(x, y, 21f, inner);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(WHITE);
        text.setTextSize(20f);
        text.setTypeface(BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, x, y + 7f, text);
    }

    private static void drawStat(Canvas canvas, float x, String label, String value) {
        drawText(canvas, label, x, 936, 20, MUTED, NORMAL, Paint.Align.LEFT);
        drawText(canvas, value, x, 1008, 52, INK, BOLD, Paint.Align.LEFT);
    }

    private static void drawDetail(Canvas canvas, float x, String label, String value) {
        drawText(canvas, label, x, 1106, 19, MUTED, NORMAL, Paint.Align.LEFT);
        drawText(canvas, value, x, 1148, 29, INK, BOLD, Paint.Align.LEFT);
    }

    private static void drawText(Canvas canvas,
                                 String text,
                                 float x,
                                 float y,
                                 float size,
                                 int color,
                                 Typeface typeface,
                                 Paint.Align align) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(typeface);
        paint.setTextAlign(align);
        canvas.drawText(text, x, y, paint);
    }

    private static List<PointEntity> smoothForDisplay(List<PointEntity> points) {
        List<TrackPoint> accepted = new ArrayList<>(points.size());
        for (PointEntity point : points) {
            if (point == null
                    || Double.isNaN(point.lat)
                    || Double.isInfinite(point.lat)
                    || Double.isNaN(point.lon)
                    || Double.isInfinite(point.lon)) {
                continue;
            }
            accepted.add(new TrackPoint(
                    point.seq,
                    point.lat,
                    point.lon,
                    point.altitude == null ? Double.NaN : point.altitude,
                    point.accuracy == null ? Double.NaN : point.accuracy,
                    point.speed == null ? Double.NaN : point.speed,
                    point.bearing == null ? Double.NaN : point.bearing,
                    point.timestamp,
                    point.elapsedMs,
                    point.movingElapsedMs
            ));
        }
        List<PointEntity> smoothed = new ArrayList<>();
        for (TrackPoint point : RouteSmoother.smooth(accepted)) {
            PointEntity entity = new PointEntity();
            entity.lat = point.getLatitude();
            entity.lon = point.getLongitude();
            smoothed.add(entity);
        }
        return smoothed;
    }

    private static Projection chooseProjection(List<PointEntity> points) {
        Projection fallback = null;
        for (int zoom = MAX_ZOOM; zoom >= MIN_ZOOM; zoom--) {
            Projection projection = new Projection(points, zoom);
            fallback = projection;
            if (projection.width() <= MAP_WIDTH - 120
                    && projection.height() <= MAP_HEIGHT - 120) {
                return projection;
            }
        }
        return fallback;
    }

    private static Bitmap loadTile(Context context, int zoom, int x, int y) {
        File cacheDir = new File(context.getCacheDir(), "share-tiles");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            return null;
        }
        File cacheFile = new File(cacheDir, zoom + "_" + x + "_" + y + ".png");
        if (cacheFile.exists()) {
            Bitmap cached = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
            if (cached != null) {
                return cached;
            }
            // A partial/corrupt cache entry should not poison future shares.
            cacheFile.delete();
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(TILE_URL + zoom + "/" + x + "/" + y + ".png");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            connection.setRequestProperty(
                    "User-Agent",
                    "OpenRun/" + context.getPackageName()
                            + " (https://github.com/ClaudioBzrr/open-run)"
            );
            connection.setRequestProperty("Accept", "image/png");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                Bitmap tile = BitmapFactory.decodeStream(input);
                if (tile != null) {
                    try (FileOutputStream output = new FileOutputStream(cacheFile)) {
                        tile.compress(Bitmap.CompressFormat.PNG, 100, output);
                    } catch (Exception ignored) {
                    }
                }
                return tile;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Locale appLocale(Context context) {
        android.os.LocaleList locales =
                context.getResources().getConfiguration().getLocales();
        return locales.isEmpty() ? Locale.getDefault() : locales.get(0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Projection {
        final double[] xs;
        final double[] ys;
        final int zoom;
        final double minX;
        final double maxX;
        final double minY;
        final double maxY;

        Projection(List<PointEntity> points, int zoom) {
            this.zoom = zoom;
            xs = new double[points.size()];
            ys = new double[points.size()];
            double localMinX = Double.MAX_VALUE;
            double localMaxX = -Double.MAX_VALUE;
            double localMinY = Double.MAX_VALUE;
            double localMaxY = -Double.MAX_VALUE;

            for (int i = 0; i < points.size(); i++) {
                PointEntity point = points.get(i);
                double[] xy = project(point.lat, point.lon, zoom);
                xs[i] = xy[0];
                ys[i] = xy[1];
                localMinX = Math.min(localMinX, xy[0]);
                localMaxX = Math.max(localMaxX, xy[0]);
                localMinY = Math.min(localMinY, xy[1]);
                localMaxY = Math.max(localMaxY, xy[1]);
            }

            minX = localMinX;
            maxX = localMaxX;
            minY = localMinY;
            maxY = localMaxY;
        }

        double width() {
            return maxX - minX;
        }

        double height() {
            return maxY - minY;
        }
    }

    private static double[] project(double latitude, double longitude, int zoom) {
        double lat = Math.max(-85.05112878d, Math.min(85.05112878d, latitude));
        double worldSize = TILE_SIZE * Math.pow(2.0d, zoom);
        double x = (longitude + 180.0d) / 360.0d * worldSize;
        double latRad = Math.toRadians(lat);
        double y = (1.0d - Math.log(
                Math.tan(latRad) + 1.0d / Math.cos(latRad)
        ) / Math.PI) / 2.0d * worldSize;
        return new double[]{x, y};
    }
}
