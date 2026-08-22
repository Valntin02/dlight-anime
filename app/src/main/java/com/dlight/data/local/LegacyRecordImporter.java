package com.dlight.data.local;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

final class LegacyRecordImporter {

    static final String PREFERENCES_NAME = "legacy_record_import";
    static final String COMPLETED_KEY = "my_star_records_imported";

    private LegacyRecordImporter() {
    }

    static void importIfNeeded(Context context, AppDatabase canonical) {
        if (context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(COMPLETED_KEY, false)) {
            return;
        }

        File legacyFile = context.getDatabasePath(AppDatabase.LEGACY_DB_NAME);
        if (!legacyFile.exists()) {
            markCompleted(context);
            return;
        }

        File readableCopy = createReadableCopy(context, legacyFile);
        SQLiteDatabase legacy = null;
        try {
            legacy = SQLiteDatabase.openDatabase(
                readableCopy.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS
            );
            SQLiteDatabase finalLegacy = legacy;
            canonical.runInTransaction(() -> {
                importPlayRecords(finalLegacy, canonical);
                importStarRecords(finalLegacy, canonical);
            });
        } finally {
            if (legacy != null) {
                legacy.close();
            }
            deleteReadableCopy(readableCopy);
        }
        markCompleted(context);
    }

    private static File createReadableCopy(Context context, File legacyFile) {
        File copy = null;
        try {
            copy = File.createTempFile("legacy-record-import-", ".db", context.getCacheDir());
            Files.copy(legacyFile.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            copySidecarIfPresent(legacyFile, copy, "-wal");
            copySidecarIfPresent(legacyFile, copy, "-shm");
            return copy;
        } catch (IOException error) {
            if (copy != null) {
                deleteReadableCopy(copy);
            }
            throw new IllegalStateException("Unable to copy legacy record database", error);
        }
    }

    private static void copySidecarIfPresent(File source, File destination, String suffix)
            throws IOException {
        File sourceSidecar = new File(source.getPath() + suffix);
        if (sourceSidecar.exists()) {
            Files.copy(
                sourceSidecar.toPath(),
                new File(destination.getPath() + suffix).toPath(),
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private static void deleteReadableCopy(File copy) {
        new File(copy.getPath() + "-wal").delete();
        new File(copy.getPath() + "-shm").delete();
        copy.delete();
    }

    private static void importPlayRecords(SQLiteDatabase legacy, AppDatabase canonical) {
        try (Cursor cursor = legacy.query("play_records", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                int userId = getInt(cursor, "userId");
                int vodId = getInt(cursor, "vod_id");
                if (canonical.playRecordDao().getPlayRecordByUserAndVideo(userId, vodId) != null) {
                    continue;
                }

                PlayRecord record = new PlayRecord();
                record.setId(0);
                record.setUserId(userId);
                record.setVod_id(vodId);
                record.setVod_name(getString(cursor, "vod_name"));
                record.setVod_pic(getString(cursor, "vod_pic"));
                record.setVod_play_url(getString(cursor, "vod_play_url"));
                record.setVod_actor(getString(cursor, "vod_actor"));
                record.setVod_remarks(getString(cursor, "vod_remarks"));
                record.setVod_year(getString(cursor, "vod_year"));
                record.setVod_content(getString(cursor, "vod_content"));
                record.setVod_total(getString(cursor, "vod_total"));
                record.setEpisodeIndex(getInt(cursor, "episodeIndex"));
                record.setIsSynced(getInt(cursor, "isSynced") != 0);
                canonical.playRecordDao().insert(record);
            }
        }
    }

    private static void importStarRecords(SQLiteDatabase legacy, AppDatabase canonical) {
        try (Cursor cursor = legacy.query("myStar_records", null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                int userId = getInt(cursor, "userId");
                int vodId = getInt(cursor, "vod_id");
                if (canonical.myStarRecordDao().getStarsByUserAndVideo(userId, vodId) != null) {
                    continue;
                }

                MyStarRecord record = new MyStarRecord();
                record.setId(0);
                record.setUserId(userId);
                record.setVod_id(vodId);
                record.setVod_name(getString(cursor, "vod_name"));
                record.setVod_pic(getString(cursor, "vod_pic"));
                record.setVod_play_url(getString(cursor, "vod_play_url"));
                record.setVod_actor(getString(cursor, "vod_actor"));
                record.setVod_remarks(getString(cursor, "vod_remarks"));
                record.setVod_year(getString(cursor, "vod_year"));
                record.setVod_content(getString(cursor, "vod_content"));
                record.setVod_total(getString(cursor, "vod_total"));
                record.setIsSynced(getInt(cursor, "isSynced") != 0);
                canonical.myStarRecordDao().insert(record);
            }
        }
    }

    private static int getInt(Cursor cursor, String columnName) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(columnName));
    }

    private static String getString(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndexOrThrow(columnName);
        return cursor.isNull(columnIndex) ? null : cursor.getString(columnIndex);
    }

    private static void markCompleted(Context context) {
        boolean committed = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(COMPLETED_KEY, true)
            .commit();
        if (!committed) {
            throw new IllegalStateException("Unable to persist legacy record import marker");
        }
    }
}
