package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
                "https://cdn.example.com/show/media.m3u8", fetcher, 0, true);

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
                "https://cdn.example.com/master.m3u8", fetcher, 0, true);

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
                "https://cdn.example.com/master.m3u8", fetcher, 0, true);

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
                "https://cdn.example.com/root.m3u8", fetcher, 0, true);

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
                        "https://cdn.example.com/root.m3u8", fetcher, 0, true);
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
                        "https://cdn.example.com/empty.m3u8", fetcher, 0, true);
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
                                "https://cdn.example.com/master.m3u8", fetcher, 0, true);
                    }
                });
    }

    @Test
    public void returnedSegmentsAreImmutable() throws Exception {
        InMemoryFetcher fetcher = new InMemoryFetcher()
                .add("https://cdn.example.com/media.m3u8", "#EXTM3U\nsegment.ts\n");
        final List<String> segments = HlsPlaylistResolver.resolve(
                "https://cdn.example.com/media.m3u8", fetcher, 0, true);

        try {
            segments.add("https://cdn.example.com/other.ts");
            fail("segments should be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void fakeFetcherFinalUriBecomesParserBase() throws Exception {
        InMemoryFetcher fetcher = new InMemoryFetcher()
                .add("https://cdn.example.com/old.m3u8",
                        "https://cdn.example.com/new/master.m3u8",
                        "#EXTM3U\nsegment.ts\n");

        List<String> segments = HlsPlaylistResolver.resolve(
                "https://cdn.example.com/old.m3u8", fetcher, 0, true);

        assertEquals(Arrays.asList("https://cdn.example.com/new/segment.ts"), segments);
    }

    @Test
    public void networkFetcherUsesFinalRedirectUriAsParserBase() throws Exception {
        JdkHttpServer server = newServer();
        server.createContext("/old", redirect("/new/master.m3u8"));
        server.createContext("/new/master.m3u8", response(200, "#EXTM3U\nsegment.ts\n"));
        server.start();
        try {
            String base = serverBase(server);

            assertEquals(Arrays.asList(base + "/new/segment.ts"),
                    HlsPlaylistResolver.resolve(base + "/old"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void networkFetcherRejectsRedirectLoopMissingLocationAndNon200() throws Exception {
        JdkHttpServer server = newServer();
        server.createContext("/loop-a", redirect("/loop-b"));
        server.createContext("/loop-b", redirect("/loop-a"));
        server.createContext("/missing-location", response(302, ""));
        server.createContext("/not-found", response(404, "missing"));
        server.createContext("/partial", response(206, "#EXTM3U\nsegment.ts\n"));
        server.start();
        try {
            final String base = serverBase(server);
            assertFails("播放列表重定向循环", new ThrowingRunnable() {
                @Override
                public void run() throws Exception {
                    HlsPlaylistResolver.resolve(base + "/loop-a");
                }
            });
            assertFails("播放列表重定向缺少 Location", new ThrowingRunnable() {
                @Override
                public void run() throws Exception {
                    HlsPlaylistResolver.resolve(base + "/missing-location");
                }
            });
            assertFails("播放列表请求失败，HTTP 状态码: 404", new ThrowingRunnable() {
                @Override
                public void run() throws Exception {
                    HlsPlaylistResolver.resolve(base + "/not-found");
                }
            });
            assertFails("播放列表请求失败，HTTP 状态码: 206", new ThrowingRunnable() {
                @Override
                public void run() throws Exception {
                    HlsPlaylistResolver.resolve(base + "/partial");
                }
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void networkFetcherRejectsMoreThanFiveRedirects() throws Exception {
        JdkHttpServer server = newServer();
        for (int index = 0; index < 6; index++) {
            server.createContext("/hop-" + index, redirect("/hop-" + (index + 1)));
        }
        server.createContext("/hop-6", response(200, "#EXTM3U\nsegment.ts\n"));
        server.start();
        try {
            final String base = serverBase(server);
            assertFails("播放列表重定向次数过多", new ThrowingRunnable() {
                @Override
                public void run() throws Exception {
                    HlsPlaylistResolver.resolve(base + "/hop-0");
                }
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void networkFetcherRejectsAbruptDisconnectAndOversizedContent() throws Exception {
        JdkHttpServer server = newServer();
        server.createContext("/disconnect", new TestHandler() {
            @Override
            public void handle(JdkHttpExchange exchange) throws Exception {
                byte[] body = "#EXTM3U\n".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length + 100);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            }
        });
        final String oversized = repeat('x', HlsPlaylistParser.MAX_CONTENT_CHARS + 1);
        server.createContext("/oversized", response(200, oversized));
        server.start();
        try {
            final String base = serverBase(server);
            assertAnyIOException(new ThrowingRunnable() {
                @Override
                public void run() throws Exception {
                    HlsPlaylistResolver.resolve(base + "/disconnect");
                }
            });
            assertFails("播放列表内容过大", new ThrowingRunnable() {
                @Override
                public void run() throws Exception {
                    HlsPlaylistResolver.resolve(base + "/oversized");
                }
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void segmentDownloaderFollowsValidatedRedirect() throws Exception {
        JdkHttpServer server = newServer();
        server.createContext("/segment-old", redirect("/segment-final"));
        server.createContext("/segment-final", response(200, "video-data"));
        server.start();
        File destination = File.createTempFile("segment-redirect", ".ts");
        try {
            VideoDownloader.downloadSegment(
                    serverBase(server) + "/segment-old", destination, null, true);

            assertArrayEquals("video-data".getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(destination.toPath()));
        } finally {
            destination.delete();
            server.stop(0);
        }
    }

    @Test
    public void segmentDownloaderRejectsUnsafeRedirectTarget() throws Exception {
        JdkHttpServer server = newServer();
        server.createContext("/segment-old", redirect("ftp://127.0.0.1/private.ts"));
        server.start();
        final File destination = File.createTempFile("segment-unsafe", ".ts");
        try {
            final String url = serverBase(server) + "/segment-old";
            assertFails("下载地址无效", new ThrowingRunnable() {
                @Override
                public void run() throws Exception {
                    VideoDownloader.downloadSegment(url, destination, null, true);
                }
            });
        } finally {
            destination.delete();
            server.stop(0);
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

    private static void assertAnyIOException(ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
            fail("Expected IOException");
        } catch (IOException expected) {
            // Expected.
        }
    }

    private static JdkHttpServer newServer() throws Exception {
        return JdkHttpServer.create(new InetSocketAddress("127.0.0.1", 0));
    }

    private static String serverBase(JdkHttpServer server) throws Exception {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static TestHandler redirect(final String location) {
        return new TestHandler() {
            @Override
            public void handle(JdkHttpExchange exchange) throws Exception {
                exchange.getResponseHeaders().add("Location", location);
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            }
        };
    }

    private static TestHandler response(final int status, final String content) {
        return new TestHandler() {
            @Override
            public void handle(JdkHttpExchange exchange) throws Exception {
                byte[] body = content.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            }
        };
    }

    private static String repeat(char character, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, character);
        return new String(characters);
    }

    private interface TestHandler {
        void handle(JdkHttpExchange exchange) throws Exception;
    }

    private static final class JdkHttpServer {
        private final Object server;
        private final Class<?> serverClass;
        private final Class<?> handlerClass;

        private JdkHttpServer(Object server, Class<?> serverClass, Class<?> handlerClass) {
            this.server = server;
            this.serverClass = serverClass;
            this.handlerClass = handlerClass;
        }

        static JdkHttpServer create(InetSocketAddress address) throws Exception {
            Class<?> serverClass = Class.forName("com.sun.net.httpserver.HttpServer");
            Object server = serverClass.getMethod("create", InetSocketAddress.class, int.class)
                    .invoke(null, address, 0);
            return new JdkHttpServer(server, serverClass,
                    Class.forName("com.sun.net.httpserver.HttpHandler"));
        }

        void createContext(String path, final TestHandler handler) throws Exception {
            Object proxy = Proxy.newProxyInstance(handlerClass.getClassLoader(),
                    new Class<?>[] {handlerClass}, new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] arguments)
                                throws Throwable {
                            if ("handle".equals(method.getName())) {
                                handler.handle(new JdkHttpExchange(arguments[0]));
                            }
                            return null;
                        }
                    });
            serverClass.getMethod("createContext", String.class, handlerClass)
                    .invoke(server, path, proxy);
        }

        void start() throws Exception {
            serverClass.getMethod("start").invoke(server);
        }

        void stop(int delay) {
            try {
                serverClass.getMethod("stop", int.class).invoke(server, delay);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        }

        InetSocketAddress getAddress() throws Exception {
            return (InetSocketAddress) serverClass.getMethod("getAddress").invoke(server);
        }
    }

    private static final class JdkHttpExchange {
        private final Object exchange;
        private final Class<?> exchangeClass;

        private JdkHttpExchange(Object exchange) throws ClassNotFoundException {
            this.exchange = exchange;
            this.exchangeClass = Class.forName("com.sun.net.httpserver.HttpExchange");
        }

        JdkHeaders getResponseHeaders() throws Exception {
            return new JdkHeaders(exchangeClass.getMethod("getResponseHeaders").invoke(exchange));
        }

        void sendResponseHeaders(int status, long length) throws Exception {
            invoke("sendResponseHeaders", new Class<?>[] {int.class, long.class}, status, length);
        }

        OutputStream getResponseBody() throws Exception {
            return (OutputStream) invoke("getResponseBody", new Class<?>[0]);
        }

        void close() throws Exception {
            invoke("close", new Class<?>[0]);
        }

        private Object invoke(String name, Class<?>[] types, Object... arguments)
                throws Exception {
            try {
                return exchangeClass.getMethod(name, types).invoke(exchange, arguments);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw error;
            }
        }
    }

    private static final class JdkHeaders {
        private final Object headers;
        private final Class<?> headersClass;

        private JdkHeaders(Object headers) throws ClassNotFoundException {
            this.headers = headers;
            this.headersClass = Class.forName("com.sun.net.httpserver.Headers");
        }

        void add(String name, String value) throws Exception {
            headersClass.getMethod("add", String.class, String.class)
                    .invoke(headers, name, value);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class InMemoryFetcher implements PlaylistFetcher {
        private final Map<String, FetchedPlaylist> playlists = new HashMap<>();

        InMemoryFetcher add(String url, String content) {
            return add(url, url, content);
        }

        InMemoryFetcher add(String url, String finalUrl, String content) {
            playlists.put(url, new FetchedPlaylist(content, URI.create(finalUrl)));
            return this;
        }

        @Override
        public FetchedPlaylist fetch(String url) throws IOException {
            if (!playlists.containsKey(url)) {
                throw new IOException("Missing fake URL: " + url);
            }
            return playlists.get(url);
        }
    }
}
