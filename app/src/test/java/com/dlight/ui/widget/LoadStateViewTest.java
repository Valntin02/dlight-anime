package com.dlight.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
public class LoadStateViewTest {
    private Context context;
    private LoadStateView view;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        view = new LoadStateView(context);
    }

    @Test
    public void initialStateIsHiddenAndLayoutIsCenteredVertical() {
        assertEquals(View.GONE, view.getVisibility());
        assertEquals(LinearLayout.VERTICAL, view.getOrientation());
        assertEquals(Gravity.CENTER, view.getGravity());
        assertEquals(3, view.getChildCount());
        assertTrue(view.getRetryButton().getMinimumWidth() >= dp(48));
        assertTrue(view.getRetryButton().getMinimumHeight() >= dp(48));
    }

    @Test
    public void attributeConstructorsCreateTheSameInitialLayout() {
        LoadStateView withAttributes = new LoadStateView(context, null);
        LoadStateView withStyle = new LoadStateView(context, null, 0);

        assertEquals(View.GONE, withAttributes.getVisibility());
        assertEquals(3, withAttributes.getChildCount());
        assertEquals(View.GONE, withStyle.getVisibility());
        assertEquals(3, withStyle.getChildCount());
    }

    @Test
    public void loadingShowsSpinnerAndMessageWithoutRetry() {
        view.showLoading("正在刷新");

        assertEquals(View.VISIBLE, view.getVisibility());
        assertEquals(View.VISIBLE, view.getProgressBar().getVisibility());
        assertEquals("正在刷新", view.getMessageView().getText().toString());
        assertEquals(View.GONE, view.getRetryButton().getVisibility());
    }

    @Test
    public void emptyShowsMessageWithoutSpinnerOrRetry() {
        view.showEmpty("没有结果");

        assertEquals(View.VISIBLE, view.getVisibility());
        assertEquals(View.GONE, view.getProgressBar().getVisibility());
        assertEquals("没有结果", view.getMessageView().getText().toString());
        assertEquals(View.GONE, view.getRetryButton().getVisibility());
    }

    @Test
    public void errorShowsMessageAndRetryWithoutSpinner() {
        view.showError("网络异常");

        assertEquals(View.VISIBLE, view.getVisibility());
        assertEquals(View.GONE, view.getProgressBar().getVisibility());
        assertEquals("网络异常", view.getMessageView().getText().toString());
        assertEquals(View.VISIBLE, view.getRetryButton().getVisibility());
    }

    @Test
    public void nullAndBlankMessagesUseChineseDefaults() {
        view.showLoading(null);
        assertEquals("加载中…", view.getMessageView().getText().toString());

        view.showEmpty("   ");
        assertEquals("暂无内容", view.getMessageView().getText().toString());

        view.showError("\t");
        assertEquals("加载失败，请重试", view.getMessageView().getText().toString());
    }

    @Test
    public void retryButtonInvokesListener() {
        AtomicBoolean clicked = new AtomicBoolean(false);
        view.setOnRetryListener(ignored -> clicked.set(true));
        view.showError(null);

        assertTrue(view.getRetryButton().performClick());
        assertTrue(clicked.get());
    }

    @Test
    public void hideMakesWholeViewGone() {
        view.showError(null);
        view.hide();

        assertEquals(View.GONE, view.getVisibility());
        assertFalse(view.isShown());
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
