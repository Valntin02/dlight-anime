package com.dlight.feature.download;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.R;
import com.dlight.ui.player.SimplePlayer;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ActvityDownVideo extends AppCompatActivity {
    private final List<DownloadTask> tasks = new ArrayList<>();
    private AdapterDownVideo adapter;
    private TextView emptyView;
    private boolean receiverRegistered;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadTasks();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downvideo);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.empty_downloads);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdapterDownVideo(tasks, new AdapterDownVideo.Listener() {
            @Override
            public void onItemClick(DownloadTask task) {
                handleTaskClick(task);
            }

            @Override
            public void onDelete(DownloadTask task) {
                deleteTask(task);
            }
        });
        recyclerView.setAdapter(adapter);
        loadTasks();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(DownloadContract.ACTION_UPDATE);
        ContextCompat.registerReceiver(this, downloadReceiver, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
        loadTasks();
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(downloadReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void handleTaskClick(DownloadTask task) {
        if (DownloadContract.STATUS_COMPLETED.equals(task.getStatus())) {
            File file = new File(task.getFilePath());
            if (!file.exists()) {
                task.setStatus(DownloadContract.STATUS_FAILED);
                task.setErrorMessage("缓存文件不存在");
                DownloadTaskStore.upsert(this, task);
                loadTasks();
                return;
            }
            Intent intent = new Intent(this, SimplePlayer.class);
            intent.putExtra("video_path", file.getAbsolutePath());
            intent.putExtra("video_name", task.getTitle());
            startActivity(intent);
        } else if (DownloadContract.STATUS_FAILED.equals(task.getStatus())) {
            startTask(task);
        } else {
            Toast.makeText(this, "任务正在下载，请稍候", Toast.LENGTH_SHORT).show();
        }
    }

    private void startTask(DownloadTask task) {
        if (task.getUrl().isEmpty()) {
            Toast.makeText(this, "旧缓存没有可重试的下载地址", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ServiceDownload.class);
        intent.setAction(ServiceDownload.ACTION_START);
        intent.putExtra(DownloadContract.EXTRA_TASK_ID, task.getTaskId());
        intent.putExtra(DownloadContract.EXTRA_VIDEO_ID, task.getVideoId());
        intent.putExtra(DownloadContract.EXTRA_EPISODE, task.getEpisode());
        intent.putExtra(DownloadContract.EXTRA_URL, task.getUrl());
        intent.putExtra(DownloadContract.EXTRA_FILE_NAME, task.getTitle());
        intent.putExtra(DownloadContract.EXTRA_PIC_URL, task.getCoverUrl());
        ContextCompat.startForegroundService(this, intent);
    }

    private void deleteTask(DownloadTask task) {
        if (!task.getFilePath().isEmpty()) {
            File file = new File(task.getFilePath());
            if (file.exists()) {
                file.delete();
            }
        }
        DownloadTaskStore.remove(this, task.getTaskId());
        loadTasks();
    }

    private void loadTasks() {
        List<DownloadTask> storedTasks = DownloadTaskStore.getAll(this);
        Set<String> knownPaths = new HashSet<>();
        for (DownloadTask task : storedTasks) {
            if (!task.getFilePath().isEmpty()) {
                knownPaths.add(task.getFilePath());
            }
        }
        storedTasks.addAll(loadLegacyFiles(knownPaths));
        tasks.clear();
        tasks.addAll(storedTasks);
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private List<DownloadTask> loadLegacyFiles(Set<String> knownPaths) {
        List<DownloadTask> legacyTasks = new ArrayList<>();
        File videoDir = new File(getFilesDir(), "video");
        Map<String, String> coverMap = readCoverMap(videoDir);
        File[] files = videoDir.listFiles();
        if (files == null) {
            return legacyTasks;
        }
        for (File file : files) {
            if (file.isDirectory() || file.getName().endsWith(".json")
                || knownPaths.contains(file.getAbsolutePath())) {
                continue;
            }
            legacyTasks.add(new DownloadTask(
                "legacy:" + file.getName(), -1, 1, file.getName(), "",
                coverMap.getOrDefault(file.getName(), ""), 100,
                DownloadContract.STATUS_COMPLETED, file.getAbsolutePath(), "", file.lastModified()
            ));
        }
        return legacyTasks;
    }

    private Map<String, String> readCoverMap(File videoDir) {
        Map<String, String> coverMap = new HashMap<>();
        File jsonFile = new File(videoDir, "cover_map.json");
        if (!jsonFile.exists()) {
            return coverMap;
        }
        try {
            String json = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(json);
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String fileName = keys.next();
                coverMap.put(fileName, jsonObject.optString(fileName));
            }
        } catch (Exception ignored) {
        }
        return coverMap;
    }
}
