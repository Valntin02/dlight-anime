package com.dlight.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dlight.R;
import com.dlight.feature.search.SearchActivity;
import com.dlight.ui.fragment.MyMainFragment;

public class TopNavigationFragment extends Fragment {


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate布局
        View rootView = inflater.inflate(R.layout.fragment_top_navigation, container, false);

        // 获取搜索按钮并设置点击事件
        TextView btnSearch = rootView.findViewById(R.id.btn_search);
        btnSearch.setOnClickListener(v -> {
            // 跳转到 SearchActivity
            Intent intent = new Intent(getActivity(), SearchActivity.class);
            startActivity(intent);
        });

        TextView tvAnime=rootView.findViewById(R.id.home_anime);
        TextView txHome=rootView.findViewById(R.id.home_text);
        txHome.setOnClickListener(v->{
            MyMainFragment parentFragment = (MyMainFragment) getParentFragment();
            if (parentFragment != null) {
                parentFragment.switchToHome(txHome,tvAnime);
            }
        });


        tvAnime.setOnClickListener(v -> {
            MyMainFragment parentFragment = (MyMainFragment) getParentFragment();
            if (parentFragment != null) {
                parentFragment.switchToAnime(tvAnime,txHome);
            }
        });
        return rootView;
    }
}
