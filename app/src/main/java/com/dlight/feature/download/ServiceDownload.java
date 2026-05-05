package com.dlight.feature.download;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;


import androidx.media3.exoplayer.offline.DownloadService;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import com.dlight.util.NotificationUtils;

public class ServiceDownload extends Service {
    public static final String ACTION_START = "ACTION_START_DOWNLOAD";
    public static final String EXTRA_URL = "EXTRA_URL";
    public static final String EXTRA_FILE_NAME = "EXTRA_FILE_NAME";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            String fileName = intent.getStringExtra(EXTRA_FILE_NAME);
            String picUrl=intent.getStringExtra("picUrl");

            Log.d("ServiceDownload", "onStartCommand: url="+url);

            Toast.makeText(this, "开始下载: " + fileName, Toast.LENGTH_SHORT).show();
            // 获取应用的私有文件目录，创建一个名为 "video" 的子目录
            File videoDir = new File(this.getFilesDir(), "video");
            if (!videoDir.exists()) {
                videoDir.mkdirs();  // 如果目录不存在，创建它
            }
            Log.d("ServiceDownload", "准备启动前台通知");
            // 启动前台服务
            startForeground(1, NotificationUtils.build(this, 1,fileName));
            Log.d("ServiceDownload", "通知启动完毕");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(1, NotificationUtils.build(ServiceDownload.this, 1,fileName));

            new Thread(() -> {
                try {
                    Log.d("ServiceDownload", "线程开始执行下载逻辑");
                    VideoDownloader.mulDownloadM3u8(url, videoDir,fileName,this ,new VideoDownloader.DownloadCallback() {
                        @Override
                        public void onProgress(int progress) {
                            //NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                            nm.notify(1, NotificationUtils.build(ServiceDownload.this, progress,fileName));
                        }

                        @Override
                        public void onSuccess(File file) {
                            //Toast.makeText(getApplicationContext(), "下载完成：" + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                            Log.d("ServiceDownload", "onStartCommand"+"下载完成"+file.getAbsolutePath());
                            saveVideoCoverMapping(fileName,picUrl);
                            stopForeground(true);
                            stopSelf();
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Log.e("ServiceDownload", "下载失败", e);
                            //stopForeground(true)只影响服务的通知，不会阻止后台任务的执行
                            stopForeground(true);
                            //NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                            nm.cancel(1);
                            //stopSelf() 会停止服务的生命周期，但 它不会中止线程 或 阻止线程内的执行。也就是说，服务可能已经停止，但线程仍在后台执行其任务。
                            stopSelf();


                        }


                    });
                } catch (Exception e) {
                    Log.e("ServiceDownload", "下载线程异常", e);
                    stopForeground(true);
                    stopSelf();
                }
            }).start();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void saveVideoCoverMapping(String fileName,String picUrl){
        try {
            File videoDir = new File(getFilesDir(), "video");
            File jsonFile = new File(videoDir, "cover_map.json");

            JSONObject jsonObject = new JSONObject();

            // 如果文件存在，先读取原来的内容
            if (jsonFile.exists()) {
                String json = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
                jsonObject = new JSONObject(json);
            }

            // 添加或更新映射
            jsonObject.put(fileName, picUrl);

            // 写回文件
            //new FileWriter(jsonFile) 会自动创建文件（如果该文件不存在）
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(jsonObject.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
