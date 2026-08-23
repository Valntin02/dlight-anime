# Player Media Thread Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move serial GSY prepare/release work off Android's main looper so immediate player teardown cannot freeze navigation or trigger an input ANR.

**Architecture:** Port GSY's configurable media-looper seam into the vendored v10 manager, then configure one process-lifetime `HandlerThread` from `DlightApplication` before playback begins. Keep UI callbacks on the main looper and preserve owner-aware player release, audio-focus cleanup, and LeakCanary.

**Tech Stack:** Java, Android `HandlerThread` / `Looper`, GSYVideoPlayer, IJK, Robolectric 4.13, JUnit 4, Gradle 8.7 / AGP 8.6.1.

---

### Task 1: Prove release work must not run on the main looper

**Files:**
- Create: `app/src/test/java/com/dlight/PlayerMediaThreadTest.java`
- Read: `gsyVideoPlayer-java/src/main/java/com/shuyu/gsyvideoplayer/GSYVideoBaseManager.java`
- Read: `app/src/main/java/com/dlight/DlightApplication.java`

- [ ] **Step 1: Add the missing-looper and blocking-release tests**

Create `PlayerMediaThreadTest.java`:

```java
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
```

- [ ] **Step 2: Run the focused tests and record the red result**

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="/Users/yg02/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest \
  --tests 'com.dlight.PlayerMediaThreadTest' \
  --rerun-tasks --console=plain
```

Expected: test compilation fails because the vendored manager has no `getLooper()`. This is the first red state. After adding only the API seam in Task 2, the tests must still fail until `DlightApplication` configures a non-main looper.

### Task 2: Port the configurable media looper and configure the App thread

**Files:**
- Modify: `gsyVideoPlayer-java/src/main/java/com/shuyu/gsyvideoplayer/GSYVideoBaseManager.java:55-60,150-153,760+`
- Modify: `app/src/main/java/com/dlight/DlightApplication.java:1-64`
- Test: `app/src/test/java/com/dlight/PlayerMediaThreadTest.java`

- [ ] **Step 1: Add the official-compatible looper seam**

In `GSYVideoBaseManager`, add the field next to `mMediaHandler`:

```java
protected Looper mLooper;
```

Replace `init()` with:

```java
protected void init() {
    initMediaHandler();
    mainThreadHandler = new Handler(Looper.getMainLooper());
}

protected void initMediaHandler() {
    mMediaHandler = new MediaHandler(mLooper == null ? Looper.getMainLooper() : mLooper);
}
```

Add these public methods at the end of the class:

```java
public Looper getLooper() {
    return mLooper;
}

/** Configure before the first prepare/release command. */
public void setLooper(Looper looper) {
    mLooper = looper;
    if (mMediaHandler != null) {
        initMediaHandler();
    }
}
```

- [ ] **Step 2: Configure one process-lifetime HandlerThread**

Add imports to `DlightApplication`:

```java
import android.os.HandlerThread;

import com.shuyu.gsyvideoplayer.GSYVideoManager;
```

Add a field:

```java
private HandlerThread playerMediaThread;
```

Replace `initPlayerConfig()` with:

```java
private void initPlayerConfig() {
    PlayerFactory.setPlayManager(IjkPlayerManager.class);

    playerMediaThread = new HandlerThread("dlight-gsy-media");
    playerMediaThread.start();
    GSYVideoManager.instance().setLooper(playerMediaThread.getLooper());

    Log.d(TAG, "Player config initialized");
}
```

- [ ] **Step 3: Run the focused tests and record green**

Run the Task 1 focused command.

Expected: both tests pass. The blocking fake release executes on `dlight-gsy-media` while a main-looper marker executes before release is unblocked.

- [ ] **Step 4: Commit the media-thread implementation**

```bash
git add \
  gsyVideoPlayer-java/src/main/java/com/shuyu/gsyvideoplayer/GSYVideoBaseManager.java \
  app/src/main/java/com/dlight/DlightApplication.java \
  app/src/test/java/com/dlight/PlayerMediaThreadTest.java
git diff --cached --check
git commit -m "fix: move player teardown off main thread"
```

### Task 3: Audit equivalent blocking paths and lifecycle regressions

**Files:**
- Verify: `gsyVideoPlayer-java/src/main/java/com/shuyu/gsyvideoplayer/GSYVideoBaseManager.java`
- Test: `app/src/test/java/com/dlight/ui/player/PlayerActivityLifecycleTest.java`

- [ ] **Step 1: Verify every blocking release stays on the serial media handler**

```bash
rg -n -C 4 'playerManager\.(release|initVideoPlayer)|HANDLER_(PREPARE|RELEASE|RELEASE_SURFACE)' \
  gsyVideoPlayer-java/src/main/java/com/shuyu/gsyvideoplayer/GSYVideoBaseManager.java
```

Expected: player replacement release and `initVideoPlayer()` are inside `initVideo()` reached from `HANDLER_PREPARE`; final release is inside `HANDLER_RELEASE`; surface release is inside `HANDLER_RELEASE_SURFACE`.

- [ ] **Step 2: Verify UI callbacks remain on the main looper**

```bash
rg -n 'mainThreadHandler = new Handler\(Looper\.getMainLooper\(\)\)' \
  gsyVideoPlayer-java/src/main/java/com/shuyu/gsyvideoplayer/GSYVideoBaseManager.java
```

Expected: exactly one match.

- [ ] **Step 3: Re-run all player lifecycle tests**

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="/Users/yg02/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest \
  --tests 'com.dlight.ui.player.PlayerActivityLifecycleTest' \
  --rerun-tasks --console=plain
```

Expected: all eight audio-focus, prepared, fullscreen, and owner-race tests pass.

### Task 4: Run the complete automated verification matrix

**Files:**
- Verify only; no planned source changes.

- [ ] **Step 1: Run Debug/Release tests, lint, and Debug assembly**

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="/Users/yg02/Library/Android/sdk" \
./gradlew \
  :app:testDebugUnitTest \
  :app:testReleaseUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  -PDLIGHT_RELEASE_API_BASE_URL=https://example.invalid/ \
  --rerun-tasks --console=plain
```

Expected: `BUILD SUCCESSFUL`; both variants have zero test failures/errors, lint has zero errors, and the Debug APK exists.

- [ ] **Step 2: Confirm LeakCanary and owner-aware release remain intact**

```bash
rg -n 'debugImplementation dataDependencies\.leakcanary' app/build.gradle
rg -n 'hostContext|setContext\(this\)' \
  app/src/main/java/com/dlight/ui/player/DanmakuVideoPlayer.java \
  app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java
```

Expected: LeakCanary matches once; the redundant-context scan returns no matches.

### Task 5: Verify the original ANR path on the emulator

**Files:**
- Install: `app/build/outputs/apk/debug/app-debug.apk`
- Do not clear App data.

- [ ] **Step 1: Install without uninstalling and clear only logs**

```bash
ADB="/Users/yg02/Library/Android/sdk/platform-tools/adb"
"$ADB" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s emulator-5554 logcat -c
```

- [ ] **Step 2: Run five effective immediate-return/reclick cycles**

For each cycle, explicitly start `com.dlight/.ui.activity.Main2Activity`, open a card, tap `R.id.start`, confirm the process generated a new `requestAudioFocus()`, press Back immediately, then tap another card. Do not use `monkey`, because LeakCanary exposes another launcher Activity in the same package.

Record the time from the second card tap to the next `DanmkuVideoActivity` becoming top-resumed. Require five effective samples and keep each response below one second.

- [ ] **Step 3: Verify absence of ANR, leak, and focus residue**

```bash
"$ADB" -s emulator-5554 logcat -d -v brief | \
  rg 'Input dispatching timed out|ANR in com\.dlight|APPLICATION LEAKS|dumping heap|35f74ec1975a1f85981ab3d0d6769913e2741'
"$ADB" -s emulator-5554 shell dumpsys audio | \
  sed -n '/Audio Focus stack entries/,/No external focus policy/p'
```

Expected: log scan returns no matches in the fresh window; final audio-focus stack contains no `com.dlight` owner.

### Task 6: Completion audit

**Files:**
- Verify all Task 2 implementation and test files.

- [ ] **Step 1: Audit the specification**

Confirm:

- blocking fake release runs off main and main remains responsive;
- media commands remain serial;
- all existing lifecycle/leak tests pass;
- five effective device samples respond below one second;
- no fresh ANR or LeakCanary signature;
- final focus stack is empty;
- App/emulator data was not cleared.

- [ ] **Step 2: Inspect final repository state**

```bash
git diff --check 17e87e3..HEAD
git status --short
git log -6 --oneline
```

Expected: only the known pre-existing `.superpowers/` path may remain untracked; implementation is committed and diff check is clean.
