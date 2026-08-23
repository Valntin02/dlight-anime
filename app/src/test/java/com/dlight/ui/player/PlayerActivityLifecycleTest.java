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
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer;

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

        ShadowAudioManager.AudioFocusRequest focusRequest = lastFocusRequest(controller.get());

        controller.pause().stop().destroy();
        activeController = null;

        assertFocusAbandoned(controller.get(), focusRequest);
    }

    @Test
    public void detailDestroyBeforePrepared_abandonsAudioFocus() throws Exception {
        ActivityController<DetailPlayer> controller = Robolectric
            .buildActivity(DetailPlayer.class)
            .create()
            .start()
            .resume()
            .visible();
        activeController = controller;
        GSYVideoPlayer player = controller.get().findViewById(R.id.detail_player);

        player.startPlayLogic();

        ShadowAudioManager.AudioFocusRequest focusRequest = lastFocusRequest(controller.get());

        controller.pause().stop().destroy();
        activeController = null;

        assertFocusAbandoned(controller.get(), focusRequest);
    }

    @Test
    public void playActivityDestroy_abandonsAudioFocus() throws Exception {
        ActivityController<PlayActivity> controller = Robolectric
            .buildActivity(PlayActivity.class)
            .create()
            .start()
            .resume();
        activeController = controller;
        ShadowAudioManager.AudioFocusRequest focusRequest = lastFocusRequest(controller.get());

        controller.pause().stop().destroy();
        activeController = null;

        assertFocusAbandoned(controller.get(), focusRequest);
    }

    @Test
    public void playTvActivityDestroy_abandonsAudioFocus() throws Exception {
        ActivityController<PlayTVActivity> controller = Robolectric
            .buildActivity(PlayTVActivity.class)
            .create()
            .start()
            .resume();
        activeController = controller;
        ShadowAudioManager.AudioFocusRequest focusRequest = lastFocusRequest(controller.get());

        controller.pause().stop().destroy();
        activeController = null;

        assertFocusAbandoned(controller.get(), focusRequest);
    }

    private static ShadowAudioManager.AudioFocusRequest lastFocusRequest(Context context) {
        AudioManager audioManager = (AudioManager) context.getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
        ShadowAudioManager.AudioFocusRequest focusRequest =
            shadowOf(audioManager).getLastAudioFocusRequest();
        assertNotNull(focusRequest);
        assertNotNull(focusRequest.listener);
        return focusRequest;
    }

    private static void assertFocusAbandoned(
        Context context,
        ShadowAudioManager.AudioFocusRequest focusRequest
    ) {
        AudioManager audioManager = (AudioManager) context.getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
        assertSame(
            focusRequest.listener,
            shadowOf(audioManager).getLastAbandonedAudioFocusListener()
        );
    }
}
