package com.dlight.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.R;
import com.dlight.data.remote.RetrofitClient;
import com.dlight.data.model.VodData;
import com.dlight.ui.widget.LoadStateView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import com.dlight.data.model.VodPageResModel;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;

public class FragmentAnime extends Fragment {

    private RecyclerView recyclerView;
    private LoadStateView loadStateView;
    private VideoAdapter videoAdapter;

    private RecyclerView recyclerViewYears;
    private List<VodData> vodDataList = new ArrayList<>();

    private List<String> yearList=new ArrayList<>();
    private final int limit = 36;
    private final HomeLoadStatePolicy.AnimeTracker animeTracker =
        new HomeLoadStatePolicy.AnimeTracker();
    private Call<VodPageResModel> activeCall;
    private Snackbar paginationErrorSnackbar;
    // 当前年份过滤的 API key; null = 全部 (后端不带 year 参数)
    private String currentYear = null;
    // UI 标签 (中文展示用)
    private static final String LABEL_ALL = "全部年份";
    private static final String LABEL_EARLIER = "更早";
    // 后端约定的稳定 key (英文; 后端同时兼容旧中文以照顾 Vue, 见 vod_service.py)
    private static final String KEY_EARLIER = "earlier";
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view= inflater.inflate(R.layout.fragment_anime, container, false);
        //索引设置模块
        recyclerViewYears=view.findViewById(R.id.recycler_view_years);

        recyclerViewYears.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        yearList.add(LABEL_ALL);
        for (int year = 2025; year >= 2010; year--) {
            yearList.add(String.valueOf(year));
        }
        yearList.add(LABEL_EARLIER);
        // 设置年份索引适配器: 点击切年份 -> 重置分页 -> 后端按 year 重新拉取
        YearIndexAdapter yearIndexAdapter = new YearIndexAdapter(yearList, year -> {
            Log.d("FragmentAnime", "Selected Year: " + year);
            applyYearFilter(year);
        });

        recyclerViewYears.setAdapter(yearIndexAdapter);

        //视频展示模块
        recyclerView = view.findViewById(R.id.recycler_view_videos);
        loadStateView = view.findViewById(R.id.anime_load_state);
        // 使用 GridLayoutManager 设置列数为 3
        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        videoAdapter = new VideoAdapter(vodDataList);
        recyclerView.setAdapter(videoAdapter);
        loadStateView.setOnRetryListener(retryView -> getVideoPage());
        getVideoPage();

        recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            //RecyclerView 的 onScrolled 方法会被连续触发多次，因为 RecyclerView 每滚动一小段就会调一次这个监听
            //这里RecyclerView 的 onScrolled 方法会被连续触发多次，因为 RecyclerView 每滚动一小段就会调一次这个监听
            // tracker 屏蔽重复请求，并记录是否已到最后一页
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // 只监听向下滑动
                if (dy > 0) {
                    GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        int visibleItemCount = layoutManager.getChildCount();
                        int totalItemCount = layoutManager.getItemCount();
                        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();//你滑到了第 24 条记录位置

                        if (!animeTracker.isRequesting()
                            && !animeTracker.isExhausted()
                            && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= limit) {
                            // 到达底部，加载下一页
                            //Log.d("FragmentAnime", "滑动到底部触发" );
                            getVideoPage();
                        }
                    }
                }
            }
        });

        return view;
    }


    private void getVideoPage() {
        boolean hasContent = HomeLoadStatePolicy.hasContent(vodDataList);
        HomeLoadStatePolicy.AnimeRequest request = animeTracker.start(hasContent);
        if (request == null) {
            if (animeTracker.isExhausted() && !hasContent) {
                loadStateView.showEmpty(null);
            }
            return;
        }

        dismissPaginationError();
        if (!request.isPagination()) {
            loadStateView.showLoading(null);
        }
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        // currentYear 为 null 时 Retrofit 会自动省略该 query 参数
        Call<VodPageResModel> call = apiService.requestVideoPage(
            request.page(),
            limit,
            currentYear
        );
        activeCall = call;

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<VodPageResModel>() {
            @Override
            public void onSuccess(VodPageResModel data) {
                if (!canHandle(call, request)) return;
                int totalPage = data == null ? 0 : data.getTotalPage();
                if (!animeTracker.succeed(request, totalPage)) return;
                activeCall = null;
                dismissPaginationError();
                Log.d("FragmentAnime", "msg" + data);
                List<VodData> items = data == null ? null : data.getVodDataList();
                if (items != null) {
                    vodDataList.addAll(items);
                }
                videoAdapter.notifyDataSetChanged();

                if (HomeLoadStatePolicy.hasContent(vodDataList)) {
                    loadStateView.hide();
                } else {
                    loadStateView.showEmpty(null);
                }
            }

            @Override
            public void onFailure(String error) {
                if (!canHandle(call, request)) return;
                if (!animeTracker.fail(request)) return;
                activeCall = null;
                Log.e("FragmentAnime", "Error: " + error);
                if (request.isPagination()
                    && HomeLoadStatePolicy.hasContent(vodDataList)) {
                    loadStateView.hide();
                    showPaginationError();
                } else {
                    loadStateView.showError(null);
                }
            }
        });
    }

    private void applyYearFilter(String label) {
        String next = labelToApiKey(label);
        // 同年份重复点击不重新请求
        if ((next == null && currentYear == null) || (next != null && next.equals(currentYear))) {
            return;
        }
        dismissPaginationError();
        if (activeCall != null) {
            activeCall.cancel();
            activeCall = null;
        }
        currentYear = next;
        animeTracker.reset();
        vodDataList.clear();
        videoAdapter.notifyDataSetChanged();
        recyclerView.scrollToPosition(0);
        getVideoPage();
    }

    private boolean canHandle(
        Call<VodPageResModel> call,
        HomeLoadStatePolicy.AnimeRequest request
    ) {
        return activeCall == call
            && animeTracker.accepts(request)
            && isAdded()
            && recyclerView != null
            && videoAdapter != null
            && loadStateView != null;
    }

    @Override
    public void onDestroyView() {
        animeTracker.invalidate();
        if (activeCall != null) {
            activeCall.cancel();
            activeCall = null;
        }
        dismissPaginationError();
        if (loadStateView != null) {
            loadStateView.setOnRetryListener(null);
        }
        recyclerView = null;
        recyclerViewYears = null;
        loadStateView = null;
        videoAdapter = null;
        super.onDestroyView();
    }

    private void showPaginationError() {
        if (!isAdded() || recyclerView == null) return;
        paginationErrorSnackbar = Snackbar.make(
            recyclerView,
            R.string.anime_pagination_error,
            Snackbar.LENGTH_INDEFINITE
        );
        paginationErrorSnackbar.setAction(
            R.string.anime_pagination_retry,
            view -> getVideoPage()
        );
        paginationErrorSnackbar.show();
    }

    private void dismissPaginationError() {
        if (paginationErrorSnackbar != null) {
            paginationErrorSnackbar.dismiss();
            paginationErrorSnackbar = null;
        }
    }

    /**
     * UI 标签 -> 后端 API key:
     *   "全部年份" -> null  (Retrofit 自动省略 year 参数)
     *   "更早"     -> "earlier"
     *   "2024" 等  -> 原样
     */
    private String labelToApiKey(String label) {
        if (LABEL_ALL.equals(label)) return null;
        if (LABEL_EARLIER.equals(label)) return KEY_EARLIER;
        return label;
    }
}
