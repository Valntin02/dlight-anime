package com.dlight.data.local;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.dlight.ui.player.DanmkuVideoActivity;
import com.dlight.R;
import com.dlight.data.model.VodData;
import com.squareup.picasso.Picasso;

import java.util.List;

public class PlayRecordAdapter extends RecyclerView.Adapter<PlayRecordAdapter.PlayRecordViewHolder> {

    private List<PlayRecord> playRecordList;
    private Context mcontext;
    public PlayRecordAdapter(List<PlayRecord> playRecordList,Context mcontext) {
        this.playRecordList = playRecordList;
        this.mcontext=mcontext;
    }

    @NonNull
    @Override
    public PlayRecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_play_record, parent, false);
        return new PlayRecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlayRecordViewHolder holder, int position) {
        PlayRecord playRecord = playRecordList.get(position);

        // 显示封面图（使用 Glide 加载图片）
        //默认自带内存和磁盘缓存 这里突然知道为什么加载不出来了 这里全局修改了glide的ssl验证 只验证自己服务器 所以导致请求
        //这个图片资源是别的服务器导致加载不出来，而评论区的头像可以加载
//        Glide.with(mcontext)
//            .load(playRecord.getPicUrl())
//            .error(R.drawable.button_style)
//            .into(holder.ivCover);
        //Piacasso 默认也支持内存磁盘缓存
        Picasso.get().load(playRecord.getVod_pic()).into(holder.ivCover);


        holder.tvVideoTitle.setText(playRecord.getVod_name());
        holder.tvPlayProgress.setText("观看到: " + playRecord.getEpisodeIndex() + " 集");

        holder.ivCover.setOnClickListener(v->{
            Intent intent = new Intent(v.getContext(), DanmkuVideoActivity.class);
            VodData video=new VodData(playRecord.getVod_id(),playRecord.getVod_name(),playRecord.getVod_pic(),playRecord.getVod_play_url(),
                playRecord.getVod_actor(),playRecord.getVod_remarks(),playRecord.getVod_year(),playRecord.getVod_content(),playRecord.getVod_total());
            intent.putExtra("video_data", video);  // 传递单个 VodData 对象
            //传递了不取也不会报错，也不会造成内存泄露，但这里是将对象序列号字节流放进intent Bundle里面
            intent.putExtra("currentEpisode",playRecord.getEpisodeIndex());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return playRecordList.size();
    }

    public void setData(List<PlayRecord> data) {
        this.playRecordList = data;
        notifyDataSetChanged(); // 通知刷新
    }

    public static class PlayRecordViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvVideoTitle, tvPlayProgress;

        public PlayRecordViewHolder(View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_cover);
            tvVideoTitle = itemView.findViewById(R.id.tv_video_title);
            tvPlayProgress = itemView.findViewById(R.id.tv_play_progress);

        }
    }
}
