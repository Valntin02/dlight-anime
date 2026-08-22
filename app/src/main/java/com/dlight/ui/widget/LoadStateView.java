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
        setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        int contentPadding = dp(16);
        setPadding(contentPadding, contentPadding, contentPadding, contentPadding);

        progressBar = new ProgressBar(context);
        progressBar.setId(R.id.load_state_progress);
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(context.getColor(R.color.brand_500)));
        addView(progressBar, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        messageView = new TextView(context);
        messageView.setId(R.id.load_state_message);
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
        retryButton.setId(R.id.load_state_retry);
        retryButton.setText(R.string.load_state_retry);
        retryButton.setTextColor(context.getColor(R.color.dark_bg));
        retryButton.setTextSize(14);
        retryButton.setAllCaps(false);
        retryButton.setFocusable(true);
        retryButton.setContentDescription(context.getString(R.string.load_state_retry));
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
        show(message, R.string.load_state_loading_default, true, false);
    }

    public void showEmpty(String message) {
        show(message, R.string.load_state_empty_default, false, false);
    }

    public void showError(String message) {
        show(message, R.string.load_state_error_default, false, true);
    }

    public void hide() {
        setVisibility(GONE);
    }

    public void setOnRetryListener(OnClickListener listener) {
        retryButton.setOnClickListener(listener);
    }

    private void show(
        String message,
        int defaultMessageResource,
        boolean showProgress,
        boolean showRetry
    ) {
        String resolvedMessage = isBlank(message)
            ? getResources().getString(defaultMessageResource)
            : message;
        messageView.setText(resolvedMessage);
        setContentDescription(resolvedMessage);
        progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(showRetry ? View.VISIBLE : View.GONE);
        setVisibility(VISIBLE);
        if (isAttachedToWindow()) {
            announceForAccessibility(resolvedMessage);
        }
    }

    private boolean isBlank(String message) {
        return message == null || message.trim().isEmpty();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
