package com.dlight.ui.player;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;

import com.dlight.R;
import com.dlight.data.model.VodData;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer;
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
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PlayerActivityLifecycleTest {
    private ActivityController<?> activeController;

    @Before
    public void setUp() {
        GSYVideoManager.releaseAllVideos();
        ShadowAudioManager.reset();
        Context app = RuntimeEnvironment.getApplication();
        ConnectivityManager connectivityManager = (ConnectivityManager) app
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifi = ShadowNetworkInfo.newInstance(
            NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI,
            0,
            true,
            NetworkInfo.State.CONNECTED
        );
        ReflectionHelpers.setField(wifi, "mTypeName", "WIFI");
        ((ShadowConnectivityManager) shadowOf(connectivityManager))
            .setActiveNetworkInfo(wifi);
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
    public void danmakuDestroyAfterPrepared_abandonsAudioFocus() throws Exception {
        ActivityController<DanmkuVideoActivity> controller = createDanmakuActivity();
        DanmakuVideoPlayer player = controller.get().findViewById(R.id.danmaku_player);

        player.startPlayLogic();
        ShadowAudioManager.AudioFocusRequest focusRequest = lastFocusRequest(controller.get());
        player.onPrepared();

        controller.pause().stop().destroy();
        activeController = null;

        assertFocusAbandoned(controller.get(), focusRequest);
    }

    @Test
    public void danmakuFullscreenExitAndDestroy_abandonsAudioFocus() throws Exception {
        ActivityController<DanmkuVideoActivity> controller = createDanmakuActivity();
        DanmakuVideoPlayer player = controller.get().findViewById(R.id.danmaku_player);

        GSYBaseVideoPlayer fullPlayer = player.startWindowFullscreen(
            controller.get(),
            true,
            true
        );

        assertNotNull(fullPlayer);
        assertSame(fullPlayer, player.getFullWindowPlayer());
        assertTrue(GSYVideoManager.backFromWindowFull(controller.get()));
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(player.getFullWindowPlayer());

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

    @Test
    public void simplePlayerDestroy_abandonsAudioFocus() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SimplePlayer.class)
            .putExtra("video_path", "android.resource://com.dlight/raw/test")
            .putExtra("video_name", "Lifecycle regression");
        ActivityController<SimplePlayer> controller = Robolectric
            .buildActivity(SimplePlayer.class, intent)
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
    public void destroyingPreviousActivity_doesNotReleaseNewPlayerFocus() throws Exception {
        ActivityController<DanmkuVideoActivity> previous = createDanmakuActivity(false);
        DanmakuVideoPlayer previousPlayer = previous.get().findViewById(R.id.danmaku_player);
        previousPlayer.startPlayLogic();
        ShadowAudioManager.AudioFocusRequest previousFocus = lastFocusRequest(previous.get());

        ActivityController<DanmkuVideoActivity> current = createDanmakuActivity(false);
        DanmakuVideoPlayer currentPlayer = current.get().findViewById(R.id.danmaku_player);
        currentPlayer.startPlayLogic();
        ShadowAudioManager.AudioFocusRequest currentFocus = lastFocusRequest(current.get());

        assertFocusAbandoned(current.get(), previousFocus);
        assertNotSame(
            currentFocus.listener,
            lastAbandonedFocusListener(current.get())
        );

        previous.pause().stop().destroy();

        assertNotSame(
            currentFocus.listener,
            lastAbandonedFocusListener(current.get())
        );

        current.pause().stop().destroy();
        activeController = null;

        assertFocusAbandoned(current.get(), currentFocus);
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

    private ActivityController<DanmkuVideoActivity> createDanmakuActivity() {
        return createDanmakuActivity(true);
    }

    private ActivityController<DanmkuVideoActivity> createDanmakuActivity(boolean visible) {
        Context app = RuntimeEnvironment.getApplication();
        VodData video = new VodData(
            2,
            "Lifecycle regression",
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
            .resume();
        if (visible) {
            controller.visible();
        }
        activeController = controller;
        return controller;
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

    private static AudioManager.OnAudioFocusChangeListener lastAbandonedFocusListener(
        Context context
    ) {
        AudioManager audioManager = (AudioManager) context.getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
        return shadowOf(audioManager).getLastAbandonedAudioFocusListener();
    }
}
