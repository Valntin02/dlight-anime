package com.dlight.ui.home;

import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.dlight.R;

import java.util.List;

public class YearIndexAdapter extends RecyclerView.Adapter<YearIndexAdapter.YearViewHolder> {

    // 接口用于监听年份点击事件
    public interface OnYearClickListener {
        void onYearClick(String year);
    }
    private int selectedPosition = 0;
    private List<String> yearList;
    private OnYearClickListener listener;
    public  static  class YearViewHolder extends RecyclerView.ViewHolder{
        TextView yearButton;
        public YearViewHolder(View itemView){
            super(itemView);
            yearButton = itemView.findViewById(R.id.year_button);
        }
    }
    public YearIndexAdapter(List<String> yearList, OnYearClickListener listener) {
        this.yearList = yearList;
        this.listener = listener;
    }

    @Override
    public YearViewHolder onCreateViewHolder(ViewGroup parent,int viewTpe){
        View view=LayoutInflater.from(parent.getContext()).inflate(R.layout.item_year_index,parent,false);
        return new YearViewHolder(view);
    }

    @Override
    public void onBindViewHolder(YearViewHolder holder, int position) {
        // 设置选中状态
        holder.yearButton.setSelected(position == selectedPosition);
        String year = yearList.get(position);
        holder.yearButton.setText(year);
        holder.yearButton.setOnClickListener(v -> {
            int preSelected=selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(preSelected);
            notifyItemChanged(selectedPosition);
            listener.onYearClick(year);

        });

    }

    @Override
    public int getItemCount() {
        return yearList.size();
    }
}
