package com.openrun.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openrun.app.OpenRunApp;
import com.openrun.app.R;
import com.openrun.app.databinding.ActivityRecordBinding;
import com.openrun.app.tracking.TrackingController;
import com.openrun.app.tracking.TrackingEvent;
import com.openrun.app.tracking.TrackingStateHolder;
import com.openrun.app.util.Formatters;
import com.openrun.core.model.ActivityType;
import com.openrun.core.model.SessionSummary;
import com.openrun.core.model.TrackingSnapshot;
import com.openrun.core.model.TrackingState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RecordActivity extends AppCompatActivity {
    public static final String EXTRA_TYPE = "com.openrun.app.extra.ACTIVITY_TYPE";
    public static final String EXTRA_RECOVER = "com.openrun.app.extra.RECOVER";

    private ActivityRecordBinding binding;
    private TrackingStateHolder stateHolder;
    private ActivityType requestedType;
    private boolean recoverMode;
    private boolean startRequested;
    private boolean pendingStart;
    private boolean summaryShown;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionResult
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        stateHolder = ((OpenRunApp) getApplication()).getTrackingStateHolder();
        requestedType = ActivityType.fromKey(getIntent().getStringExtra(EXTRA_TYPE));
        recoverMode = getIntent().getBooleanExtra(EXTRA_RECOVER, false);
        binding.toolbar.setTitle(titleRes(requestedType));
        binding.toolbar.setNavigationContentDescription(R.string.navigate_back);
        binding.toolbar.setNavigationOnClickListener(v -> handleBack());

        binding.buttonPause.setOnClickListener(v -> togglePause());
        binding.buttonFinish.setOnClickListener(v -> confirmFinish());

        stateHolder.getSnapshot().observe(this, this::render);
        stateHolder.getEvent().observe(this, event -> {
            if (event != null) {
                handleEvent(event);
                stateHolder.clearEvent();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });

        TrackingSnapshot current = stateHolder.getCurrentSnapshot();
        boolean hasPendingEvent = stateHolder.getEvent().getValue() != null;
        if (current == null || (!current.isActive() && !hasPendingEvent)) {
            requestPermissionsAndStart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingStart) {
            pendingStart = false;
            startTracking();
        }
    }

    private void onPermissionResult(Map<String, Boolean> result) {
        boolean fineGranted = Boolean.TRUE.equals(
                result.get(Manifest.permission.ACCESS_FINE_LOCATION)
        );
        if (fineGranted) {
            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                startTracking();
            } else {
                pendingStart = true;
            }
        } else {
            showPermissionDenied();
        }
    }

    private void requestPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // Starting a foreground service from onCreate can be rejected on
            // Android 12+. Wait until the activity is resumed.
            pendingStart = true;
            return;
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_title)
                    .setMessage(R.string.permission_message)
                    .setCancelable(false)
                    .setNegativeButton(R.string.button_cancel, (dialog, which) -> finish())
                    .setPositiveButton(R.string.button_continue, (dialog, which) ->
                            launchPermissionRequest())
                    .show();
            return;
        }

        launchPermissionRequest();
    }

    private void launchPermissionRequest() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        permissionLauncher.launch(permissions.toArray(new String[0]));
    }

    private void startTracking() {
        if (startRequested || isFinishing()) {
            return;
        }
        startRequested = true;
        try {
            if (recoverMode) {
                TrackingController.recover(this);
            } else {
                TrackingController.start(this, requestedType);
            }
        } catch (RuntimeException error) {
            startRequested = false;
            showError(R.string.error_location_unavailable);
        }
    }

    private void render(TrackingSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        ActivityType type = snapshot.getState() == TrackingState.IDLE
                ? requestedType
                : snapshot.getType();
        binding.toolbar.setTitle(titleRes(type));

        int statusText;
        int statusColor;
        switch (snapshot.getState()) {
            case RECORDING:
                statusText = R.string.status_recording;
                statusColor = R.color.status_recording;
                break;
            case PAUSED:
                statusText = R.string.status_paused;
                statusColor = R.color.status_paused;
                break;
            case FINISHED:
                statusText = R.string.status_finished;
                statusColor = R.color.brand_primary;
                break;
            case DISCARDED:
                statusText = R.string.status_discarded;
                statusColor = R.color.status_acquiring;
                break;
            case ACQUIRING:
            case IDLE:
            default:
                statusText = R.string.status_acquiring;
                statusColor = R.color.status_acquiring;
                break;
        }
        binding.textStatus.setText(statusText);
        binding.textStatus.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, statusColor))
        );

        binding.textTime.setText(Formatters.duration(snapshot.getMovingTimeMs()));
        binding.textDistance.setText(Formatters.distance(this, snapshot.getDistanceMeters()));

        double pace = snapshot.getCurrentPaceSecPerKm() > 0
                ? snapshot.getCurrentPaceSecPerKm()
                : snapshot.getAveragePaceSecPerKm();
        binding.textPace.setText(Formatters.pace(this, pace));
        binding.textAccuracy.setText(
                getString(
                        R.string.accuracy_labeled,
                        Formatters.accuracy(this, snapshot.getAccuracyMeters())
                )
        );

        binding.textSignalLost.setVisibility(
                snapshot.isSignalLost() ? View.VISIBLE : View.GONE
        );

        if (snapshot.getState() == TrackingState.RECORDING) {
            binding.buttonPause.setVisibility(View.VISIBLE);
            binding.buttonPause.setEnabled(true);
            binding.buttonPause.setText(R.string.button_pause);
        } else if (snapshot.getState() == TrackingState.PAUSED) {
            binding.buttonPause.setVisibility(View.VISIBLE);
            binding.buttonPause.setEnabled(true);
            binding.buttonPause.setText(R.string.button_resume);
        } else if (snapshot.getState() == TrackingState.ACQUIRING) {
            binding.buttonPause.setVisibility(View.VISIBLE);
            binding.buttonPause.setEnabled(false);
            binding.buttonPause.setText(R.string.button_pause);
        } else {
            binding.buttonPause.setVisibility(View.GONE);
        }

        binding.buttonFinish.setEnabled(snapshot.isActive());
    }

    private void togglePause() {
        TrackingSnapshot snapshot = stateHolder.getCurrentSnapshot();
        if (snapshot == null) {
            return;
        }
        if (snapshot.getState() == TrackingState.RECORDING) {
            TrackingController.pause(this);
        } else if (snapshot.getState() == TrackingState.PAUSED) {
            TrackingController.resume(this);
        }
    }

    private void confirmFinish() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.finish_title)
                .setMessage(R.string.finish_message)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_finish, (dialog, which) ->
                        TrackingController.finish(this))
                .show();
    }

    private void handleEvent(TrackingEvent event) {
        if (event.getType() == TrackingEvent.Type.FINISHED) {
            if (event.getSessionId() != null) {
                Intent intent = new Intent(this, SessionDetailActivity.class);
                intent.putExtra(
                        SessionDetailActivity.EXTRA_SESSION_ID,
                        event.getSessionId()
                );
                startActivity(intent);
                finish();
            } else {
                showSummary(event.getSummary());
            }
        } else if (event.getType() == TrackingEvent.Type.ERROR_PERMISSION) {
            showError(R.string.error_permission);
        } else {
            showError(R.string.error_location_unavailable);
        }
    }

    private void showSummary(SessionSummary summary) {
        if (summary == null || summaryShown || isFinishing()) {
            return;
        }
        summaryShown = true;
        String message = getString(
                R.string.summary_message,
                getString(
                        R.string.summary_distance,
                        Formatters.distance(this, summary.getDistanceMeters())
                ),
                getString(
                        R.string.summary_duration,
                        Formatters.duration(summary.getMovingTimeMs())
                ),
                getString(
                        R.string.summary_pace,
                        Formatters.pace(this, summary.getAveragePaceSecPerKm())
                )
        );

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.summary_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(R.string.button_done, (dialog, which) -> finish())
                .show();
    }

    private void showError(int messageRes) {
        if (isFinishing()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error_title)
                .setMessage(messageRes)
                .setCancelable(false)
                .setPositiveButton(R.string.button_done, (dialog, which) -> finish())
                .show();
    }

    private void showPermissionDenied() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_denied_title)
                .setMessage(R.string.permission_denied_message)
                .setCancelable(false)
                .setNegativeButton(R.string.button_cancel, (dialog, which) -> finish())
                .setPositiveButton(R.string.button_open_settings, (dialog, which) -> {
                    openAppSettings();
                })
                .show();
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        } catch (RuntimeException ignored) {
        }
        finish();
    }

    private void handleBack() {
        TrackingSnapshot snapshot = stateHolder.getCurrentSnapshot();
        if (snapshot != null && snapshot.isActive()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.leave_title)
                    .setMessage(R.string.leave_message)
                    .setNegativeButton(R.string.button_stay, null)
                    .setPositiveButton(R.string.button_leave, (dialog, which) -> finish())
                    .show();
        } else {
            finish();
        }
    }

    private int titleRes(ActivityType type) {
        return type == ActivityType.WALK ? R.string.activity_walk : R.string.activity_run;
    }
}
