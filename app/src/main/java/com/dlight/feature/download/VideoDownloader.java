package com.dlight.feature.download;

import android.content.Context;
import android.util.Log;

import com.dlight.BuildConfig;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class VideoDownloader {
    private static final String TAG = "VideoDownloader";
    public interface DownloadCallback {
        void onProgress(int progress);
        void onSuccess(File file);
        void onFailure(Exception e);
        default void onPaused() {
        }
    }

    public interface PauseSignal {
        boolean isPauseRequested();
    }
    private static final int THREAD_POOL_SIZE = 10;
    private static final int MAX_SEGMENT_REDIRECTS = 5;
    //普通文件下载
    public static void download(String urlStr, File originDest, Context context, DownloadCallback callback) {
        // 设置文件的保存路径
        File dest = originDest;  // 你可以根据需要修改文件名

        try (BufferedInputStream in = new BufferedInputStream(new URL(urlStr).openStream());
             FileOutputStream out = new FileOutputStream(dest)) {

            byte[] buffer = new byte[1024];
            int count;
            long total = 0;
            URLConnection conn = new URL(urlStr).openConnection();
            int fileLength = conn.getContentLength();

            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    callback.onProgress(progress);
                }
                out.write(buffer, 0, count);
            }
            callback.onSuccess(dest);
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    // 下载 m3u8 文件中的 ts 文件
    public static void downloadM3u8(String m3u8Url, File destination, Context context, DownloadCallback callback) {
        try {
            // 自动创建保存目录（避免 ENOENT）
            if (!destination.exists()) {
                destination.mkdirs();
            }

            // 解析 m3u8 文件
            URL url = new URL(m3u8Url);
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String line;
            List<String> tsUrls = new ArrayList<>();

            // 计算 ts 文件的完整地址
            String baseUrl = m3u8Url.substring(0, m3u8Url.lastIndexOf("/") + 1);

            while ((line = reader.readLine()) != null) {
                if (line.endsWith(".ts")) {
                    if (!line.startsWith("http")) {
                        tsUrls.add(baseUrl + line); // 拼接完整地址
                    } else {
                        tsUrls.add(line); // 已经是完整地址
                    }
                }
            }
            reader.close();

            // 创建输出视频文件
            File videoFile = new File(destination, "video.ts");

            try (FileOutputStream out = new FileOutputStream(videoFile, true)) {
                int totalTsFiles = tsUrls.size();

                for (int i = 0; i < totalTsFiles; i++) {
                    String tsUrl = tsUrls.get(i);
                    URL tsURL = new URL(tsUrl);
                    URLConnection conn = tsURL.openConnection();
                    BufferedInputStream in = new BufferedInputStream(tsURL.openStream());
                    byte[] buffer = new byte[4096];
                    int count;

                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }

                    in.close();

                    // 整体进度回调（基于文件数）
                    int progress = (int) ((i + 1) * 100.0 / totalTsFiles);
                    callback.onProgress(progress);
                }

                callback.onSuccess(videoFile);
            }
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    /**
     * 开发时遇到一个问题卡了很久 就是每次都是进度条到百分之50左右的时候 前面 000000.ts ~0000006.ts报错 一开始以为是我的我问题
     * 和cdn节点的问题但是发现 浏览器都是正常播放 后面才注意到https://p.bvvvvvvv7f.com/video/qishilujiudian/第01集//video/adjump/time/17413531275310000005.ts
     * 这里指的是 这个广告的ts文件缺少 因为我们这里组成的tS文件 没有做广告的 所有导致报错了 ，这个下载一共搞了3天才搞完
     * @param m3u8Url
     * @param destination
     * @param fileName
     * @param context
     * @param callback
     */
    public static void mulDownloadM3u8(String m3u8Url, File destination, String fileName,
                                       PauseSignal pauseSignal, DownloadCallback callback) {
        mulDownloadM3u8(m3u8Url, destination, fileName, BuildConfig.DEBUG,
                pauseSignal, callback);
    }

    static void mulDownloadM3u8(String m3u8Url, File destination, String fileName,
            boolean allowPrivate, PauseSignal pauseSignal, DownloadCallback callback) {
        ExecutorService executorService = null;
        TransferController transferController = null;
        CountDownLatch workerLatch = null;
        File videoTempDir = null;
        boolean terminalCallbackStarted = false;
        try {
            if (m3u8Url == null || m3u8Url.trim().isEmpty()) {
                throw new IOException("下载地址为空");
            }
            throwIfPaused(pauseSignal);
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("无法创建缓存目录: " + destination.getAbsolutePath());
            }

            List<String> tsUrls = HlsPlaylistResolver.resolveNetwork(m3u8Url, allowPrivate);
            if (tsUrls.isEmpty()) {
                throw new IOException("播放列表中没有可下载的视频分片");
            }

            String safeName = sanitizeFileName(fileName);
            videoTempDir = new File(destination,
                ".temp_" + Integer.toHexString((m3u8Url + safeName).hashCode()));
            if (!videoTempDir.exists() && !videoTempDir.mkdirs()) {
                throw new IOException("无法创建临时目录");
            }
            preparePlaylistState(videoTempDir, tsUrls);

            final File taskTempDir = videoTempDir;
            int totalTsFiles = tsUrls.size();
            List<Integer> missingSegments = prepareMissingSegments(
                    taskTempDir, totalTsFiles);
            int existingSegments = totalTsFiles - missingSegments.size();
            CountDownLatch latch = new CountDownLatch(missingSegments.size());
            workerLatch = latch;
            CountDownLatch startGate = new CountDownLatch(1);
            AtomicInteger completedFiles = new AtomicInteger(existingSegments);
            AtomicReference<Exception> failure = new AtomicReference<>();
            executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            transferController = new TransferController(executorService, latch);
            final TransferController taskController = transferController;

            for (int missingIndex : missingSegments) {
                final int index = missingIndex;
                final String tsUrl = tsUrls.get(index);
                executorService.execute(() -> {
                    try {
                        startGate.await();
                        if (failure.get() != null) {
                            return;
                        }
                        throwIfPaused(pauseSignal);
                        downloadSegment(tsUrl, new File(taskTempDir, index + ".ts"),
                                pauseSignal, allowPrivate, taskController);
                        int completed = completedFiles.incrementAndGet();
                        callback.onProgress((int) ((completed * 100.0) / totalTsFiles));
                    } catch (Exception e) {
                        if (failure.compareAndSet(null, e)) {
                            taskController.cancelAll();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            if (existingSegments > 0) {
                callback.onProgress((int) ((existingSegments * 100.0) / totalTsFiles));
            }
            startGate.countDown();

            latch.await();
            transferController.shutdownAndAwait();
            if (failure.get() != null) {
                throw failure.get();
            }
            throwIfPaused(pauseSignal);

            File mergedFile = new File(destination, safeName + ".ts");
            mergeSegments(videoTempDir, totalTsFiles, mergedFile);
            deleteDirectory(videoTempDir);
            terminalCallbackStarted = true;
            callback.onSuccess(mergedFile);
        } catch (DownloadPausedException e) {
            cancelAndAwait(transferController, workerLatch);
            if (!terminalCallbackStarted) {
                callback.onPaused();
            }
        } catch (InterruptedException e) {
            cancelAndAwait(transferController, workerLatch);
            Thread.currentThread().interrupt();
            if (!terminalCallbackStarted) {
                callback.onFailure(e);
            }
        } catch (Exception e) {
            cancelAndAwait(transferController, workerLatch);
            if (!terminalCallbackStarted) {
                callback.onFailure(e);
            }
        } finally {
            if (executorService != null && !executorService.isTerminated()) {
                executorService.shutdownNow();
            }
        }
    }

    private static void downloadSegment(String url, File destination, PauseSignal pauseSignal)
        throws IOException {
        downloadSegment(url, destination, pauseSignal, BuildConfig.DEBUG);
    }

    static void downloadSegment(String url, File destination, PauseSignal pauseSignal,
            boolean allowPrivate) throws IOException {
        downloadSegment(url, destination, pauseSignal, allowPrivate, null);
    }

    private static void downloadSegment(String url, File destination, PauseSignal pauseSignal,
            boolean allowPrivate, TransferController controller) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            File partFile = segmentPartFile(destination);
            try {
                deleteIfExists(partFile);
                throwIfPaused(pauseSignal);
                try (TrackedResponse tracked = openSegmentResponse(
                        url, allowPrivate, controller)) {
                    Response response = tracked.response;
                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new IOException("视频分片响应内容为空");
                    }
                    writeSegment(body.byteStream(), destination, pauseSignal);
                }
                return;
            } catch (DownloadPausedException e) {
                throw e;
            } catch (IOException e) {
                lastError = e;
                if (attempt < 3) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("下载被中断", interrupted);
                    }
                }
            } finally {
                deleteIfExists(partFile);
            }
        }
        throw lastError == null ? new IOException("视频分片下载失败") : lastError;
    }

    static void writeSegment(InputStream input, File destination, PauseSignal pauseSignal)
            throws IOException {
        File partFile = segmentPartFile(destination);
        deleteIfExists(partFile);
        boolean published = false;
        try {
            try (BufferedInputStream in = new BufferedInputStream(input);
                 FileOutputStream out = new FileOutputStream(partFile, false)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    throwIfPaused(pauseSignal);
                    out.write(buffer, 0, count);
                }
            }
            replaceWithPartFile(partFile, destination, "无法保存完整视频分片");
            published = true;
        } finally {
            if (!published) {
                deleteQuietly(partFile);
            }
        }
    }

    static File mergeSegments(File tempDirectory, int totalSegments, File destination)
            throws IOException {
        File partFile = new File(destination.getParentFile(), destination.getName() + ".part");
        deleteIfExists(partFile);
        deleteIfExists(destination);
        boolean published = false;
        try {
            try (FileOutputStream finalOut = new FileOutputStream(partFile, false)) {
                for (int i = 0; i < totalSegments; i++) {
                    File segment = new File(tempDirectory, i + ".ts");
                    if (!segment.exists() || segment.length() <= 0) {
                        throw new FileNotFoundException("缺失视频分片: " + i);
                    }
                    try (FileInputStream input = new FileInputStream(segment)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            finalOut.write(buffer, 0, count);
                        }
                    }
                }
            }
            replaceWithPartFile(partFile, destination, "无法保存完整视频文件");
            published = true;
            return destination;
        } finally {
            if (!published) {
                deleteQuietly(partFile);
                deleteQuietly(destination);
            }
        }
    }

    static List<Integer> findMissingSegments(File tempDirectory, int totalSegments) {
        List<Integer> missingSegments = new ArrayList<>();
        for (int i = 0; i < totalSegments; i++) {
            File segment = new File(tempDirectory, i + ".ts");
            if (!segment.exists() || segment.length() <= 0) {
                missingSegments.add(i);
            }
        }
        return missingSegments;
    }

    static List<Integer> prepareMissingSegments(File tempDirectory, int totalSegments)
            throws IOException {
        File[] files = tempDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".ts.part")) {
                    Integer index = parseSegmentIndex(
                            name.substring(0, name.length() - ".ts.part".length()));
                    if (index != null) {
                        deleteIfExists(file);
                    }
                } else if (name.endsWith(".ts")) {
                    Integer index = parseSegmentIndex(
                            name.substring(0, name.length() - ".ts".length()));
                    if (index != null && (index >= totalSegments || file.length() <= 0)) {
                        deleteIfExists(file);
                    }
                }
            }
        }
        List<Integer> missingSegments = findMissingSegments(tempDirectory, totalSegments);
        return missingSegments;
    }

    static void preparePlaylistState(File tempDirectory, List<String> segmentUrls)
            throws IOException {
        File fingerprintFile = new File(tempDirectory, ".playlist.sha256");
        deleteQuietly(new File(tempDirectory, ".playlist.sha256.part"));
        String expected = playlistFingerprint(segmentUrls);
        boolean matches = fingerprintFile.isFile()
                && expected.equals(readUtf8(fingerprintFile));
        if (!matches) {
            boolean cleared = deleteDirectory(tempDirectory);
            if (!cleared || tempDirectory.exists() || !tempDirectory.mkdirs()) {
                throw new IOException("无法重置临时下载目录");
            }
            writeUtf8Atomically(fingerprintFile, expected);
        }
    }

    private static String playlistFingerprint(List<String> segmentUrls) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("设备不支持 SHA-256", error);
        }
        for (String url : segmentUrls) {
            byte[] bytes = url.getBytes(StandardCharsets.UTF_8);
            digest.update((byte) (bytes.length >>> 24));
            digest.update((byte) (bytes.length >>> 16));
            digest.update((byte) (bytes.length >>> 8));
            digest.update((byte) bytes.length);
            digest.update(bytes);
        }
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest()) {
            value.append(String.format("%02x", item & 0xff));
        }
        return value.toString();
    }

    private static String readUtf8(File file) throws IOException {
        if (file.length() > 128) {
            return "";
        }
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count == -1) {
                    break;
                }
                offset += count;
            }
            if (offset != bytes.length) {
                return "";
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeUtf8Atomically(File destination, String value) throws IOException {
        File partFile = new File(destination.getPath() + ".part");
        deleteIfExists(partFile);
        boolean published = false;
        try {
            try (FileOutputStream output = new FileOutputStream(partFile, false)) {
                output.write(value.getBytes(StandardCharsets.UTF_8));
            }
            replaceWithPartFile(partFile, destination, "无法保存播放列表指纹");
            published = true;
        } finally {
            if (!published) {
                deleteQuietly(partFile);
            }
        }
    }

    private static Integer parseSegmentIndex(String value) {
        if (value.isEmpty()) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return null;
            }
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void replaceWithPartFile(File partFile, File destination, String errorMessage)
            throws IOException {
        deleteIfExists(destination);
        if (!partFile.renameTo(destination)) {
            deleteQuietly(partFile);
            throw new IOException(errorMessage);
        }
    }

    private static File segmentPartFile(File destination) {
        return new File(destination.getParentFile(), destination.getName() + ".part");
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("无法删除不完整文件: " + file.getAbsolutePath());
        }
    }

    private static TrackedResponse openSegmentResponse(String url, boolean allowPrivate,
            TransferController controller)
            throws IOException {
        URI currentUri = parseDownloadUri(url);
        Set<URI> visited = new HashSet<>();
        visited.add(currentUri);
        int redirectCount = 0;

        while (true) {
            Call call = DownloadHttpClient.newCall(currentUri, allowPrivate,
                    DownloadHttpClient.Purpose.SEGMENT);
            if (controller != null && !controller.register(call)) {
                call.cancel();
                throw new IOException("视频分片下载已取消");
            }
            final Response response;
            try {
                response = call.execute();
            } catch (IOException error) {
                if (controller != null) {
                    controller.unregister(call);
                }
                throw error;
            }
            TrackedResponse tracked = new TrackedResponse(response, call, controller);
            boolean keepResponse = false;
            try {
                int status = response.code();
                if (!isRedirect(status)) {
                    if (status != 200) {
                        throw new IOException("视频分片请求失败，HTTP 状态码: " + status);
                    }
                    keepResponse = true;
                    return tracked;
                }

                if (redirectCount >= MAX_SEGMENT_REDIRECTS) {
                    throw new IOException("视频分片重定向次数过多");
                }
                String location = response.header("Location");
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("视频分片重定向缺少 Location");
                }

                final URI nextUri;
                try {
                    nextUri = currentUri.resolve(location);
                } catch (IllegalArgumentException error) {
                    throw new IOException("视频分片重定向地址无效", error);
                }
                if (!visited.add(nextUri)) {
                    throw new IOException("视频分片重定向循环");
                }
                currentUri = nextUri;
                redirectCount++;
            } finally {
                if (!keepResponse) {
                    tracked.close();
                }
            }
        }
    }

    private static void cancelAndAwait(TransferController controller, CountDownLatch latch) {
        if (controller == null) {
            return;
        }
        controller.cancelAll();
        boolean interrupted = false;
        if (latch != null) {
            while (true) {
                try {
                    latch.await();
                    break;
                } catch (InterruptedException error) {
                    interrupted = true;
                    controller.cancelAll();
                }
            }
        }
        interrupted |= controller.awaitTerminationUninterruptibly();
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class TrackedResponse implements AutoCloseable {
        private final Response response;
        private final Call call;
        private final TransferController controller;

        private TrackedResponse(Response response, Call call, TransferController controller) {
            this.response = response;
            this.call = call;
            this.controller = controller;
        }

        @Override
        public void close() {
            try {
                response.close();
            } finally {
                if (controller != null) {
                    controller.unregister(call);
                }
            }
        }
    }

    private static final class TransferController {
        private final Set<Call> activeCalls = Collections.newSetFromMap(
                new ConcurrentHashMap<Call, Boolean>());
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final ExecutorService executor;
        private final CountDownLatch latch;

        private TransferController(ExecutorService executor, CountDownLatch latch) {
            this.executor = executor;
            this.latch = latch;
        }

        private boolean register(Call call) {
            if (cancelled.get()) {
                return false;
            }
            activeCalls.add(call);
            if (cancelled.get() && activeCalls.remove(call)) {
                call.cancel();
                return false;
            }
            return true;
        }

        private void unregister(Call call) {
            activeCalls.remove(call);
        }

        private void cancelAll() {
            cancelled.set(true);
            for (Call call : activeCalls) {
                call.cancel();
            }
            List<Runnable> neverStarted = executor.shutdownNow();
            for (int i = 0; i < neverStarted.size(); i++) {
                latch.countDown();
            }
        }

        private void shutdownAndAwait() throws InterruptedException {
            executor.shutdown();
            while (!executor.awaitTermination(1, TimeUnit.DAYS)) {
                // Keep waiting until no worker can publish progress or files.
            }
        }

        private boolean awaitTerminationUninterruptibly() {
            boolean interrupted = false;
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1, TimeUnit.DAYS);
                } catch (InterruptedException error) {
                    interrupted = true;
                    cancelAll();
                }
            }
            return interrupted;
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301
                || status == 302
                || status == 303
                || status == 307
                || status == 308;
    }

    private static URI parseDownloadUri(String url) throws IOException {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IOException("下载地址无效", error);
        }
    }

    private static String sanitizeFileName(String fileName) {
        String value = fileName == null ? "video" : fileName.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|]", "_");
        return value.isEmpty() ? "video" : value;
    }

    public static void deletePartialDownload(File destination, String m3u8Url, String fileName) {
        String safeName = sanitizeFileName(fileName);
        File tempDirectory = new File(destination,
            ".temp_" + Integer.toHexString((m3u8Url + safeName).hashCode()));
        deleteDirectory(tempDirectory);
    }

    private static void throwIfPaused(PauseSignal pauseSignal) throws DownloadPausedException {
        if (pauseSignal != null && pauseSignal.isPauseRequested()) {
            throw new DownloadPausedException();
        }
    }

    private static class DownloadPausedException extends IOException {
        DownloadPausedException() {
            super("下载已暂停");
        }
    }

    private static boolean deleteDirectory(File directory) {
        if (!directory.exists()) {
            return true;
        }
        boolean deleted = true;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleted &= deleteDirectory(file);
                } else {
                    deleted &= deleteQuietly(file);
                }
            }
        }
        return deleteQuietly(directory) && deleted;
    }

    private static boolean deleteQuietly(File file) {
        if (!file.exists() || file.delete()) {
            return true;
        }
        Log.w(TAG, "无法删除下载缓存: " + file.getAbsolutePath());
        return false;
    }


}
