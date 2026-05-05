package com.dlight.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;

//点赞缓存
public class LikeCacheManager {

    private static final String PREF_NAME = "like_cache";
    private static LikeCacheManager instance;
    private final SharedPreferences sharedPreferences;
    private final Map<String, Runnable> pendingTasks;  // 防抖任务
    private final Handler handler;

    private LikeCacheManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        pendingTasks = new HashMap<>();
        handler = new Handler(Looper.getMainLooper());
    }

    public static  LikeCacheManager getInstance(Context context) {
        if (instance == null) {
            synchronized (LikeCacheManager.class) {
                if (instance == null) {
                    instance = new LikeCacheManager(context);
                }
            }
        }
        return instance;
    }

    // 缓存点赞状态与数量
    public void cacheLikeStatus(int commentId, boolean liked, int likeCount) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String key = "comment_" + commentId;
        long timestamp = System.currentTimeMillis() / 1000;  // 当前时间戳
        editor.putBoolean(key + "_liked", liked);
        editor.putInt(key + "_like_count", likeCount);
        editor.putLong(key + "_last_sync", timestamp);
        editor.apply();
    }

    // 获取缓存的点赞状态
    public boolean isLiked(int commentId) {
        return sharedPreferences.getBoolean("comment_" + commentId + "_liked", false);
    }

    public int getLikeCount(int commentId) {
        return sharedPreferences.getInt("comment_" + commentId + "_like_count", 0);
    }

    // 异步防抖网络请求
    public void delayNetworkSync(int commentId, Runnable task, long delayMs) {
        String taskKey = "task_" + commentId;

        // 防抖处理：清除之前的任务，重新计时
        if (pendingTasks.containsKey(taskKey)) {
            handler.removeCallbacks(pendingTasks.get(taskKey));
        }

        pendingTasks.put(taskKey, task);
        handler.postDelayed(task, delayMs);
    }
}
