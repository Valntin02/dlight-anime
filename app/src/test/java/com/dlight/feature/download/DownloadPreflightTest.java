package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;

public class DownloadPreflightTest {
    private static final File VIDEO_DIRECTORY = new File("video");

    @Test
    public void missingActiveNetworkIsOffline() {
        assertDecision(DownloadPreflight.Snapshot.disconnected(),
                DownloadPreflight.MINIMUM_FREE_BYTES, DownloadPreflight.Result.OFFLINE);
    }

    @Test
    public void nullCapabilitiesAreOffline() {
        assertDecision(null, DownloadPreflight.MINIMUM_FREE_BYTES,
                DownloadPreflight.Result.OFFLINE);
    }

    @Test
    public void internetAndValidationAreBothRequired() {
        assertDecision(snapshot(false, true, DownloadPreflight.Transport.WIFI),
                DownloadPreflight.MINIMUM_FREE_BYTES, DownloadPreflight.Result.OFFLINE);
        assertDecision(snapshot(true, false, DownloadPreflight.Transport.WIFI),
                DownloadPreflight.MINIMUM_FREE_BYTES, DownloadPreflight.Result.OFFLINE);
    }

    @Test
    public void cellularRequiresConfirmation() {
        assertDecision(snapshot(true, true, DownloadPreflight.Transport.CELLULAR),
                DownloadPreflight.MINIMUM_FREE_BYTES,
                DownloadPreflight.Result.CONFIRM_CELLULAR);
    }

    @Test
    public void validatedWifiEthernetVpnAndOtherAreReady() {
        DownloadPreflight.Transport[] readyTransports = {
            DownloadPreflight.Transport.WIFI,
            DownloadPreflight.Transport.ETHERNET,
            DownloadPreflight.Transport.VPN,
            DownloadPreflight.Transport.OTHER
        };

        for (DownloadPreflight.Transport transport : readyTransports) {
            assertDecision(snapshot(true, true, transport),
                    DownloadPreflight.MINIMUM_FREE_BYTES, DownloadPreflight.Result.READY);
        }
    }

    @Test
    public void unknownValidatedTransportIsReady() {
        assertDecision(snapshot(true, true, null), DownloadPreflight.MINIMUM_FREE_BYTES,
                DownloadPreflight.Result.READY);
    }

    @Test
    public void oneByteBelowReserveIsLowStorage() {
        assertDecision(snapshot(true, true, DownloadPreflight.Transport.WIFI),
                DownloadPreflight.MINIMUM_FREE_BYTES - 1L,
                DownloadPreflight.Result.LOW_STORAGE);
    }

    @Test
    public void reserveBoundaryIsReady() {
        assertDecision(snapshot(true, true, DownloadPreflight.Transport.WIFI),
                DownloadPreflight.MINIMUM_FREE_BYTES, DownloadPreflight.Result.READY);
    }

    @Test
    public void lowStorageWinsOverCellularConfirmation() {
        assertDecision(snapshot(true, true, DownloadPreflight.Transport.CELLULAR),
                DownloadPreflight.MINIMUM_FREE_BYTES - 1L,
                DownloadPreflight.Result.LOW_STORAGE);
    }

    @Test
    public void storageRuntimeFailureReturnsError() {
        DownloadPreflight.Result result = DownloadPreflight.evaluate(
                snapshot(true, true, DownloadPreflight.Transport.WIFI), VIDEO_DIRECTORY,
                directory -> {
                    throw new IllegalStateException("StatFs unavailable");
                });

        assertEquals(DownloadPreflight.Result.ERROR, result);
    }

    @Test
    public void storageSecurityFailureReturnsError() {
        DownloadPreflight.Result result = DownloadPreflight.evaluate(
                snapshot(true, true, DownloadPreflight.Transport.WIFI), VIDEO_DIRECTORY,
                directory -> {
                    throw new SecurityException("StatFs denied");
                });

        assertEquals(DownloadPreflight.Result.ERROR, result);
    }

    private static DownloadPreflight.Snapshot snapshot(boolean internet, boolean validated,
            DownloadPreflight.Transport transport) {
        return DownloadPreflight.Snapshot.connected(internet, validated, transport);
    }

    private static void assertDecision(DownloadPreflight.Snapshot snapshot, long availableBytes,
            DownloadPreflight.Result expected) {
        DownloadPreflight.Result actual = DownloadPreflight.evaluate(snapshot, VIDEO_DIRECTORY,
                directory -> availableBytes);
        assertEquals(expected, actual);
    }
}
