package com.openrun.app.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                SessionEntity.class,
                PointEntity.class,
                PauseEntity.class,
                SplitEntity.class
        },
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    /**
     * Single-threaded on purpose: session inserts, point inserts and summary
     * updates must reach Room in the same order they were produced.
     */
    public static final ExecutorService databaseWriteExecutor =
            Executors.newSingleThreadExecutor();

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE sessions ADD COLUMN recording_started_elapsed "
                            + "INTEGER NOT NULL DEFAULT 0"
            );
            database.execSQL(
                    "ALTER TABLE sessions ADD COLUMN recording_started_at "
                            + "INTEGER NOT NULL DEFAULT 0"
            );
            database.execSQL(
                    "ALTER TABLE points ADD COLUMN moving_elapsed_ms "
                            + "INTEGER NOT NULL DEFAULT 0"
            );
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pauses` ("
                            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "`session_id` TEXT NOT NULL, "
                            + "`started_elapsed_ms` INTEGER NOT NULL, "
                            + "`ended_elapsed_ms` INTEGER, "
                            + "`started_at` INTEGER NOT NULL, "
                            + "`ended_at` INTEGER, "
                            + "`auto_pause` INTEGER NOT NULL, "
                            + "FOREIGN KEY(`session_id`) REFERENCES `sessions`(`id`) "
                            + "ON UPDATE NO ACTION ON DELETE CASCADE)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pauses_session_id` "
                            + "ON `pauses` (`session_id`)"
            );
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `splits` ("
                            + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                            + "`session_id` TEXT NOT NULL, "
                            + "`split_index` INTEGER NOT NULL, "
                            + "`distance_m` REAL NOT NULL, "
                            + "`duration_ms` INTEGER NOT NULL, "
                            + "`pace_sec_per_km` REAL NOT NULL, "
                            + "FOREIGN KEY(`session_id`) REFERENCES `sessions`(`id`) "
                            + "ON UPDATE NO ACTION ON DELETE CASCADE)"
            );
            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS "
                            + "`index_splits_session_id_split_index` "
                            + "ON `splits` (`session_id`, `split_index`)"
            );
        }
    };

    public abstract SessionDao sessionDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "openrun.db"
                            )
                            .addMigrations(MIGRATION_2_3)
                            .fallbackToDestructiveMigrationFrom(1)
                            .build();
                }
            }
        }
        return instance;
    }
}
