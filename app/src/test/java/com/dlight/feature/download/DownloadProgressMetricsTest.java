package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DownloadProgressMetricsTest {
    @Test
    public void throttleEstimateResetAndSaturate() {
        FakeClock clock = new FakeClock();
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(4, clock);

        assertNull(metrics.recordBytes(-1L));
        assertNull(metrics.recordBytes(400L));
        clock.advance(999L);
        assertNull(metrics.recordBytes(599L));
        clock.advance(1L);
        DownloadProgressMetrics.Snapshot timed = metrics.recordBytes(1L);
        assertEquals(1000L, timed.getDownloadedBytes());
        assertEquals(1000L, timed.getBytesPerSecond());
        assertEquals(0, timed.getProgress());
        assertEquals(-1L, timed.getEtaSeconds());

        DownloadProgressMetrics.Snapshot completed = metrics.segmentCompleted(1000L);
        assertEquals(25, completed.getProgress());
        assertEquals(3L, completed.getEtaSeconds());

        metrics.reset(1, Long.MAX_VALUE - 5L);
        metrics.recordBytes(10L);
        DownloadProgressMetrics.Snapshot saturated = metrics.segmentCompleted(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, saturated.getDownloadedBytes());
        assertTrue(saturated.getBytesPerSecond() >= 0L);
        assertTrue(saturated.getEtaSeconds() >= -1L);
    }

    @Test
    public void recordsBytesSafelyFromConcurrentSegments() throws Exception {
        FakeClock clock = new FakeClock();
        DownloadProgressMetrics metrics = new DownloadProgressMetrics(1, clock);
        Thread first = new Thread(() -> recordBytes(metrics, 10_000));
        Thread second = new Thread(() -> recordBytes(metrics, 10_000));

        first.start();
        second.start();
        first.join();
        second.join();

        DownloadProgressMetrics.Snapshot snapshot = metrics.segmentCompleted(20_000L);
        assertEquals(20_000L, snapshot.getDownloadedBytes());
        assertEquals(100, snapshot.getProgress());
    }

    private static void recordBytes(DownloadProgressMetrics metrics, int count) {
        for (int i = 0; i < count; i++) {
            metrics.recordBytes(1L);
        }
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
