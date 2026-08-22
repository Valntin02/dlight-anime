package com.dlight.ui.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;

import com.dlight.R;

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
    private ProgressBar progressBar;
    private TextView messageView;
    private Button retryButton;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        view = new LoadStateView(context);
        progressBar = view.findViewById(R.id.load_state_progress);
        messageView = view.findViewById(R.id.load_state_message);
        retryButton = view.findViewById(R.id.load_state_retry);
    }

    @Test
    public void initialStateIsHiddenAndLayoutIsCenteredVertical() {
        assertEquals(View.GONE, view.getVisibility());
        assertEquals(LinearLayout.VERTICAL, view.getOrientation());
        assertEquals(Gravity.CENTER, view.getGravity());
        assertEquals(3, view.getChildCount());
        assertTrue(progressBar != null);
        assertTrue(messageView != null);
        assertTrue(retryButton != null);
        assertTrue(retryButton.getMinimumWidth() >= dp(48));
        assertTrue(retryButton.getMinimumHeight() >= dp(48));
        assertEquals(context.getColor(R.color.dark_bg), retryButton.getCurrentTextColor());
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
        assertEquals(View.VISIBLE, progressBar.getVisibility());
        assertEquals("正在刷新", messageView.getText().toString());
        assertEquals(View.GONE, retryButton.getVisibility());
    }

    @Test
    public void emptyShowsMessageWithoutSpinnerOrRetry() {
        view.showEmpty("没有结果");

        assertEquals(View.VISIBLE, view.getVisibility());
        assertEquals(View.GONE, progressBar.getVisibility());
        assertEquals("没有结果", messageView.getText().toString());
        assertEquals(View.GONE, retryButton.getVisibility());
    }

    @Test
    public void errorShowsMessageAndRetryWithoutSpinner() {
        view.showError("网络异常");

        assertEquals(View.VISIBLE, view.getVisibility());
        assertEquals(View.GONE, progressBar.getVisibility());
        assertEquals("网络异常", messageView.getText().toString());
        assertEquals(View.VISIBLE, retryButton.getVisibility());
    }

    @Test
    public void nullAndBlankMessagesUseChineseDefaults() {
        view.showLoading(null);
        assertEquals(context.getString(R.string.load_state_loading_default), messageView.getText().toString());

        view.showEmpty("   ");
        assertEquals(context.getString(R.string.load_state_empty_default), messageView.getText().toString());

        view.showError("\t");
        assertEquals(context.getString(R.string.load_state_error_default), messageView.getText().toString());
    }

    @Test
    public void retryButtonInvokesListener() {
        AtomicBoolean clicked = new AtomicBoolean(false);
        view.setOnRetryListener(ignored -> clicked.set(true));
        view.showError(null);

        assertTrue(retryButton.performClick());
        assertTrue(clicked.get());
    }

    @Test
    public void errorStateIsDiscoverableByAccessibilityServices() {
        view.showError(null);

        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, view.getAccessibilityLiveRegion());
        assertEquals(
            context.getString(R.string.load_state_error_default),
            view.getContentDescription().toString()
        );
        assertTrue(retryButton.isFocusable());
        assertEquals(
            context.getString(R.string.load_state_retry),
            retryButton.getContentDescription().toString()
        );
    }

    @Test
    public void announcesOnlyAfterViewIsAttached() {
        RecordingLoadStateView recordingView = new RecordingLoadStateView(context);
        recordingView.showLoading(null);
        assertNull(recordingView.announcedMessage);

        Activity activity = org.robolectric.Robolectric.buildActivity(Activity.class).setup().get();
        activity.setContentView(recordingView);
        recordingView.showEmpty(null);

        assertEquals(
            context.getString(R.string.load_state_empty_default),
            recordingView.announcedMessage.toString()
        );
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

    private static final class RecordingLoadStateView extends LoadStateView {
        private CharSequence announcedMessage;

        private RecordingLoadStateView(Context context) {
            super(context);
        }

        @Override
        public void announceForAccessibility(CharSequence text) {
            announcedMessage = text;
        }
    }
}
