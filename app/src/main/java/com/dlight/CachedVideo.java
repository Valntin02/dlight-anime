package com.dlight;

import java.io.File;

public class CachedVideo {
    private final File videoFile;
    private final String coverUrl;

    public CachedVideo(File videoFile, String coverUrl) {
        this.videoFile = videoFile;
        this.coverUrl = coverUrl == null ? "" : coverUrl;
    }

    public File getVideoFile() {
        return videoFile;
    }

    public String getCoverUrl() {
        return coverUrl;
    }
}
