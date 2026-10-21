package com.openrun.app.tracking;

import android.content.Context;
import android.provider.Settings;

import com.openrun.app.data.SessionEntity;

/**
 * Decides whether an unfinished session can be resumed on this boot.
 */
public final class RecoveryManager {
    private RecoveryManager() {
    }

    public static boolean canResume(Context context, SessionEntity session) {
        if (session == null) {
            return false;
        }
        if (!TrackingStateNames.isResumable(session.state)) {
            return false;
        }
        if (session.bootCount != bootCount(context)) {
            return false;
        }
        long lastActivity = session.lastPointAt != null
                ? session.lastPointAt
                : session.startedAt;
        return lastActivity > 0;
    }

    public static int bootCount(Context context) {
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    Settings.Global.BOOT_COUNT
            );
        } catch (Exception error) {
            return 0;
        }
    }

    private static final class TrackingStateNames {
        static boolean isResumable(String state) {
            return "ACQUIRING".equals(state)
                    || "RECORDING".equals(state)
                    || "PAUSED".equals(state);
        }
    }
}
