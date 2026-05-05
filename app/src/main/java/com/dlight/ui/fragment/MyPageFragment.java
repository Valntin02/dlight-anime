package com.dlight.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.dlight.R;
import com.dlight.feature.user.LoginFragment;
import com.dlight.feature.user.UserFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MyPageFragment extends Fragment implements UserFragment.OnLogoutListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_my_page, container, false);

        Log.d("myFragment","MyPageFragmentinit");

        // 检查登录状态，是否已经登录
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("user_prefs", requireActivity().MODE_PRIVATE);
        boolean isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false);

        Fragment existingFragment = getChildFragmentManager().findFragmentByTag("UserFragment");

        // 根据登录状态加载对应的 Fragment
        if (isLoggedIn) {
            // 如果已登录，加载用户主页
            UserFragment userFragment = new UserFragment();
            getChildFragmentManager().beginTransaction()
                .replace(R.id.fragment_login, userFragment)  // 传递用户数据给 UserFragment
                .commit();
        } else {
            // 如果没有登录，加载登录界面
            LoginFragment loginFragment = new LoginFragment();
            getChildFragmentManager().beginTransaction()
                .replace(R.id.fragment_login, loginFragment)  // 加载登录界面
                .commit();
        }


        return view;
    }

    @Override
    public void Logout() {

// 删除 SharedPreferences 文件
        requireActivity().getApplicationContext().deleteSharedPreferences("user_prefs");
// 尝试重新获取 SharedPreferences
//        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
//
//// 判断 SharedPreferences 是否为空或删除成功
//        if (sharedPreferences.getAll().isEmpty()) {
//            // 删除成功
//            Log.d("SharedPreferences", "SharedPreferences deleted successfully");
//        } else {
//            // 删除失败
//            Log.d("SharedPreferences", "Failed to delete SharedPreferences");
//        }

        Toast.makeText(getContext(), "已退出登录", Toast.LENGTH_SHORT).show();
        // 显示登录界面
        LoginFragment loginFragment = new LoginFragment();
        getChildFragmentManager().beginTransaction()
            .replace(R.id.fragment_login, loginFragment)  // 加载登录界面
            .commit();

    }
}
