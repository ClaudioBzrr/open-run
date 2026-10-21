package com.openrun.app.ui;

import android.app.Activity;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.openrun.app.R;
import com.openrun.app.data.AppDatabase;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SessionRepository;

/**
 * Shared destructive-action dialog for history rows.
 */
public final class SessionDeleteDialog {
    private SessionDeleteDialog() {
    }

    public static void confirm(Activity activity,
                               SessionRepository repository,
                               SessionEntity session) {
        if (activity == null || session == null || repository == null) {
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.delete_activity_title)
                .setMessage(R.string.delete_activity_message)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(
                        R.string.delete_activity,
                        (d, which) -> delete(activity, repository, session)
                )
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(activity, R.color.error)
        );
    }

    private static void delete(Activity activity,
                               SessionRepository repository,
                               SessionEntity session) {
        final String id = session.id;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                repository.deleteSession(id);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    Toast.makeText(
                            activity,
                            R.string.activity_deleted,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    Toast.makeText(
                            activity,
                            R.string.delete_error,
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }
}
