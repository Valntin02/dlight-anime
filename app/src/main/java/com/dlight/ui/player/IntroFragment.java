package com.dlight.ui.player;



import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.dlight.R;
import com.dlight.data.model.VodData;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

import android.widget.LinearLayout;
import android.widget.Toast;
import com.dlight.data.local.AppDatabase;
import com.dlight.data.local.MyStarRecord;
import com.dlight.data.local.MyStarRecordDao;
import com.dlight.data.local.PlayRecord;
import com.dlight.data.local.PlayRecordDao;
import com.dlight.feature.download.ServiceDownload;
import com.dlight.feature.download.DownloadContract;
import com.dlight.feature.download.DownloadTask;
import com.dlight.feature.download.DownloadTaskStore;

import java.io.File;

public class IntroFragment extends Fragment {

    private VodData videoData;
    private ArrayList<String> videourls;

    private TextView introView,vodNameView,yearView,cvView,statusView;
    private ImageView imageView;

    private Button btnStar,btnDownLoader;
    private boolean downloadReceiverRegistered;
    private int currentPlayingIndex = 0; // 当前正在播放的索引

    private int currentEpisode;
    private List<Button> episodeButtons = new ArrayList<>(); // 保存按钮列表
    private LinearLayout  episodeContainer;
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String updatedTaskId = intent.getStringExtra(DownloadContract.EXTRA_TASK_ID);
            if (getCurrentTaskId().equals(updatedTaskId)) {
                refreshDownloadState();
            }
        }
    };
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_intro, container, false);

        DanmkuVideoActivity activity=(DanmkuVideoActivity) getActivity();
        if(activity!=null){
            videoData = activity.getVideoData();
            videourls = activity.getVideoUrls();
            episodeContainer = view.findViewById(R.id.episodeContainer); // 初始化 episodeContainer
            currentEpisode = activity.getCurrentEpisode();
        }else {
            Log.e("IntroFragment","get activity error");
        }
        introView =view.findViewById(R.id.textIntro);
        vodNameView=view.findViewById(R.id.vod_name);
        yearView=view.findViewById(R.id.years);
        cvView=view.findViewById(R.id.cvActors);
        statusView=view.findViewById(R.id.status);
        imageView=view.findViewById(R.id.videoImage);
        btnStar=view.findViewById(R.id.btn_star);
        btnDownLoader=view.findViewById(R.id.btn_downloader);
        currentPlayingIndex = Math.max(0, currentEpisode - 1);
        if(videoData!=null){
            String str;
            vodNameView.setText(videoData.getVod_name());
            yearView.setText("年份:"+videoData.getVod_year());
            cvView.setText("CV:"+videoData.getVod_actor());
            statusView.setText("状态:"+videoData.getVod_remarks());
            String content = videoData.getVod_content();
            if (content == null || content.trim().isEmpty()) {
                str = "简介: 什么都都没有留下。。。";
            } else {
                str = "简介: " + content;
                //Log.d("IntroFragment", "videoData.getVod_content(): " + content);
            }
            introView.setText(str);
            // 使用 Picasso 来加载图片
            Picasso.get().load(videoData.getVod_pic()).into(imageView);
        }else{
            Log.e("IntroFragment","get videoData error");
        }
        addEpisodeButton();
        updateEpisodeButtonStyle();
        bingBtnStar();
        bingBtnDownLoader();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(DownloadContract.ACTION_UPDATE);
        ContextCompat.registerReceiver(requireContext(), downloadReceiver, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED);
        downloadReceiverRegistered = true;
        refreshDownloadState();
    }

    @Override
    public void onStop() {
        if (downloadReceiverRegistered) {
            requireContext().unregisterReceiver(downloadReceiver);
            downloadReceiverRegistered = false;
        }
        super.onStop();
    }

    private void bingBtnDownLoader(){
        btnDownLoader.setOnClickListener(v->{
            if (videoData == null || videourls == null || videourls.isEmpty()
                || currentPlayingIndex < 0 || currentPlayingIndex >= videourls.size()) {
                Toast.makeText(getContext(), "当前视频暂无可下载地址", Toast.LENGTH_SHORT).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }

            int episode = currentPlayingIndex + 1;
            String taskId = DownloadContract.taskId(videoData.getVod_id(), episode);
            DownloadTask existing = DownloadTaskStore.get(requireContext(), taskId);
            if (existing != null && existing.isActive()) {
                Toast.makeText(getContext(), "该集正在下载", Toast.LENGTH_SHORT).show();
                return;
            }
            if (existing != null && existing.isCompleted()
                && !existing.getFilePath().isEmpty() && new File(existing.getFilePath()).exists()) {
                Toast.makeText(getContext(), "该集已经缓存", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(requireContext(), ServiceDownload.class);
            intent.setAction(ServiceDownload.ACTION_START);
            intent.putExtra(DownloadContract.EXTRA_TASK_ID, taskId);
            intent.putExtra(DownloadContract.EXTRA_VIDEO_ID, videoData.getVod_id());
            intent.putExtra(DownloadContract.EXTRA_EPISODE, episode);
            intent.putExtra(DownloadContract.EXTRA_URL, videourls.get(currentPlayingIndex));
            intent.putExtra(DownloadContract.EXTRA_FILE_NAME,
                videoData.getVod_name() + "—第" + episode + "集");
            intent.putExtra(DownloadContract.EXTRA_PIC_URL, videoData.getVod_pic());
            ContextCompat.startForegroundService(requireContext(), intent);
            btnDownLoader.setEnabled(false);
            btnDownLoader.setText("等待中");
            Log.d("bingBtnDownLoader", "开始下载任务: " + taskId);

        });

    }

    private String getCurrentTaskId() {
        if (videoData == null) {
            return "";
        }
        return DownloadContract.taskId(videoData.getVod_id(), currentPlayingIndex + 1);
    }

    private void refreshDownloadState() {
        if (videoData == null || btnDownLoader == null) {
            return;
        }
        DownloadTask task = DownloadTaskStore.get(requireContext(), getCurrentTaskId());
        if (task == null) {
            btnDownLoader.setEnabled(true);
            btnDownLoader.setText("下载");
            return;
        }

        if (DownloadContract.STATUS_QUEUED.equals(task.getStatus())) {
            btnDownLoader.setText("等待中");
            btnDownLoader.setEnabled(false);
        } else if (DownloadContract.STATUS_DOWNLOADING.equals(task.getStatus())) {
            btnDownLoader.setText(task.getProgress() + "%");
            btnDownLoader.setEnabled(false);
        } else if (DownloadContract.STATUS_COMPLETED.equals(task.getStatus())) {
            btnDownLoader.setText("已缓存");
            btnDownLoader.setEnabled(false);
        } else {
            btnDownLoader.setText("重试");
            btnDownLoader.setEnabled(true);
        }
    }
    private  void bingBtnStar(){
        btnStar.setOnClickListener(v-> {

            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
            int userId=sharedPreferences.getInt("Id",-1);
            //登录了才提高播放记录
            if(userId<0) {
                Toast.makeText(getContext(), "未登录用户！", Toast.LENGTH_SHORT).show();
                return;
            }
            // 异步插入播放记录
            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstanceMyStarRecord(getContext());
                MyStarRecordDao myStarRecordDao=db.myStarRecordDao();
                //这里一开始没有做判断 联合唯一索引报错 会导致整个应用崩溃
                MyStarRecord existing =myStarRecordDao.getStarsByUserAndVideo(userId,videoData.getVod_id());
                if(existing== null){
                    myStarRecordDao.insert(new MyStarRecord(userId,videoData));
                }
                //upset会内部会自己判断 是inset 还是update
                //playRecordDao.upsert(playRecord);
            }).start();

        });


    }
    private void addEpisodeButton(){
        DanmkuVideoActivity activity=(DanmkuVideoActivity) getActivity();
        if (videourls == null || videourls.isEmpty()) return;
        for (int i = 0; i < videourls.size(); i++) {
            final int index = i;
            Button button = new Button(getContext());
            button.setText("第 " + (i + 1) + " 集");
            button.setTextSize(12);
            button.setTypeface(Typeface.DEFAULT_BOLD);
            button.setTextColor(Color.WHITE);
            button.setBackgroundResource(R.drawable.button_style); // 设置按钮背景（可以自定义）
            button.setPadding(10, 5, 10, 5);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                200,
              120);
            params.setMargins(10, 0, 10, 0);
            button.setLayoutParams(params);

            final String nextVideoUrl = videourls.get(i);
            final String titleName=videoData.getVod_name()+"—第"+(i+1)+"集";;


            button.setOnClickListener(v -> {
                // 更新播放的 URL
                // 更新播放的 URL
                currentPlayingIndex = index;
                activity.getBinding().danmakuPlayer.setUp(nextVideoUrl, true, null, titleName);
                updateEpisodeButtonStyle();
                refreshDownloadState();

                SharedPreferences sharedPreferences = getActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                int userId=sharedPreferences.getInt("Id",-1);
                //登录了才提高播放记录
                if(userId<0) return;

                // 异步插入播放记录
                new Thread(() -> {
                    AppDatabase db = AppDatabase.getInstancePlayRecord(getContext());
                    PlayRecordDao playRecordDao = db.playRecordDao();
                    PlayRecord existing = playRecordDao.getPlayRecordByUserAndVideo(userId, videoData.getVod_id());
                    if (existing == null) {
                        PlayRecord playRecord = new PlayRecord(userId,currentPlayingIndex+1,videoData);
                        playRecordDao.insert(playRecord);
                    } else {
                        playRecordDao.updateEpisode(userId,videoData.getVod_id(),currentPlayingIndex+1);
                    }
                    //upset会内部会自己判断 是inset 还是update
                    //playRecordDao.upsert(playRecord);
                }).start();

            });
            //按钮列表
            episodeButtons.add(button);

            episodeContainer.addView(button);
        }
    }

    private void updateEpisodeButtonStyle() {
        for (int i = 0; i < episodeButtons.size(); i++) {
            Button button = episodeButtons.get(i);
            if (i == currentPlayingIndex) {
                // 当前集样式
                button.setBackgroundResource(R.drawable.button_style2);

            } else {
                // 默认样式
                button.setBackgroundResource(R.drawable.button_style);
                button.setTextColor(Color.WHITE);
            }
        }
    }

    public void setCurrentPlayingIndex(int index){
        this.currentPlayingIndex=index;
        updateEpisodeButtonStyle();
        refreshDownloadState();
    }
}
