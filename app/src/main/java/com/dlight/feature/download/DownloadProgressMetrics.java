package com.dlight.feature.download;

import java.util.IdentityHashMap;
import java.util.Map;

/** Thread-safe transfer metrics for downloads whose total byte size is initially unknown. */
public final class DownloadProgressMetrics {
    private static final long EMIT_INTERVAL_MILLIS = 1000L;

    public interface Clock {
        long nowMillis();
    }

    public static final class Attempt {
        private Attempt() {
        }
    }

    public static final class Snapshot {
        private final int progress;
        private final long downloadedBytes;
        private final long bytesPerSecond;
        private final long etaSeconds;
        private final long sequence;

        private Snapshot(int progress, long downloadedBytes, long bytesPerSecond,
                long etaSeconds, long sequence) {
            this.progress = progress;
            this.downloadedBytes = downloadedBytes;
            this.bytesPerSecond = bytesPerSecond;
            this.etaSeconds = etaSeconds;
            this.sequence = sequence;
        }

        public int getProgress() {
            return progress;
        }

        public long getDownloadedBytes() {
            return downloadedBytes;
        }

        public long getBytesPerSecond() {
            return bytesPerSecond;
        }

        public long getEtaSeconds() {
            return etaSeconds;
        }

        long getSequence() {
            return sequence;
        }
    }

    private final int totalSegments;
    private final Clock clock;
    private final Map<Attempt, Long> inFlightBytes = new IdentityHashMap<>();
    private int completedSegments;
    private long downloadedBytes;
    private long completedBytes;
    private long sampleBytes;
    private long sampleTimeMillis;
    private long lastEmitTimeMillis;
    private long bytesPerSecond;
    private long sequence;

    public DownloadProgressMetrics(int totalSegments, Clock clock) {
        this.totalSegments = Math.max(1, totalSegments);
        this.clock = clock;
        reset(0, 0L);
    }

    public synchronized void reset(int existingSegments, long existingBytes) {
        completedSegments = Math.max(0, Math.min(totalSegments, existingSegments));
        downloadedBytes = Math.max(0L, existingBytes);
        completedBytes = downloadedBytes;
        sampleBytes = downloadedBytes;
        sampleTimeMillis = clock.nowMillis();
        lastEmitTimeMillis = sampleTimeMillis;
        bytesPerSecond = 0L;
        sequence = 0L;
        inFlightBytes.clear();
    }

    public synchronized Attempt beginAttempt() {
        Attempt attempt = new Attempt();
        inFlightBytes.put(attempt, 0L);
        return attempt;
    }

    public synchronized Snapshot recordBytes(Attempt attempt, long byteCount) {
        if (!inFlightBytes.containsKey(attempt)) {
            return null;
        }
        if (byteCount > 0L) {
            downloadedBytes = saturatedAdd(downloadedBytes, byteCount);
            inFlightBytes.put(attempt,
                    saturatedAdd(inFlightBytes.get(attempt), byteCount));
        }
        return throttledSnapshot();
    }

    public synchronized Snapshot attemptFailed(Attempt attempt) {
        if (inFlightBytes.remove(attempt) == null) {
            return null;
        }
        return throttledSnapshot();
    }

    public synchronized Snapshot segmentCompleted(Attempt attempt, long segmentBytes) {
        if (inFlightBytes.remove(attempt) == null) {
            return null;
        }
        if (completedSegments < totalSegments) {
            completedSegments++;
        }
        if (segmentBytes > 0L) {
            completedBytes = saturatedAdd(completedBytes, segmentBytes);
        }
        return forceSnapshot();
    }

    public synchronized Snapshot snapshot() {
        return createSnapshot();
    }

    private Snapshot forceSnapshot() {
        long now = clock.nowMillis();
        lastEmitTimeMillis = now;
        updateSpeed(now);
        return createSnapshot();
    }

    private Snapshot throttledSnapshot() {
        long now = clock.nowMillis();
        if (elapsed(now, lastEmitTimeMillis) < EMIT_INTERVAL_MILLIS) {
            return null;
        }
        lastEmitTimeMillis = now;
        updateSpeed(now);
        return createSnapshot();
    }

    private void updateSpeed(long now) {
        long duration = elapsed(now, sampleTimeMillis);
        if (duration < EMIT_INTERVAL_MILLIS) {
            return;
        }
        long bytes = Math.max(0L, downloadedBytes - sampleBytes);
        bytesPerSecond = ratePerSecond(bytes, duration);
        sampleBytes = downloadedBytes;
        sampleTimeMillis = now;
    }

    private Snapshot createSnapshot() {
        long validBytes = saturatedAdd(completedBytes, inFlightByteCount());
        int progress = estimateProgress(validBytes);
        long eta = estimateEta(progress, validBytes);
        sequence = saturatedAdd(sequence, 1L);
        return new Snapshot(progress, downloadedBytes, Math.max(0L, bytesPerSecond), eta,
                sequence);
    }

    private int estimateProgress(long validBytes) {
        if (completedSegments >= totalSegments) {
            return 100;
        }
        if (completedSegments == 0 || completedBytes == 0L) {
            return 0;
        }
        double estimatedTotal = ((double) completedBytes * totalSegments) / completedSegments;
        int value = (int) Math.floor((validBytes * 100.0d) / estimatedTotal);
        return Math.max(0, Math.min(99, value));
    }

    private long estimateEta(int progress, long validBytes) {
        if (progress >= 100) {
            return 0L;
        }
        if (bytesPerSecond <= 0L || completedSegments == 0 || completedBytes == 0L) {
            return -1L;
        }
        double estimatedTotal = ((double) completedBytes * totalSegments) / completedSegments;
        double remaining = Math.max(0.0d, estimatedTotal - validBytes);
        double seconds = Math.ceil(remaining / bytesPerSecond);
        if (!Double.isFinite(seconds) || seconds >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) seconds);
    }

    private long inFlightByteCount() {
        long total = 0L;
        for (long bytes : inFlightBytes.values()) {
            total = saturatedAdd(total, bytes);
        }
        return total;
    }

    private static long elapsed(long now, long earlier) {
        if (now <= earlier) {
            return 0L;
        }
        return now - earlier;
    }

    private static long ratePerSecond(long bytes, long durationMillis) {
        if (bytes == 0L || durationMillis == 0L) {
            return 0L;
        }
        double value = bytes * 1000.0d / durationMillis;
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, (long) value);
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
