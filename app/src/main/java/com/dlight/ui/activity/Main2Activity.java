package com.dlight.ui.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.dlight.R;
import com.dlight.data.local.AppDatabase;
import com.dlight.data.local.MyStarRecord;
import com.dlight.data.local.MyStarRecordDao;
import com.dlight.data.local.PlayRecord;
import com.dlight.data.local.PlayRecordDao;
import com.dlight.data.model.JsonResModel;
import com.dlight.data.remote.ApiClient;
import com.dlight.data.remote.ApiService;
import com.dlight.data.remote.AuthHeaderUtil;
import com.dlight.data.remote.RetrofitClient;
import com.dlight.ui.fragment.MyMainFragment;
import com.dlight.ui.fragment.MyPageFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.dlight.util.Param;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;

public class Main2Activity extends AppCompatActivity {

    private MyMainFragment myMainFragment;
    private MyPageFragment myPageFragment;
    private FragmentTransaction transaction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        Param.setStatusBarTransparent(this, false, getResources().getColor(R.color.dark_bg));
        // 获取 BottomNavigationView
        BottomNavigationView bottomNavigationView = findViewById(R.id.fragment_bottom_navigation);
        // 设置默认选中项
        bottomNavigationView.setSelectedItemId(R.id.nav_home);

        // 初始化 Fragment
        myMainFragment = new MyMainFragment();
        myPageFragment = new MyPageFragment();

        // 获取 FragmentTransaction
        transaction = getSupportFragmentManager().beginTransaction();

        // 添加 Fragment 到容器
        transaction.add(R.id.main2_fragment_container, myMainFragment);
        transaction.add(R.id.main2_fragment_container, myPageFragment);

        // 设置默认 Fragment 可见
        transaction.hide(myPageFragment); // 隐藏我的页面 Fragment
        transaction.show(myMainFragment); // 显示首页 Fragment
        transaction.commit();

        // 设置导航栏的点击事件监听器
        bottomNavigationView.setOnItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.nav_home:
                    loadFragment(myPageFragment, myMainFragment);
                    return true;
                case R.id.nav_profile:
                    loadFragment(myMainFragment, myPageFragment);
                    return true;
                default:
                    return false;
            }
        });
    }

    // 加载 Fragment 的方法
    private void loadFragment(Fragment form,Fragment to) {
        // 获取当前显示的 Fragment

            transaction = getSupportFragmentManager().beginTransaction();
            transaction.hide(form);
            // 显示目标 Fragment
            transaction.show(to);

            // 提交事务
            transaction.commit();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                syncMyStarData(); // 在应用退出时触发同步
                syncPlayRecord();
            }
        });

    }

    public void syncPlayRecord(){
        String token = getTokenFromPrefs();
        if (token.isEmpty()) {
            return;
        }

        AppDatabase db = AppDatabase.getInstanceMyStarRecord(getApplicationContext());
        PlayRecordDao syncPlayRecord=db.playRecordDao();
        // 获取所有未同步的记录
        List<PlayRecord> unsyncedRecords = syncPlayRecord.getUnsyncedRecords();

        if (unsyncedRecords.isEmpty()) {
            return; // 没有未同步的数据
        }

        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        //这里就不创建新的接受类了 使用泛 Map
        Call<JsonResModel> call = apiService.syncPlayRecords(unsyncedRecords, token);

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<JsonResModel>() {
            @Override
            public void onSuccess(JsonResModel data) {
                if (data.isSuccessCode()) {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.submit(() -> {
                        Log.d("syncPlayRecord", "同步成功:");
                        List<Integer> ids = new ArrayList<>();
                        for (PlayRecord record : unsyncedRecords) {
                            ids.add(record.getId());
                        }
                        syncPlayRecord.markRecordsAsSynced(ids);
                    });
                }else{
                    Log.e("syncPlayRecord", "同步失败:" +data);
                }
            }
            @Override
            public void onFailure(String error) {

                Log.e("syncPlayRecord", "同步失败:"+error);
            }
        });

    }

    // 同步数据的方法
    public void syncMyStarData() {
        String token = getTokenFromPrefs();
        if (token.isEmpty()) {
            return;
        }

        AppDatabase db = AppDatabase.getInstanceMyStarRecord(getApplicationContext());
        MyStarRecordDao myStarRecordDao=db.myStarRecordDao();
        // 获取所有未同步的记录
        List<MyStarRecord> unsyncedRecords = myStarRecordDao.getUnsyncedRecords();

        if (unsyncedRecords.isEmpty()) {
            return; // 没有未同步的数据
        }

        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);
        //这里就不创建新的接受类了 使用泛 Map
        Call<JsonResModel> call = apiService.syncStarRecords(unsyncedRecords, token);

        ApiClient.requestData(call, new ApiClient.ApiResponseCallback<JsonResModel>() {
            @Override
            public void onSuccess(JsonResModel data) {
                if (data.isSuccessCode()) {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    executor.submit(() -> {
                        Log.d("syncMyStarData", "同步成功:");
                        List<Integer> ids = new ArrayList<>();
                        for (MyStarRecord record : unsyncedRecords) {
                            ids.add(record.getId());
                        }
                        myStarRecordDao.markRecordsAsSynced(ids);
                    });
                }else{
                    Log.e("syncMyStarData", "同步失败:" +data);
                }
            }
            @Override
            public void onFailure(String error) {

                Log.e("syncMyStarData", "同步失败:"+error);
            }
        });
    }

    private String getTokenFromPrefs() {
        SharedPreferences sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        boolean isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false);
        if (!isLoggedIn) {
            return "";
        }
        String token = sharedPreferences.getString("token", "");
        return AuthHeaderUtil.bearer(token);
    }
}
