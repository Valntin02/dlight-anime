package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DownloadProgressMetricsTest {
    @Test
    public void resetEstablishesZeroOrExistingBaseline() {
        FakeClock clock = new FakeClock();
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(4, clock);

        assertSnapshot(metrics.snapshot(), 0, 0L, 0L, -1L);
        metrics.reset(1, 1000L);
        assertSnapshot(metrics.snapshot(), 25, 1000L, 0L, -1L);
    }

    @Test
    public void transferredBytesAreNondecreasingAndSaturate() {
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(1, () -> 0L);
        DownloadProgressMetrics.Attempt attempt = metrics.beginAttempt();

        metrics.recordBytes(attempt, -1L);
        metrics.recordBytes(attempt, Long.MAX_VALUE - 1L);
        metrics.recordBytes(attempt, 10L);

        assertEquals(Long.MAX_VALUE, metrics.snapshot().getDownloadedBytes());
    }

    @Test
    public void unknownAverageAvoidsDivideByZero() {
        FakeClock clock = new FakeClock();
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(2, clock);
        DownloadProgressMetrics.Attempt attempt = metrics.beginAttempt();
        clock.advance(1000L);

        DownloadProgressMetrics.Snapshot snapshot = metrics.recordBytes(attempt, 500L);

        assertSnapshot(snapshot, 0, 500L, 500L, -1L);
    }

    @Test
    public void rateCalculationSaturatesAfterRealClockAdvance() {
        FakeClock clock = new FakeClock();
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(1, clock);
        DownloadProgressMetrics.Attempt attempt = metrics.beginAttempt();
        clock.advance(1000L);

        DownloadProgressMetrics.Snapshot snapshot =
                metrics.recordBytes(attempt, Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, snapshot.getBytesPerSecond());
    }

    @Test
    public void etaCalculationSaturates() {
        FakeClock clock = new FakeClock();
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(Integer.MAX_VALUE, clock);
        DownloadProgressMetrics.Attempt attempt = metrics.beginAttempt();
        clock.advance(1000L);
        metrics.recordBytes(attempt, 1L);

        DownloadProgressMetrics.Snapshot snapshot =
                metrics.segmentCompleted(attempt, Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, snapshot.getEtaSeconds());
    }

    @Test
    public void byteUpdatesAreThrottledToOncePerSecond() {
        FakeClock clock = new FakeClock();
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(2, clock);
        DownloadProgressMetrics.Attempt attempt = metrics.beginAttempt();

        assertNull(metrics.recordBytes(attempt, 400L));
        clock.advance(999L);
        assertNull(metrics.recordBytes(attempt, 599L));
        clock.advance(1L);
        assertEquals(1000L, metrics.recordBytes(attempt, 1L).getDownloadedBytes());
    }

    @Test
    public void validInFlightBytesAdvanceProgressAndEta() {
        FakeClock clock = new FakeClock();
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(2, clock);
        DownloadProgressMetrics.Attempt first = metrics.beginAttempt();
        clock.advance(1000L);
        metrics.recordBytes(first, 1000L);
        metrics.segmentCompleted(first, 1000L);
        DownloadProgressMetrics.Attempt second = metrics.beginAttempt();
        clock.advance(1000L);

        DownloadProgressMetrics.Snapshot snapshot = metrics.recordBytes(second, 500L);

        assertSnapshot(snapshot, 75, 1500L, 500L, 1L);
    }

    @Test
    public void failedAttemptRollsBackValidProgressButNotTransferredBytes() {
        FakeClock clock = new FakeClock();
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(2, clock);
        DownloadProgressMetrics.Attempt first = metrics.beginAttempt();
        clock.advance(1000L);
        metrics.recordBytes(first, 1000L);
        metrics.segmentCompleted(first, 1000L);
        DownloadProgressMetrics.Attempt failed = metrics.beginAttempt();
        clock.advance(1000L);
        assertEquals(75, metrics.recordBytes(failed, 500L).getProgress());

        assertNull(metrics.attemptFailed(failed));
        DownloadProgressMetrics.Snapshot corrected = metrics.snapshot();

        assertSnapshot(corrected, 50, 1500L, 500L, 2L);
    }

    @Test
    public void concurrentAttemptsRemainIndependent() throws Exception {
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(2, () -> 0L);
        DownloadProgressMetrics.Attempt first = metrics.beginAttempt();
        DownloadProgressMetrics.Attempt second = metrics.beginAttempt();
        Thread firstThread = new Thread(() -> record(metrics, first, 10_000));
        Thread secondThread = new Thread(() -> record(metrics, second, 20_000));

        firstThread.start();
        secondThread.start();
        firstThread.join(5000L);
        secondThread.join(5000L);
        assertEquals(false, firstThread.isAlive());
        assertEquals(false, secondThread.isAlive());
        metrics.segmentCompleted(first, 10_000L);
        DownloadProgressMetrics.Snapshot completed =
                metrics.segmentCompleted(second, 20_000L);

        assertSnapshot(completed, 100, 30_000L, 0L, 0L);
    }

    @Test
    public void concurrentAttemptFailuresShareTheThrottleWindow() {
        FakeClock clock = new FakeClock();
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(3, clock);
        DownloadProgressMetrics.Attempt completed = metrics.beginAttempt();
        metrics.recordBytes(completed, 1000L);
        metrics.segmentCompleted(completed, 1000L);
        DownloadProgressMetrics.Attempt first = metrics.beginAttempt();
        DownloadProgressMetrics.Attempt second = metrics.beginAttempt();
        metrics.recordBytes(first, 500L);
        metrics.recordBytes(second, 500L);
        clock.advance(1000L);

        assertEquals(50, metrics.attemptFailed(first).getProgress());
        assertNull(metrics.attemptFailed(second));
        assertEquals(33, metrics.snapshot().getProgress());
    }

    private static void record(DownloadProgressMetrics metrics,
            DownloadProgressMetrics.Attempt attempt, int count) {
        for (int i = 0; i < count; i++) {
            metrics.recordBytes(attempt, 1L);
        }
    }

    private static void assertSnapshot(DownloadProgressMetrics.Snapshot snapshot, int progress,
            long downloadedBytes, long bytesPerSecond, long etaSeconds) {
        assertEquals(progress, snapshot.getProgress());
        assertEquals(downloadedBytes, snapshot.getDownloadedBytes());
        assertEquals(bytesPerSecond, snapshot.getBytesPerSecond());
        assertEquals(etaSeconds, snapshot.getEtaSeconds());
    }

    private static final class FakeClock implements DownloadProgressMetrics.Clock {
        private long now;

        @Override
        public long nowMillis() {
            return now;
        }

        private void advance(long millis) {
            now += millis;
        }
    }
}
