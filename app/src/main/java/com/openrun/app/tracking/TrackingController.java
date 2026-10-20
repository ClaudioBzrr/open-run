package com.openrun.app.tracking;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.openrun.core.model.ActivityType;

public final class TrackingController {
    private TrackingController() {
    }

    public static void start(Context context, ActivityType type) {
        Intent intent = new Intent(context, TrackingService.class)
                .setAction(TrackingContract.ACTION_START)
                .putExtra(TrackingContract.EXTRA_TYPE, type.getKey());
        ContextCompat.startForegroundService(context, intent);
    }

    public static void pause(Context context) {
        send(context, TrackingContract.ACTION_PAUSE);
    }

    public static void resume(Context context) {
        send(context, TrackingContract.ACTION_RESUME);
    }

    public static void finish(Context context) {
        send(context, TrackingContract.ACTION_FINISH);
    }

    public static void discard(Context context) {
        send(context, TrackingContract.ACTION_DISCARD);
    }

    private static void send(Context context, String action) {
        context.startService(new Intent(context, TrackingService.class).setAction(action));
    }
}
