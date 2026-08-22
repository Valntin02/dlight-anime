package com.dlight.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.dlight.R;

public class LoadStateView extends LinearLayout {
    private static final String DEFAULT_LOADING_MESSAGE = "加载中…";
    private static final String DEFAULT_EMPTY_MESSAGE = "暂无内容";
    private static final String DEFAULT_ERROR_MESSAGE = "加载失败，请重试";

    private final ProgressBar progressBar;
    private final TextView messageView;
    private final Button retryButton;

    public LoadStateView(Context context) {
        this(context, null);
    }

    public LoadStateView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LoadStateView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        int contentPadding = dp(16);
        setPadding(contentPadding, contentPadding, contentPadding, contentPadding);

        progressBar = new ProgressBar(context);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(context.getColor(R.color.brand_500)));
        addView(progressBar, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        messageView = new TextView(context);
        messageView.setGravity(Gravity.CENTER);
        messageView.setTextColor(context.getColor(R.color.text_secondary));
        messageView.setTextSize(14);
        LayoutParams messageParams = new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        );
        messageParams.topMargin = dp(12);
        addView(messageView, messageParams);

        retryButton = new Button(context);
        retryButton.setText("重试");
        retryButton.setTextColor(context.getColor(R.color.text_on_brand));
        retryButton.setTextSize(14);
        retryButton.setAllCaps(false);
        retryButton.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.brand_500)));
        retryButton.setMinWidth(dp(48));
        retryButton.setMinHeight(dp(48));
        LayoutParams retryParams = new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        );
        retryParams.topMargin = dp(8);
        addView(retryButton, retryParams);

        setVisibility(GONE);
    }

    public void showLoading(String message) {
        show(message, DEFAULT_LOADING_MESSAGE, true, false);
    }

    public void showEmpty(String message) {
        show(message, DEFAULT_EMPTY_MESSAGE, false, false);
    }

    public void showError(String message) {
        show(message, DEFAULT_ERROR_MESSAGE, false, true);
    }

    public void hide() {
        setVisibility(GONE);
    }

    public void setOnRetryListener(OnClickListener listener) {
        retryButton.setOnClickListener(listener);
    }

    ProgressBar getProgressBar() {
        return progressBar;
    }

    TextView getMessageView() {
        return messageView;
    }

    Button getRetryButton() {
        return retryButton;
    }

    private void show(
        String message,
        String defaultMessage,
        boolean showProgress,
        boolean showRetry
    ) {
        messageView.setText(isBlank(message) ? defaultMessage : message);
        progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(showRetry ? View.VISIBLE : View.GONE);
        setVisibility(VISIBLE);
    }

    private boolean isBlank(String message) {
        return message == null || message.trim().isEmpty();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
