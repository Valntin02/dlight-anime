# Player Audio Focus Leak Design

## Goal

Eliminate the confirmed `DanmkuVideoActivity` memory leak and close the same lifecycle gap in every Activity that owns the shared GSY player manager. Keep LeakCanary enabled so future leaks remain visible.

## Evidence and root cause

The device report identified this strong-reference chain:

```text
AudioManager.mAudioFocusIdListenerMap
→ AudioFocusRequest.mFocusListener
→ GSYVideoView.onAudioFocusChangeListener
→ DanmakuVideoPlayer
→ destroyed DanmkuVideoActivity
```

The incident timeline proves the lifecycle gap:

- `12:56:37.893`: `GSYVideoView.startPrepare()` requested audio focus before media preparation completed.
- `12:56:49.669`: that `DanmkuVideoActivity` received `Activity#onDestroy()`.
- There was no matching `abandonAudioFocus()` between those events.
- LeakCanary reported the same destroyed Activity key and 394,223 retained bytes.

`DanmkuVideoActivity.onDestroy()` currently calls `release()` only when `isPlay` is true. `isPlay` becomes true only in `onPrepared()`, but audio focus is requested earlier in `startPrepare()`. Leaving during preparation or after a preparation error therefore skips cleanup and leaves the listener registered in `AudioManager`.

The same conditional cleanup exists in `DetailPlayer`. `PlayActivity` and `PlayTVActivity` release only from their back-button path, so other destruction paths have the same risk. `SimplePlayer` already releases unconditionally.

## Chosen approach

Use Activity lifecycle cleanup, which matches the GSY manager's documented contract: an Activity that owns the shared player must unconditionally release it when destroyed.

For every affected playback Activity:

1. Stop relying on `isPlay` to decide whether cleanup is needed.
2. Clear the current player's callback before teardown so completion cannot call back into a dying Activity.
3. Call `GSYVideoManager.releaseAllVideos()` before `super.onDestroy()` so the current listener receives `onCompletion()`, abandons audio focus, releases the media player, and releases Danmaku resources while the Activity window is still valid.
4. Release orientation listeners on every path.

Apply this to:

- `DanmkuVideoActivity`
- `DetailPlayer`
- `PlayActivity`
- `PlayTVActivity`

Keep `SimplePlayer` behavior unchanged because it already releases the shared manager from `onDestroy()`.

Remove `DanmakuVideoPlayer.hostContext`, its setter/getter, and the Activity call site. The field is unused and duplicates the View's existing Activity context, adding another direct Activity reference without providing behavior.

Do not release from `View#onDetachedFromWindow()`: the normal/fullscreen handoff intentionally detaches player views and would be mistaken for final teardown.

## Error and concurrency behavior

Cleanup is idempotent at the Activity level. If the back-button path released the manager first, the later `onDestroy()` call sees no current listener and only asks the already-released manager to release again. If another player has already replaced the current listener, GSY's single-manager ownership model makes that transition responsible for completing the previous listener before registering the new one.

The release path must cover all player states: normal, preparing, playing, paused, error, completed, and fullscreen.

## Verification

### Automated regression

Add a Robolectric integration test that:

1. Creates `DanmkuVideoActivity` with a valid `VodData` and playable URL.
2. Starts playback far enough for `AudioManager` to register the GSY focus listener, without delivering `onPrepared()`.
3. Confirms the focus listener is registered.
4. Destroys the Activity.
5. Confirms the focus listener registration is removed.

Add lifecycle coverage for the other affected player Activities or a shared contract test proving each `onDestroy()` delegates to unconditional shared-manager release.

Run Debug and Release unit tests, app lint, and Debug assembly.

### Device regression

On the configured emulator, install the new Debug APK without clearing App data. In one App process, repeat at least five times:

1. Open a video detail page.
2. Start playback.
3. Return before preparation completes.

For every `requestAudioFocus()` in `dumpsys audio`, require a matching `abandonAudioFocus()`. Wait past LeakCanary's retained-object delay and confirm it does not report the existing signature `35f74ec1975a1f85981ab3d0d6769913e2741` again.

Also verify normal prepared playback, fullscreen enter/exit, and final Activity exit still work.

## Non-goals

- Do not remove, disable, silence, reconfigure, or exclude findings from LeakCanary.
- Do not hide the yellow-bird heap-dump notification.
- Do not change playback source selection, networking, download behavior, or release packaging.
- Do not clear emulator or App data.
