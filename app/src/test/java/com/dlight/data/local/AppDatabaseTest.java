package com.dlight.data.local;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

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

        try {
            inBackground(() -> AppDatabase.getInstancePlayRecord(context));
            fail("Expected malformed legacy database to fail import");
        } catch (ExecutionException expected) {
            assertFalse(importCompleted());
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

    private boolean importCompleted() {
        return context.getSharedPreferences(
                LegacyRecordImporter.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(LegacyRecordImporter.COMPLETED_KEY, false);
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
