package com.dlight.feature.download;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.CachedVideo;
import com.dlight.R;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.dlight.ui.player.SimplePlayer;

public class ActvityDownVideo extends AppCompatActivity {
    private RecyclerView recyclerView;
    private AdapterDownVideo videoAdapter;
    private List<CachedVideo> videoFiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downvideo);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        videoAdapter = new AdapterDownVideo(videoFiles, file -> {
            // 点击文件后的操作，比如播放或者弹个 Toast
            //Toast.makeText(this, "点击了：" + file.getName(), Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(this, SimplePlayer.class);
            intent.putExtra("video_path", file.getAbsolutePath());
            intent.putExtra("video_name", file.getName());  // 可选
            startActivity(intent);
            // 你可以跳转到播放界面传 file.getAbsolutePath()
        });
        recyclerView.setAdapter(videoAdapter);

        loadVideos();
    }

    private void loadVideos() {
        File videoDir = new File(getFilesDir(), "video");
        File jsonFile = new File(videoDir, "cover_map.json");
        Map<String, String> coverMap = new HashMap<>();
        if (jsonFile.exists()) {
            try {
                String json = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
                JSONObject jsonObject = new JSONObject(json);
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String fileName = keys.next();
                    String coverUrl = jsonObject.getString(fileName);
                    coverMap.put(fileName, coverUrl);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (videoDir.exists()) {
            File[] files = videoDir.listFiles();
            if (files != null) {
                videoFiles.clear();
                for (File file : files) {
                    if (file.isDirectory() || file.getName().endsWith(".json")) {
                        continue;
                    }
                        String cover = coverMap.getOrDefault(file.getName(), ""); // 没有就空
                        videoFiles.add(new CachedVideo(file, cover));

                }
                videoAdapter.notifyDataSetChanged();
            }
        }
    }
}
