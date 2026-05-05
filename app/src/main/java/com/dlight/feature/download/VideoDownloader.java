package com.dlight.feature.download;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VideoDownloader {
    public interface DownloadCallback {
        void onProgress(int progress);
        void onSuccess(File file);
        void onFailure(Exception e);
    }
    private static final int THREAD_POOL_SIZE = 10;
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
    public static void mulDownloadM3u8(String m3u8Url, File destination,String fileName, Context context, DownloadCallback callback) {
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
                //直接跳过广告ts
                if (line.endsWith(".ts") && !line.contains("adjump")) {
                    if (!line.startsWith("http")) {
                        tsUrls.add(baseUrl + line); // 拼接完整地址
                        Log.d("mulDownloadM3u8", "mulDownloadM3u8: "+baseUrl+line);
                    } else {
                        tsUrls.add(line); // 已经是完整地址
                    }
                }
            }
            reader.close();


            // 使用线程池下载 TS 文件
            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            CountDownLatch latch = new CountDownLatch(tsUrls.size());  // 用于等待所有下载线程完成

            AtomicBoolean hasFailed = new AtomicBoolean(false);
                int totalTsFiles = tsUrls.size();
                AtomicInteger completedFiles = new AtomicInteger(0);

            File videoTempDir = new File(destination, "temp");
            if (!videoTempDir.exists()) {
                videoTempDir.mkdirs();  // 如果目录不存在，创建它
            }

                for (int i = 0; i < totalTsFiles; i++) {
                    final int index = i;
                    final String tsUrl = tsUrls.get(i);
                    //提交到线程池
                    executorService.submit(() -> {

                        // 如果已经有一个下载失败，跳过该任务
                        if (hasFailed.get()) {
                            latch.countDown();  // 仍然减去计数，避免死锁
                            return;  // 如果失败了，就跳过后面的任务
                        }
                        File tempFile = new File(videoTempDir, index+fileName + ".ts");
                            int maxRetries = 3;

                            for (int retry = 0; retry < maxRetries; retry++) {

                                try  (BufferedInputStream in = new BufferedInputStream(new URL(tsUrl).openStream());
                                FileOutputStream out = new FileOutputStream(tempFile)) {

                                    byte[] buffer = new byte[4096];
                                    int count;
                                    while ((count = in.read(buffer)) != -1) {
                                        out.write(buffer, 0, count);
                                    }
                                    in.close();
                                    out.close();
                                    int completed = completedFiles.incrementAndGet();
                                    int progress = (int) ((completed * 100.0) / totalTsFiles);
                                    callback.onProgress(progress);
                                    latch.countDown();
                                    break;

                            } catch (IOException e) {
                                    if (retry == maxRetries-1){
                                        callback.onFailure(e);
                                        latch.countDown();
                                        hasFailed.compareAndSet(false, true);
                                    }
                                    try {
                                        Thread.sleep(500 * (retry + 1)); // 加退避时间
                                    } catch (InterruptedException ex) {
                                        ex.printStackTrace();
                                    }
                                    Log.e("mulDownloadM3u8", "mulDownloadM3u8:重试出错 " );
                           }
                            }
                    });
                }

            if (hasFailed.get()) {
                callback.onFailure(new FileNotFoundException("文件下载失败" + fileName));
                return;
            }

            // 等所有 ts 文件下载完再合并
            new Thread(() -> {
                try {
                    latch.await();
                    //最终生成的文件
                    File mergedFile = new File(destination, fileName);
                    try (FileOutputStream finalOut = new FileOutputStream(mergedFile)) {

                        for (int i = 0; i < totalTsFiles; i++) {
                            File tempFile = new File(videoTempDir, i+fileName + ".ts");
                            if (!tempFile.exists()) {
                                callback.onFailure(new FileNotFoundException("缺失 TS 文件：" + tempFile.getName()));
                                //缺失文件  for (int i = 0; i < totalTsFiles; i++)
                                //删除创建的临时文件 再返回
                                for (int j = 0; j < totalTsFiles; j++){
                                    File dtempFile = new File(videoTempDir, j+fileName + ".ts");
                                    dtempFile.delete();
                                }
                                mergedFile.delete();
                                return;
                            }

                            try (FileInputStream fis = new FileInputStream(tempFile)) {
                                byte[] buffer = new byte[4096];
                                int count;
                                while ((count = fis.read(buffer)) != -1) {
                                    finalOut.write(buffer, 0, count);
                                }
                            }
                            tempFile.delete();  // 删除临时文件
                        }
                    }

                    callback.onSuccess(mergedFile);
                } catch (Exception e) {
                    callback.onFailure(e);
                } finally {
                    executorService.shutdown();
                }
            }).start();
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }


}

