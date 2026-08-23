# Player Media Thread Design

## Goal

Keep the player leak fix while removing the 5–6 second UI freeze and input ANR caused by IJK shutdown during an immediate return from the preparing state. Audit and cover equivalent blocking media-manager paths.

## Evidence and root cause

The device reproduction showed that the second detail Activity was displayed in 55 ms, but Android later reported:

```text
Input dispatching timed out
DanmkuVideoActivity is not responding
Waited 5001ms for FocusEvent(hasFocus=true)
```

The ANR main-thread stack was:

```text
GSYVideoBaseManager$MediaHandler.handleMessage(HANDLER_RELEASE)
→ IjkPlayerManager.release()
→ IjkMediaPlayer.release()
→ ijkmp_shutdown()
→ ffp_wait_stop_l()
→ SDL_WaitThread()
→ pthread_join()
```

`GSYVideoBaseManager.init()` currently constructs `MediaHandler` with `Looper.getMainLooper()`. The leak fix correctly calls owner-aware `release()` during Activity destruction. That release queues `HANDLER_RELEASE`, which then performs the blocking native shutdown on the UI thread. The old behavior avoided the freeze only by skipping release and leaking the Activity.

## Chosen approach

Port the official GSY configurable media-looper seam into the vendored v10-era manager:

- add a nullable `Looper mLooper` field;
- create `initMediaHandler()` and use the configured looper, falling back to the main looper for library compatibility;
- add `getLooper()` and `setLooper(Looper)`;
- keep `mainThreadHandler` explicitly bound to `Looper.getMainLooper()` for UI callbacks.

In `DlightApplication`, start one process-lifetime `HandlerThread` named `dlight-gsy-media` before any playback, and pass its looper to `GSYVideoManager.instance().setLooper(...)` during player configuration.

The media handler remains serial. A `HANDLER_RELEASE` that blocks in native IJK shutdown delays the next `HANDLER_PREPARE`, but it no longer blocks window focus, rendering, navigation, or clicks. Serial ordering also prevents a release and a subsequent prepare from racing over the shared `playerManager` field.

Do not move only `playerManager.release()` to an ad-hoc thread: detaching just that call could release a newly assigned player or race the cache manager. Do not skip release, delay Activity teardown, disable LeakCanary, or swallow ANRs.

## Similar-path audit

The same media handler owns these potentially blocking operations and they will move together:

- `HANDLER_PREPARE` → `initVideo()` → release previous player, construct player, `prepareAsync()`;
- `HANDLER_RELEASE` → release player and cache manager;
- `HANDLER_RELEASE_SURFACE` → release the player surface.

All existing calls to `releaseMediaPlayer()` from normal/error UI state, network recovery, Activity teardown, and manager-wide release already enqueue through `mMediaHandler`; they require no call-site changes.

Direct `start()`, `stop()`, `pause()`, and `seekTo()` calls remain unchanged because the captured ANRs consistently identify `IjkMediaPlayer.release()` / `pthread_join()`, not those operations. Expanding their threading contract without evidence would introduce ordering and UI-state risk.

`GSYVideoADManager` is not used by the App and is outside this fix. It inherits the configurable seam, so a future ad player can opt into its own looper without changing this implementation.

## Lifecycle

The HandlerThread is owned for the lifetime of the App process. Android does not provide a reliable production `Application.onTerminate()` callback, so the thread is not stopped during normal operation; process death reclaims it. `DlightApplication.onCreate()` runs once per process, and configuration happens before any player instance receives prepare/release messages.

## Verification

### Automated

Add a Robolectric threading test that injects a blocking fake `IPlayerManager` into `GSYVideoManager` and proves:

1. `releaseMediaPlayer()` invokes fake `release()` on `dlight-gsy-media`, not the main looper.
2. While fake release is blocked, a main-looper callback still executes.
3. Releasing the fake allows the media command to finish cleanly.
4. The existing eight lifecycle/audio-focus tests remain green, including old-owner/new-owner protection.

Run Debug and Release unit tests, app lint, and Debug assembly.

### Device

Install the Debug APK without uninstalling or clearing App data. In one App process, repeat at least five effective cycles:

1. Open a video detail page.
2. Start playback and confirm `requestAudioFocus()`.
3. Return before prepare completes.
4. Immediately click another video.

Require:

- the next Activity responds without a 5-second input timeout;
- no `Input dispatching timed out` or `ANR in com.dlight` appears in the fresh log window;
- final audio-focus stack is empty after exit;
- the prior LeakCanary signature does not return;
- request/abandon pairing remains intact.

## Non-goals

- Do not revert owner-aware player release.
- Do not disable, hide, or reconfigure LeakCanary.
- Do not change playback source selection, CDN/DNS behavior, or download state.
- Do not clear App or emulator data.
