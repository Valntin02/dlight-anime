package com.dlight.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dlight.R;
import com.dlight.data.remote.AuthHeaderUtil;
import com.dlight.data.remote.RetrofitClient;
import com.dlight.util.Param;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;

public class PlayRecordActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PlayRecordAdapter playRecordAdapter;
    private List<PlayRecord> playRecordList=new ArrayList<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // 异步线程池
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_record);
        Param.setStatusBarTransparent(this, false, getResources().getColor(R.color.dark_bg));
        recyclerView = findViewById(R.id.rv_play_records);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // 异步加载数据
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getInstancePlayRecord(this);
            playRecordList = db.playRecordDao().getAllPlayRecords();
            //这里有给技巧就是只有本地数据为空我们才去获取后台的记录，其他情况不需要去请求后端
            //因为但凡有一个记录 就说明之前是同步过的
            if(playRecordList.isEmpty()){
                getPlayRecord();
            }
            // 回到主线程更新 UI
            runOnUiThread(() -> playRecordAdapter.setData(playRecordList));

        });

        playRecordAdapter = new PlayRecordAdapter(playRecordList,this);
        recyclerView.setAdapter(playRecordAdapter);
    }

    private void getPlayRecord(){

        SharedPreferences sharedPreferences = this.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);

        // 检查用户是否已登录
        boolean isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false);
        if(!isLoggedIn) return;

        String token = AuthHeaderUtil.bearer(sharedPreferences.getString("token", ""));
        if (token == null || token.isEmpty()) {
            return;
        }

        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        //这里就不创建新的接受类了 使用泛 Map
        Call<PlayRecordResModel> call = apiService.requestPlayRecords(token);

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<PlayRecordResModel>() {
            @Override
            public void onSuccess(PlayRecordResModel data) {
                if(data.isSuccessCode()){
                    playRecordList=data.getData();
                    playRecordAdapter.setData(playRecordList);
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.submit(() -> {
                        AppDatabase db = AppDatabase.getInstanceMyStarRecord(getApplicationContext());
                        PlayRecordDao syncPlayRecord=db.playRecordDao();
                        syncPlayRecord.insert(playRecordList);
                    });
                    Log.d("requestPlayRecords", "获取同步成功:" +data.getMsg());
                }else {
                    Log.e("requestPlayRecords", "获取同步失败:" +data.getMsg());

                }

            }
            @Override
            public void onFailure(String error) {
                Log.e("requestPlayRecords", "获取同步失败:" +error);

            }
        });
    }
}
