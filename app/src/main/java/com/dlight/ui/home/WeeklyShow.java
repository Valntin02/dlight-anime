package com.dlight.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.R;
import com.dlight.data.remote.RetrofitClient;
import com.dlight.data.model.VodData;
import com.dlight.ui.widget.LoadStateView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import com.dlight.data.model.VodResModel;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;

public class WeeklyShow extends Fragment {

    private static final String TAG = "GetData";
    private RecyclerView recyclerView;
    private LoadStateView loadStateView;
    private VideoAdapter videoAdapter;
    private List<VodData> vodDataList = new ArrayList<>();
    // 用来缓存每个星期的数据
    private Map<String, List<VodData>> dataCache = new HashMap<>();
    private Call<VodResModel> activeCall;
    private int requestGeneration;
    private String selectedWeekday = "日";


    private String getWeekdayString(int checkedId) {
        switch (checkedId) {
            case R.id.radio_monday:
                return "一";
            case R.id.radio_tuesday:
                return "二";
            case R.id.radio_wednesday:
                return "三";
            case R.id.radio_thursday:
                return "四";
            case R.id.radio_friday:
                return "五";
            case R.id.radio_saturday:
                return "六";
            case R.id.radio_sunday:
                return "日";
            default:
                return "日";
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 设置布局
        View rootView = inflater.inflate(R.layout.activity_get_data, container, false);

        // 初始化RecyclerView
        recyclerView = rootView.findViewById(R.id.recycler_view_videos);
        loadStateView = rootView.findViewById(R.id.weekly_load_state);
        // 使用 GridLayoutManager 设置列数为 3
        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        videoAdapter = new VideoAdapter(vodDataList);
        recyclerView.setAdapter(videoAdapter);
        loadStateView.setOnRetryListener(view -> fetchDataForWeekday(selectedWeekday));
        // 获取RadioGroup
        RadioGroup weekdayRadioGroup = rootView.findViewById(R.id.weekday_radio_group);

        // 设置默认选中周日
        RadioButton defaultButton = rootView.findViewById(R.id.radio_sunday);
        defaultButton.setChecked(true);
        defaultButton.setBackgroundResource(R.drawable.button_style2);

        // 设置星期选择按钮的监听器

        weekdayRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String weekday = getWeekdayString(checkedId);
            fetchDataForWeekday(weekday);
            // 遍历所有子视图并重置背景
            for (int i = 0; i < group.getChildCount(); i++) {
                View view = group.getChildAt(i);
                if (view instanceof RadioButton) {
                    RadioButton radioButton = (RadioButton) view;
                    if (radioButton.getId() == checkedId) {
                        radioButton.setBackgroundResource(R.drawable.button_style2);
                    } else {
                        radioButton.setBackgroundResource(R.drawable.button_style);
                    }
                }
            }
        });

        // 默认显示周一的视频数据
        fetchDataForWeekday("日");

        return rootView;
    }

    private void fetchDataForWeekday(String weekday) {
        if (activeCall != null) {
            activeCall.cancel();
            activeCall = null;
        }
        selectedWeekday = weekday;
        int generation = ++requestGeneration;

        // 首先检查缓存中是否有该星期的数据
        if (dataCache.containsKey(weekday)) {
            // 如果缓存中有数据，则直接使用缓存的数据
            List<VodData> cachedItems = dataCache.get(weekday);
            vodDataList.clear();
            if (cachedItems != null) {
                vodDataList.addAll(cachedItems);
            }
            videoAdapter.notifyDataSetChanged();
            if (HomeLoadStatePolicy.hasContent(cachedItems)) {
                loadStateView.hide();
            } else {
                loadStateView.showEmpty(null);
            }
            return;
        }

        vodDataList.clear();
        videoAdapter.notifyDataSetChanged();
        loadStateView.showLoading(null);

        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<VodResModel> call = apiService.requestVodData(weekday);
        activeCall = call;

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<VodResModel>() {
            @Override
            public void onSuccess(VodResModel data) {
                if (!canHandle(generation, weekday)) return;
                activeCall = null;
                Log.d(TAG, "msg" + data);
                List<VodData> items = data == null ? null : data.getVodDataList();
                vodDataList.clear();
                if (items != null) {
                    vodDataList.addAll(items);
                }
                videoAdapter.notifyDataSetChanged();
                // 将请求到的数据缓存到 Map 中
                dataCache.put(weekday, new ArrayList<>(vodDataList));
                if (HomeLoadStatePolicy.hasContent(items)) {
                    loadStateView.hide();
                } else {
                    loadStateView.showEmpty(null);
                }
            }

            @Override
            public void onFailure(String error) {
                if (!canHandle(generation, weekday)) return;
                activeCall = null;
                Log.e(TAG, "Error: " + error);
                loadStateView.showError(null);
            }
        });
    }

    private boolean canHandle(int generation, String weekday) {
        return HomeLoadStatePolicy.isCurrentWeeklyRequest(
                generation,
                weekday,
                requestGeneration,
                selectedWeekday
            )
            && isAdded()
            && recyclerView != null
            && videoAdapter != null
            && loadStateView != null;
    }

    @Override
    public void onDestroyView() {
        requestGeneration++;
        if (activeCall != null) {
            activeCall.cancel();
            activeCall = null;
        }
        if (loadStateView != null) {
            loadStateView.setOnRetryListener(null);
        }
        recyclerView = null;
        loadStateView = null;
        videoAdapter = null;
        super.onDestroyView();
    }
}
