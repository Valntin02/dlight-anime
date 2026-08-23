package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;

public class DownloadPreflightTest {
    private static final File VIDEO_DIRECTORY = new File("video");

    @Test
    public void missingActiveNetworkAndNullCapabilitiesAreOffline() {
        assertDecision(DownloadPreflight.Snapshot.disconnected(), false,
                DownloadPreflight.MINIMUM_FREE_BYTES, DownloadPreflight.Result.OFFLINE);
        assertDecision(null, false, DownloadPreflight.MINIMUM_FREE_BYTES,
                DownloadPreflight.Result.OFFLINE);
    }

    @Test
    public void internetAndValidationAreBothRequired() {
        assertDecision(snapshot(false, true, DownloadPreflight.Transport.WIFI, false), false,
                DownloadPreflight.MINIMUM_FREE_BYTES, DownloadPreflight.Result.OFFLINE);
        assertDecision(snapshot(true, false, DownloadPreflight.Transport.WIFI, false), false,
                DownloadPreflight.MINIMUM_FREE_BYTES, DownloadPreflight.Result.OFFLINE);
    }

    @Test
    public void meteredVpnRequiresConfirmation() {
        assertDecision(snapshot(true, true, DownloadPreflight.Transport.VPN, true), false,
                DownloadPreflight.MINIMUM_FREE_BYTES,
                DownloadPreflight.Result.CONFIRM_CELLULAR);
    }

    @Test
    public void unmeteredVpnIsReady() {
        assertDecision(snapshot(true, true, DownloadPreflight.Transport.VPN, false), false,
                DownloadPreflight.MINIMUM_FREE_BYTES, DownloadPreflight.Result.READY);
    }

    @Test
    public void unmeteredCellularIsReady() {
        assertDecision(snapshot(true, true, DownloadPreflight.Transport.CELLULAR, false), false,
                DownloadPreflight.MINIMUM_FREE_BYTES, DownloadPreflight.Result.READY);
    }

    @Test
    public void meteredOtherTransportRequiresConfirmation() {
        assertDecision(snapshot(true, true, DownloadPreflight.Transport.OTHER, true), false,
                DownloadPreflight.MINIMUM_FREE_BYTES,
                DownloadPreflight.Result.CONFIRM_CELLULAR);
    }

    @Test
    public void confirmedMeteredNetworkIsReady() {
        assertDecision(snapshot(true, true, DownloadPreflight.Transport.CELLULAR, true), true,
                DownloadPreflight.MINIMUM_FREE_BYTES, DownloadPreflight.Result.READY);
    }

    @Test
    public void storageReserveBoundaryIsInclusive() {
        DownloadPreflight.Snapshot snapshot =
                snapshot(true, true, DownloadPreflight.Transport.WIFI, false);
        assertDecision(snapshot, false, DownloadPreflight.MINIMUM_FREE_BYTES - 1L,
                DownloadPreflight.Result.LOW_STORAGE);
        assertDecision(snapshot, false, DownloadPreflight.MINIMUM_FREE_BYTES,
                DownloadPreflight.Result.READY);
    }

    @Test
    public void lowStorageWinsOverMeteredConfirmation() {
        assertDecision(snapshot(true, true, DownloadPreflight.Transport.CELLULAR, true), false,
                DownloadPreflight.MINIMUM_FREE_BYTES - 1L,
                DownloadPreflight.Result.LOW_STORAGE);
    }

    @Test
    public void storageRuntimeAndSecurityFailuresReturnError() {
        DownloadPreflight.Snapshot snapshot =
                snapshot(true, true, DownloadPreflight.Transport.WIFI, false);
        assertStorageFailure(snapshot, new IllegalStateException("StatFs unavailable"));
        assertStorageFailure(snapshot, new SecurityException("StatFs denied"));
    }

    @Test
    public void confirmedRecheckCapturesNetworkAgainWithoutRepeatedConfirmation() {
        SequenceNetworkProvider networks = new SequenceNetworkProvider(
                snapshot(true, true, DownloadPreflight.Transport.VPN, true),
                snapshot(true, true, DownloadPreflight.Transport.VPN, true));

        assertEquals(DownloadPreflight.Result.CONFIRM_CELLULAR,
                check(networks, constantStorage(DownloadPreflight.MINIMUM_FREE_BYTES), false));
        assertEquals(DownloadPreflight.Result.READY,
                check(networks, constantStorage(DownloadPreflight.MINIMUM_FREE_BYTES), true));
        assertEquals(2, networks.captureCount);
    }

    @Test
    public void confirmedRecheckStillBlocksOfflineNetwork() {
        SequenceNetworkProvider networks = new SequenceNetworkProvider(
                snapshot(true, true, DownloadPreflight.Transport.CELLULAR, true),
                DownloadPreflight.Snapshot.disconnected());

        assertEquals(DownloadPreflight.Result.CONFIRM_CELLULAR,
                check(networks, constantStorage(DownloadPreflight.MINIMUM_FREE_BYTES), false));
        assertEquals(DownloadPreflight.Result.OFFLINE,
                check(networks, constantStorage(DownloadPreflight.MINIMUM_FREE_BYTES), true));
    }

    @Test
    public void confirmedRecheckStillBlocksNewLowStorage() {
        SequenceNetworkProvider networks = new SequenceNetworkProvider(
                snapshot(true, true, DownloadPreflight.Transport.OTHER, true),
                snapshot(true, true, DownloadPreflight.Transport.OTHER, true));
        SequenceStorageProvider storage = new SequenceStorageProvider(
                DownloadPreflight.MINIMUM_FREE_BYTES,
                DownloadPreflight.MINIMUM_FREE_BYTES - 1L);

        assertEquals(DownloadPreflight.Result.CONFIRM_CELLULAR,
                check(networks, storage, false));
        assertEquals(DownloadPreflight.Result.LOW_STORAGE,
                check(networks, storage, true));
    }

    @Test
    public void confirmedRecheckStillBlocksStorageError() {
        SequenceNetworkProvider networks = new SequenceNetworkProvider(
                snapshot(true, true, DownloadPreflight.Transport.VPN, true),
                snapshot(true, true, DownloadPreflight.Transport.VPN, true));
        DownloadPreflight.StorageProvider storage = new DownloadPreflight.StorageProvider() {
            private int calls;

            @Override
            public long availableBytes(File directory) {
                if (calls++ == 0) {
                    return DownloadPreflight.MINIMUM_FREE_BYTES;
                }
                throw new IllegalStateException("StatFs unavailable");
            }
        };

        assertEquals(DownloadPreflight.Result.CONFIRM_CELLULAR,
                check(networks, storage, false));
        assertEquals(DownloadPreflight.Result.ERROR,
                check(networks, storage, true));
    }

    @Test
    public void networkProviderRuntimeFailureReturnsError() {
        DownloadPreflight.Result result = DownloadPreflight.check(
                () -> {
                    throw new SecurityException("network denied");
                }, VIDEO_DIRECTORY, constantStorage(DownloadPreflight.MINIMUM_FREE_BYTES), false);

        assertEquals(DownloadPreflight.Result.ERROR, result);
    }

    private static DownloadPreflight.Snapshot snapshot(boolean internet, boolean validated,
            DownloadPreflight.Transport transport, boolean metered) {
        return DownloadPreflight.Snapshot.connected(internet, validated, transport, metered);
    }

    private static void assertDecision(DownloadPreflight.Snapshot snapshot,
            boolean meteredConfirmed, long availableBytes, DownloadPreflight.Result expected) {
        DownloadPreflight.Result actual = DownloadPreflight.evaluate(snapshot, VIDEO_DIRECTORY,
                constantStorage(availableBytes), meteredConfirmed);
        assertEquals(expected, actual);
    }

    private static void assertStorageFailure(DownloadPreflight.Snapshot snapshot,
            RuntimeException failure) {
        DownloadPreflight.Result result = DownloadPreflight.evaluate(snapshot, VIDEO_DIRECTORY,
                directory -> {
                    throw failure;
                }, false);
        assertEquals(DownloadPreflight.Result.ERROR, result);
    }

    private static DownloadPreflight.Result check(DownloadPreflight.NetworkProvider networks,
            DownloadPreflight.StorageProvider storage, boolean meteredConfirmed) {
        return DownloadPreflight.check(networks, VIDEO_DIRECTORY, storage, meteredConfirmed);
    }

    private static DownloadPreflight.StorageProvider constantStorage(long availableBytes) {
        return directory -> availableBytes;
    }

    private static final class SequenceNetworkProvider
            implements DownloadPreflight.NetworkProvider {
        private final DownloadPreflight.Snapshot[] snapshots;
        private int captureCount;

        private SequenceNetworkProvider(DownloadPreflight.Snapshot... snapshots) {
            this.snapshots = snapshots;
        }

        @Override
        public DownloadPreflight.Snapshot capture() {
            return snapshots[captureCount++];
        }
    }

    private static final class SequenceStorageProvider
            implements DownloadPreflight.StorageProvider {
        private final long[] availableBytes;
        private int index;

        private SequenceStorageProvider(long... availableBytes) {
            this.availableBytes = availableBytes;
        }

        @Override
        public long availableBytes(File directory) {
            return availableBytes[index++];
        }
    }
}
