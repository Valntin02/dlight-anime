package com.dlight.feature.search;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.dlight.ui.player.DanmkuVideoActivity;
import com.dlight.R;
import com.dlight.data.model.VodData;
import com.squareup.picasso.Picasso;

import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    private  List<VodData> data;

    public SearchResultAdapter(List<VodData> data) {
        this.data = data;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title,status,years,cv;
        ImageView imageView;
        public ViewHolder(View view) {
            super(view);
            imageView=view.findViewById(R.id.se_video_cover);
            title = view.findViewById(R.id.se_video_name);
            years = view.findViewById(R.id.se_video_years);
            status=view.findViewById(R.id.se_video_status);
            cv=view.findViewById(R.id.se_video_cv);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_search_video, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if(data.isEmpty()) return;
        VodData item = data.get(position);
        Picasso.get().load(item.getVod_pic()).into(holder.imageView);

        holder.title.setText(item.getVod_name());
        holder.years.setText(item.getVod_year());
        holder.status.setText(item.getVod_remarks());
        holder.cv.setText(item.getVod_actor());

        // 给每个item设置点击事件
        holder.imageView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), DanmkuVideoActivity.class);
            intent.putExtra("video_data", item);  // 传递单个 VodData 对象
            intent.putExtra("currentEpisode",1);
            v.getContext().startActivity(intent);

        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public void setData(List<VodData> data){
        this.data=data;
        notifyDataSetChanged();
    }
}
