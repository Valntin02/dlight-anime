package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class DownloadTaskStoreTest {
    private static final String PREFS_NAME = "download_tasks";
    private static final String KEY_TASKS = "tasks";

    private Application context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @After
    public void tearDown() {
        preferences.edit().clear().commit();
    }

    @Test
    public void upsertReplacesSameIdWithoutDuplicationAndSortsNewestFirst() {
        DownloadTask older = task("older", DownloadContract.STATUS_PAUSED, 10, 10L);
        DownloadTask original = task("same", DownloadContract.STATUS_QUEUED, 20, 20L);
        DownloadTask replacement = task("same", DownloadContract.STATUS_COMPLETED, 100, 30L);

        DownloadTaskStore.upsert(context, original);
        DownloadTaskStore.upsert(context, older);
        DownloadTaskStore.upsert(context, replacement);

        List<DownloadTask> tasks = DownloadTaskStore.getAll(context);
        assertEquals(2, tasks.size());
        assertEquals("same", tasks.get(0).getTaskId());
        assertEquals(DownloadContract.STATUS_COMPLETED, tasks.get(0).getStatus());
        assertEquals("older", tasks.get(1).getTaskId());
    }

    @Test
    public void removeDeletesOnlyMatchingTask() {
        DownloadTaskStore.upsert(context, task("one", DownloadContract.STATUS_PAUSED, 10, 10L));
        DownloadTaskStore.upsert(context, task("two", DownloadContract.STATUS_PAUSED, 20, 20L));

        DownloadTaskStore.remove(context, "one");

        assertNull(DownloadTaskStore.get(context, "one"));
        assertEquals("two", DownloadTaskStore.getAll(context).get(0).getTaskId());
    }

    @Test
    public void malformedJsonReturnsEmptyListWithoutCrash() {
        putRaw("not json");

        assertTrue(DownloadTaskStore.getAll(context).isEmpty());
    }

    @Test
    public void nonObjectsAndEmptyTaskIdsAreSkipped() throws Exception {
        JSONArray array = new JSONArray();
        array.put(1);
        array.put("text");
        array.put(new JSONObject());
        array.put(new JSONObject().put("taskId", ""));
        array.put(task("valid", DownloadContract.STATUS_PAUSED, 50, 50L).toJson());
        putRaw(array.toString());

        List<DownloadTask> tasks = DownloadTaskStore.getAll(context);

        assertEquals(1, tasks.size());
        assertEquals("valid", tasks.get(0).getTaskId());
    }

    @Test
    public void oldJsonWithMissingFieldsStillLoads() throws Exception {
        JSONArray array = new JSONArray();
        array.put(new JSONObject().put("taskId", "legacy").put("videoId", 9));
        putRaw(array.toString());

        DownloadTask task = DownloadTaskStore.get(context, "legacy");

        assertEquals(9, task.getVideoId());
        assertEquals(1, task.getEpisode());
        assertEquals(0, task.getProgress());
        assertEquals(DownloadContract.STATUS_FAILED, task.getStatus());
        assertEquals("", task.getUrl());
        assertEquals(0L, task.getUpdatedAt());
    }

    @Test
    public void reconcilePausesOnlyActiveTasksAndWritesOnce() throws Exception {
        DownloadTask queued = detailedTask("queued", DownloadContract.STATUS_QUEUED, 11, 100L, "queued error");
        DownloadTask downloading = detailedTask("downloading", DownloadContract.STATUS_DOWNLOADING, 22, 200L, "download error");
        DownloadTask completed = detailedTask("completed", DownloadContract.STATUS_COMPLETED, 100, 300L, "completed error");
        DownloadTask failed = detailedTask("failed", DownloadContract.STATUS_FAILED, 44, 400L, "failed error");
        DownloadTask paused = detailedTask("paused", DownloadContract.STATUS_PAUSED, 55, 500L, "paused error");
        putTasks(queued, downloading, completed, failed, paused);
        AtomicInteger writes = new AtomicInteger();
        SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> {
            if (KEY_TASKS.equals(key)) {
                writes.incrementAndGet();
            }
        };
        preferences.registerOnSharedPreferenceChangeListener(listener);

        DownloadTaskStore.reconcileInterruptedTasks(context);

        preferences.unregisterOnSharedPreferenceChangeListener(listener);
        assertEquals(1, writes.get());
        assertReconciled(queued, DownloadTaskStore.get(context, "queued"));
        assertReconciled(downloading, DownloadTaskStore.get(context, "downloading"));
        assertUnchanged(completed, DownloadTaskStore.get(context, "completed"));
        assertUnchanged(failed, DownloadTaskStore.get(context, "failed"));
        assertUnchanged(paused, DownloadTaskStore.get(context, "paused"));
    }

    @Test
    public void reconcileDoesNotWriteWhenNothingIsActive() throws Exception {
        putTasks(
            task("completed", DownloadContract.STATUS_COMPLETED, 100, 100L),
            task("failed", DownloadContract.STATUS_FAILED, 50, 200L),
            task("paused", DownloadContract.STATUS_PAUSED, 30, 300L)
        );
        AtomicInteger writes = new AtomicInteger();
        SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> writes.incrementAndGet();
        preferences.registerOnSharedPreferenceChangeListener(listener);

        DownloadTaskStore.reconcileInterruptedTasks(context);

        preferences.unregisterOnSharedPreferenceChangeListener(listener);
        assertEquals(0, writes.get());
    }

    private void assertReconciled(DownloadTask before, DownloadTask after) {
        assertEquals(DownloadContract.STATUS_PAUSED, after.getStatus());
        assertEquals("", after.getErrorMessage());
        assertEquals(before.getProgress(), after.getProgress());
        assertEquals(before.getUrl(), after.getUrl());
        assertEquals(before.getCoverUrl(), after.getCoverUrl());
        assertEquals(before.getFilePath(), after.getFilePath());
        assertEquals(before.getTaskId(), after.getTaskId());
        assertTrue(after.getUpdatedAt() > before.getUpdatedAt());
    }

    private void assertUnchanged(DownloadTask before, DownloadTask after) throws Exception {
        assertEquals(before.toJson().toString(), after.toJson().toString());
    }

    private void putTasks(DownloadTask... tasks) throws Exception {
        JSONArray array = new JSONArray();
        for (DownloadTask task : tasks) {
            array.put(task.toJson());
        }
        putRaw(array.toString());
    }

    private void putRaw(String raw) {
        preferences.edit().putString(KEY_TASKS, raw).commit();
    }

    private static DownloadTask task(String id, String status, int progress, long updatedAt) {
        return new DownloadTask(id, 1, 1, id, "url", "cover", progress, status, "path", "", updatedAt);
    }

    private static DownloadTask detailedTask(String id, String status, int progress, long updatedAt,
                                             String error) {
        return new DownloadTask(
            id, 7, 3, "title", "https://example.com/" + id + ".m3u8",
            "https://example.com/" + id + ".jpg", progress, status,
            "/downloads/" + id + ".mp4", error, updatedAt
        );
    }
}
