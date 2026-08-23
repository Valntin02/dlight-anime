# Player Audio Focus Leak Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure every GSY playback Activity abandons its own audio focus when destroyed, including destruction before `onPrepared()`, without allowing an older Activity to release a newer player.

**Architecture:** Keep audio-focus ownership in the existing GSY player and fix the Activity lifecycle boundary that currently skips teardown. Add Robolectric integration tests against audio-focus request/abandon events, then make all declared playback Activities unconditionally call their local player `release()`; its existing `isCurrentMediaListener()` guard preserves newer-owner focus. LeakCanary remains enabled and unchanged.

**Tech Stack:** Java, Android lifecycle, GSYVideoPlayer, AudioManager, Robolectric 4.13, JUnit 4, Gradle 8.7 / AGP 8.6.1.

---

### Task 1: Reproduce the pre-prepared audio-focus leak

**Files:**
- Create: `app/src/test/java/com/dlight/ui/player/PlayerActivityLifecycleTest.java`
- Read: `app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java`
- Read: `gsyVideoPlayer-java/src/main/java/com/shuyu/gsyvideoplayer/video/base/GSYVideoView.java`

- [ ] **Step 1: Write the failing Robolectric integration test**

Create `PlayerActivityLifecycleTest.java` with this complete initial test:

```java
package com.dlight.ui.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

import com.dlight.R;
import com.dlight.data.model.VodData;
import com.shuyu.gsyvideoplayer.GSYVideoManager;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PlayerActivityLifecycleTest {
    private ActivityController<?> activeController;

    @After
    public void tearDown() throws Exception {
        if (activeController != null) {
            activeController.pause().stop().destroy();
            activeController = null;
        }
        GSYVideoManager.releaseAllVideos();
        audioFocusRequests(RuntimeEnvironment.getApplication()).clear();
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

        assertFalse(audioFocusRequests(controller.get()).isEmpty());

        controller.pause().stop().destroy();
        activeController = null;

        assertTrue(audioFocusRequests(controller.get()).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> audioFocusRequests(Context context) throws Exception {
        AudioManager audioManager = (AudioManager) context.getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
        Field field = AudioManager.class.getDeclaredField("mAudioFocusIdListenerMap");
        field.setAccessible(true);
        return (Map<String, ?>) field.get(audioManager);
    }
}
```

- [ ] **Step 2: Run the focused test and capture the red result**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="/Users/yg02/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest \
  --tests 'com.dlight.ui.player.PlayerActivityLifecycleTest.danmakuDestroyBeforePrepared_abandonsAudioFocus' \
  --rerun-tasks --console=plain
```

Expected: the test reaches the final assertion and fails because `AudioManager.mAudioFocusIdListenerMap` still contains the GSY listener after `DanmkuVideoActivity.onDestroy()`.

- [ ] **Step 3: If the test cannot reach the assertion, adjust only the fixture**

Keep the production code unchanged. If Robolectric needs its own URL, lifecycle ordering, or idle call, change only the test fixture until it proves these two facts in one run:

```java
assertFalse(audioFocusRequests(controller.get()).isEmpty());
controller.pause().stop().destroy();
assertTrue(audioFocusRequests(controller.get()).isEmpty());
```

The first assertion must pass and the second must fail before implementation begins.

### Task 2: Fix the confirmed Danmaku lifecycle gap

**Files:**
- Modify: `app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java:142-148,280-295`
- Modify: `app/src/main/java/com/dlight/ui/player/DanmakuVideoPlayer.java:65-68,126-133`
- Test: `app/src/test/java/com/dlight/ui/player/PlayerActivityLifecycleTest.java`

- [ ] **Step 1: Remove the redundant Activity reference**

Delete this call from `DanmkuVideoActivity`:

```java
binding.danmakuPlayer.setContext(this);
```

Delete this field and its setter/getter from `DanmakuVideoPlayer`:

```java
private Context hostContext;

public void setContext(Context context) {
    this.hostContext = context;
}

@Nullable
public Context getHostContext() {
    return hostContext;
}
```

- [ ] **Step 2: Replace conditional teardown with unconditional manager release**

Replace `DanmkuVideoActivity.onDestroy()` with:

```java
@Override
protected void onDestroy() {
    isDestory = true;
    if (recoveryTracker != null) {
        recoveryTracker.destroy();
    }
    if (activeRecoveryCall != null) {
        activeRecoveryCall.cancel();
        activeRecoveryCall = null;
    }
    getCurPlay().setVideoAllCallBack(null);
    getCurPlay().release();
    if (orientationUtils != null) {
        orientationUtils.releaseListener();
    }
    super.onDestroy();
}
```

This deliberately does not inspect `isPlay`: audio focus is acquired during preparation, before `isPlay` becomes true.

- [ ] **Step 3: Run the focused test and capture the green result**

Run the same focused command from Task 1.

Expected: `BUILD SUCCESSFUL`; the focus map is non-empty before destruction and empty afterward.

- [ ] **Step 4: Commit the confirmed-path fix**

```bash
git add \
  app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java \
  app/src/main/java/com/dlight/ui/player/DanmakuVideoPlayer.java \
  app/src/test/java/com/dlight/ui/player/PlayerActivityLifecycleTest.java
git diff --cached --check
git commit -m "fix: release player focus during early teardown"
```

### Task 3: Close the same lifecycle gap in sibling player Activities

**Files:**
- Modify: `app/src/main/java/com/dlight/ui/player/DetailPlayer.java:513-521`
- Modify: `app/src/main/java/com/dlight/ui/player/PlayActivity.java:140-146`
- Modify: `app/src/main/java/com/dlight/ui/player/PlayTVActivity.java:140-146`
- Modify: `app/src/main/java/com/dlight/ui/player/SimplePlayer.java:96-102`
- Modify: `app/src/test/java/com/dlight/ui/player/PlayerActivityLifecycleTest.java`

- [ ] **Step 1: Add sibling lifecycle tests**

Add these tests and helpers to `PlayerActivityLifecycleTest`:

```java
@Test
public void detailDestroyBeforePrepared_abandonsAudioFocus() throws Exception {
    ActivityController<DetailPlayer> controller = Robolectric
        .buildActivity(DetailPlayer.class)
        .create()
        .start()
    .resume()
    .visible();
    activeController = controller;
    com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer player =
        controller.get().findViewById(R.id.detail_player);
    player.startPlayLogic();
    assertFalse(audioFocusRequests(controller.get()).isEmpty());

    controller.pause().stop().destroy();
    activeController = null;

    assertTrue(audioFocusRequests(controller.get()).isEmpty());
}

@Test
public void playActivityDestroy_abandonsAudioFocus() throws Exception {
    ActivityController<PlayActivity> controller = Robolectric
        .buildActivity(PlayActivity.class)
        .create()
        .start()
        .resume()
        .visible();
    activeController = controller;
    assertFalse(audioFocusRequests(controller.get()).isEmpty());

    controller.pause().stop().destroy();
    activeController = null;

    assertTrue(audioFocusRequests(controller.get()).isEmpty());
}

@Test
public void playTvActivityDestroy_abandonsAudioFocus() throws Exception {
    ActivityController<PlayTVActivity> controller = Robolectric
        .buildActivity(PlayTVActivity.class)
        .create()
        .start()
        .resume()
        .visible();
    activeController = controller;
    assertFalse(audioFocusRequests(controller.get()).isEmpty());

    controller.pause().stop().destroy();
    activeController = null;

    assertTrue(audioFocusRequests(controller.get()).isEmpty());
}
```

- [ ] **Step 2: Run the sibling tests and confirm they fail on teardown**

Run:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="/Users/yg02/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest \
  --tests 'com.dlight.ui.player.PlayerActivityLifecycleTest' \
  --rerun-tasks --console=plain
```

Expected: `DetailPlayer`, `PlayActivity`, and `PlayTVActivity` retain their focus request after destruction with the current implementation.

- [ ] **Step 3: Make every sibling Activity release before `super.onDestroy()`**

Use this exact teardown shape in `DetailPlayer`, selecting `getCurPlay()`:

```java
@Override
protected void onDestroy() {
    getCurPlay().setVideoAllCallBack(null);
    getCurPlay().release();
    if (orientationUtils != null) {
        orientationUtils.releaseListener();
    }
    super.onDestroy();
}
```

Use this shape in `PlayActivity`, selecting `binding.videoPlayer`:

```java
@TargetApi(Build.VERSION_CODES.KITKAT)
@Override
protected void onDestroy() {
    binding.videoPlayer.setVideoAllCallBack(null);
    binding.videoPlayer.release();
    if (orientationUtils != null) {
        orientationUtils.releaseListener();
    }
    super.onDestroy();
}
```

Use this shape in `PlayTVActivity`, selecting `binding.videoPlayerTv`:

```java
@TargetApi(Build.VERSION_CODES.KITKAT)
@Override
protected void onDestroy() {
    binding.videoPlayerTv.setVideoAllCallBack(null);
    binding.videoPlayerTv.release();
    if (orientationUtils != null) {
        orientationUtils.releaseListener();
    }
    super.onDestroy();
}
```

Use this shape in `SimplePlayer`, selecting `videoPlayer`:

```java
@Override
protected void onDestroy() {
    videoPlayer.setVideoAllCallBack(null);
    videoPlayer.release();
    if (orientationUtils != null) {
        orientationUtils.releaseListener();
    }
    super.onDestroy();
}
```

- [ ] **Step 4: Run all lifecycle tests**

Run the focused class command from Step 2.

Expected: all four tests pass; every focus map is empty after destruction.

- [ ] **Step 5: Commit sibling lifecycle fixes**

```bash
git add \
  app/src/main/java/com/dlight/ui/player/DetailPlayer.java \
  app/src/main/java/com/dlight/ui/player/PlayActivity.java \
  app/src/main/java/com/dlight/ui/player/PlayTVActivity.java \
  app/src/test/java/com/dlight/ui/player/PlayerActivityLifecycleTest.java
git diff --cached --check
git commit -m "fix: release shared player from every owner"
```

### Task 4: Run the project verification matrix

**Files:**
- Verify only; no planned source changes.

- [ ] **Step 1: Run Debug and Release unit tests, app lint, and Debug assembly**

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME="/Users/yg02/Library/Android/sdk" \
./gradlew \
  :app:testDebugUnitTest \
  :app:testReleaseUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  -PDLIGHT_RELEASE_API_BASE_URL=https://example.invalid/ \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`, both test variants report zero failures/errors, lint reports zero errors, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 2: Verify LeakCanary remains active only in Debug**

```bash
rg -n 'debugImplementation dataDependencies\.leakcanary' app/build.gradle
rg -n 'leakcanary.*2\.7' gradle/dependencies.gradle
```

Expected: both commands match the existing declarations; no LeakCanary configuration or exclusion file was added.

- [ ] **Step 3: Verify the source invariant**

```bash
rg -n -U 'onDestroy\(\)[\s\S]{0,500}if \(isPlay\)[\s\S]{0,120}(release|releaseAllVideos)' \
  app/src/main/java/com/dlight/ui/player
rg -n 'hostContext|setContext\(this\)' \
  app/src/main/java/com/dlight/ui/player/DanmakuVideoPlayer.java \
  app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java
```

Expected: both commands return no matches.

### Task 5: Verify the original symptom on the emulator

**Files:**
- Install: `app/build/outputs/apk/debug/app-debug.apk`
- Do not clear App data.

- [ ] **Step 1: Install the Debug APK and reset only diagnostic logs**

```bash
ADB="/Users/yg02/Library/Android/sdk/platform-tools/adb"
"$ADB" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s emulator-5554 logcat -c
```

Expected: install reports `Success`; App data is preserved because neither uninstall nor `pm clear` is used.

- [ ] **Step 2: Reproduce the former leak path five times in one App process**

For each iteration, use UI automation against the current 1344×2992 emulator:

1. Open the first home video.
2. Tap the player start control.
3. Press Back before `onPrepared()`.
4. Confirm the home Activity is visible before starting the next iteration.

Do not force-stop the App between iterations because process death would erase the leaked in-memory listener and invalidate the test.

- [ ] **Step 3: Verify audio-focus request/abandon pairing**

```bash
"$ADB" -s emulator-5554 shell dumpsys audio | \
  rg 'requestAudioFocus\(\)|abandonAudioFocus\(\)|pack: com\.dlight'
```

Expected: every new `com.dlight` request from the five-iteration run has a matching abandon, and the final Audio Focus stack has no `com.dlight` entry after returning home.

- [ ] **Step 4: Wait for LeakCanary and verify the old signature is absent**

Wait at least six seconds after the fifth destroyed Activity, then run:

```bash
"$ADB" -s emulator-5554 logcat -d -v brief | \
  rg '35f74ec1975a1f85981ab3d0d6769913e2741|APPLICATION LEAKS|dumping heap'
```

Expected: no new heap dump and no occurrence of the old signature in the log buffer collected after installation.

- [ ] **Step 5: Verify normal and fullscreen playback cleanup**

Start a video and allow `onPrepared()` to complete, enter fullscreen, return to normal mode, then exit the Activity. Confirm playback controls remain functional and `dumpsys audio` again has no `com.dlight` focus owner after exit.

### Task 6: Completion audit and final commit state

**Files:**
- Verify all files changed by Tasks 1-3.

- [ ] **Step 1: Audit the approved specification requirement by requirement**

Check:

- confirmed pre-prepared leak test is red before and green after;
- all four affected Activities release unconditionally;
- `SimplePlayer` uses the same owner-aware local release;
- `hostContext` is removed;
- LeakCanary remains enabled and visible;
- automated matrix passes;
- five-cycle and normal/fullscreen device regressions pass;
- no App/emulator data was cleared.

- [ ] **Step 2: Inspect repository state**

```bash
git status --short
git log -4 --oneline
git diff --check HEAD~2..HEAD
```

Expected: only the known pre-existing `.superpowers/` path may remain untracked; implementation files are committed, recent commits match Tasks 2 and 3, and diff check is clean.
