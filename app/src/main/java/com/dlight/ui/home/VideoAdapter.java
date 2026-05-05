package com.dlight.ui.home;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.ui.player.DanmkuVideoActivity;
import com.dlight.data.model.VodData;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import com.dlight.R;


public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private List<VodData> videoList;

    public VideoAdapter(List<VodData> videoList) {
        this.videoList = videoList;
    }

    @Override
    public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.video_item, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(VideoViewHolder holder, int position) {

        VodData video = videoList.get(position);
        holder.videoTitle.setText(video.getVod_name());
        // 这里可以加载图片，比如使用 Glide 加载缩略图
        // 使用 Picasso 来加载图片
        Picasso.get().load(video.getVod_pic()).into(holder.videoImage);

        // 添加点击事件，跳转到视频播放页面
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), DanmkuVideoActivity.class);
            intent.putExtra("video_data", video);  // 传递单个 VodData 对象
            intent.putExtra("currentEpisode",1);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView videoImage;
        TextView videoTitle;

        public VideoViewHolder(View itemView) {
            super(itemView);
            videoImage = itemView.findViewById(R.id.videoImage);
            videoTitle = itemView.findViewById(R.id.videoTitle);
        }
    }

    public void setVodDataList(List<VodData> vodDataList){

        this.videoList=vodDataList;
        notifyDataSetChanged(); // 通知刷新
    }
}
