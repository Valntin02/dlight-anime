package com.dlight.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.dlight.R;

public class FragmentHomePage extends Fragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_homepage, container, false);
        Log.d("myFragment","MyMainFragmentinit");
        // 加载顶部导航栏
        if (savedInstanceState == null) {

            getChildFragmentManager().beginTransaction()
                .replace(R.id.fragment_announcement_bar, new AnnouncementBar(),"announcement")
                .commit();

            getChildFragmentManager().beginTransaction()
                .replace(R.id.fragment_update_today, new UpdateTodayFragment())
                .commit();

            // 加载中间模块
            getChildFragmentManager().beginTransaction()
                .replace(R.id.fragment_content, new WeeklyShow())
                .commit();
        }

        return view;
    }
}
