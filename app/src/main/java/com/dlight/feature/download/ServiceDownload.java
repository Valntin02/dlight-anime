package com.dlight.feature.download;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.dlight.util.NotificationUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ServiceDownload extends Service {
    public static final String ACTION_START = "ACTION_START_DOWNLOAD";
    public static final String EXTRA_URL = DownloadContract.EXTRA_URL;
    public static final String EXTRA_FILE_NAME = DownloadContract.EXTRA_FILE_NAME;

    private static final String TAG = "ServiceDownload";
    private static final int NOTIFICATION_ID = 1001;

    private final ExecutorService downloadQueue = Executors.newSingleThreadExecutor();
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    private final AtomicInteger latestStartId = new AtomicInteger(0);
    private final Set<String> activeTaskIds = ConcurrentHashMap.newKeySet();
    private final Set<String> pauseRequests = ConcurrentHashMap.newKeySet();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        latestStartId.set(startId);

        if (DownloadContract.ACTION_PAUSE.equals(intent.getAction())) {
            pauseTask(intent.getStringExtra(DownloadContract.EXTRA_TASK_ID));
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) {
            return START_NOT_STICKY;
        }

        DownloadTask task = taskFromIntent(intent);
        if (task == null) {
            Log.e(TAG, "下载参数不完整");
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        if (!activeTaskIds.add(task.getTaskId())) {
            Log.d(TAG, "任务已在队列中: " + task.getTaskId());
            return START_NOT_STICKY;
        }
        pauseRequests.remove(task.getTaskId());

        DownloadTask existing = DownloadTaskStore.get(this, task.getTaskId());
        if (existing != null && existing.isCompleted()
            && !existing.getFilePath().isEmpty() && new File(existing.getFilePath()).exists()) {
            activeTaskIds.remove(task.getTaskId());
            broadcastUpdate(existing);
            return START_NOT_STICKY;
        }

        DownloadTaskStore.upsert(this, task);
        broadcastUpdate(task);
        pendingTasks.incrementAndGet();
        startForeground(NOTIFICATION_ID,
            NotificationUtils.build(this, task.getTitle(), 0, DownloadContract.STATUS_QUEUED));

        downloadQueue.execute(() -> runDownload(task));
        return START_REDELIVER_INTENT;
    }

    private DownloadTask taskFromIntent(Intent intent) {
        String taskId = intent.getStringExtra(DownloadContract.EXTRA_TASK_ID);
        String url = intent.getStringExtra(DownloadContract.EXTRA_URL);
        String fileName = intent.getStringExtra(DownloadContract.EXTRA_FILE_NAME);
        int videoId = intent.getIntExtra(DownloadContract.EXTRA_VIDEO_ID, -1);
        int episode = intent.getIntExtra(DownloadContract.EXTRA_EPISODE, 1);
        String picUrl = intent.getStringExtra(DownloadContract.EXTRA_PIC_URL);
        if (taskId == null || taskId.isEmpty() || url == null || url.trim().isEmpty()
            || fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        return DownloadTask.queued(taskId, videoId, episode, fileName, url, picUrl);
    }

    private void runDownload(DownloadTask task) {
        if (pauseRequests.contains(task.getTaskId())) {
            markPaused(task);
            finishTask(task.getTaskId());
            return;
        }
        task.setStatus(DownloadContract.STATUS_DOWNLOADING);
        task.setProgress(0);
        task.setErrorMessage("");
        publish(task);

        File videoDir = new File(getFilesDir(), "video");
        VideoDownloader.mulDownloadM3u8(task.getUrl(), videoDir, task.getTitle(),
            () -> pauseRequests.contains(task.getTaskId()),
            new VideoDownloader.DownloadCallback() {
                @Override
                public void onProgress(int progress) {
                    synchronized (task) {
                        if (pauseRequests.contains(task.getTaskId())) {
                            return;
                        }
                        if (progress <= task.getProgress()) {
                            return;
                        }
                        task.setProgress(progress);
                        publish(task);
                    }
                }

                @Override
                public void onSuccess(File file) {
                    task.setProgress(100);
                    task.setFilePath(file.getAbsolutePath());
                    task.setStatus(DownloadContract.STATUS_COMPLETED);
                    task.setErrorMessage("");
                    saveVideoCoverMapping(task.getTitle(), task.getCoverUrl());
                    publish(task);
                }

                @Override
                public void onFailure(Exception error) {
                    task.setStatus(DownloadContract.STATUS_FAILED);
                    task.setErrorMessage(error.getMessage() == null ? "下载失败" : error.getMessage());
                    publish(task);
                    Log.e(TAG, "下载失败: " + task.getTaskId(), error);
                }

                @Override
                public void onPaused() {
                    markPaused(task);
                }
            });

        finishTask(task.getTaskId());
    }

    private void pauseTask(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return;
        }
        pauseRequests.add(taskId);
        if (pendingTasks.get() == 0) {
            DownloadTask task = DownloadTaskStore.get(this, taskId);
            if (task != null && task.isActive()) {
                task.setStatus(DownloadContract.STATUS_PAUSED);
                task.setErrorMessage("");
                DownloadTaskStore.upsert(this, task);
                broadcastUpdate(task);
            }
            stopSelfResult(latestStartId.get());
        }
    }

    private void markPaused(DownloadTask task) {
        activeTaskIds.remove(task.getTaskId());
        task.setStatus(DownloadContract.STATUS_PAUSED);
        task.setErrorMessage("");
        publish(task);
    }

    private void finishTask(String taskId) {
        activeTaskIds.remove(taskId);
        if (pendingTasks.decrementAndGet() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelfResult(latestStartId.get());
        }
    }

    private void publish(DownloadTask task) {
        DownloadTaskStore.upsert(this, task);
        broadcastUpdate(task);
        startForeground(NOTIFICATION_ID,
            NotificationUtils.build(this, task.getTitle(), task.getProgress(), task.getStatus()));
    }

    private void broadcastUpdate(DownloadTask task) {
        Intent update = new Intent(DownloadContract.ACTION_UPDATE);
        update.setPackage(getPackageName());
        update.putExtra(DownloadContract.EXTRA_TASK_ID, task.getTaskId());
        sendBroadcast(update);
    }

    @Override
    public void onDestroy() {
        downloadQueue.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void saveVideoCoverMapping(String fileName, String picUrl) {
        try {
            File videoDir = new File(getFilesDir(), "video");
            File jsonFile = new File(videoDir, "cover_map.json");
            JSONObject jsonObject = new JSONObject();
            if (jsonFile.exists()) {
                String json = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
                jsonObject = new JSONObject(json);
            }
            jsonObject.put(fileName, picUrl == null ? "" : picUrl);
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(jsonObject.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "保存封面映射失败", e);
        }
    }
}
