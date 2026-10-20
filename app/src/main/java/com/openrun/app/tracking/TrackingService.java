package com.openrun.app.tracking;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.openrun.app.OpenRunApp;
import com.openrun.app.R;
import com.openrun.app.data.AppDatabase;
import com.openrun.app.data.PointEntity;
import com.openrun.app.data.SessionEntity;
import com.openrun.app.data.SessionRepository;
import com.openrun.app.ui.RecordActivity;
import com.openrun.app.util.Formatters;
import com.openrun.core.model.ActivityType;
import com.openrun.core.model.RawFix;
import com.openrun.core.model.RecordingConfig;
import com.openrun.core.model.SessionSummary;
import com.openrun.core.model.TrackPoint;
import com.openrun.core.model.TrackingSnapshot;
import com.openrun.core.model.TrackingState;
import com.openrun.core.stats.TrackingEngine;

import java.util.UUID;

public class TrackingService extends Service {
    private static final String CHANNEL_ID = "tracking";
    private static final int NOTIFICATION_ID = 42;
    private static final long WARMUP_TIMEOUT_MS = 30_000L;
    private static final long SUMMARY_PERSIST_INTERVAL_MS = 5_000L;

    private FusedLocationProviderClient locationClient;
    private HandlerThread handlerThread;
    private Handler handler;
    private Handler mainHandler;
    private LocationCallback locationCallback;
    private PowerManager.WakeLock wakeLock;

    private SessionRepository repository;
    private TrackingStateHolder stateHolder;
    private volatile TrackingEngine engine;
    private volatile String sessionId;
    private volatile boolean foregroundStarted;
    private volatile boolean locationUpdatesStarted;
    private volatile boolean finishing;
    private long lastSummaryPersistElapsedMs;
    private volatile String lastNotificationSignature;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            TrackingEngine current = engine;
            if (current == null) {
                return;
            }
            long nowElapsedMs = SystemClock.elapsedRealtime();
            long nowEpochMs = System.currentTimeMillis();
            if (current.getState() == TrackingState.ACQUIRING
                    && nowElapsedMs - current.getSessionStartElapsedMs() >= WARMUP_TIMEOUT_MS) {
                current.forceStart(nowElapsedMs, nowEpochMs);
            }
            TrackingSnapshot snapshot = current.snapshot(nowElapsedMs, nowEpochMs);
            stateHolder.postSnapshot(snapshot);
            updateNotification(snapshot);
            if (current.isActive()) {
                handler.postDelayed(this, 1000L);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        repository = OpenRunApp.get().getRepository();
        stateHolder = OpenRunApp.get().getTrackingStateHolder();
        createNotificationChannel();

        try {
            locationClient = LocationServices.getFusedLocationProviderClient(this);
        } catch (RuntimeException error) {
            locationClient = null;
        }
        handlerThread = new HandlerThread("OpenRunTracking");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        handleLocation(location);
                    }
                }
            }
        };

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "OpenRun:Tracking"
            );
            wakeLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            if (engine == null || !engine.isActive()) {
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case TrackingContract.ACTION_START:
                handleStart(intent);
                break;
            case TrackingContract.ACTION_PAUSE:
                handlePause();
                break;
            case TrackingContract.ACTION_RESUME:
                handleResume();
                break;
            case TrackingContract.ACTION_FINISH:
                handleFinish();
                break;
            case TrackingContract.ACTION_DISCARD:
                handleDiscard();
                break;
            default:
                break;
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleStart(Intent intent) {
        if (finishing || (engine != null && engine.isActive())) {
            return;
        }
        ActivityType type = ActivityType.fromKey(
                intent.getStringExtra(TrackingContract.EXTRA_TYPE)
        );
        startSession(type);
    }

    private void startSession(ActivityType type) {
        sessionId = UUID.randomUUID().toString();
        long nowElapsedMs = SystemClock.elapsedRealtime();
        long nowEpochMs = System.currentTimeMillis();

        engine = new TrackingEngine();
        engine.start(RecordingConfig.defaults(type), nowElapsedMs, nowEpochMs);
        finishing = false;
        lastSummaryPersistElapsedMs = 0;
        lastNotificationSignature = null;

        final SessionEntity session = new SessionEntity();
        session.id = sessionId;
        session.type = type.getKey();
        session.state = TrackingState.ACQUIRING.name();
        session.startedAt = nowEpochMs;
        session.startedElapsed = nowElapsedMs;
        session.bootCount = getBootCount();
        session.createdAt = nowEpochMs;
        session.updatedAt = nowEpochMs;
        session.deleted = false;

        TrackingSnapshot initial = engine.snapshot(nowElapsedMs, nowEpochMs);
        try {
            startForegroundCompat(buildNotification(initial));
        } catch (RuntimeException error) {
            stateHolder.postEvent(TrackingEvent.error(TrackingEvent.Type.ERROR_LOCATION));
            stopSelf();
            return;
        }
        acquireWakeLock();
        stateHolder.postSnapshot(initial);

        final String id = sessionId;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                repository.insertSession(session);
                handler.post(() -> {
                    TrackingEngine current = engine;
                    if (current == null || !current.isActive() || !id.equals(sessionId)) {
                        return;
                    }
                    if (startLocationUpdates()) {
                        handler.post(tick);
                        maybePersistSummary(current.snapshot(
                                SystemClock.elapsedRealtime(),
                                System.currentTimeMillis()
                        ));
                    }
                });
            } catch (RuntimeException error) {
                stateHolder.postEvent(TrackingEvent.error(TrackingEvent.Type.ERROR_LOCATION));
                mainHandler.post(() -> stopTracking(true));
            }
        });
    }

    private boolean startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            stateHolder.postEvent(TrackingEvent.error(TrackingEvent.Type.ERROR_PERMISSION));
            mainHandler.post(() -> stopTracking(true));
            return false;
        }
        if (locationClient == null) {
            stateHolder.postEvent(TrackingEvent.error(TrackingEvent.Type.ERROR_LOCATION));
            mainHandler.post(() -> stopTracking(true));
            return false;
        }

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
        )
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build();

        try {
            locationClient.requestLocationUpdates(request, locationCallback, handler.getLooper())
                    .addOnFailureListener(error -> {
                        stateHolder.postEvent(
                                TrackingEvent.error(TrackingEvent.Type.ERROR_LOCATION)
                        );
                        mainHandler.post(() -> stopTracking(true));
                    });
            locationUpdatesStarted = true;
            return true;
        } catch (RuntimeException error) {
            stateHolder.postEvent(TrackingEvent.error(TrackingEvent.Type.ERROR_LOCATION));
            mainHandler.post(() -> stopTracking(true));
            return false;
        }
    }

    private void handleLocation(Location location) {
        TrackingEngine current = engine;
        String id = sessionId;
        if (current == null || id == null || !current.isActive()) {
            return;
        }

        RawFix fix = new RawFix(
                location.getLatitude(),
                location.getLongitude(),
                location.hasAltitude() ? location.getAltitude() : Double.NaN,
                location.hasAccuracy() ? location.getAccuracy() : Double.NaN,
                location.hasSpeed() ? location.getSpeed() : Double.NaN,
                location.hasBearing() ? location.getBearing() : Double.NaN,
                location.getTime() > 0 ? location.getTime() : System.currentTimeMillis()
        );

        int before = current.getPoints().size();
        TrackingSnapshot snapshot = current.onFix(fix, SystemClock.elapsedRealtime());
        if (snapshot == null) {
            return;
        }

        if (current.getPoints().size() > before) {
            TrackPoint point = current.getLastAcceptedPoint();
            if (point != null) {
                PointEntity entity = PointEntity.from(id, point);
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    try {
                        repository.insertPoint(entity);
                    } catch (RuntimeException ignored) {
                        // A missing session row is recovered by the interruption
                        // path on the next launch.
                    }
                });
            }
            maybePersistSummary(snapshot);
        }

        stateHolder.postSnapshot(snapshot);
        updateNotification(snapshot);
    }

    private void handlePause() {
        TrackingEngine current = engine;
        if (current == null) {
            stopSelf();
            return;
        }
        current.pause(SystemClock.elapsedRealtime());
        TrackingSnapshot snapshot = current.snapshot(
                SystemClock.elapsedRealtime(),
                System.currentTimeMillis()
        );
        stateHolder.postSnapshot(snapshot);
        persistSummary(snapshot);
        updateNotification(snapshot);
    }

    private void handleResume() {
        TrackingEngine current = engine;
        if (current == null) {
            stopSelf();
            return;
        }
        current.resume(SystemClock.elapsedRealtime());
        TrackingSnapshot snapshot = current.snapshot(
                SystemClock.elapsedRealtime(),
                System.currentTimeMillis()
        );
        stateHolder.postSnapshot(snapshot);
        persistSummary(snapshot);
        updateNotification(snapshot);
    }

    private void handleFinish() {
        if (finishing) {
            return;
        }
        finishing = true;
        handler.removeCallbacks(tick);

        TrackingEngine current = engine;
        if (current == null) {
            stopTracking(true);
            return;
        }

        long nowElapsedMs = SystemClock.elapsedRealtime();
        long nowEpochMs = System.currentTimeMillis();
        SessionSummary summary = current.finish(nowElapsedMs, nowEpochMs);
        if (summary == null) {
            stopTracking(true);
            return;
        }

        stateHolder.postSnapshot(current.snapshot(nowElapsedMs, nowEpochMs));
        final String id = sessionId;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (id != null) {
                try {
                    SessionEntity session = repository.getSession(id);
                    if (session != null) {
                        session.state = TrackingState.FINISHED.name();
                        session.endedAt = summary.getEndedAtEpochMs();
                        session.movingMs = summary.getMovingTimeMs();
                        session.pausedMs = summary.getPausedTimeMs();
                        session.distanceM = summary.getDistanceMeters();
                        session.elevationGainM = summary.getElevationGainMeters();
                        session.elevationLossM = summary.getElevationLossMeters();
                        session.pointCount = summary.getPointCount();
                        session.updatedAt = System.currentTimeMillis();
                        repository.updateSession(session);
                    }
                } catch (RuntimeException ignored) {
                    // The summary event still lets the UI show the result.
                }
            }
            stateHolder.postEvent(TrackingEvent.finished(id, summary));
            mainHandler.post(() -> stopTracking(true));
        });
    }

    private void handleDiscard() {
        TrackingEngine current = engine;
        ActivityType type = current == null ? ActivityType.RUN : current.getType();
        if (current != null) {
            current.discard();
        }
        String id = sessionId;
        if (id != null) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    repository.deleteSession(id);
                } catch (RuntimeException ignored) {
                }
            });
        }
        stateHolder.postSnapshot(TrackingSnapshot.idle(type));
        stopTracking(true);
    }

    private void maybePersistSummary(TrackingSnapshot snapshot) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastSummaryPersistElapsedMs < SUMMARY_PERSIST_INTERVAL_MS) {
            return;
        }
        lastSummaryPersistElapsedMs = now;
        persistSummary(snapshot);
    }

    private void persistSummary(TrackingSnapshot snapshot) {
        final String id = sessionId;
        if (id == null || snapshot == null) {
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                SessionEntity session = repository.getSession(id);
                if (session == null) {
                    return;
                }
                session.state = snapshot.getState().name();
                session.movingMs = snapshot.getMovingTimeMs();
                session.pausedMs = snapshot.getPausedTimeMs();
                session.distanceM = snapshot.getDistanceMeters();
                session.elevationGainM = snapshot.getElevationGainMeters();
                session.elevationLossM = snapshot.getElevationLossMeters();
                session.pointCount = snapshot.getPointCount();
                session.lastLat = finiteOrNull(snapshot.getLastLatitude());
                session.lastLon = finiteOrNull(snapshot.getLastLongitude());
                session.lastAccuracy = finiteOrNull(snapshot.getAccuracyMeters());
                session.lastPointAt = snapshot.getLastFixAtEpochMs() > 0
                        ? snapshot.getLastFixAtEpochMs()
                        : null;
                session.updatedAt = System.currentTimeMillis();
                repository.updateSession(session);
            } catch (RuntimeException ignored) {
            }
        });
    }

    @SuppressLint("WakelockTimeout")
    private void acquireWakeLock() {
        try {
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
            }
        } catch (RuntimeException ignored) {
            // Recording still works without the wake lock; it is a battery
            // optimisation, not a hard requirement.
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void stopTracking(boolean removeNotification) {
        if (handler != null) {
            handler.removeCallbacks(tick);
        }
        if (locationUpdatesStarted && locationClient != null && locationCallback != null) {
            try {
                locationClient.removeLocationUpdates(locationCallback);
            } catch (RuntimeException ignored) {
            }
            locationUpdatesStarted = false;
        }
        releaseWakeLock();
        if (foregroundStarted) {
            if (removeNotification) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            }
            foregroundStarted = false;
        }
        engine = null;
        sessionId = null;
        stopSelf();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.notification_channel_description));
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @SuppressLint("InlinedApi")
    private void startForegroundCompat(Notification notification) {
        ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        );
        foregroundStarted = true;
    }

    private void updateNotification(TrackingSnapshot snapshot) {
        if (!foregroundStarted || snapshot == null) {
            return;
        }
        String signature = snapshot.getState().name()
                + "|" + Math.round(snapshot.getDistanceMeters() / 10.0d)
                + "|" + Math.round(snapshot.getAveragePaceSecPerKm())
                + "|" + snapshot.isSignalLost();
        if (signature.equals(lastNotificationSignature)) {
            return;
        }
        lastNotificationSignature = signature;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(snapshot));
        }
    }

    private Notification buildNotification(TrackingSnapshot snapshot) {
        String title;
        String text;
        switch (snapshot.getState()) {
            case ACQUIRING:
                title = getString(R.string.notification_acquiring_title);
                text = getString(
                        R.string.notification_text,
                        Formatters.distance(this, snapshot.getDistanceMeters()),
                        getString(R.string.status_acquiring)
                );
                break;
            case PAUSED:
                title = getString(R.string.notification_paused_title);
                text = getString(
                        R.string.notification_paused_text,
                        Formatters.distance(this, snapshot.getDistanceMeters())
                );
                break;
            case RECORDING:
                title = snapshot.getType() == ActivityType.WALK
                        ? getString(R.string.notification_walk_title)
                        : getString(R.string.notification_recording_title);
                text = getString(
                        R.string.notification_text,
                        Formatters.distance(this, snapshot.getDistanceMeters()),
                        Formatters.pace(this, snapshot.getAveragePaceSecPerKm())
                );
                break;
            default:
                title = getString(R.string.app_name);
                text = Formatters.distance(this, snapshot.getDistanceMeters());
                break;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shoe_small)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        if (snapshot.getState() == TrackingState.RECORDING) {
            long movingStart = System.currentTimeMillis() - Math.max(0, snapshot.getMovingTimeMs());
            builder.setWhen(movingStart)
                    .setUsesChronometer(true)
                    .setShowWhen(true);
        } else {
            builder.setUsesChronometer(false)
                    .setShowWhen(false);
        }

        if (snapshot.getState() == TrackingState.RECORDING) {
            builder.addAction(
                    0,
                    getString(R.string.notification_action_pause),
                    actionIntent(TrackingContract.ACTION_PAUSE, 10)
            );
        } else if (snapshot.getState() == TrackingState.PAUSED) {
            builder.addAction(
                    0,
                    getString(R.string.notification_action_resume),
                    actionIntent(TrackingContract.ACTION_RESUME, 11)
            );
        }
        builder.addAction(
                0,
                getString(R.string.notification_action_finish),
                actionIntent(TrackingContract.ACTION_FINISH, 12)
        );

        return builder.build();
    }

    private PendingIntent contentIntent() {
        Intent intent = new Intent(this, RecordActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, TrackingService.class).setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private int getBootCount() {
        try {
            return Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT);
        } catch (Exception error) {
            return 0;
        }
    }

    private Double finiteOrNull(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return value;
    }

    @Override
    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (locationUpdatesStarted && locationClient != null && locationCallback != null) {
            try {
                locationClient.removeLocationUpdates(locationCallback);
            } catch (RuntimeException ignored) {
            }
            locationUpdatesStarted = false;
        }
        releaseWakeLock();
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        super.onDestroy();
    }
}
