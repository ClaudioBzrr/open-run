package com.openrun.app.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuItemCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openrun.app.OpenRunApp;
import com.openrun.app.R;
import com.openrun.app.data.AppDatabase;
import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SessionRepository;
import com.openrun.app.data.SplitEntity;
import com.openrun.app.databinding.ActivitySessionDetailBinding;
import com.openrun.app.util.Formatters;
import com.openrun.app.util.PerformanceSeries;
import com.openrun.app.util.PerformanceSeriesBuilder;
import com.openrun.app.util.SessionFitWriter;
import com.openrun.app.util.SessionGpxWriter;
import com.openrun.app.util.SessionPdfWriter;
import com.openrun.app.util.SessionXlsxWriter;
import com.openrun.app.util.ShareCardGenerator;
import com.openrun.core.model.ActivityType;
import com.openrun.core.stats.GeoMath;
import com.openrun.core.stats.RouteSmoother;
import com.openrun.core.model.TrackPoint;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionDetailActivity extends AppCompatActivity {
    public static final String EXTRA_SESSION_ID = "com.openrun.app.extra.SESSION_ID";

    private static final int EXPORT_PDF = 1;
    private static final int EXPORT_XLSX = 2;
    private static final int EXPORT_GPX = 3;
    private static final int EXPORT_FIT = 4;

    private final ExecutorService shareExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> saveImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("image/png"),
                    this::onSaveImageSelected
            );
    private final ActivityResultLauncher<String> exportPdfLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/pdf"),
                    uri -> onExportDestinationSelected(uri, EXPORT_PDF)
            );
    private final ActivityResultLauncher<String> exportXlsxLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            "application/vnd.openxmlformats-officedocument."
                                    + "spreadsheetml.sheet"
                    ),
                    uri -> onExportDestinationSelected(uri, EXPORT_XLSX)
            );
    private final ActivityResultLauncher<String> exportGpxLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/gpx+xml"),
                    uri -> onExportDestinationSelected(uri, EXPORT_GPX)
            );
    private final ActivityResultLauncher<String> exportFitLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/vnd.ant.fit"),
                    uri -> onExportDestinationSelected(uri, EXPORT_FIT)
            );

    private ActivitySessionDetailBinding binding;
    private SessionRepository repository;
    private String sessionId;
    private SessionEntity currentSession;
    private List<PointEntity> currentPoints = Collections.emptyList();
    private List<SplitEntity> currentSplits = Collections.emptyList();
    private List<GeoPoint> routePoints = Collections.emptyList();
    private File pendingImageFile;
    private boolean shareBusy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySessionDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationContentDescription(R.string.navigate_back);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.inflateMenu(R.menu.session_detail);
        tintToolbarActions();
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_delete) {
                confirmDelete();
                return true;
            }
            if (itemId == R.id.action_export) {
                showExportDialog();
                return true;
            }
            return false;
        });
        binding.buttonSaveImage.setOnClickListener(v -> prepareShare(true));
        binding.buttonShare.setOnClickListener(v -> prepareShare(false));
        setBusy(false);

        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (sessionId == null) {
            finish();
            return;
        }

        repository = ((OpenRunApp) getApplication()).getRepository();
        configureMap();
        loadSession();
    }

    private void tintToolbarActions() {
        // The delete action uses a self-colored icon (red circle + white
        // glyph), so it stays readable even where AppCompat ignores menu item
        // icon tint. The export icon still follows the current theme.
        MenuItem export = binding.toolbar.getMenu().findItem(R.id.action_export);
        if (export != null) {
            MenuItemCompat.setIconTintList(
                    export,
                    ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.on_surface)
                    )
            );
        }
    }

    private void showExportDialog() {
        if (currentSession == null || shareBusy) {
            return;
        }
        String[] options = {
                getString(R.string.export_pdf),
                getString(R.string.export_xlsx),
                getString(R.string.export_gpx),
                getString(R.string.export_fit)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.export_title)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            exportPdfLauncher.launch(exportFileName("pdf"));
                            break;
                        case 1:
                            exportXlsxLauncher.launch(exportFileName("xlsx"));
                            break;
                        case 2:
                            exportGpxLauncher.launch(exportFileName("gpx"));
                            break;
                        case 3:
                            exportFitLauncher.launch(exportFileName("fit"));
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private String exportFileName(String extension) {
        SessionEntity session = currentSession;
        long when = session != null && session.startedAt > 0
                ? session.startedAt
                : System.currentTimeMillis();
        String type = session != null
                && ActivityType.fromKey(session.type) == ActivityType.WALK
                ? "walk"
                : "run";
        String stamp = new SimpleDateFormat(
                "yyyyMMdd-HHmm",
                Locale.US
        ).format(new Date(when));
        return "openrun-" + type + "-" + stamp + "." + extension;
    }

    private void onExportDestinationSelected(Uri uri, int format) {
        if (uri == null
                || currentSession == null
                || isFinishing()
                || isDestroyed()) {
            return;
        }
        final SessionEntity session = currentSession;
        final List<PointEntity> points = new ArrayList<>(currentPoints);
        final List<SplitEntity> splits = new ArrayList<>(currentSplits);
        setBusy(true);

        shareExecutor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) {
                    throw new IOException("Cannot open export destination");
                }
                if (format == EXPORT_PDF) {
                    SessionPdfWriter.write(this, output, session, points);
                } else if (format == EXPORT_XLSX) {
                    SessionXlsxWriter.write(this, output, session, points);
                } else if (format == EXPORT_GPX) {
                    SessionGpxWriter.write(output, session, points);
                } else if (format == EXPORT_FIT) {
                    File temporary = File.createTempFile(
                            "openrun-export",
                            ".fit",
                            getCacheDir()
                    );
                    try {
                        SessionFitWriter.write(temporary, session, points, splits);
                        try (InputStream input = new FileInputStream(temporary)) {
                            byte[] buffer = new byte[16 * 1024];
                            int read;
                            while ((read = input.read(buffer)) > 0) {
                                output.write(buffer, 0, read);
                            }
                        }
                    } finally {
                        if (temporary.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            temporary.delete();
                        }
                    }
                } else {
                    throw new IOException("Unknown export format");
                }
                output.flush();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBusy(false);
                    Toast.makeText(
                            this,
                            R.string.export_saved,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBusy(false);
                    Toast.makeText(
                            this,
                            R.string.export_error,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void configureMap() {
        MapView map = binding.map;
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.getController().setZoom(16.0);
        binding.buttonRecenterRoute.setOnClickListener(v -> fitRoute(true));
    }

    private void loadSession() {
        final String id = sessionId;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            SessionEntity session = repository.getSession(id);
            List<PointEntity> points = repository.loadPoints(id);
            List<SplitEntity> splits = repository.loadSplits(id);
            if (session == null) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Toast.makeText(
                            this,
                            R.string.session_not_found,
                            Toast.LENGTH_SHORT
                    ).show();
                    finish();
                });
                return;
            }

            // Chart series and route smoothing are linear in the number of
            // points, which can be thousands for a long activity. Keep that
            // work off the UI thread so opening and scrolling the detail
            // screen never waits for it.
            PerformanceSeries series = PerformanceSeriesBuilder.build(session, points);
            List<GeoPoint> route = buildRoute(points);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                currentSplits = splits == null
                        ? Collections.emptyList()
                        : splits;
                bind(session, points, series, route);
            });
        });
    }

    private void bind(SessionEntity session,
                      List<PointEntity> points,
                      PerformanceSeries series,
                      List<GeoPoint> route) {
        currentSession = session;
        currentPoints = points != null ? points : Collections.emptyList();
        setBusy(false);

        ActivityType type = ActivityType.fromKey(session.type);
        binding.toolbar.setTitle(
                type == ActivityType.WALK ? R.string.activity_walk : R.string.activity_run
        );

        binding.textDetailDate.setText(Formatters.dateTime(this, session.startedAt));
        binding.textDetailDistance.setText(Formatters.distance(this, session.distanceM));
        binding.textDetailPace.setText(
                Formatters.pace(
                        this,
                        GeoMath.paceSecPerKm(session.distanceM, session.movingMs)
                )
        );
        binding.textDetailElevation.setText(
                Formatters.elevation(this, session.elevationGainM)
        );

        long start = session.startedAt;
        long end = session.endedAt != null
                ? session.endedAt
                : start + session.movingMs + session.pausedMs;
        long total = Math.max(0, end - start);

        binding.textStartTime.setText(Formatters.dateTime(this, start));
        binding.textEndTime.setText(Formatters.dateTime(this, end));
        binding.textTotalDuration.setText(Formatters.duration(total));
        binding.textMovingTime.setText(Formatters.duration(session.movingMs));

        binding.chartPace.setData(
                getString(R.string.chart_pace_title),
                getString(R.string.chart_unit_pace),
                series.paceX,
                series.paceY,
                true,
                ContextCompat.getColor(this, R.color.brand_primary)
        );
        binding.chartElevation.setData(
                getString(R.string.chart_elevation_title),
                getString(R.string.chart_unit_elevation),
                series.elevationX,
                series.elevationY,
                false,
                ContextCompat.getColor(this, R.color.brand_accent)
        );

        applyRoute(route);
        renderSplits();
    }

    private void renderSplits() {
        binding.splitsContainer.removeAllViews();
        if (currentSplits == null || currentSplits.isEmpty()) {
            binding.cardSplits.setVisibility(View.GONE);
            return;
        }
        binding.cardSplits.setVisibility(View.VISIBLE);
        int padding = Math.round(8 * getResources().getDisplayMetrics().density);
        for (SplitEntity split : currentSplits) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, padding, 0, padding);

            TextView label = new TextView(this);
            label.setText(getString(R.string.split_km, split.splitIndex));
            label.setTextSize(14);
            label.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.8f
            ));

            TextView duration = new TextView(this);
            duration.setText(Formatters.duration(split.durationMs));
            duration.setTextSize(14);
            duration.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant));
            duration.setGravity(android.view.Gravity.END);
            duration.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            TextView pace = new TextView(this);
            pace.setText(Formatters.pace(this, split.paceSecPerKm));
            pace.setTextSize(14);
            pace.setTextColor(ContextCompat.getColor(this, R.color.brand_primary));
            pace.setGravity(android.view.Gravity.END);
            pace.setLayoutParams(new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            row.addView(label);
            row.addView(duration);
            row.addView(pace);
            binding.splitsContainer.addView(row);
        }
    }

    private void confirmDelete() {
        if (currentSession == null || shareBusy) {
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_activity_title)
                .setMessage(R.string.delete_activity_message)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.delete_activity, (d, which) ->
                        deleteActivity())
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(this, R.color.error)
        );
    }

    private void deleteActivity() {
        final String id = sessionId;
        if (id == null) {
            return;
        }
        setBusy(true);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                repository.deleteSession(id);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Toast.makeText(
                            this,
                            R.string.activity_deleted,
                            Toast.LENGTH_SHORT
                    ).show();
                    finish();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBusy(false);
                    Toast.makeText(
                            this,
                            R.string.delete_error,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void prepareShare(boolean save) {
        if (currentSession == null || shareBusy) {
            return;
        }

        final SessionEntity session = currentSession;
        final List<PointEntity> points = new ArrayList<>(currentPoints);
        setBusy(true);
        Toast.makeText(this, R.string.share_preparing, Toast.LENGTH_SHORT).show();

        shareExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = ShareCardGenerator.generate(this, session, points);
                File directory = new File(getCacheDir(), "share");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Cannot create share cache");
                }
                File file = new File(
                        directory,
                        "openrun-" + System.currentTimeMillis() + ".png"
                );
                try (FileOutputStream output = new FileOutputStream(file)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw new IllegalStateException("Cannot encode share card");
                    }
                }

                final File result = file;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBusy(false);
                    if (save) {
                        pendingImageFile = result;
                        saveImageLauncher.launch(result.getName());
                    } else {
                        shareImage(result);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBusy(false);
                    Toast.makeText(
                            this,
                            R.string.share_error,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
        });
    }

    private void shareImage(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("route", uri));
            startActivity(Intent.createChooser(
                    intent,
                    getString(R.string.share_chooser_title)
            ));
        } catch (ActivityNotFoundException | IllegalArgumentException error) {
            Toast.makeText(this, R.string.share_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void onSaveImageSelected(Uri uri) {
        if (uri == null || pendingImageFile == null) {
            return;
        }
        final File source = pendingImageFile;
        pendingImageFile = null;
        setBusy(true);

        shareExecutor.execute(() -> {
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) {
                    throw new IllegalStateException("Cannot open destination");
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBusy(false);
                    Toast.makeText(
                            this,
                            R.string.share_saved,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBusy(false);
                    Toast.makeText(
                            this,
                            R.string.share_error,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        shareBusy = busy;
        if (binding == null) {
            return;
        }
        binding.shareProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        boolean enabled = !busy && currentSession != null;
        binding.buttonSaveImage.setEnabled(enabled);
        binding.buttonShare.setEnabled(enabled);
        MenuItem export = binding.toolbar.getMenu().findItem(R.id.action_export);
        if (export != null) {
            export.setEnabled(enabled);
        }
        MenuItem delete = binding.toolbar.getMenu().findItem(R.id.action_delete);
        if (delete != null) {
            delete.setEnabled(enabled);
        }
    }

    private static List<GeoPoint> buildRoute(List<PointEntity> points) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }

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

        List<GeoPoint> route = new ArrayList<>(accepted.size());
        for (TrackPoint point : RouteSmoother.smooth(accepted)) {
            route.add(new GeoPoint(point.getLatitude(), point.getLongitude()));
        }
        return route;
    }

    private void applyRoute(List<GeoPoint> route) {
        binding.mapProgress.setVisibility(View.GONE);
        binding.map.getOverlays().clear();
        routePoints = Collections.emptyList();

        if (route == null || route.isEmpty()) {
            showEmptyMap();
            return;
        }

        binding.map.setVisibility(View.VISIBLE);
        binding.textMapEmpty.setVisibility(View.GONE);
        binding.textMapAttribution.setVisibility(View.VISIBLE);
        binding.buttonRecenterRoute.setVisibility(View.VISIBLE);

        float density = getResources().getDisplayMetrics().density;

        // A light casing under the route keeps the line readable over dense
        // map areas and on long, zoomed-out routes.
        Polyline halo = new Polyline(binding.map);
        halo.setPoints(route);
        halo.setColor(ContextCompat.getColor(this, R.color.map_route_halo));
        halo.setWidth(13f * density);
        Paint haloPaint = halo.getOutlinePaint();
        haloPaint.setStrokeCap(Paint.Cap.ROUND);
        haloPaint.setStrokeJoin(Paint.Join.ROUND);
        haloPaint.setAlpha(235);
        binding.map.getOverlays().add(halo);

        Polyline line = new Polyline(binding.map);
        line.setPoints(route);
        line.setColor(ContextCompat.getColor(this, R.color.map_route));
        line.setWidth(7f * density);
        Paint linePaint = line.getOutlinePaint();
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        binding.map.getOverlays().add(line);

        Marker start = new Marker(binding.map);
        start.setPosition(route.get(0));
        start.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_map_start));
        start.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        start.setTitle(getString(R.string.marker_start));
        binding.map.getOverlays().add(start);

        Marker end = new Marker(binding.map);
        end.setPosition(route.get(route.size() - 1));
        end.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_map_end));
        end.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        end.setTitle(getString(R.string.marker_end));
        binding.map.getOverlays().add(end);

        routePoints = route;
        binding.map.post(() -> fitRoute(false));
    }

    private void showEmptyMap() {
        binding.map.getOverlays().clear();
        binding.map.setVisibility(View.GONE);
        binding.textMapEmpty.setVisibility(View.VISIBLE);
        binding.textMapAttribution.setVisibility(View.GONE);
        binding.buttonRecenterRoute.setVisibility(View.GONE);
        binding.mapProgress.setVisibility(View.GONE);
    }

    private void fitRoute(boolean animate) {
        if (routePoints.isEmpty()) {
            return;
        }
        if (routePoints.size() == 1) {
            binding.map.getController().setCenter(routePoints.get(0));
            binding.map.getController().setZoom(17.0);
        } else {
            BoundingBox box = BoundingBox.fromGeoPoints(routePoints);
            int padding = Math.round(
                    48f * getResources().getDisplayMetrics().density
            );
            binding.map.zoomToBoundingBox(box, animate, padding);
        }
        binding.map.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) {
            binding.map.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (binding != null) {
            binding.map.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        shareExecutor.shutdownNow();
        if (binding != null) {
            binding.map.onDetach();
        }
        super.onDestroy();
    }
}
