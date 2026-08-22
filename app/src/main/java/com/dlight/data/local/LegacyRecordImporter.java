package com.dlight.data.local;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

final class LegacyRecordImporter {

    private static final String TAG = "LegacyRecordImporter";
    private static final int SNAPSHOT_ATTEMPTS = 3;

    static final String PREFERENCES_NAME = "legacy_record_import";
    static final String COMPLETED_KEY = "my_star_records_imported";

    interface SnapshotHook {
        void afterBeforeFingerprint(int attempt, File source);
    }

    private LegacyRecordImporter() {
    }

    static void importIfNeeded(Context context, AppDatabase canonical) {
        importIfNeeded(context, canonical, null);
    }

    static void importIfNeeded(
            Context context, AppDatabase canonical, SnapshotHook snapshotHook) {
        if (context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(COMPLETED_KEY, false)) {
            return;
        }

        File legacyFile = context.getDatabasePath(AppDatabase.LEGACY_DB_NAME);
        if (!legacyFile.exists()) {
            markCompleted(context);
            return;
        }

        File readableCopy = createReadableCopy(context, legacyFile, snapshotHook);
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
            try {
                if (legacy != null) {
                    legacy.close();
                }
            } finally {
                deleteReadableCopy(readableCopy);
            }
        }
        markCompleted(context);
    }

    private static File createReadableCopy(
            Context context, File legacyFile, SnapshotHook snapshotHook) {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= SNAPSHOT_ATTEMPTS; attempt++) {
            File copy = null;
            boolean stable = false;
            try {
                SnapshotFingerprint before = fingerprint(legacyFile);
                if (snapshotHook != null) {
                    snapshotHook.afterBeforeFingerprint(attempt, legacyFile);
                }

                copy = File.createTempFile(
                    "legacy-record-import-", ".db", context.getCacheDir());
                copySnapshot(legacyFile, copy, before);
                SnapshotFingerprint after = fingerprint(legacyFile);
                SnapshotFingerprint copied = fingerprint(copy);
                stable = before.equals(after) && before.equals(copied);
                if (stable) {
                    return copy;
                }
            } catch (IOException error) {
                lastFailure = error;
            } finally {
                if (copy != null && !stable) {
                    deleteReadableCopy(copy);
                }
            }
        }
        throw new IllegalStateException(
            "Unable to capture a stable legacy record database snapshot", lastFailure);
    }

    private static void copySnapshot(
            File source, File destination, SnapshotFingerprint expected)
            throws IOException {
        copyFile(source, destination);
        if (expected.wal.exists) {
            copyFile(sidecar(source, "-wal"), sidecar(destination, "-wal"));
        }
        if (expected.shm.exists) {
            copyFile(sidecar(source, "-shm"), sidecar(destination, "-shm"));
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static SnapshotFingerprint fingerprint(File databaseFile) throws IOException {
        return new SnapshotFingerprint(
            fingerprintFile(databaseFile),
            fingerprintFile(sidecar(databaseFile, "-wal")),
            fingerprintFile(sidecar(databaseFile, "-shm"))
        );
    }

    private static FileFingerprint fingerprintFile(File file) throws IOException {
        if (!file.exists()) {
            return FileFingerprint.MISSING;
        }

        long length = file.length();
        MessageDigest digest = sha256();
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(file.toPath())) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return new FileFingerprint(true, length, digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static File sidecar(File databaseFile, String suffix) {
        return new File(databaseFile.getPath() + suffix);
    }

    private static void deleteReadableCopy(File copy) {
        deleteSnapshotFile(sidecar(copy, "-wal"));
        deleteSnapshotFile(sidecar(copy, "-shm"));
        deleteSnapshotFile(copy);
    }

    private static void deleteSnapshotFile(File file) {
        try {
            if (file.exists() && !file.delete() && file.exists()) {
                Log.w(TAG, "Unable to delete legacy snapshot file: " + file.getAbsolutePath());
            }
        } catch (SecurityException error) {
            Log.w(
                TAG,
                "Unable to delete legacy snapshot file: " + file.getAbsolutePath(),
                error
            );
        }
    }

    private static void importPlayRecords(SQLiteDatabase legacy, AppDatabase canonical) {
        try (Cursor cursor = legacy.query("play_records", null, null, null, null, null, null)) {
            requireColumns(
                cursor,
                "id", "userId", "vod_id", "vod_name", "vod_pic", "vod_play_url",
                "vod_actor", "vod_remarks", "vod_year", "vod_content", "vod_total",
                "episodeIndex", "isSynced"
            );
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
            requireColumns(
                cursor,
                "id", "userId", "vod_id", "vod_name", "vod_pic", "vod_play_url",
                "vod_actor", "vod_remarks", "vod_year", "vod_content", "vod_total", "isSynced"
            );
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

    private static void requireColumns(Cursor cursor, String... columnNames) {
        for (String columnName : columnNames) {
            cursor.getColumnIndexOrThrow(columnName);
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

    private static final class SnapshotFingerprint {
        final FileFingerprint main;
        final FileFingerprint wal;
        final FileFingerprint shm;

        SnapshotFingerprint(
                FileFingerprint main, FileFingerprint wal, FileFingerprint shm) {
            this.main = main;
            this.wal = wal;
            this.shm = shm;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof SnapshotFingerprint)) {
                return false;
            }
            SnapshotFingerprint other = (SnapshotFingerprint) object;
            return main.equals(other.main) && wal.equals(other.wal) && shm.equals(other.shm);
        }

        @Override
        public int hashCode() {
            int result = main.hashCode();
            result = 31 * result + wal.hashCode();
            return 31 * result + shm.hashCode();
        }
    }

    private static final class FileFingerprint {
        static final FileFingerprint MISSING = new FileFingerprint(false, 0, new byte[0]);

        final boolean exists;
        final long length;
        final byte[] sha256;

        FileFingerprint(boolean exists, long length, byte[] sha256) {
            this.exists = exists;
            this.length = length;
            this.sha256 = sha256;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof FileFingerprint)) {
                return false;
            }
            FileFingerprint other = (FileFingerprint) object;
            return exists == other.exists
                && length == other.length
                && Arrays.equals(sha256, other.sha256);
        }

        @Override
        public int hashCode() {
            int result = Boolean.valueOf(exists).hashCode();
            result = 31 * result + Long.valueOf(length).hashCode();
            return 31 * result + Arrays.hashCode(sha256);
        }
    }
}
