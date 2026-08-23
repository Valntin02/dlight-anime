package com.dlight.feature.download;

/** Thread-safe transfer metrics for downloads whose total byte size is initially unknown. */
public final class DownloadProgressMetrics {
    private static final long EMIT_INTERVAL_MILLIS = 1000L;

    public interface Clock {
        long nowMillis();
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
    private int completedSegments;
    private long downloadedBytes;
    private long completedBytes;
    private long sampleBytes;
    private long sampleTimeMillis;
    private long lastEmitTimeMillis;
    private long bytesPerSecond;
    private int lastProgress;
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
        lastProgress = (int) ((completedSegments * 100L) / totalSegments);
        sequence = 0L;
    }

    public synchronized Snapshot recordBytes(long byteCount) {
        if (byteCount > 0L) {
            downloadedBytes = saturatedAdd(downloadedBytes, byteCount);
        }
        long now = clock.nowMillis();
        if (elapsed(now, lastEmitTimeMillis) < EMIT_INTERVAL_MILLIS) {
            return null;
        }
        lastEmitTimeMillis = now;
        updateSpeed(now);
        return snapshot();
    }

    public synchronized Snapshot segmentCompleted(long segmentBytes) {
        if (completedSegments < totalSegments) {
            completedSegments++;
        }
        if (segmentBytes > 0L) {
            completedBytes = saturatedAdd(completedBytes, segmentBytes);
        }
        long now = clock.nowMillis();
        lastEmitTimeMillis = now;
        updateSpeed(now);
        return snapshot();
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

    private Snapshot snapshot() {
        int progress = Math.max(lastProgress, estimateProgress());
        lastProgress = progress;
        long eta = estimateEta(progress);
        sequence = saturatedAdd(sequence, 1L);
        return new Snapshot(progress, downloadedBytes, Math.max(0L, bytesPerSecond), eta,
                sequence);
    }

    private int estimateProgress() {
        if (completedSegments >= totalSegments) {
            return 100;
        }
        if (completedSegments == 0 || completedBytes == 0L) {
            return 0;
        }
        double estimatedTotal = ((double) completedBytes * totalSegments) / completedSegments;
        int value = (int) Math.floor((completedBytes * 100.0d) / estimatedTotal);
        return Math.max(0, Math.min(99, value));
    }

    private long estimateEta(int progress) {
        if (progress >= 100) {
            return 0L;
        }
        if (bytesPerSecond <= 0L || completedSegments == 0 || completedBytes == 0L) {
            return -1L;
        }
        double estimatedTotal = ((double) completedBytes * totalSegments) / completedSegments;
        double remaining = Math.max(0.0d, estimatedTotal - completedBytes);
        double seconds = Math.ceil(remaining / bytesPerSecond);
        if (!Double.isFinite(seconds) || seconds >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) seconds);
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
