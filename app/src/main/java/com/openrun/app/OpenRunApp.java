package com.openrun.app;

import android.app.Application;
import android.preference.PreferenceManager;

import com.openrun.app.data.AppDatabase;
import com.openrun.app.data.SessionRepository;
import com.openrun.app.tracking.TrackingStateHolder;
import com.openrun.app.util.LanguageManager;

import org.osmdroid.config.Configuration;

import java.io.File;

public class OpenRunApp extends Application {
    private static OpenRunApp instance;

    private SessionRepository repository;
    private TrackingStateHolder trackingStateHolder;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        LanguageManager.applySavedLanguage(this);

        Configuration.getInstance().load(
                this,
                PreferenceManager.getDefaultSharedPreferences(this)
        );
        // OSM asks apps to identify themselves with a contact channel. Using
        // only the package name can get tile requests throttled or rejected.
        Configuration.getInstance().setUserAgentValue(
                "OpenRun/" + getPackageName()
                        + " (https://github.com/ClaudioBzrr/open-run)"
        );

        // Keep tiles in app-private storage. This avoids legacy storage
        // permissions and keeps the map cache out of shared folders.
        File mapBase = new File(getFilesDir(), "osmdroid");
        File mapCache = new File(getCacheDir(), "osmdroid-tiles");
        if (!mapBase.exists()) {
            mapBase.mkdirs();
        }
        if (!mapCache.exists()) {
            mapCache.mkdirs();
        }
        Configuration.getInstance().setOsmdroidBasePath(mapBase);
        Configuration.getInstance().setOsmdroidTileCache(mapCache);
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(100L * 1024 * 1024);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(80L * 1024 * 1024);

        AppDatabase database = AppDatabase.getInstance(this);
        repository = new SessionRepository(database);
        trackingStateHolder = new TrackingStateHolder();

        // If the process died while a session was active, the service is gone
        // with it. Preserve the row for a future recovery screen instead of
        // leaving it in an "active" state forever.
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                repository.markActiveSessionsInterrupted();
            } catch (RuntimeException ignored) {
                // A failed cleanup must not prevent the app from starting.
            }
        });
    }

    public static OpenRunApp get() {
        return instance;
    }

    public SessionRepository getRepository() {
        return repository;
    }

    public TrackingStateHolder getTrackingStateHolder() {
        return trackingStateHolder;
    }
}
