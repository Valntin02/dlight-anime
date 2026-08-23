package com.dlight.ui.player;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

import com.dlight.R;
import com.dlight.data.model.VodData;
import com.shuyu.gsyvideoplayer.GSYVideoManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAudioManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PlayerActivityLifecycleTest {
    private ActivityController<?> activeController;

    @Before
    public void setUp() {
        GSYVideoManager.releaseAllVideos();
        ShadowAudioManager.reset();
    }

    @After
    public void tearDown() throws Exception {
        if (activeController != null) {
            activeController.pause().stop().destroy();
            activeController = null;
        }
        GSYVideoManager.releaseAllVideos();
        ShadowAudioManager.reset();
    }

    @Test
    public void danmakuDestroyBeforePrepared_abandonsAudioFocus() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        VodData video = new VodData(
            1,
            "Leak regression",
            "",
            "https://example.com/video.m3u8",
            "",
            "1集",
            "2026",
            "",
            "1"
        );
        Intent intent = new Intent(app, DanmkuVideoActivity.class)
            .putExtra("video_data", video)
            .putExtra("currentEpisode", 1);
        ActivityController<DanmkuVideoActivity> controller = Robolectric
            .buildActivity(DanmkuVideoActivity.class, intent)
            .create()
            .start()
            .resume()
            .visible();
        activeController = controller;
        DanmakuVideoPlayer player = controller.get().findViewById(R.id.danmaku_player);

        player.startPlayLogic();

        AudioManager audioManager = (AudioManager) controller.get().getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
        ShadowAudioManager shadowAudioManager = shadowOf(audioManager);
        ShadowAudioManager.AudioFocusRequest focusRequest =
            shadowAudioManager.getLastAudioFocusRequest();
        assertNotNull(focusRequest);
        assertNotNull(focusRequest.listener);

        controller.pause().stop().destroy();
        activeController = null;

        assertSame(
            focusRequest.listener,
            shadowAudioManager.getLastAbandonedAudioFocusListener()
        );
    }
}
