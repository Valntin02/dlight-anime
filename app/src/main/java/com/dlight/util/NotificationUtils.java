package com.dlight.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationUtils {
    public static Notification build(Context context, String title, int progress, String status) {
        String channelId = "download_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "视频下载", NotificationManager.IMPORTANCE_LOW);
            channel.enableVibration(false);
            channel.enableLights(false);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }

        String contentText;
        boolean indeterminate = false;
        if ("queued".equals(status)) {
            contentText = "等待下载";
            indeterminate = true;
        } else if ("completed".equals(status)) {
            contentText = "下载完成";
        } else if ("paused".equals(status)) {
            contentText = "已暂停 " + progress + "%";
        } else if ("failed".equals(status)) {
            contentText = "下载失败";
        } else {
            contentText = "下载中 " + progress + "%";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing("queued".equals(status) || "downloading".equals(status))
            .setPriority(NotificationCompat.PRIORITY_LOW);

        if ("queued".equals(status) || "downloading".equals(status)) {
            builder.setProgress(100, progress, indeterminate);
        }


        return builder.build();
    }
}
