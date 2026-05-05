package com.dlight.ui.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.R;
import com.dlight.network.model.Vod;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频列表适配器
 */
public class VodAdapter extends RecyclerView.Adapter<VodAdapter.VodViewHolder> {

    private List<Vod> vodList;
    private OnVodClickListener listener;

    public interface OnVodClickListener {
        void onVodClick(Vod vod);
    }

    public VodAdapter(List<Vod> vodList, OnVodClickListener listener) {
        this.vodList = vodList != null ? vodList : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public VodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vod_card, parent, false);
        return new VodViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VodViewHolder holder, int position) {
        Vod vod = vodList.get(position);

        holder.titleTextView.setText(vod.getVodName());

        // 加载封面图
        if (vod.getVodPic() != null && !vod.getVodPic().isEmpty()) {
            Picasso.get()
                    .load(vod.getVodPic())
                    .placeholder(R.drawable.placeholder)
                    .error(R.drawable.error_placeholder)
                    .into(holder.coverImageView);
        }

        // 显示集数信息
        if (vod.getVodRemarks() != null && !vod.getVodRemarks().isEmpty()) {
            holder.episodeTextView.setVisibility(View.VISIBLE);
            holder.episodeTextView.setText(vod.getVodRemarks());
        } else {
            holder.episodeTextView.setVisibility(View.GONE);
        }

        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onVodClick(vod);
            }
        });
    }

    @Override
    public int getItemCount() {
        return vodList.size();
    }

    /**
     * 更新数据
     */
    public void updateData(List<Vod> newList) {
        this.vodList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * 添加数据
     */
    public void addData(List<Vod> moreData) {
        if (moreData != null && !moreData.isEmpty()) {
            int startPosition = vodList.size();
            vodList.addAll(moreData);
            notifyItemRangeInserted(startPosition, moreData.size());
        }
    }

    public static class VodViewHolder extends RecyclerView.ViewHolder {
        ImageView coverImageView;
        TextView titleTextView;
        TextView episodeTextView;

        public VodViewHolder(@NonNull View itemView) {
            super(itemView);
            coverImageView = itemView.findViewById(R.id.iv_cover);
            titleTextView = itemView.findViewById(R.id.tv_title);
            episodeTextView = itemView.findViewById(R.id.tv_episode);
        }
    }
}
