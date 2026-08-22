package com.dlight.data.local;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class AppDatabaseTest {

    private Context context;
    private ExecutorService databaseExecutor;

    @Before
    public void setUp() throws Exception {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        databaseExecutor = Executors.newSingleThreadExecutor();
        AppDatabase.resetInstanceForTests();
        context.deleteDatabase(AppDatabase.CANONICAL_DB_NAME);
        context.deleteDatabase(AppDatabase.LEGACY_DB_NAME);
        context.getSharedPreferences(LegacyRecordImporter.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit();
    }

    @After
    public void tearDown() throws Exception {
        AppDatabase.resetInstanceForTests();
        context.deleteDatabase(AppDatabase.CANONICAL_DB_NAME);
        context.deleteDatabase(AppDatabase.LEGACY_DB_NAME);
        context.getSharedPreferences(LegacyRecordImporter.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit();
        databaseExecutor.shutdownNow();
    }

    @Test
    public void playThenStarAccessorsReturnSameCanonicalDatabase() throws Exception {
        AppDatabase play = inBackground(() -> AppDatabase.getInstancePlayRecord(context));
        AppDatabase star = inBackground(() -> AppDatabase.getInstanceMyStarRecord(context));

        assertSame(play, star);
        inBackground(() -> play.playRecordDao().getAllPlayRecords());
        assertTrue(context.getDatabasePath(AppDatabase.CANONICAL_DB_NAME).exists());
        assertFalse(context.getDatabasePath(AppDatabase.LEGACY_DB_NAME).exists());
    }

    @Test
    public void starThenPlayAccessorsReturnSameCanonicalDatabase() throws Exception {
        AppDatabase star = inBackground(() -> AppDatabase.getInstanceMyStarRecord(context));
        AppDatabase play = inBackground(() -> AppDatabase.getInstancePlayRecord(context));

        assertSame(star, play);
        inBackground(() -> star.myStarRecordDao().getAllStarRecords());
        assertTrue(context.getDatabasePath(AppDatabase.CANONICAL_DB_NAME).exists());
        assertFalse(context.getDatabasePath(AppDatabase.LEGACY_DB_NAME).exists());
    }

    @Test
    public void unsupportedNewerCanonicalSchemaFailsWithoutDeletingExistingData() throws Exception {
        File canonicalFile = context.getDatabasePath(AppDatabase.CANONICAL_DB_NAME);
        assertTrue(canonicalFile.getParentFile().mkdirs()
            || canonicalFile.getParentFile().isDirectory());
        SQLiteDatabase fixture = SQLiteDatabase.openOrCreateDatabase(canonicalFile, null);
        try {
            fixture.execSQL("CREATE TABLE sentinel (value TEXT NOT NULL)");
            fixture.execSQL("INSERT INTO sentinel (value) VALUES ('preserve me')");
            fixture.setVersion(2);
        } finally {
            fixture.close();
        }

        try {
            inBackground(() -> {
                AppDatabase database = AppDatabase.getInstancePlayRecord(context);
                database.playRecordDao().getAllPlayRecords();
                return null;
            });
            fail("Expected unsupported newer schema to fail opening");
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
        }

        SQLiteDatabase reopened = SQLiteDatabase.openDatabase(
            canonicalFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try (android.database.Cursor cursor = reopened.rawQuery(
                "SELECT value FROM sentinel", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("preserve me", cursor.getString(0));
        } finally {
            reopened.close();
        }
    }

    @Test
    public void importsPlayAndFavoriteRowsFromLegacyDatabase() throws Exception {
        PlayRecord legacyPlay = playRecord(41, 101, "legacy play");
        legacyPlay.setId(987);
        MyStarRecord legacyStar = starRecord(42, 202, "legacy star");
        legacyStar.setId(654);
        createLegacyDatabase(legacyPlay, legacyStar);

        AppDatabase canonical = inBackground(() -> AppDatabase.getInstancePlayRecord(context));

        PlayRecord importedPlay = inBackground(
            () -> canonical.playRecordDao().getPlayRecordByUserAndVideo(41, 101));
        MyStarRecord importedStar = inBackground(
            () -> canonical.myStarRecordDao().getStarsByUserAndVideo(42, 202));
        assertNotNull(importedPlay);
        assertEquals("legacy play", importedPlay.getVod_name());
        assertTrue(importedPlay.getId() > 0);
        assertFalse(importedPlay.getId() == legacyPlay.getId());
        assertNotNull(importedStar);
        assertEquals("legacy star", importedStar.getVod_name());
        assertTrue(importedStar.getId() > 0);
        assertFalse(importedStar.getId() == legacyStar.getId());
    }

    @Test
    public void canonicalBusinessKeysWinWithoutDuplicates() throws Exception {
        createCanonicalDatabase(
            playRecord(7, 70, "canonical play"), starRecord(8, 80, "canonical star"));
        createLegacyDatabase(
            playRecord(7, 70, "legacy play"), starRecord(8, 80, "legacy star"));

        AppDatabase canonical = inBackground(() -> AppDatabase.getInstancePlayRecord(context));

        assertEquals(1, (int) inBackground(() -> canonical.playRecordDao().getAllPlayRecords().size()));
        assertEquals("canonical play", inBackground(
            () -> canonical.playRecordDao().getPlayRecordByUserAndVideo(7, 70).getVod_name()));
        assertEquals(1, (int) inBackground(() -> canonical.myStarRecordDao().getAllStarRecords().size()));
        assertEquals("canonical star", inBackground(
            () -> canonical.myStarRecordDao().getStarsByUserAndVideo(8, 80).getVod_name()));
    }

    @Test
    public void missingLegacyDatabaseIsNotCreatedAndImportIsMarkedComplete() throws Exception {
        File legacyFile = context.getDatabasePath(AppDatabase.LEGACY_DB_NAME);
        assertFalse(legacyFile.exists());

        inBackground(() -> AppDatabase.getInstancePlayRecord(context));

        assertFalse(legacyFile.exists());
        assertTrue(importCompleted());
    }

    @Test
    public void malformedLegacyDatabaseLeavesMarkerUnsetAndCanRetry() throws Exception {
        File legacyFile = context.getDatabasePath(AppDatabase.LEGACY_DB_NAME);
        assertTrue(legacyFile.getParentFile().mkdirs() || legacyFile.getParentFile().isDirectory());
        try (FileOutputStream output = new FileOutputStream(legacyFile)) {
            output.write(new byte[]{1, 2, 3, 4});
        }
        int snapshotsBefore = snapshotFileCount();

        try {
            inBackground(() -> AppDatabase.getInstancePlayRecord(context));
            fail("Expected malformed legacy database to fail import");
        } catch (ExecutionException expected) {
            assertFalse(importCompleted());
            assertEquals(snapshotsBefore, snapshotFileCount());
        }

        AppDatabase.resetInstanceForTests();
        assertTrue(context.deleteDatabase(AppDatabase.LEGACY_DB_NAME));
        createLegacyDatabase(playRecord(9, 90, "retry play"), null);

        AppDatabase canonical = inBackground(() -> AppDatabase.getInstancePlayRecord(context));

        assertNotNull(inBackground(
            () -> canonical.playRecordDao().getPlayRecordByUserAndVideo(9, 90)));
        assertTrue(importCompleted());
    }

    @Test
    public void emptyLegacyTableMissingRequiredColumnLeavesCanonicalUnchangedAndCanRetry()
            throws Exception {
        createCanonicalDatabase(
            playRecord(30, 300, "canonical play"), starRecord(31, 310, "canonical star"));
        createLegacyDatabaseWithEmptyStarTableMissingSyncedColumn();

        try {
            inBackground(() -> AppDatabase.getInstancePlayRecord(context));
            fail("Expected incomplete legacy schema to fail import");
        } catch (ExecutionException expected) {
            assertFalse(importCompleted());
        }

        AppDatabase.resetInstanceForTests();
        assertCanonicalRecordCounts(1, 1);
        assertTrue(context.deleteDatabase(AppDatabase.LEGACY_DB_NAME));
        createLegacyDatabase(
            playRecord(20, 200, "retry play"), starRecord(21, 210, "retry star"));

        AppDatabase canonical = inBackground(() -> AppDatabase.getInstancePlayRecord(context));

        assertEquals(2, (int) inBackground(() -> canonical.playRecordDao().getAllPlayRecords().size()));
        assertEquals(2, (int) inBackground(() -> canonical.myStarRecordDao().getAllStarRecords().size()));
        assertTrue(importCompleted());
    }

    @Test
    public void successfulImportLeavesLegacyDatabaseBytesUnchanged() throws Exception {
        createLegacyDatabase(playRecord(10, 100, "preserved"), starRecord(11, 110, "preserved"));
        File legacyFile = context.getDatabasePath(AppDatabase.LEGACY_DB_NAME);
        File legacyWal = new File(legacyFile.getPath() + "-wal");
        File legacyShm = new File(legacyFile.getPath() + "-shm");
        byte[] before = Files.readAllBytes(legacyFile.toPath());
        long modifiedBefore = legacyFile.lastModified();
        byte[] walBefore = legacyWal.exists() ? Files.readAllBytes(legacyWal.toPath()) : null;
        byte[] shmBefore = legacyShm.exists() ? Files.readAllBytes(legacyShm.toPath()) : null;

        inBackground(() -> AppDatabase.getInstancePlayRecord(context));

        assertTrue(legacyFile.exists());
        assertArrayEquals(before, Files.readAllBytes(legacyFile.toPath()));
        assertEquals(modifiedBefore, legacyFile.lastModified());
        assertEquals(walBefore != null, legacyWal.exists());
        assertEquals(shmBefore != null, legacyShm.exists());
        if (walBefore != null) {
            assertArrayEquals(walBefore, Files.readAllBytes(legacyWal.toPath()));
        }
        if (shmBefore != null) {
            assertArrayEquals(shmBefore, Files.readAllBytes(legacyShm.toPath()));
        }
        assertTrue(importCompleted());
    }

    @Test
    public void changingLegacyFilesDiscardFirstSnapshotAndRetryWithAllRows() throws Exception {
        createLegacyDatabase(playRecord(50, 500, "before snapshot"), null);
        AppDatabase legacy = Room.databaseBuilder(
            context, AppDatabase.class, AppDatabase.LEGACY_DB_NAME).build();
        AppDatabase canonical = Room.databaseBuilder(
            context, AppDatabase.class, AppDatabase.CANONICAL_DB_NAME).build();
        AtomicInteger snapshotAttempts = new AtomicInteger();
        try {
            inBackground(() -> {
                legacy.playRecordDao().getAllPlayRecords();
                LegacyRecordImporter.importIfNeeded(context, canonical, (attempt, source) -> {
                    snapshotAttempts.incrementAndGet();
                    if (attempt == 1) {
                        legacy.playRecordDao().insert(playRecord(51, 510, "during snapshot"));
                        try (android.database.Cursor ignored = legacy.getOpenHelper()
                                .getWritableDatabase()
                                .query("PRAGMA wal_checkpoint(TRUNCATE)")) {
                            ignored.moveToFirst();
                        }
                    }
                });
                return null;
            });

            assertEquals(2, snapshotAttempts.get());
            assertNotNull(inBackground(
                () -> canonical.playRecordDao().getPlayRecordByUserAndVideo(50, 500)));
            assertNotNull(inBackground(
                () -> canonical.playRecordDao().getPlayRecordByUserAndVideo(51, 510)));
            assertTrue(importCompleted());
        } finally {
            inBackground(() -> {
                canonical.close();
                legacy.close();
                return null;
            });
        }
    }

    @Test
    public void openLegacyWalWithCommittedRowIsIncludedInSnapshot() throws Exception {
        createLegacyDatabase(playRecord(60, 600, "main row"), null);
        File legacyFile = context.getDatabasePath(AppDatabase.LEGACY_DB_NAME);
        File legacyWal = new File(legacyFile.getPath() + "-wal");
        AppDatabase legacy = Room.databaseBuilder(
            context, AppDatabase.class, AppDatabase.LEGACY_DB_NAME).build();
        try {
            inBackground(() -> legacy.playRecordDao().getAllPlayRecords());
            byte[] mainBeforeInsert = Files.readAllBytes(legacyFile.toPath());

            inBackground(() -> {
                legacy.playRecordDao().insert(playRecord(61, 610, "wal only row"));
                return null;
            });

            assertArrayEquals(mainBeforeInsert, Files.readAllBytes(legacyFile.toPath()));
            assertTrue(legacyWal.exists());
            assertTrue(legacyWal.length() > 0);

            AppDatabase canonical = inBackground(
                () -> AppDatabase.getInstancePlayRecord(context));

            assertNotNull(inBackground(
                () -> canonical.playRecordDao().getPlayRecordByUserAndVideo(61, 610)));
            assertTrue(importCompleted());
        } finally {
            inBackground(() -> {
                legacy.close();
                return null;
            });
        }
    }

    @Test
    public void continuouslyChangingLegacyFilesFailAfterThreeAttemptsWithoutMarker()
            throws Exception {
        createLegacyDatabase(playRecord(70, 700, "changing row"), null);
        AppDatabase legacy = Room.databaseBuilder(
            context, AppDatabase.class, AppDatabase.LEGACY_DB_NAME).build();
        AppDatabase canonical = Room.databaseBuilder(
            context, AppDatabase.class, AppDatabase.CANONICAL_DB_NAME).build();
        AtomicInteger snapshotAttempts = new AtomicInteger();
        int snapshotsBefore = snapshotFileCount();
        try {
            try {
                inBackground(() -> {
                    legacy.playRecordDao().getAllPlayRecords();
                    LegacyRecordImporter.importIfNeeded(context, canonical, (attempt, source) -> {
                        snapshotAttempts.incrementAndGet();
                        legacy.playRecordDao().updateEpisode(70, 700, attempt);
                    });
                    return null;
                });
                fail("Expected continuously changing legacy database to fail import");
            } catch (ExecutionException expected) {
                assertTrue(expected.getCause() instanceof IllegalStateException);
            }

            assertEquals(3, snapshotAttempts.get());
            assertFalse(importCompleted());
            assertEquals(0, (int) inBackground(
                () -> canonical.playRecordDao().getAllPlayRecords().size()));
            assertEquals(snapshotsBefore, snapshotFileCount());
        } finally {
            inBackground(() -> {
                canonical.close();
                legacy.close();
                return null;
            });
        }
    }

    @Test
    public void secondInitializationIsIdempotent() throws Exception {
        createLegacyDatabase(playRecord(12, 120, "once"), starRecord(13, 130, "once"));

        AppDatabase first = inBackground(() -> AppDatabase.getInstancePlayRecord(context));
        AppDatabase.resetInstanceForTests();
        AppDatabase second = inBackground(() -> AppDatabase.getInstanceMyStarRecord(context));

        assertEquals(1, (int) inBackground(() -> second.playRecordDao().getAllPlayRecords().size()));
        assertEquals(1, (int) inBackground(() -> second.myStarRecordDao().getAllStarRecords().size()));
        assertFalse(first == second);
    }

    private void createCanonicalDatabase(PlayRecord play, MyStarRecord star) throws Exception {
        createDatabase(AppDatabase.CANONICAL_DB_NAME, play, star);
    }

    private void createLegacyDatabase(PlayRecord play, MyStarRecord star) throws Exception {
        createDatabase(AppDatabase.LEGACY_DB_NAME, play, star);
    }

    private void createDatabase(String name, PlayRecord play, MyStarRecord star) throws Exception {
        inBackground(() -> {
            AppDatabase database = Room.databaseBuilder(context, AppDatabase.class, name).build();
            try {
                if (play != null) {
                    database.playRecordDao().insert(play);
                }
                if (star != null) {
                    database.myStarRecordDao().insert(star);
                }
            } finally {
                database.close();
            }
            return null;
        });
    }

    private void createLegacyDatabaseWithEmptyStarTableMissingSyncedColumn() throws Exception {
        inBackground(() -> {
            File legacyFile = context.getDatabasePath(AppDatabase.LEGACY_DB_NAME);
            assertTrue(legacyFile.getParentFile().mkdirs() || legacyFile.getParentFile().isDirectory());
            SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(legacyFile, null);
            try {
                database.execSQL("CREATE TABLE play_records ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "userId INTEGER NOT NULL, vod_id INTEGER NOT NULL, "
                    + "vod_name TEXT, vod_pic TEXT, vod_play_url TEXT, vod_actor TEXT, "
                    + "vod_remarks TEXT, vod_year TEXT, vod_content TEXT, vod_total TEXT, "
                    + "episodeIndex INTEGER NOT NULL, isSynced INTEGER NOT NULL)");
                database.execSQL("INSERT INTO play_records "
                    + "(userId, vod_id, vod_name, episodeIndex, isSynced) "
                    + "VALUES (20, 200, 'legacy pending play', 3, 0)");
                database.execSQL("CREATE TABLE myStar_records ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "userId INTEGER NOT NULL, vod_id INTEGER NOT NULL, "
                    + "vod_name TEXT, vod_pic TEXT, vod_play_url TEXT, vod_actor TEXT, "
                    + "vod_remarks TEXT, vod_year TEXT, vod_content TEXT, vod_total TEXT)");
            } finally {
                database.close();
            }
            return null;
        });
    }

    private void assertCanonicalRecordCounts(int playCount, int starCount) throws Exception {
        inBackground(() -> {
            AppDatabase canonical = Room.databaseBuilder(
                context, AppDatabase.class, AppDatabase.CANONICAL_DB_NAME).build();
            try {
                assertEquals(playCount, canonical.playRecordDao().getAllPlayRecords().size());
                assertEquals(starCount, canonical.myStarRecordDao().getAllStarRecords().size());
            } finally {
                canonical.close();
            }
            return null;
        });
    }

    private boolean importCompleted() {
        return context.getSharedPreferences(
                LegacyRecordImporter.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(LegacyRecordImporter.COMPLETED_KEY, false);
    }

    private int snapshotFileCount() {
        File[] snapshots = context.getCacheDir().listFiles(
            (directory, name) -> name.startsWith("legacy-record-import-"));
        return snapshots == null ? 0 : snapshots.length;
    }

    private PlayRecord playRecord(int userId, int vodId, String name) {
        PlayRecord record = new PlayRecord();
        record.setUserId(userId);
        record.setVod_id(vodId);
        record.setVod_name(name);
        record.setEpisodeIndex(3);
        return record;
    }

    private MyStarRecord starRecord(int userId, int vodId, String name) {
        MyStarRecord record = new MyStarRecord();
        record.setUserId(userId);
        record.setVod_id(vodId);
        record.setVod_name(name);
        return record;
    }

    private <T> T inBackground(Callable<T> callable) throws Exception {
        return databaseExecutor.submit(callable).get();
    }
}
