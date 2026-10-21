package com.openrun.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openrun.app.OpenRunApp;
import com.openrun.app.R;
import com.openrun.app.data.AppDatabase;
import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SessionRepository;
import com.openrun.app.databinding.ActivityMainBinding;
import com.openrun.app.tracking.RecoveryManager;
import com.openrun.app.tracking.TrackingController;
import com.openrun.app.tracking.TrackingStateHolder;
import com.openrun.app.util.BackupManager;
import com.openrun.app.util.Formatters;
import com.openrun.app.util.LanguageManager;
import com.openrun.core.model.ActivityType;
import com.openrun.core.model.TrackingSnapshot;
import com.openrun.core.stats.GeoMath;

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

public class MainActivity extends AppCompatActivity {
    private static final int HOME_RECENT_LIMIT = 3;

    private ActivityMainBinding binding;
    private SessionAdapter adapter;
    private SessionRepository repository;
    private TrackingStateHolder stateHolder;
    private SessionEntity unfinishedSession;
    private String promptedSessionId;
    private boolean recoveryCheckInFlight;
    private boolean backupBusy;
    private char[] pendingBackupPassphrase;
    private final ExecutorService backupExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> backupExportLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument(
                            "application/octet-stream"
                    ),
                    this::onBackupExportDestination
            );
    private final ActivityResultLauncher<String[]> backupImportLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::onBackupImportSelected
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = ((OpenRunApp) getApplication()).getRepository();
        stateHolder = ((OpenRunApp) getApplication()).getTrackingStateHolder();
        adapter = new SessionAdapter(this::openDetail);
        binding.recyclerSessions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerSessions.setAdapter(adapter);

        ItemTouchHelper historySwipe = new ItemTouchHelper(
                new SessionSwipeCallback(this, this::onSessionSwiped)
        );
        historySwipe.attachToRecyclerView(binding.recyclerSessions);

        HistoryViewModel viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
        viewModel.getSessions().observe(this, sessions -> {
            List<SessionEntity> all = sessions == null
                    ? Collections.emptyList()
                    : sessions;
            List<SessionEntity> recent = all.size() > HOME_RECENT_LIMIT
                    ? new ArrayList<>(all.subList(0, HOME_RECENT_LIMIT))
                    : all;
            adapter.submitList(recent);
            boolean empty = all.isEmpty();
            binding.emptyHistory.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerSessions.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.buttonViewAll.setVisibility(
                    all.size() > HOME_RECENT_LIMIT ? View.VISIBLE : View.GONE
            );
        });

        binding.cardStartRun.setOnClickListener(v -> openRecord(ActivityType.RUN));
        binding.cardStartWalk.setOnClickListener(v -> openRecord(ActivityType.WALK));
        binding.buttonViewAll.setOnClickListener(v -> openAllActivities(true));
        setupNavigationDrawer();
        updateLanguageMenu();
    }

    private void setupNavigationDrawer() {
        binding.buttonMenu.setOnClickListener(v ->
                binding.drawerLayout.openDrawer(GravityCompat.START));
        binding.navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_language) {
                // Let NavigationView expand the language submenu in place.
                return true;
            }
            if (id == R.id.nav_all_activities) {
                openAllActivities(false);
            } else if (id == R.id.nav_backup_export) {
                binding.drawerLayout.closeDrawers();
                showBackupPassphraseDialog(true, null);
                return true;
            } else if (id == R.id.nav_backup_import) {
                binding.drawerLayout.closeDrawers();
                if (!backupBusy) {
                    backupImportLauncher.launch(new String[]{"*/*"});
                }
                return true;
            } else if (id == R.id.nav_language_system) {
                setDrawerLanguage(LanguageManager.SYSTEM);
            } else if (id == R.id.nav_language_en) {
                setDrawerLanguage(LanguageManager.ENGLISH);
            } else if (id == R.id.nav_language_pt) {
                setDrawerLanguage(LanguageManager.PORTUGUESE);
            }
            binding.drawerLayout.closeDrawers();
            return true;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawers();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void setDrawerLanguage(String tag) {
        binding.drawerLayout.closeDrawers();
        LanguageManager.setLanguage(this, tag);
        updateLanguageMenu();
    }

    private void updateLanguageMenu() {
        String saved = LanguageManager.getSavedTag(this);
        String current = LanguageManager.getCurrentTag(this);
        int checkedId;
        if (LanguageManager.PORTUGUESE.equals(saved)
                && LanguageManager.PORTUGUESE.equals(current)) {
            checkedId = R.id.nav_language_pt;
        } else if (LanguageManager.ENGLISH.equals(saved)
                && LanguageManager.ENGLISH.equals(current)) {
            checkedId = R.id.nav_language_en;
        } else {
            checkedId = R.id.nav_language_system;
        }
        binding.navigationView.setCheckedItem(checkedId);
    }

    private void openAllActivities(boolean showAll) {
        Intent intent = new Intent(this, AllActivitiesActivity.class);
        intent.putExtra(AllActivitiesActivity.EXTRA_SHOW_ALL, showAll);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        clearPendingPassphrase();
        backupExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLanguageMenu();
        checkUnfinishedSession();
    }

    private void checkUnfinishedSession() {
        if (recoveryCheckInFlight) {
            return;
        }
        recoveryCheckInFlight = true;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            SessionEntity session = repository.findUnfinishedSession();
            runOnUiThread(() -> {
                recoveryCheckInFlight = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                unfinishedSession = session;
                if (session == null) {
                    promptedSessionId = null;
                } else if (!isTrackingActive()
                        && !session.id.equals(promptedSessionId)) {
                    promptedSessionId = session.id;
                    showRecoveryDialog(session);
                }
            });
        });
    }

    private boolean isTrackingActive() {
        TrackingSnapshot snapshot = stateHolder.getCurrentSnapshot();
        return snapshot != null && snapshot.isActive();
    }

    private void showRecoveryDialog(SessionEntity session) {
        if (session == null || isFinishing()) {
            return;
        }
        boolean serviceActive = isTrackingActive();
        boolean canResume = serviceActive || RecoveryManager.canResume(this, session);
        ActivityType type = ActivityType.fromKey(session.type);
        String typeLabel = getString(
                type == ActivityType.WALK ? R.string.activity_walk : R.string.activity_run
        );
        String message = getString(
                R.string.recovery_message,
                typeLabel,
                Formatters.dateTime(this, session.startedAt),
                Formatters.distance(this, session.distanceM)
        );

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.recovery_title)
                .setMessage(message)
                .setNegativeButton(R.string.recovery_discard, (dialog, which) ->
                        discardUnfinished(session, serviceActive));

        if (canResume) {
            builder.setPositiveButton(R.string.recovery_resume, (dialog, which) ->
                    resumeUnfinished(session));
            if (!serviceActive) {
                builder.setNeutralButton(R.string.recovery_save, (dialog, which) ->
                        saveUnfinished(session));
            }
        } else {
            builder.setPositiveButton(R.string.recovery_save, (dialog, which) ->
                    saveUnfinished(session));
        }
        builder.show();
    }

    private void resumeUnfinished(SessionEntity session) {
        Intent intent = new Intent(this, RecordActivity.class);
        intent.putExtra(RecordActivity.EXTRA_TYPE, session.type);
        intent.putExtra(RecordActivity.EXTRA_RECOVER, true);
        startActivity(intent);
    }

    private void saveUnfinished(SessionEntity session) {
        final String id = session.id;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                SessionEntity stored = repository.getSession(id);
                if (stored == null) {
                    return;
                }
                List<PointEntity> points = repository.loadPoints(id);
                if (points.isEmpty()) {
                    repository.deleteSession(id);
                } else {
                    PointEntity last = points.get(points.size() - 1);
                    long pauseEndElapsed = stored.recordingStartedElapsed > 0
                            ? stored.recordingStartedElapsed + Math.max(0, last.elapsedMs)
                            : stored.startedElapsed + Math.max(0, last.elapsedMs);
                    repository.finishOpenPauses(
                            id,
                            pauseEndElapsed,
                            last.timestamp > 0
                                    ? last.timestamp
                                    : System.currentTimeMillis()
                    );
                    stored.state = "FINISHED";
                    stored.endedAt = last.timestamp > 0
                            ? last.timestamp
                            : System.currentTimeMillis();
                    stored.pointCount = points.size();
                    if (stored.distanceM <= 0) {
                        stored.distanceM = distanceOf(points);
                    }
                    stored.updatedAt = System.currentTimeMillis();
                    repository.updateSession(stored);
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    unfinishedSession = null;
                    promptedSessionId = null;
                    Toast.makeText(
                            this,
                            R.string.recovery_saved,
                            Toast.LENGTH_SHORT
                    ).show();
                    checkUnfinishedSession();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Toast.makeText(
                            this,
                            R.string.delete_error,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void discardUnfinished(SessionEntity session, boolean serviceActive) {
        if (serviceActive) {
            TrackingController.discard(this);
            unfinishedSession = null;
            promptedSessionId = null;
            return;
        }
        final String id = session.id;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                repository.deleteSession(id);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    unfinishedSession = null;
                    promptedSessionId = null;
                    Toast.makeText(
                            this,
                            R.string.activity_deleted,
                            Toast.LENGTH_SHORT
                    ).show();
                    checkUnfinishedSession();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    Toast.makeText(
                            this,
                            R.string.delete_error,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private double distanceOf(List<PointEntity> points) {
        double meters = 0;
        PointEntity previous = null;
        for (PointEntity point : points) {
            if (previous != null) {
                meters += GeoMath.distanceMeters(
                        previous.lat,
                        previous.lon,
                        point.lat,
                        point.lon
                );
            }
            previous = point;
        }
        return meters;
    }

    private void openDetail(SessionEntity session) {
        Intent intent = new Intent(this, SessionDetailActivity.class);
        intent.putExtra(SessionDetailActivity.EXTRA_SESSION_ID, session.id);
        startActivity(intent);
    }

    private void onSessionSwiped(int position) {
        List<SessionEntity> sessions = adapter.getCurrentList();
        if (position < 0 || position >= sessions.size()) {
            return;
        }
        SessionEntity session = sessions.get(position);
        adapter.notifyItemChanged(position);
        confirmDelete(session);
    }

    private void confirmDelete(SessionEntity session) {
        SessionDeleteDialog.confirm(this, repository, session);
    }

    private void showBackupPassphraseDialog(boolean export, Uri importUri) {
        if (backupBusy) {
            return;
        }
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true);
        input.setHint(R.string.backup_passphrase_hint);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_passphrase_title)
                .setMessage(R.string.backup_passphrase_message)
                .setView(input)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(
                        export ? R.string.backup_export : R.string.backup_import,
                        null
                );
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = input.getText().toString();
                    if (value.length() < 8) {
                        input.setError(getString(R.string.backup_passphrase_short));
                        return;
                    }
                    dialog.dismiss();
                    char[] passphrase = value.toCharArray();
                    if (export) {
                        pendingBackupPassphrase = passphrase;
                        backupExportLauncher.launch(backupFileName());
                    } else if (importUri != null) {
                        startBackupImport(importUri, passphrase);
                    } else {
                        clearPassphrase(passphrase);
                    }
                }));
        dialog.show();
    }

    private String backupFileName() {
        String stamp = new SimpleDateFormat(
                "yyyyMMdd-HHmm",
                Locale.US
        ).format(new Date());
        return "open-run-" + stamp + ".runbackup";
    }

    private void onBackupExportDestination(Uri uri) {
        char[] passphrase = pendingBackupPassphrase;
        pendingBackupPassphrase = null;
        if (uri == null || passphrase == null) {
            clearPassphrase(passphrase);
            return;
        }
        if (backupBusy) {
            clearPassphrase(passphrase);
            return;
        }
        setBackupBusy(true);
        backupExecutor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) {
                    throw new IllegalStateException("Cannot open backup destination");
                }
                BackupManager.exportBackup(repository, output, passphrase);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBackupBusy(false);
                    Toast.makeText(
                            this,
                            R.string.backup_exported,
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBackupBusy(false);
                    Toast.makeText(
                            this,
                            R.string.backup_export_error,
                            Toast.LENGTH_LONG
                    ).show();
                });
            } finally {
                clearPassphrase(passphrase);
            }
        });
    }

    private void onBackupImportSelected(Uri uri) {
        if (uri == null || backupBusy) {
            return;
        }
        showBackupPassphraseDialog(false, uri);
    }

    private void startBackupImport(Uri uri, char[] passphrase) {
        if (backupBusy) {
            clearPassphrase(passphrase);
            return;
        }
        setBackupBusy(true);
        backupExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IllegalStateException("Cannot open backup file");
                }
                BackupManager.ImportResult result = BackupManager.importBackup(
                        repository,
                        input,
                        passphrase
                );
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBackupBusy(false);
                    Toast.makeText(
                            this,
                            getString(
                                    R.string.backup_imported,
                                    result.sessionsImported
                            ),
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setBackupBusy(false);
                    Toast.makeText(
                            this,
                            R.string.backup_import_error,
                            Toast.LENGTH_LONG
                    ).show();
                });
            } finally {
                clearPassphrase(passphrase);
            }
        });
    }

    private void setBackupBusy(boolean busy) {
        backupBusy = busy;
        android.view.MenuItem export = binding.navigationView.getMenu()
                .findItem(R.id.nav_backup_export);
        android.view.MenuItem restore = binding.navigationView.getMenu()
                .findItem(R.id.nav_backup_import);
        if (export != null) {
            export.setEnabled(!busy);
        }
        if (restore != null) {
            restore.setEnabled(!busy);
        }
    }

    private void clearPendingPassphrase() {
        clearPassphrase(pendingBackupPassphrase);
        pendingBackupPassphrase = null;
    }

    private static void clearPassphrase(char[] passphrase) {
        if (passphrase != null) {
            java.util.Arrays.fill(passphrase, '\0');
        }
    }

    private void openRecord(ActivityType type) {
        if (unfinishedSession != null) {
            showRecoveryDialog(unfinishedSession);
            return;
        }
        OpenRunApp app = (OpenRunApp) getApplication();
        TrackingSnapshot current = app.getTrackingStateHolder().getCurrentSnapshot();
        if (current == null || !current.isActive()) {
            // A previous summary may still be pending. Starting a new activity
            // from the home screen should not reopen that old dialog.
            app.getTrackingStateHolder().clearEvent();
        }
        Intent intent = new Intent(this, RecordActivity.class);
        intent.putExtra(RecordActivity.EXTRA_TYPE, type.getKey());
        startActivity(intent);
    }
}
