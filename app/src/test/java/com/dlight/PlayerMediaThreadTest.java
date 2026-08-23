package com.dlight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Handler;
import android.os.Looper;

import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.player.IPlayerManager;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(application = DlightApplication.class, sdk = 34)
public class PlayerMediaThreadTest {

    @Test
    public void applicationConfiguresDedicatedMediaLooper() {
        Looper mediaLooper = GSYVideoManager.instance().getLooper();

        assertNotNull(mediaLooper);
        assertNotSame(Looper.getMainLooper(), mediaLooper);
        assertEquals("dlight-gsy-media", mediaLooper.getThread().getName());
    }

    @Test
    public void blockingRelease_runsOffMainAndLeavesMainResponsive() throws Exception {
        GSYVideoManager manager = GSYVideoManager.instance();
        IPlayerManager original = ReflectionHelpers.getField(manager, "playerManager");
        CountDownLatch releaseStarted = new CountDownLatch(1);
        CountDownLatch allowRelease = new CountDownLatch(1);
        CountDownLatch releaseFinished = new CountDownLatch(1);
        AtomicReference<Thread> releaseThread = new AtomicReference<>();
        AtomicBoolean mainMarkerRan = new AtomicBoolean(false);
        IPlayerManager blockingPlayer = (IPlayerManager) Proxy.newProxyInstance(
            IPlayerManager.class.getClassLoader(),
            new Class<?>[]{IPlayerManager.class},
            (proxy, method, args) -> {
                if ("release".equals(method.getName())) {
                    releaseThread.set(Thread.currentThread());
                    releaseStarted.countDown();
                    allowRelease.await(5, TimeUnit.SECONDS);
                    releaseFinished.countDown();
                }
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == long.class) return 0L;
                return null;
            }
        );

        try {
            ReflectionHelpers.setField(manager, "playerManager", blockingPlayer);

            manager.releaseMediaPlayer();

            assertTrue(releaseStarted.await(2, TimeUnit.SECONDS));
            assertNotSame(Looper.getMainLooper().getThread(), releaseThread.get());

            new Handler(Looper.getMainLooper()).post(() -> mainMarkerRan.set(true));
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(mainMarkerRan.get());

            allowRelease.countDown();
            assertTrue(releaseFinished.await(2, TimeUnit.SECONDS));
        } finally {
            allowRelease.countDown();
            ReflectionHelpers.setField(manager, "playerManager", original);
        }
    }
}
