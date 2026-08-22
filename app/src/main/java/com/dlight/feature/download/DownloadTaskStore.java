package com.dlight.feature.download;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DownloadTaskStore {
    private static final String TAG = "DownloadTaskStore";
    private static final String PREFS_NAME = "download_tasks";
    private static final String KEY_TASKS = "tasks";

    private DownloadTaskStore() {
    }

    public static synchronized List<DownloadTask> getAll(Context context) {
        List<DownloadTask> tasks = read(context.getApplicationContext());
        Collections.sort(tasks, (left, right) -> Long.compare(right.getUpdatedAt(), left.getUpdatedAt()));
        return tasks;
    }

    public static synchronized DownloadTask get(Context context, String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return null;
        }
        for (DownloadTask task : read(context.getApplicationContext())) {
            if (taskId.equals(task.getTaskId())) {
                return task;
            }
        }
        return null;
    }

    public static synchronized void upsert(Context context, DownloadTask task) {
        Context appContext = context.getApplicationContext();
        List<DownloadTask> tasks = read(appContext);
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getTaskId().equals(task.getTaskId())) {
                tasks.set(i, task);
                write(appContext, tasks);
                return;
            }
        }
        tasks.add(task);
        write(appContext, tasks);
    }

    public static synchronized void remove(Context context, String taskId) {
        Context appContext = context.getApplicationContext();
        List<DownloadTask> tasks = read(appContext);
        tasks.removeIf(task -> task.getTaskId().equals(taskId));
        write(appContext, tasks);
    }

    private static List<DownloadTask> read(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_TASKS, "[]");
        List<DownloadTask> tasks = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) {
                    continue;
                }
                DownloadTask task = DownloadTask.fromJson(json);
                if (!task.getTaskId().isEmpty()) {
                    tasks.add(task);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "读取下载任务失败", e);
        }
        return tasks;
    }

    private static void write(Context context, List<DownloadTask> tasks) {
        JSONArray array = new JSONArray();
        try {
            for (DownloadTask task : tasks) {
                array.put(task.toJson());
            }
        } catch (Exception e) {
            Log.e(TAG, "序列化下载任务失败", e);
            return;
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASKS, array.toString())
            .apply();
    }
}
