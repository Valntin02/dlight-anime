package com.dlight.feature.download;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class VideoDownloaderFileTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void segmentWritePublishesOnlyCompletedFile() throws Exception {
        File destination = new File(temporaryFolder.getRoot(), "0.ts");

        VideoDownloader.writeSegment(
                new ByteArrayInputStream(bytes("complete")), destination, null);

        assertArrayEquals(bytes("complete"), Files.readAllBytes(destination.toPath()));
        assertFalse(new File(destination.getPath() + ".part").exists());
    }

    @Test
    public void segmentReadFailureRemovesPartAndDoesNotPublishFinalFile() throws Exception {
        File destination = new File(temporaryFolder.getRoot(), "0.ts");

        try {
            VideoDownloader.writeSegment(new FailingInputStream(bytes("partial")),
                    destination, null);
            fail("Expected IOException");
        } catch (IOException expected) {
            assertEquals("simulated read failure", expected.getMessage());
        }

        assertFalse(destination.exists());
        assertFalse(new File(destination.getPath() + ".part").exists());
    }

    @Test
    public void pausedSegmentWriteRemovesPartAndDoesNotPublishFinalFile() throws Exception {
        File destination = new File(temporaryFolder.getRoot(), "0.ts");

        try {
            VideoDownloader.writeSegment(new ByteArrayInputStream(bytes("partial")),
                    destination, () -> true);
            fail("Expected IOException");
        } catch (IOException expected) {
            assertEquals("下载已暂停", expected.getMessage());
        }

        assertFalse(destination.exists());
        assertFalse(new File(destination.getPath() + ".part").exists());
    }

    @Test
    public void pauseBeforeOpenRemovesStalePartAndPreservesCompletedDestination()
            throws Exception {
        File destination = new File(temporaryFolder.getRoot(), "0.ts");
        write(destination, "verified");
        File partFile = new File(destination.getPath() + ".part");
        write(partFile, "stale");

        try {
            VideoDownloader.downloadSegment(
                    "https://cdn.example.com/segment.ts", destination, () -> true, true);
            fail("Expected IOException");
        } catch (IOException expected) {
            assertEquals("下载已暂停", expected.getMessage());
        }

        assertFalse(partFile.exists());
        assertArrayEquals(bytes("verified"), Files.readAllBytes(destination.toPath()));
    }

    @Test
    public void openFailureRemovesStalePartAndPreservesCompletedDestination() throws Exception {
        File destination = new File(temporaryFolder.getRoot(), "0.ts");
        write(destination, "verified");
        File partFile = new File(destination.getPath() + ".part");
        write(partFile, "stale");

        try {
            VideoDownloader.downloadSegment(
                    "not-a-download-url", destination, null, true);
            fail("Expected IOException");
        } catch (IOException expected) {
            assertEquals("下载地址无效", expected.getMessage());
        }

        assertFalse(partFile.exists());
        assertArrayEquals(bytes("verified"), Files.readAllBytes(destination.toPath()));
    }

    @Test
    public void retryFailureRemovesStalePartAndPreservesCompletedDestination() throws Exception {
        JdkHttpServer server = newServer();
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/segment.ts", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 500, "failure");
        });
        server.start();
        try {
            File destination = new File(temporaryFolder.getRoot(), "0.ts");
            write(destination, "verified");
            File partFile = new File(destination.getPath() + ".part");
            write(partFile, "stale");

            try {
                VideoDownloader.downloadSegment(
                        serverBase(server) + "/segment.ts", destination, null, true);
                fail("Expected IOException");
            } catch (IOException expected) {
                assertEquals("视频分片请求失败，HTTP 状态码: 500", expected.getMessage());
            }

            assertEquals(3, requests.get());
            assertFalse(partFile.exists());
            assertArrayEquals(bytes("verified"), Files.readAllBytes(destination.toPath()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void successfulAttemptReplacesStalePartAndCompletedDestination() throws Exception {
        JdkHttpServer server = newServer();
        server.createContext("/segment.ts", exchange -> respond(exchange, 200, "replacement"));
        server.start();
        try {
            File destination = new File(temporaryFolder.getRoot(), "0.ts");
            write(destination, "verified");
            File partFile = new File(destination.getPath() + ".part");
            write(partFile, "stale");

            VideoDownloader.downloadSegment(
                    serverBase(server) + "/segment.ts", destination, null, true);

            assertFalse(partFile.exists());
            assertArrayEquals(bytes("replacement"), Files.readAllBytes(destination.toPath()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void mergePublishesOnlyAfterAllSegmentsAreRead() throws Exception {
        File tempDirectory = temporaryFolder.newFolder("segments");
        write(new File(tempDirectory, "0.ts"), "first");
        write(new File(tempDirectory, "1.ts"), "second");
        File destination = new File(temporaryFolder.getRoot(), "safe.ts");

        VideoDownloader.mergeSegments(tempDirectory, 2, destination);

        assertArrayEquals(bytes("firstsecond"), Files.readAllBytes(destination.toPath()));
        assertFalse(new File(destination.getPath() + ".part").exists());
        assertTrue(new File(tempDirectory, "0.ts").exists());
        assertTrue(new File(tempDirectory, "1.ts").exists());
    }

    @Test
    public void missingSegmentLeavesNoMergedFilesAndPreservesCompletedSegments()
            throws Exception {
        File tempDirectory = temporaryFolder.newFolder("segments");
        File completed = new File(tempDirectory, "0.ts");
        write(completed, "verified");
        File destination = new File(temporaryFolder.getRoot(), "safe.ts");
        write(destination, "stale-final");
        write(new File(destination.getPath() + ".part"), "stale-part");

        try {
            VideoDownloader.mergeSegments(tempDirectory, 2, destination);
            fail("Expected IOException");
        } catch (IOException expected) {
            assertEquals("缺失视频分片: 1", expected.getMessage());
        }

        assertFalse(destination.exists());
        assertFalse(new File(destination.getPath() + ".part").exists());
        assertArrayEquals(bytes("verified"), Files.readAllBytes(completed.toPath()));
        assertTrue(tempDirectory.exists());
    }

    @Test
    public void mergeReadFailureLeavesNoMergedFilesAndPreservesCompletedSegments()
            throws Exception {
        File tempDirectory = temporaryFolder.newFolder("segments");
        File completed = new File(tempDirectory, "0.ts");
        write(completed, "verified");
        assertTrue(new File(tempDirectory, "1.ts").mkdir());
        File destination = new File(temporaryFolder.getRoot(), "safe.ts");

        try {
            VideoDownloader.mergeSegments(tempDirectory, 2, destination);
            fail("Expected IOException");
        } catch (IOException expected) {
            // A directory cannot be opened as the next segment input stream.
        }

        assertFalse(destination.exists());
        assertFalse(new File(destination.getPath() + ".part").exists());
        assertArrayEquals(bytes("verified"), Files.readAllBytes(completed.toPath()));
        assertTrue(tempDirectory.exists());
    }

    @Test
    public void retryRequestsOnlyMissingAtomicFinalSegments() throws Exception {
        File tempDirectory = temporaryFolder.newFolder("segments");
        write(new File(tempDirectory, "0.ts"), "verified");
        write(new File(tempDirectory, "1.ts.part"), "partial");
        assertTrue(new File(tempDirectory, "2.ts").createNewFile());

        List<Integer> missing = VideoDownloader.findMissingSegments(tempDirectory, 3);

        assertEquals(Arrays.asList(1, 2), missing);
    }

    @Test
    public void schedulingPreparationClearsAllMissingPartsBeforeFailureShortCircuit()
            throws Exception {
        File tempDirectory = temporaryFolder.newFolder("segments");
        File completed = new File(tempDirectory, "0.ts");
        write(completed, "verified");
        File completedPart = new File(tempDirectory, "0.ts.part");
        File firstMissingPart = new File(tempDirectory, "1.ts.part");
        File secondMissingPart = new File(tempDirectory, "2.ts.part");
        File obsoleteSegment = new File(tempDirectory, "3.ts");
        File obsoletePart = new File(tempDirectory, "4.ts.part");
        write(completedPart, "stale-completed");
        write(firstMissingPart, "stale-one");
        write(secondMissingPart, "stale-two");
        write(obsoleteSegment, "obsolete");
        write(obsoletePart, "obsolete-part");

        List<Integer> missing = VideoDownloader.prepareMissingSegments(tempDirectory, 3);

        assertEquals(Arrays.asList(1, 2), missing);
        assertFalse(completedPart.exists());
        assertFalse(firstMissingPart.exists());
        assertFalse(secondMissingPart.exists());
        assertFalse(obsoleteSegment.exists());
        assertFalse(obsoletePart.exists());
        assertArrayEquals(bytes("verified"), Files.readAllBytes(completed.toPath()));
    }

    @Test
    public void matchingPlaylistFingerprintPreservesCompletedSegments() throws Exception {
        File tempDirectory = new File(temporaryFolder.getRoot(), "segments");
        List<String> urls = Arrays.asList(
                "https://cdn.example.com/0.ts", "https://cdn.example.com/1.ts");
        VideoDownloader.preparePlaylistState(tempDirectory, urls);
        File completed = new File(tempDirectory, "0.ts");
        write(completed, "verified");

        VideoDownloader.preparePlaylistState(tempDirectory, urls);

        assertArrayEquals(bytes("verified"), Files.readAllBytes(completed.toPath()));
    }

    @Test
    public void matchingPlaylistFingerprintClearsStaleFingerprintPart() throws Exception {
        File tempDirectory = new File(temporaryFolder.getRoot(), "segments");
        List<String> urls = Arrays.asList(
                "https://cdn.example.com/0.ts", "https://cdn.example.com/1.ts");
        VideoDownloader.preparePlaylistState(tempDirectory, urls);
        File completed = new File(tempDirectory, "0.ts");
        File fingerprintPart = new File(tempDirectory, ".playlist.sha256.part");
        write(completed, "verified");
        write(fingerprintPart, "stale");

        VideoDownloader.preparePlaylistState(tempDirectory, urls);

        assertFalse(fingerprintPart.exists());
        assertArrayEquals(bytes("verified"), Files.readAllBytes(completed.toPath()));
    }

    @Test
    public void changedPlaylistFingerprintClearsCompletedSegments() throws Exception {
        File tempDirectory = new File(temporaryFolder.getRoot(), "segments");
        VideoDownloader.preparePlaylistState(tempDirectory,
                Arrays.asList("https://cdn.example.com/old.ts"));
        File completed = new File(tempDirectory, "0.ts");
        write(completed, "verified");

        VideoDownloader.preparePlaylistState(tempDirectory,
                Arrays.asList("https://cdn.example.com/new.ts"));

        assertFalse(completed.exists());
        assertTrue(new File(tempDirectory, ".playlist.sha256").isFile());
    }

    @Test
    public void legacyDirectoryWithoutFingerprintIsCleared() throws Exception {
        File tempDirectory = temporaryFolder.newFolder("segments");
        File completed = new File(tempDirectory, "0.ts");
        write(completed, "legacy");

        VideoDownloader.preparePlaylistState(tempDirectory,
                Arrays.asList("https://cdn.example.com/0.ts"));

        assertFalse(completed.exists());
        assertTrue(new File(tempDirectory, ".playlist.sha256").isFile());
    }

    @Test
    public void playlistFingerprintIsPublishedAtomically() throws Exception {
        File tempDirectory = new File(temporaryFolder.getRoot(), "segments");

        VideoDownloader.preparePlaylistState(tempDirectory, Arrays.asList(
                "https://cdn.example.com/a.ts", "https://cdn.example.com/b.ts"));

        File fingerprint = new File(tempDirectory, ".playlist.sha256");
        String value = new String(Files.readAllBytes(fingerprint.toPath()),
                StandardCharsets.UTF_8);
        assertEquals(64, value.length());
        assertTrue(value.matches("[0-9a-f]{64}"));
        assertFalse(new File(tempDirectory, ".playlist.sha256.part").exists());
    }

    @Test
    public void ordinaryTaskFailurePreservesVerifiedSegmentsForRetry() throws Exception {
        JdkHttpServer server = newServer();
        AtomicInteger existingRequests = new AtomicInteger();
        AtomicInteger missingRequests = new AtomicInteger();
        server.createContext("/playlist.m3u8", exchange -> respond(exchange, 200,
                "#EXTM3U\n#EXTINF:1,\nexisting.ts\n#EXTINF:1,\nmissing.ts\n"));
        server.createContext("/existing.ts", exchange -> {
            existingRequests.incrementAndGet();
            respond(exchange, 200, "unexpected");
        });
        server.createContext("/missing.ts", exchange -> {
            missingRequests.incrementAndGet();
            respond(exchange, 500, "failure");
        });
        server.start();
        try {
            File destination = temporaryFolder.newFolder("downloads");
            String playlistUrl = serverBase(server) + "/playlist.m3u8";
            String fileName = "episode";
            File taskDirectory = taskDirectory(destination, playlistUrl, fileName);
            assertTrue(taskDirectory.mkdir());
            VideoDownloader.preparePlaylistState(taskDirectory, Arrays.asList(
                    serverBase(server) + "/existing.ts",
                    serverBase(server) + "/missing.ts"));
            File completed = new File(taskDirectory, "0.ts");
            write(completed, "verified");
            RecordingCallback callback = new RecordingCallback();

            VideoDownloader.mulDownloadM3u8(
                    playlistUrl, destination, fileName, true, null, callback);

            assertTrue(callback.failure.get() instanceof IOException);
            assertEquals(0, existingRequests.get());
            assertEquals(3, missingRequests.get());
            assertTrue(taskDirectory.exists());
            assertArrayEquals(bytes("verified"), Files.readAllBytes(completed.toPath()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void interruptedTaskPreservesVerifiedSegmentsForRetry() throws Exception {
        JdkHttpServer server = newServer();
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        CountDownLatch responseFinished = new CountDownLatch(1);
        server.createContext("/playlist.m3u8", exchange -> respond(exchange, 200,
                "#EXTM3U\n#EXTINF:1,\nexisting.ts\n#EXTINF:1,\nslow.ts\n"));
        server.createContext("/slow.ts", exchange -> {
            requestStarted.countDown();
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
                respond(exchange, 200, "slow");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                exchange.close();
            } finally {
                responseFinished.countDown();
            }
        });
        server.start();
        try {
            File destination = temporaryFolder.newFolder("downloads");
            String playlistUrl = serverBase(server) + "/playlist.m3u8";
            String fileName = "episode";
            File taskDirectory = taskDirectory(destination, playlistUrl, fileName);
            assertTrue(taskDirectory.mkdir());
            VideoDownloader.preparePlaylistState(taskDirectory, Arrays.asList(
                    serverBase(server) + "/existing.ts",
                    serverBase(server) + "/slow.ts"));
            File completed = new File(taskDirectory, "0.ts");
            write(completed, "verified");
            RecordingCallback callback = new RecordingCallback();
            Thread downloadThread = new Thread(() -> VideoDownloader.mulDownloadM3u8(
                    playlistUrl, destination, fileName, true, null, callback));

            downloadThread.start();
            assertTrue(requestStarted.await(5, TimeUnit.SECONDS));
            downloadThread.interrupt();
            downloadThread.join(5000);
            int progressAtTerminal = callback.progress.get();
            assertEquals(1, callback.terminals.get());
            releaseResponse.countDown();
            assertTrue(responseFinished.await(5, TimeUnit.SECONDS));
            Thread.sleep(200);

            assertFalse(downloadThread.isAlive());
            assertTrue(callback.failure.get() instanceof InterruptedException);
            assertEquals(progressAtTerminal, callback.progress.get());
            assertEquals(1, callback.terminals.get());
            assertTrue(taskDirectory.exists());
            assertArrayEquals(bytes("verified"), Files.readAllBytes(completed.toPath()));
            assertFalse(new File(taskDirectory, "1.ts").exists());
            assertFalse(new File(taskDirectory, "1.ts.part").exists());
            assertFalse(new File(destination, fileName + ".ts").exists());
        } finally {
            releaseResponse.countDown();
            server.stop(0);
        }
    }

    @Test
    public void workerFailureCancelsBlockedPeerBeforeTerminalCallback() throws Exception {
        JdkHttpServer server = newServer();
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        CountDownLatch slowFinished = new CountDownLatch(1);
        server.createContext("/playlist.m3u8", exchange -> respond(exchange, 200,
                "#EXTM3U\n#EXTINF:1,\nslow.ts\n#EXTINF:1,\nfail.ts\n"));
        server.createContext("/slow.ts", exchange -> {
            slowStarted.countDown();
            try {
                releaseSlow.await(15, TimeUnit.SECONDS);
                respond(exchange, 200, "late-data");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                exchange.close();
            } finally {
                slowFinished.countDown();
            }
        });
        server.createContext("/fail.ts", exchange -> {
            slowStarted.await(5, TimeUnit.SECONDS);
            respond(exchange, 500, "failure");
        });
        server.start();
        Thread downloadThread = null;
        try {
            File destination = temporaryFolder.newFolder("downloads");
            String playlistUrl = serverBase(server) + "/playlist.m3u8";
            String fileName = "episode";
            File taskDirectory = taskDirectory(destination, playlistUrl, fileName);
            RecordingCallback callback = new RecordingCallback();
            downloadThread = new Thread(() -> VideoDownloader.mulDownloadM3u8(
                    playlistUrl, destination, fileName, true, null, callback));

            downloadThread.start();
            assertTrue("slow request did not start; failure=" + callback.failure.get(),
                    slowStarted.await(5, TimeUnit.SECONDS));
            downloadThread.join(5000);
            boolean finishedBeforeServerRelease = !downloadThread.isAlive();
            int progressAtTerminal = callback.progress.get();
            int terminalsAtReturn = callback.terminals.get();
            releaseSlow.countDown();
            assertTrue(slowFinished.await(5, TimeUnit.SECONDS));
            downloadThread.join(5000);
            Thread.sleep(200);

            assertTrue("terminal callback waited for cancellation",
                    finishedBeforeServerRelease);
            assertEquals(1, terminalsAtReturn);
            assertEquals(1, callback.terminals.get());
            assertTrue(callback.failure.get() instanceof IOException);
            assertEquals(progressAtTerminal, callback.progress.get());
            assertFalse(new File(taskDirectory, "0.ts").exists());
            assertFalse(new File(taskDirectory, "0.ts.part").exists());
            assertFalse(new File(destination, fileName + ".ts").exists());
        } finally {
            releaseSlow.countDown();
            if (downloadThread != null) {
                downloadThread.join(5000);
            }
            server.stop(0);
        }
    }

    @Test
    public void pausedTaskPreservesVerifiedSegmentsForRetry() throws Exception {
        File destination = temporaryFolder.newFolder("downloads");
        String playlistUrl = "https://cdn.example.com/playlist.m3u8";
        String fileName = "episode";
        File taskDirectory = taskDirectory(destination, playlistUrl, fileName);
        assertTrue(taskDirectory.mkdir());
        File completed = new File(taskDirectory, "0.ts");
        write(completed, "verified");
        RecordingCallback callback = new RecordingCallback();

        VideoDownloader.mulDownloadM3u8(
                playlistUrl, destination, fileName, true, () -> true, callback);

        assertEquals(1, callback.paused.get());
        assertTrue(taskDirectory.exists());
        assertArrayEquals(bytes("verified"), Files.readAllBytes(completed.toPath()));
    }

    @Test
    public void successfulTaskDeletesTempDirectory() throws Exception {
        JdkHttpServer server = newServer();
        server.createContext("/playlist.m3u8", exchange -> respond(exchange, 200,
                "#EXTM3U\n#EXTINF:1,\nexisting.ts\n"));
        server.start();
        try {
            File destination = temporaryFolder.newFolder("downloads");
            String playlistUrl = serverBase(server) + "/playlist.m3u8";
            String fileName = "episode";
            File taskDirectory = taskDirectory(destination, playlistUrl, fileName);
            assertTrue(taskDirectory.mkdir());
            VideoDownloader.preparePlaylistState(taskDirectory,
                    Arrays.asList(serverBase(server) + "/existing.ts"));
            write(new File(taskDirectory, "0.ts"), "verified");
            RecordingCallback callback = new RecordingCallback();

            VideoDownloader.mulDownloadM3u8(
                    playlistUrl, destination, fileName, true, null, callback);

            assertTrue(callback.success.get().isFile());
            assertFalse(taskDirectory.exists());
            assertArrayEquals(bytes("verified"),
                    Files.readAllBytes(callback.success.get().toPath()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void explicitDeleteRemovesTaskTempDirectory() throws Exception {
        File destination = temporaryFolder.newFolder("downloads");
        String playlistUrl = "https://cdn.example.com/playlist.m3u8";
        String fileName = "episode";
        File taskDirectory = taskDirectory(destination, playlistUrl, fileName);
        assertTrue(taskDirectory.mkdir());
        write(new File(taskDirectory, "0.ts"), "verified");

        VideoDownloader.deletePartialDownload(destination, playlistUrl, fileName);

        assertFalse(taskDirectory.exists());
    }

    private static File taskDirectory(File destination, String playlistUrl, String fileName) {
        return new File(destination,
                ".temp_" + Integer.toHexString((playlistUrl + fileName).hashCode()));
    }

    private static JdkHttpServer newServer() throws Exception {
        return JdkHttpServer.create(new InetSocketAddress("127.0.0.1", 0));
    }

    private static String serverBase(JdkHttpServer server) throws Exception {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(JdkHttpExchange exchange, int status, String content)
            throws Exception {
        byte[] body = bytes(content);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void write(File file, String content) throws IOException {
        Files.write(file.toPath(), bytes(content));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private interface TestHandler {
        void handle(JdkHttpExchange exchange) throws Exception;
    }

    private static final class JdkHttpServer {
        private final Object server;
        private final Class<?> serverClass;
        private final Class<?> handlerClass;
        private final ExecutorService executor;

        private JdkHttpServer(Object server, Class<?> serverClass, Class<?> handlerClass,
                ExecutorService executor) {
            this.server = server;
            this.serverClass = serverClass;
            this.handlerClass = handlerClass;
            this.executor = executor;
        }

        static JdkHttpServer create(InetSocketAddress address) throws Exception {
            Class<?> serverClass = Class.forName("com.sun.net.httpserver.HttpServer");
            Object server = serverClass.getMethod("create", InetSocketAddress.class, int.class)
                    .invoke(null, address, 0);
            ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                return thread;
            });
            serverClass.getMethod("setExecutor", Executor.class).invoke(server, executor);
            return new JdkHttpServer(server, serverClass,
                    Class.forName("com.sun.net.httpserver.HttpHandler"), executor);
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
            } finally {
                executor.shutdownNow();
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

    private static final class RecordingCallback implements VideoDownloader.DownloadCallback {
        private final AtomicReference<Exception> failure = new AtomicReference<>();
        private final AtomicReference<File> success = new AtomicReference<>();
        private final AtomicInteger paused = new AtomicInteger();
        private final AtomicInteger progress = new AtomicInteger();
        private final AtomicInteger terminals = new AtomicInteger();

        @Override
        public void onProgress(int progress) {
            this.progress.incrementAndGet();
        }

        @Override
        public void onSuccess(File file) {
            success.set(file);
            terminals.incrementAndGet();
        }

        @Override
        public void onFailure(Exception error) {
            failure.set(error);
            terminals.incrementAndGet();
        }

        @Override
        public void onPaused() {
            paused.incrementAndGet();
            terminals.incrementAndGet();
        }
    }

    private static final class FailingInputStream extends InputStream {
        private final byte[] bytes;
        private boolean returnedBytes;

        private FailingInputStream(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int read() throws IOException {
            throw new IOException("simulated read failure");
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (returnedBytes) {
                throw new IOException("simulated read failure");
            }
            returnedBytes = true;
            int count = Math.min(length, bytes.length);
            System.arraycopy(bytes, 0, buffer, offset, count);
            return count;
        }
    }
}
