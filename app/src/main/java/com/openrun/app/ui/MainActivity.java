package com.openrun.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openrun.app.OpenRunApp;
import com.openrun.app.R;
import com.openrun.app.data.AppDatabase;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SessionRepository;
import com.openrun.app.databinding.ActivityMainBinding;
import com.openrun.app.util.LanguageManager;
import com.openrun.core.model.ActivityType;
import com.openrun.core.model.TrackingSnapshot;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private SessionAdapter adapter;
    private SessionRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = ((OpenRunApp) getApplication()).getRepository();
        adapter = new SessionAdapter(this::openDetail, this::confirmDelete);
        binding.recyclerSessions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerSessions.setAdapter(adapter);

        ItemTouchHelper historySwipe = new ItemTouchHelper(
                new SessionSwipeCallback(this, this::onSessionSwiped)
        );
        historySwipe.attachToRecyclerView(binding.recyclerSessions);

        HistoryViewModel viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
        viewModel.getSessions().observe(this, sessions -> {
            adapter.submitList(sessions);
            boolean empty = sessions == null || sessions.isEmpty();
            binding.emptyHistory.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recyclerSessions.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        binding.cardStartRun.setOnClickListener(v -> openRecord(ActivityType.RUN));
        binding.cardStartWalk.setOnClickListener(v -> openRecord(ActivityType.WALK));
        binding.languageButton.setOnClickListener(v -> showLanguageDialog());
        updateLanguageButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLanguageButton();
    }

    private void updateLanguageButton() {
        String tag = LanguageManager.getCurrentTag(this);
        binding.languageButton.setText(
                LanguageManager.PORTUGUESE.equals(tag)
                        ? R.string.language_code_pt
                        : R.string.language_code_en
        );
        binding.languageButton.setContentDescription(getString(R.string.language_button));
    }

    private void showLanguageDialog() {
        String saved = LanguageManager.getSavedTag(this);
        String current = LanguageManager.getCurrentTag(this);
        int checked;
        if (LanguageManager.PORTUGUESE.equals(saved)
                && LanguageManager.PORTUGUESE.equals(current)) {
            checked = 2;
        } else if (LanguageManager.ENGLISH.equals(saved)
                && LanguageManager.ENGLISH.equals(current)) {
            checked = 1;
        } else {
            checked = 0;
        }
        String[] labels = {
                getString(R.string.language_system),
                getString(R.string.language_english),
                getString(R.string.language_portuguese)
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    String tag;
                    if (which == 2) {
                        tag = LanguageManager.PORTUGUESE;
                    } else if (which == 1) {
                        tag = LanguageManager.ENGLISH;
                    } else {
                        tag = LanguageManager.SYSTEM;
                    }
                    LanguageManager.setLanguage(this, tag);
                })
                .show();
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
        if (session == null) {
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_activity_title)
                .setMessage(R.string.delete_activity_message)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(
                        R.string.delete_activity,
                        (d, which) -> deleteSession(session)
                )
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(this, R.color.error)
        );
    }

    private void deleteSession(SessionEntity session) {
        final String id = session.id;
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

    private void openRecord(ActivityType type) {
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
