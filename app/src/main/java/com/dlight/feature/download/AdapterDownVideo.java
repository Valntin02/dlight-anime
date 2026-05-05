package com.dlight.feature.download;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.CachedVideo;
import com.dlight.R;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.List;

public class AdapterDownVideo extends RecyclerView.Adapter<AdapterDownVideo.VideoViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(File file);
    }

    private List<CachedVideo> fileList;
    private OnItemClickListener listener;


    public AdapterDownVideo(List<CachedVideo> fileList, OnItemClickListener listener) {
        this.fileList = fileList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_downvideo, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        CachedVideo cachedVideo=fileList.get(position);
        File file = cachedVideo.getVideoFile();
        String picUrl=cachedVideo.getCoverUrl();

        if(!picUrl.isEmpty())
        Picasso.get().load(picUrl).into(holder.imageView);

        holder.textView.setText(file.getName());
        holder.itemView.setOnClickListener(v -> listener.onItemClick(file));

        // 设置长按删除
        holder.itemView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(holder.itemView.getContext())
                .setTitle("删除视频")
                .setMessage("确定要删除这个视频吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    int realPos = holder.getAdapterPosition(); // 防止位置异常
                    File deleteFile = fileList.get(realPos).getVideoFile();
                    if (deleteFile.exists()) {
                        deleteFile.delete(); // 删除文件
                    }
                    fileList.remove(realPos); // 移除列表
                    notifyItemRemoved(realPos); // 更新UI
                })
                .setNegativeButton("取消", null)
                .show();
            return true; // 表示事件已处理
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ImageView imageView;
        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.tv_video_title);
            imageView=itemView.findViewById(R.id.iv_cover);
        }
    }
}
