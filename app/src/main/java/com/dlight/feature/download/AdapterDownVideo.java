package com.dlight.feature.download;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.R;
import com.squareup.picasso.Picasso;

import java.util.List;

public class AdapterDownVideo extends RecyclerView.Adapter<AdapterDownVideo.VideoViewHolder> {
    public interface Listener {
        void onItemClick(DownloadTask task);
        void onDelete(DownloadTask task);
    }

    private final List<DownloadTask> tasks;
    private final Listener listener;

    public AdapterDownVideo(List<DownloadTask> tasks, Listener listener) {
        this.tasks = tasks;
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
        DownloadTask task = tasks.get(position);
        holder.title.setText(task.getTitle());
        holder.image.setImageResource(R.drawable.button_style);
        if (!task.getCoverUrl().isEmpty()) {
            Picasso.get().load(task.getCoverUrl()).placeholder(R.drawable.button_style).into(holder.image);
        }

        if (DownloadContract.STATUS_QUEUED.equals(task.getStatus())) {
            holder.status.setText("等待下载 · 点击暂停");
            holder.progress.setVisibility(View.VISIBLE);
            holder.progress.setIndeterminate(true);
        } else if (DownloadContract.STATUS_DOWNLOADING.equals(task.getStatus())) {
            holder.status.setText("下载中 " + task.getProgress() + "% · 点击暂停");
            holder.progress.setVisibility(View.VISIBLE);
            holder.progress.setIndeterminate(false);
            holder.progress.setProgress(task.getProgress());
        } else if (DownloadContract.STATUS_PAUSED.equals(task.getStatus())) {
            holder.status.setText("已暂停 " + task.getProgress() + "% · 点击继续");
            holder.progress.setVisibility(View.VISIBLE);
            holder.progress.setIndeterminate(false);
            holder.progress.setProgress(task.getProgress());
        } else if (DownloadContract.STATUS_COMPLETED.equals(task.getStatus())) {
            holder.status.setText("已缓存 · 点击播放");
            holder.progress.setVisibility(View.GONE);
        } else {
            String message = task.getErrorMessage().isEmpty() ? "下载失败" : task.getErrorMessage();
            holder.status.setText("下载失败 · 点击重试\n" + message);
            holder.progress.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(task));
        holder.itemView.setOnLongClickListener(v -> {
            if (task.isActive()) {
                Toast.makeText(holder.itemView.getContext(), "下载中的任务暂不能删除",
                    Toast.LENGTH_SHORT).show();
                return true;
            }
            new AlertDialog.Builder(holder.itemView.getContext())
                .setTitle("删除缓存任务")
                .setMessage("确定删除“" + task.getTitle() + "”及其缓存文件吗？")
                .setPositiveButton("删除", (dialog, which) -> listener.onDelete(task))
                .setNegativeButton("取消", null)
                .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView status;
        final ImageView image;
        final ProgressBar progress;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_video_title);
            status = itemView.findViewById(R.id.tv_download_status);
            image = itemView.findViewById(R.id.iv_cover);
            progress = itemView.findViewById(R.id.item_download_progress);
        }
    }
}
