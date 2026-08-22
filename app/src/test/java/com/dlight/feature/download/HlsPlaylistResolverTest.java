package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HlsPlaylistResolverTest {
    @Test
    public void mediaPlaylistReturnsSegments() throws Exception {
        InMemoryFetcher fetcher = new InMemoryFetcher()
                .add("https://cdn.example.com/show/media.m3u8",
                        "#EXTM3U\n#EXTINF:4,\nfirst.ts\n#EXTINF:4,\nsecond.ts\n");

        List<String> segments = HlsPlaylistResolver.resolve(
                "https://cdn.example.com/show/media.m3u8", fetcher, 0);

        assertEquals(Arrays.asList(
                "https://cdn.example.com/show/first.ts",
                "https://cdn.example.com/show/second.ts"), segments);
    }

    @Test
    public void masterPlaylistFollowsHighestBandwidthVariant() throws Exception {
        InMemoryFetcher fetcher = new InMemoryFetcher()
                .add("https://cdn.example.com/master.m3u8",
                        "#EXTM3U\n"
                                + "#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow.m3u8\n"
                                + "#EXT-X-STREAM-INF:BANDWIDTH=2400000\nhigh.m3u8\n")
                .add("https://cdn.example.com/low.m3u8", "#EXTM3U\nlow.ts\n")
                .add("https://cdn.example.com/high.m3u8", "#EXTM3U\nhigh.ts\n");

        List<String> segments = HlsPlaylistResolver.resolve(
                "https://cdn.example.com/master.m3u8", fetcher, 0);

        assertEquals(Arrays.asList("https://cdn.example.com/high.ts"), segments);
    }

    @Test
    public void equalBandwidthKeepsInputOrder() throws Exception {
        InMemoryFetcher fetcher = new InMemoryFetcher()
                .add("https://cdn.example.com/master.m3u8",
                        "#EXTM3U\n"
                                + "#EXT-X-STREAM-INF:BANDWIDTH=2400000\nfirst.m3u8\n"
                                + "#EXT-X-STREAM-INF:BANDWIDTH=2400000\nsecond.m3u8\n")
                .add("https://cdn.example.com/first.m3u8", "#EXTM3U\nfirst.ts\n")
                .add("https://cdn.example.com/second.m3u8", "#EXTM3U\nsecond.ts\n");

        List<String> segments = HlsPlaylistResolver.resolve(
                "https://cdn.example.com/master.m3u8", fetcher, 0);

        assertEquals(Arrays.asList("https://cdn.example.com/first.ts"), segments);
    }

    @Test
    public void resolvesNestedMasterPlaylists() throws Exception {
        InMemoryFetcher fetcher = new InMemoryFetcher()
                .add("https://cdn.example.com/root.m3u8",
                        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=10\nlevel-one.m3u8\n")
                .add("https://cdn.example.com/level-one.m3u8",
                        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=20\nlevel-two.m3u8\n")
                .add("https://cdn.example.com/level-two.m3u8", "#EXTM3U\nsegment.ts\n");

        List<String> segments = HlsPlaylistResolver.resolve(
                "https://cdn.example.com/root.m3u8", fetcher, 0);

        assertEquals(Arrays.asList("https://cdn.example.com/segment.ts"), segments);
    }

    @Test
    public void rejectsMoreThanThreeNestedRedirects() throws Exception {
        InMemoryFetcher fetcher = new InMemoryFetcher()
                .add("https://cdn.example.com/root.m3u8", master("one.m3u8"))
                .add("https://cdn.example.com/one.m3u8", master("two.m3u8"))
                .add("https://cdn.example.com/two.m3u8", master("three.m3u8"))
                .add("https://cdn.example.com/three.m3u8", master("four.m3u8"))
                .add("https://cdn.example.com/four.m3u8", "#EXTM3U\nsegment.ts\n");

        assertFails("播放列表嵌套层级过深", new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                HlsPlaylistResolver.resolve(
                        "https://cdn.example.com/root.m3u8", fetcher, 0);
            }
        });
    }

    @Test
    public void emptyMediaPlaylistFails() throws Exception {
        final InMemoryFetcher fetcher = new InMemoryFetcher()
                .add("https://cdn.example.com/empty.m3u8", "#EXTM3U\n# empty\n");

        assertFails("播放列表中没有可下载的视频分片", new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                HlsPlaylistResolver.resolve(
                        "https://cdn.example.com/empty.m3u8", fetcher, 0);
            }
        });
    }

    @Test
    public void missingHighestVariantPropagatesWithoutFallback() throws Exception {
        final InMemoryFetcher fetcher = new InMemoryFetcher()
                .add("https://cdn.example.com/master.m3u8",
                        "#EXTM3U\n"
                                + "#EXT-X-STREAM-INF:BANDWIDTH=10\nlow.m3u8\n"
                                + "#EXT-X-STREAM-INF:BANDWIDTH=20\nmissing.m3u8\n")
                .add("https://cdn.example.com/low.m3u8", "#EXTM3U\nlow.ts\n");

        assertFails("Missing fake URL: https://cdn.example.com/missing.m3u8",
                new ThrowingRunnable() {
                    @Override
                    public void run() throws Exception {
                        HlsPlaylistResolver.resolve(
                                "https://cdn.example.com/master.m3u8", fetcher, 0);
                    }
                });
    }

    @Test
    public void returnedSegmentsAreImmutable() throws Exception {
        InMemoryFetcher fetcher = new InMemoryFetcher()
                .add("https://cdn.example.com/media.m3u8", "#EXTM3U\nsegment.ts\n");
        final List<String> segments = HlsPlaylistResolver.resolve(
                "https://cdn.example.com/media.m3u8", fetcher, 0);

        try {
            segments.add("https://cdn.example.com/other.ts");
            fail("segments should be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static String master(String child) {
        return "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n" + child + "\n";
    }

    private static void assertFails(String expectedMessage, ThrowingRunnable runnable)
            throws Exception {
        try {
            runnable.run();
            fail("Expected IOException");
        } catch (IOException error) {
            assertEquals(expectedMessage, error.getMessage());
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class InMemoryFetcher implements PlaylistFetcher {
        private final Map<String, String> playlists = new HashMap<>();

        InMemoryFetcher add(String url, String content) {
            playlists.put(url, content);
            return this;
        }

        @Override
        public String fetch(String url) throws IOException {
            if (!playlists.containsKey(url)) {
                throw new IOException("Missing fake URL: " + url);
            }
            return playlists.get(url);
        }
    }
}
