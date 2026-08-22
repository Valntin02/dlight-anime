package com.dlight.feature.download;

public final class DownloadContract {
    public static final String ACTION_UPDATE = "com.dlight.action.DOWNLOAD_UPDATE";

    public static final String EXTRA_TASK_ID = "download_task_id";
    public static final String EXTRA_VIDEO_ID = "download_video_id";
    public static final String EXTRA_EPISODE = "download_episode";
    public static final String EXTRA_URL = "download_url";
    public static final String EXTRA_FILE_NAME = "download_file_name";
    public static final String EXTRA_PIC_URL = "download_pic_url";

    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_DOWNLOADING = "downloading";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    private DownloadContract() {
    }

    public static String taskId(int videoId, int episode) {
        return videoId + ":" + episode;
    }
}
