package com.dlight.ui.player;

import android.view.View;

import com.dlight.ui.widget.LoadStateView;

final class PlayerLoadStateController {
    private final View player;
    private final LoadStateView overlay;

    PlayerLoadStateController(View player, LoadStateView overlay) {
        this.player = player;
        this.overlay = overlay;
    }

    void showLoading(String message) {
        prepareToShow();
        overlay.showLoading(message);
        overlay.bringToFront();
    }

    void showError(String message, Runnable retryAction) {
        prepareToShow();
        overlay.setOnRetryListener(ignored -> {
            hide();
            retryAction.run();
        });
        overlay.showError(message);
        overlay.bringToFront();
    }

    void hide() {
        overlay.hide();
        player.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    private void prepareToShow() {
        overlay.setClickable(true);
        overlay.setFocusable(true);
        player.setImportantForAccessibility(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        );
    }
}
