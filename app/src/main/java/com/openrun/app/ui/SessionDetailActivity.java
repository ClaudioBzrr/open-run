package com.openrun.app.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openrun.app.OpenRunApp;
import com.openrun.app.R;
import com.openrun.app.data.AppDatabase;
import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SessionRepository;
import com.openrun.app.databinding.ActivitySessionDetailBinding;
import com.openrun.app.util.Formatters;
import com.openrun.app.util.PerformanceSeries;
import com.openrun.app.util.PerformanceSeriesBuilder;
import com.openrun.app.util.ShareCardGenerator;
import com.openrun.core.model.ActivityType;
import com.openrun.core.stats.GeoMath;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionDetailActivity extends AppCompatActivity {
    public static final String EXTRA_SESSION_ID = "com.openrun.app.extra.SESSION_ID";

    private final ExecutorService shareExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> saveImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("image/png"),
                    this::onSaveImageSelected
            );

    private ActivitySessionDetailBinding binding;
    private SessionRepository repository;
    private String sessionId;
    private SessionEntity currentSession;
    private List<PointEntity> currentPoints = Collections.emptyList();
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
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete) {
                confirmDelete();
                return true;
            }
            return false;
        });
        binding.buttonSaveImage.setOnClickListener(v -> prepareShare(true));
        binding.buttonShare.setOnClickListener(v -> prepareShare(false));
        setShareBusy(false);

        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (sessionId == null) {
            finish();
            return;
        }

        repository = ((OpenRunApp) getApplication()).getRepository();
        configureMap();
        loadSession();
    }

    private void configureMap() {
        MapView map = binding.map;
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.getController().setZoom(16.0);
    }

    private void loadSession() {
        final String id = sessionId;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            SessionEntity session = repository.getSession(id);
            List<PointEntity> points = repository.loadPoints(id);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (session == null) {
                    Toast.makeText(
                            this,
                            R.string.session_not_found,
                            Toast.LENGTH_SHORT
                    ).show();
                    finish();
                    return;
                }
                bind(session, points);
            });
        });
    }

    private void bind(SessionEntity session, List<PointEntity> points) {
        currentSession = session;
        currentPoints = points != null ? points : Collections.emptyList();
        setShareBusy(false);

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

        PerformanceSeries series = PerformanceSeriesBuilder.build(session, points);
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

        renderRoute(points);
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_activity_title)
                .setMessage(R.string.delete_activity_message)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.delete_activity, (dialog, which) ->
                        deleteActivity())
                .show();
    }

    private void deleteActivity() {
        final String id = sessionId;
        if (id == null) {
            return;
        }
        setShareBusy(true);
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
                    setShareBusy(false);
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
        setShareBusy(true);
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
                    setShareBusy(false);
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
                    setShareBusy(false);
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
        setShareBusy(true);

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
                    setShareBusy(false);
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
                    setShareBusy(false);
                    Toast.makeText(
                            this,
                            R.string.share_error,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void setShareBusy(boolean busy) {
        shareBusy = busy;
        if (binding == null) {
            return;
        }
        binding.shareProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        boolean enabled = !busy && currentSession != null;
        binding.buttonSaveImage.setEnabled(enabled);
        binding.buttonShare.setEnabled(enabled);
    }

    private void renderRoute(List<PointEntity> points) {
        if (points == null || points.isEmpty()) {
            binding.map.setVisibility(View.GONE);
            binding.textMapEmpty.setVisibility(View.VISIBLE);
            return;
        }

        List<GeoPoint> route = new ArrayList<>(points.size());
        for (PointEntity point : points) {
            if (point == null
                    || Double.isNaN(point.lat)
                    || Double.isInfinite(point.lat)
                    || Double.isNaN(point.lon)
                    || Double.isInfinite(point.lon)) {
                continue;
            }
            route.add(new GeoPoint(point.lat, point.lon));
        }

        if (route.isEmpty()) {
            binding.map.setVisibility(View.GONE);
            binding.textMapEmpty.setVisibility(View.VISIBLE);
            return;
        }

        binding.map.setVisibility(View.VISIBLE);
        binding.textMapEmpty.setVisibility(View.GONE);

        Polyline line = new Polyline(binding.map);
        line.setPoints(route);
        line.setColor(ContextCompat.getColor(this, R.color.brand_primary));
        line.setWidth(10f);
        binding.map.getOverlays().add(line);

        Marker start = new Marker(binding.map);
        start.setPosition(route.get(0));
        start.setTextLabelBackgroundColor(
                ContextCompat.getColor(this, R.color.brand_primary)
        );
        start.setTextLabelForegroundColor(
                ContextCompat.getColor(this, R.color.color_on_primary)
        );
        start.setTextLabelFontSize(14);
        start.setTextIcon(getString(R.string.marker_start_short));
        start.setTitle(getString(R.string.marker_start));
        binding.map.getOverlays().add(start);

        Marker end = new Marker(binding.map);
        end.setPosition(route.get(route.size() - 1));
        end.setTextLabelBackgroundColor(
                ContextCompat.getColor(this, R.color.brand_accent)
        );
        end.setTextLabelForegroundColor(
                ContextCompat.getColor(this, R.color.brand_on_accent)
        );
        end.setTextLabelFontSize(14);
        end.setTextIcon(getString(R.string.marker_end_short));
        end.setTitle(getString(R.string.marker_end));
        binding.map.getOverlays().add(end);

        binding.map.post(() -> {
            if (route.size() == 1) {
                binding.map.getController().setCenter(route.get(0));
                binding.map.getController().setZoom(17.0);
            } else {
                BoundingBox box = BoundingBox.fromGeoPoints(route);
                binding.map.zoomToBoundingBox(box, false, 90);
            }
            binding.map.invalidate();
        });
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
