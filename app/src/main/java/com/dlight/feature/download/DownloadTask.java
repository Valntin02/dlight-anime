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

    public DownloadTask(String taskId, int videoId, int episode, String title, String url,
                        String coverUrl, int progress, String status, String filePath,
                        String errorMessage, long updatedAt) {
        this.taskId = taskId;
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
        return json;
    }

    public static DownloadTask fromJson(JSONObject json) {
        return new DownloadTask(
            json.optString("taskId"),
            json.optInt("videoId", -1),
            json.optInt("episode", 1),
            json.optString("title"),
            json.optString("url"),
            json.optString("coverUrl"),
            json.optInt("progress", 0),
            json.optString("status", DownloadContract.STATUS_FAILED),
            json.optString("filePath"),
            json.optString("errorMessage"),
            json.optLong("updatedAt", 0L)
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
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
        touch();
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
        updatedAt = System.currentTimeMillis();
    }
}
