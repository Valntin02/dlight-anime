package com.dlight.feature.download;

import org.json.JSONException;
import org.json.JSONObject;

public class DownloadTask {
    private final String taskId;
    private final int videoId;
    private final int episode;
    private final String title;
    private final String url;
    private final String coverUrl;
    private int progress;
    private String status;
    private String filePath;
    private String errorMessage;
    private long updatedAt;
    private long bytesDownloaded;
    private long bytesPerSecond;
    private long etaSeconds;

    public DownloadTask(String taskId, int videoId, int episode, String title, String url,
                        String coverUrl, int progress, String status, String filePath,
                        String errorMessage, long updatedAt) {
        this(taskId, videoId, episode, title, url, coverUrl, progress, status, filePath,
            errorMessage, updatedAt, 0L, 0L, -1L);
    }

    public DownloadTask(String taskId, int videoId, int episode, String title, String url,
                        String coverUrl, int progress, String status, String filePath,
                        String errorMessage, long updatedAt, long bytesDownloaded,
                        long bytesPerSecond, long etaSeconds) {
        this.taskId = safe(taskId);
        this.videoId = videoId;
        this.episode = episode;
        this.title = safe(title);
        this.url = safe(url);
        this.coverUrl = safe(coverUrl);
        this.progress = Math.max(0, Math.min(100, progress));
        this.status = safe(status);
        this.filePath = safe(filePath);
        this.errorMessage = safe(errorMessage);
        this.updatedAt = updatedAt;
        this.bytesDownloaded = Math.max(0L, bytesDownloaded);
        this.bytesPerSecond = Math.max(0L, bytesPerSecond);
        this.etaSeconds = Math.max(-1L, etaSeconds);
        normalizeMetricsForStatus();
    }

    public static DownloadTask queued(String taskId, int videoId, int episode, String title,
                                      String url, String coverUrl) {
        return new DownloadTask(taskId, videoId, episode, title, url, coverUrl, 0,
            DownloadContract.STATUS_QUEUED, "", "", System.currentTimeMillis());
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("taskId", taskId);
        json.put("videoId", videoId);
        json.put("episode", episode);
        json.put("title", title);
        json.put("url", url);
        json.put("coverUrl", coverUrl);
        json.put("progress", progress);
        json.put("status", status);
        json.put("filePath", filePath);
        json.put("errorMessage", errorMessage);
        json.put("updatedAt", updatedAt);
        json.put("bytesDownloaded", bytesDownloaded);
        json.put("bytesPerSecond", bytesPerSecond);
        json.put("etaSeconds", etaSeconds);
        return json;
    }

    public static DownloadTask fromJson(JSONObject json) {
        return new DownloadTask(
            stringValue(json, "taskId"),
            json.optInt("videoId", -1),
            json.optInt("episode", 1),
            stringValue(json, "title"),
            stringValue(json, "url"),
            stringValue(json, "coverUrl"),
            json.optInt("progress", 0),
            json.has("status") ? stringValue(json, "status") : DownloadContract.STATUS_FAILED,
            stringValue(json, "filePath"),
            stringValue(json, "errorMessage"),
            json.optLong("updatedAt", 0L),
            json.optLong("bytesDownloaded", 0L),
            json.optLong("bytesPerSecond", 0L),
            json.optLong("etaSeconds", -1L)
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String stringValue(JSONObject json, String key) {
        return json.isNull(key) ? "" : json.optString(key);
    }

    public String getTaskId() {
        return taskId;
    }

    public int getVideoId() {
        return videoId;
    }

    public int getEpisode() {
        return episode;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
        touch();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = safe(status);
        normalizeMetricsForStatus();
        touch();
    }

    public long getBytesPerSecond() {
        return bytesPerSecond;
    }

    public long getBytesDownloaded() {
        return bytesDownloaded;
    }

    public long getEtaSeconds() {
        return etaSeconds;
    }

    public void setTransferMetrics(long bytesDownloaded, long bytesPerSecond, long etaSeconds) {
        if (DownloadContract.STATUS_DOWNLOADING.equals(status)) {
            this.bytesDownloaded = Math.max(0L, bytesDownloaded);
            this.bytesPerSecond = Math.max(0L, bytesPerSecond);
            this.etaSeconds = Math.max(-1L, etaSeconds);
        }
        normalizeMetricsForStatus();
        touch();
    }

    private void normalizeMetricsForStatus() {
        if (DownloadContract.STATUS_QUEUED.equals(status)) {
            bytesDownloaded = 0L;
            bytesPerSecond = 0L;
            etaSeconds = -1L;
        } else if (DownloadContract.STATUS_COMPLETED.equals(status)) {
            progress = 100;
            bytesPerSecond = 0L;
            etaSeconds = 0L;
        } else if (!DownloadContract.STATUS_DOWNLOADING.equals(status)) {
            bytesPerSecond = 0L;
            etaSeconds = -1L;
        }
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = safe(filePath);
        touch();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = safe(errorMessage);
        touch();
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public boolean isCompleted() {
        return DownloadContract.STATUS_COMPLETED.equals(status);
    }

    public boolean isActive() {
        return DownloadContract.STATUS_QUEUED.equals(status)
            || DownloadContract.STATUS_DOWNLOADING.equals(status);
    }

    public boolean isPaused() {
        return DownloadContract.STATUS_PAUSED.equals(status);
    }

    private void touch() {
        if (updatedAt == Long.MAX_VALUE) {
            return;
        }
        updatedAt = Math.max(System.currentTimeMillis(), updatedAt + 1L);
    }
}
