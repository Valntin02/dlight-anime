package com.dlight.ui.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.dlight.R;
import com.dlight.ui.home.AnnouncementBar;
import com.dlight.ui.home.FragmentAnime;
import com.dlight.ui.home.FragmentHomePage;
import com.dlight.ui.home.TopNavigationFragment;

public class MyMainFragment extends Fragment {
    private Fragment currentFragment;
    private Fragment fragmentHomePage;
    private Fragment fragmentAnime;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        fragmentHomePage = new FragmentHomePage();
        fragmentAnime = new FragmentAnime(); // 提前初始化
        currentFragment=fragmentHomePage;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mymain, container, false);
        Log.d("myFragment","MyMainFragmentinit");
        // 加载顶部导航栏

            getChildFragmentManager().beginTransaction()
                .replace(R.id.fragment_top_navigation, new TopNavigationFragment())
                .commit();

            // 加载中间模块
            getChildFragmentManager().beginTransaction()
                .add(R.id.fragment_container, fragmentHomePage)
                .commit();


        return view;
    }

    public void switchToHome(TextView selected, TextView unselected) {
        boolean flag=switchContent(fragmentHomePage);
        if(flag){
            applyTabState(selected, unselected);

            //公告栏的焦点
            FragmentManager childFragmentManager = fragmentHomePage.getChildFragmentManager();
            Fragment announcementFragment = childFragmentManager.findFragmentByTag("announcement");

            if (announcementFragment instanceof AnnouncementBar) {
                ((AnnouncementBar) announcementFragment).onShow();
            }
        }
    }

    public void switchToAnime(TextView selected, TextView unselected) {
        boolean flag=switchContent(fragmentAnime);
        if(flag){
            applyTabState(selected, unselected);
        }
    }

    private void applyTabState(TextView selected, TextView unselected) {
        selected.setTextColor(getResources().getColor(R.color.text_primary));
        unselected.setTextColor(getResources().getColor(R.color.text_secondary));
    }

    // 切换 fragment_content 内容的方法 使用add
    public boolean switchContent(Fragment targetFragment) {
        if (targetFragment == currentFragment) return false;

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();

        if (!targetFragment.isAdded()) {
            transaction.hide(currentFragment).add(R.id.fragment_container, targetFragment);
        } else {
            transaction.hide(currentFragment).show(targetFragment);
        }

        transaction.commit();
        currentFragment = targetFragment;
        return true;
    }
}
