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

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import com.dlight.data.model.VodResModel;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;

public class UpdateTodayFragment extends Fragment {

    private RecyclerView recyclerView;
    private VideoAdapter videoAdapter;
    private String TAG="today video";
    private List<VodData> vodDataList = new ArrayList<>();
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView =  inflater.inflate(R.layout.fragment_update_today, container, false);

        recyclerView = rootView.findViewById(R.id.recycler_view_videos);
//        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), ,LinearLayoutManager.HORIZONTAL, false));
//        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3, GridLayoutManager.HORIZONTAL, false));
        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 1,GridLayoutManager.HORIZONTAL,false));

        videoAdapter = new VideoAdapter(vodDataList);
        recyclerView.setAdapter(videoAdapter);

        gettodayvideo();
        return rootView;
    }


    private void gettodayvideo() {


        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        Call<VodResModel> call = apiService.requestVodData();

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<VodResModel>() {
            @Override
            public void onSuccess(VodResModel data) {
                Log.d(TAG, "msg" + data);
                vodDataList.clear();
                vodDataList.addAll(data.getVodDataList());
                videoAdapter.notifyDataSetChanged();

            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Error: " + error);
            }
        });
    }
}
