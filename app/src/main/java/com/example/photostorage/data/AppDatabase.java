package com.example.photostorage.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {MediaItemEntity.class}, version = 6, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MediaItemDao mediaItemDao();

    public static AppDatabase create(Context context) {
        return Room.databaseBuilder(
                context.getApplicationContext(),
                AppDatabase.class,
                "photo-storage.db"
        ).addMigrations(MIGRATION_4_5, MIGRATION_5_6).fallbackToDestructiveMigration(false).build();
    }

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE media_items ADD COLUMN optimizationMode TEXT NOT NULL DEFAULT 'NONE'");
            database.execSQL("ALTER TABLE media_items ADD COLUMN optimizedMimeType TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE media_items ADD COLUMN optimizedExtension TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE media_items ADD COLUMN screenshotCategory TEXT NOT NULL DEFAULT 'OTHER'");
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE media_items ADD COLUMN optimizationStatus TEXT NOT NULL DEFAULT 'UNKNOWN'");
            database.execSQL("ALTER TABLE media_items ADD COLUMN optimizationSettingsFingerprint TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE media_items ADD COLUMN optimizationRequiresReview INTEGER NOT NULL DEFAULT 0");
        }
    };
}
