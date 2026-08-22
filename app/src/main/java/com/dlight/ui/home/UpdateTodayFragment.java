package com.dlight.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.R;
import com.dlight.data.remote.RetrofitClient;
import com.dlight.data.model.VodData;
import com.dlight.ui.widget.LoadStateView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import com.dlight.data.model.VodResModel;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;

public class UpdateTodayFragment extends Fragment {

    private RecyclerView recyclerView;
    private LoadStateView loadStateView;
    private VideoAdapter videoAdapter;
    private String TAG="today video";
    private List<VodData> vodDataList = new ArrayList<>();
    private Call<VodResModel> activeCall;
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView =  inflater.inflate(R.layout.fragment_update_today, container, false);

        recyclerView = rootView.findViewById(R.id.recycler_view_videos);
        loadStateView = rootView.findViewById(R.id.today_load_state);
//        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), ,LinearLayoutManager.HORIZONTAL, false));
//        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3, GridLayoutManager.HORIZONTAL, false));
        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 1,GridLayoutManager.HORIZONTAL,false));

        videoAdapter = new VideoAdapter(vodDataList);
        recyclerView.setAdapter(videoAdapter);
        loadStateView.setOnRetryListener(view -> gettodayvideo());

        gettodayvideo();
        return rootView;
    }


    private void gettodayvideo() {
        if (!HomeLoadStatePolicy.hasContent(vodDataList)) {
            loadStateView.showLoading(null);
        }
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<VodResModel> call = apiService.requestVodData();
        activeCall = call;

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<VodResModel>() {
            @Override
            public void onSuccess(VodResModel data) {
                if (!canHandle(call)) return;
                activeCall = null;
                Log.d(TAG, "msg" + data);
                List<VodData> items = data == null ? null : data.getVodDataList();
                vodDataList.clear();
                if (items != null) {
                    vodDataList.addAll(items);
                }
                videoAdapter.notifyDataSetChanged();
                if (HomeLoadStatePolicy.hasContent(items)) {
                    loadStateView.hide();
                } else {
                    loadStateView.showEmpty(null);
                }
            }

            @Override
            public void onFailure(String error) {
                if (!canHandle(call)) return;
                activeCall = null;
                Log.e(TAG, "Error: " + error);
                if (HomeLoadStatePolicy.shouldShowError(
                    HomeLoadStatePolicy.hasContent(vodDataList)
                )) {
                    loadStateView.showError(null);
                }
            }
        });
    }

    private boolean canHandle(Call<VodResModel> call) {
        return activeCall == call
            && isAdded()
            && recyclerView != null
            && videoAdapter != null
            && loadStateView != null;
    }

    @Override
    public void onDestroyView() {
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
