package com.dlight.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationUtils {
    public static Notification build(Context context, int progress,String title) {
        String channelId = "download_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "视频下载", NotificationManager.IMPORTANCE_DEFAULT);
            channel.enableVibration(false);
            channel.enableLights(false);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(progress + "%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 兼容 Android 7 以下
            .setProgress(100, progress, false);


        return builder.build();
    }
}

