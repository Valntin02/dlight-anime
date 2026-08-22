package com.dlight.feature.download;

import android.content.Context;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Response;
import okhttp3.ResponseBody;

public class VideoDownloader {
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
        ExecutorService executorService = null;
        File videoTempDir = null;
        try {
            if (m3u8Url == null || m3u8Url.trim().isEmpty()) {
                throw new IOException("下载地址为空");
            }
            throwIfPaused(pauseSignal);
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("无法创建缓存目录: " + destination.getAbsolutePath());
            }

            List<String> tsUrls = HlsPlaylistResolver.resolve(m3u8Url);
            if (tsUrls.isEmpty()) {
                throw new IOException("播放列表中没有可下载的视频分片");
            }

            String safeName = sanitizeFileName(fileName);
            videoTempDir = new File(destination,
                ".temp_" + Integer.toHexString((m3u8Url + safeName).hashCode()));
            if (!videoTempDir.exists() && !videoTempDir.mkdirs()) {
                throw new IOException("无法创建临时目录");
            }

            final File taskTempDir = videoTempDir;
            int totalTsFiles = tsUrls.size();
            List<Integer> missingSegments = findMissingSegments(taskTempDir, totalTsFiles);
            int existingSegments = totalTsFiles - missingSegments.size();
            CountDownLatch latch = new CountDownLatch(missingSegments.size());
            AtomicInteger completedFiles = new AtomicInteger(existingSegments);
            AtomicReference<Exception> failure = new AtomicReference<>();
            executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

            if (existingSegments > 0) {
                callback.onProgress((int) ((existingSegments * 100.0) / totalTsFiles));
            }

            for (int missingIndex : missingSegments) {
                final int index = missingIndex;
                final String tsUrl = tsUrls.get(index);
                executorService.submit(() -> {
                    try {
                        if (failure.get() != null) {
                            return;
                        }
                        throwIfPaused(pauseSignal);
                        downloadSegment(tsUrl, new File(taskTempDir, index + ".ts"), pauseSignal);
                        int completed = completedFiles.incrementAndGet();
                        callback.onProgress((int) ((completed * 100.0) / totalTsFiles));
                    } catch (Exception e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            if (failure.get() != null) {
                throw failure.get();
            }
            throwIfPaused(pauseSignal);

            File mergedFile = new File(destination, safeName + ".ts");
            mergeSegments(videoTempDir, totalTsFiles, mergedFile);
            deleteDirectory(videoTempDir);
            callback.onSuccess(mergedFile);
        } catch (DownloadPausedException e) {
            callback.onPaused();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.onFailure(e);
        } catch (Exception e) {
            callback.onFailure(e);
        } finally {
            if (executorService != null) {
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
        IOException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                throwIfPaused(pauseSignal);
                try (Response response = openSegmentResponse(url, allowPrivate)) {
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
            }
        }
        throw lastError == null ? new IOException("视频分片下载失败") : lastError;
    }

    static void writeSegment(InputStream input, File destination, PauseSignal pauseSignal)
            throws IOException {
        File partFile = new File(destination.getParentFile(), destination.getName() + ".part");
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
                partFile.delete();
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
                partFile.delete();
                destination.delete();
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

    private static void replaceWithPartFile(File partFile, File destination, String errorMessage)
            throws IOException {
        deleteIfExists(destination);
        if (!partFile.renameTo(destination)) {
            partFile.delete();
            throw new IOException(errorMessage);
        }
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("无法删除不完整文件: " + file.getAbsolutePath());
        }
    }

    private static Response openSegmentResponse(String url, boolean allowPrivate)
            throws IOException {
        URI currentUri = parseDownloadUri(url);
        Set<URI> visited = new HashSet<>();
        visited.add(currentUri);
        int redirectCount = 0;

        while (true) {
            Response response = DownloadHttpClient.execute(currentUri, allowPrivate,
                    DownloadHttpClient.Purpose.SEGMENT);
            boolean keepResponse = false;
            try {
                int status = response.code();
                if (!isRedirect(status)) {
                    if (status != 200) {
                        throw new IOException("视频分片请求失败，HTTP 状态码: " + status);
                    }
                    keepResponse = true;
                    return response;
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
                    response.close();
                }
            }
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

    private static void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }


}
