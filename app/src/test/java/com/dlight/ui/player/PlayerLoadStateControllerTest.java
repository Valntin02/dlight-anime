package com.dlight.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import com.dlight.R;
import com.dlight.ui.widget.LoadStateView;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class PlayerLoadStateControllerTest {
    private View player;
    private LoadStateView overlay;
    private FrameLayout root;
    private PlayerLoadStateController controller;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        root = new FrameLayout(context);
        player = new View(context);
        overlay = new LoadStateView(context);
        root.addView(player);
        root.addView(overlay);
        root.addView(new View(context));
        controller = new PlayerLoadStateController(player, overlay);
    }

    @Test
    public void showError_blocksPlayerAccessibilityAndConsumesBlankTouches() {
        controller.showError("播放失败", () -> { });

        assertEquals(View.VISIBLE, overlay.getVisibility());
        assertTrue(overlay.isClickable());
        assertTrue(overlay.isFocusable());
        assertEquals(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
            player.getImportantForAccessibility()
        );
        assertSame(overlay, root.getChildAt(root.getChildCount() - 1));
    }

    @Test
    public void hide_restoresPlayerAccessibilityAndHidesOverlay() {
        controller.showLoading("正在查找");

        controller.hide();

        assertEquals(View.GONE, overlay.getVisibility());
        assertEquals(
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
            player.getImportantForAccessibility()
        );
    }

    @Test
    public void retry_restoresPlayerBeforeRunningAction() {
        AtomicBoolean retriedAfterRestore = new AtomicBoolean(false);
        controller.showError("播放失败", () -> retriedAfterRestore.set(
            overlay.getVisibility() == View.GONE
                && player.getImportantForAccessibility()
                    == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        ));

        Button retryButton = overlay.findViewById(R.id.load_state_retry);
        assertTrue(retryButton.performClick());

        assertTrue(retriedAfterRestore.get());
    }
}
