package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DownloadTaskTest {
    @Test
    public void jsonRoundTripPreservesEveryField() throws Exception {
        DownloadTask original = new DownloadTask(
            "42:3", 42, 3, "title", "https://example.com/video.m3u8",
            "https://example.com/cover.jpg", 67, DownloadContract.STATUS_DOWNLOADING,
            "/downloads/video.mp4", "network error", 123456789L
        );

        DownloadTask restored = DownloadTask.fromJson(original.toJson());

        assertEquals("42:3", restored.getTaskId());
        assertEquals(42, restored.getVideoId());
        assertEquals(3, restored.getEpisode());
        assertEquals("title", restored.getTitle());
        assertEquals("https://example.com/video.m3u8", restored.getUrl());
        assertEquals("https://example.com/cover.jpg", restored.getCoverUrl());
        assertEquals(67, restored.getProgress());
        assertEquals(DownloadContract.STATUS_DOWNLOADING, restored.getStatus());
        assertEquals("/downloads/video.mp4", restored.getFilePath());
        assertEquals("network error", restored.getErrorMessage());
        assertEquals(123456789L, restored.getUpdatedAt());
    }

    @Test
    public void nullStringsBecomeEmpty() {
        DownloadTask task = new DownloadTask(
            null, 1, 1, null, null, null, 0, null, null, null, 1L
        );

        assertEquals("", task.getTaskId());
        assertEquals("", task.getTitle());
        assertEquals("", task.getUrl());
        assertEquals("", task.getCoverUrl());
        assertEquals("", task.getStatus());
        assertEquals("", task.getFilePath());
        assertEquals("", task.getErrorMessage());
    }

    @Test
    public void jsonNullStringsBecomeEmpty() throws Exception {
        JSONObject json = new JSONObject()
            .put("taskId", JSONObject.NULL)
            .put("title", JSONObject.NULL)
            .put("url", JSONObject.NULL)
            .put("coverUrl", JSONObject.NULL)
            .put("status", JSONObject.NULL)
            .put("filePath", JSONObject.NULL)
            .put("errorMessage", JSONObject.NULL);

        DownloadTask task = DownloadTask.fromJson(json);

        assertEquals("", task.getTaskId());
        assertEquals("", task.getTitle());
        assertEquals("", task.getUrl());
        assertEquals("", task.getCoverUrl());
        assertEquals("", task.getStatus());
        assertEquals("", task.getFilePath());
        assertEquals("", task.getErrorMessage());
    }

    @Test
    public void progressIsClampedInConstructorAndSetter() {
        DownloadTask belowZero = taskWithStatus(DownloadContract.STATUS_QUEUED, -1L);
        DownloadTask aboveHundred = new DownloadTask(
            "2:1", 2, 1, "", "", "", 101, DownloadContract.STATUS_QUEUED,
            "", "", 1L
        );

        assertEquals(0, belowZero.getProgress());
        assertEquals(100, aboveHundred.getProgress());

        aboveHundred.setProgress(-100);
        assertEquals(0, aboveHundred.getProgress());
        aboveHundred.setProgress(200);
        assertEquals(100, aboveHundred.getProgress());
    }

    @Test
    public void missingJsonFieldsUseLegacyDefaults() {
        DownloadTask task = DownloadTask.fromJson(new JSONObject());

        assertEquals("", task.getTaskId());
        assertEquals(-1, task.getVideoId());
        assertEquals(1, task.getEpisode());
        assertEquals("", task.getTitle());
        assertEquals("", task.getUrl());
        assertEquals("", task.getCoverUrl());
        assertEquals(0, task.getProgress());
        assertEquals(DownloadContract.STATUS_FAILED, task.getStatus());
        assertEquals("", task.getFilePath());
        assertEquals("", task.getErrorMessage());
        assertEquals(0L, task.getUpdatedAt());
    }

    @Test
    public void queuedCreatesNewActiveZeroProgressTask() {
        long before = System.currentTimeMillis();

        DownloadTask task = DownloadTask.queued("7:2", 7, 2, "title", "url", "cover");

        assertEquals("7:2", task.getTaskId());
        assertEquals(7, task.getVideoId());
        assertEquals(2, task.getEpisode());
        assertEquals("title", task.getTitle());
        assertEquals("url", task.getUrl());
        assertEquals("cover", task.getCoverUrl());
        assertEquals(0, task.getProgress());
        assertEquals(DownloadContract.STATUS_QUEUED, task.getStatus());
        assertEquals("", task.getFilePath());
        assertEquals("", task.getErrorMessage());
        assertTrue(task.getUpdatedAt() >= before);
        assertTrue(task.isActive());
        assertFalse(task.isPaused());
        assertFalse(task.isCompleted());
    }

    @Test
    public void statusHelpersAreMutuallyConsistent() {
        assertState(DownloadContract.STATUS_QUEUED, true, false, false);
        assertState(DownloadContract.STATUS_DOWNLOADING, true, false, false);
        assertState(DownloadContract.STATUS_PAUSED, false, true, false);
        assertState(DownloadContract.STATUS_COMPLETED, false, false, true);
        assertState(DownloadContract.STATUS_FAILED, false, false, false);
    }

    @Test
    public void settersAdvanceUpdatedAtStrictly() {
        DownloadTask task = taskWithStatus(
            DownloadContract.STATUS_DOWNLOADING, System.currentTimeMillis() + 10_000L
        );

        long previous = task.getUpdatedAt();
        task.setProgress(10);
        assertTrue(task.getUpdatedAt() > previous);
        previous = task.getUpdatedAt();
        task.setStatus(DownloadContract.STATUS_PAUSED);
        assertTrue(task.getUpdatedAt() > previous);
        previous = task.getUpdatedAt();
        task.setFilePath("/tmp/file");
        assertTrue(task.getUpdatedAt() > previous);
        previous = task.getUpdatedAt();
        task.setErrorMessage("error");
        assertTrue(task.getUpdatedAt() > previous);
    }

    @Test
    public void setterKeepsMaxUpdatedAtSaturated() throws Exception {
        DownloadTask task = DownloadTask.fromJson(
            new JSONObject().put("taskId", "1:1").put("updatedAt", Long.MAX_VALUE)
        );

        task.setStatus(DownloadContract.STATUS_PAUSED);

        assertEquals(Long.MAX_VALUE, task.getUpdatedAt());
    }

    private static void assertState(String status, boolean active, boolean paused, boolean completed) {
        DownloadTask task = taskWithStatus(status, 1L);
        assertEquals(active, task.isActive());
        assertEquals(paused, task.isPaused());
        assertEquals(completed, task.isCompleted());
    }

    private static DownloadTask taskWithStatus(String status, long updatedAt) {
        return new DownloadTask("1:1", 1, 1, "", "", "", -1, status, "", "", updatedAt);
    }
}
