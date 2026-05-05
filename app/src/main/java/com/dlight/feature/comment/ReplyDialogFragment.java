package com.dlight.feature.comment;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.dlight.R;

public class ReplyDialogFragment extends DialogFragment  {


    private int viewPagerHeight = 0;  // 存储 ViewPager 高度

    public ReplyDialogFragment(int viewPagerHeight) {
        this.viewPagerHeight = viewPagerHeight;  // 将 ViewPager 高度传递到 DialogFragment
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reply_dialog, container, false);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        // 确保窗口已经创建完毕后再设置属性
        if (getDialog() != null && getDialog().getWindow() != null) {

            // 去除外部灰色背景阴影
            getDialog().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // 去除默认内边距
            getDialog().getWindow().getDecorView().setPadding(0, 0, 0, 0);

            WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();


            // 设置窗口大小占满整个屏幕
            getDialog().getWindow().setLayout(viewPagerHeight, 1500);  // 宽度占满，高度1500px


            // 设置窗口位置（距离顶部 200px）
            params = getDialog().getWindow().getAttributes();
            params.y = 200;  // 距离顶部200px
            getDialog().getWindow().setAttributes(params);
        }
    }
}
