package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;

import android.app.Service;

import org.junit.Test;

public class ServiceDownloadTest {
    @Test
    public void interruptedDownloadsRequireExplicitRestartAfterPreflight() {
        assertEquals(Service.START_NOT_STICKY, ServiceDownload.restartPolicy());
    }
}
