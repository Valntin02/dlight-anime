# Player Recovery State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the detail screen usable when playback sources are missing or playback start fails, with visible loading/error states and retry actions.

**Architecture:** Overlay the existing accessible `LoadStateView` on the player bounds. A small `PlayerRecoveryTracker` owns request generation/destroy state; `DanmkuVideoActivity` retains the Retrofit call and delegates retry decisions to the tracker.

**Tech Stack:** Java, Android Views, Retrofit, GSYVideoPlayer, Robolectric/JUnit

---

### Task 1: Add lifecycle-safe recovery state and player retry

**Files:**
- Modify: `app/src/main/res/layout/activity_danmaku_layout.xml`
- Modify: `app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java`
- Create: `app/src/main/java/com/dlight/ui/player/PlayerRecoveryTracker.java`
- Create: `app/src/test/java/com/dlight/ui/player/PlayerRecoveryTrackerTest.java`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add a player-bounds state overlay**

Add `player_load_state` constrained to the same top/start/end/bottom bounds as `danmaku_player`, with a higher elevation and initial GONE inherited from the view.

- [ ] **Step 2: Implement and test PlayerRecoveryTracker**

Track generation, destroyed state, and whether an automatic recovery has already been attempted. Cover:

- first missing-source event starts recovery and returns a generation token;
- a second automatic attempt is rejected;
- explicit user retry starts a new generation;
- stale generation callbacks are rejected;
- destroy invalidates every callback;
- success/failure completes only the current request.

- [ ] **Step 3: Render source recovery without finishing the Activity**

When selected URLs are empty, show resource-backed loading text and start recovery. On no match or transport failure, remain on the current Activity and show an error with retry. Retry performs a user-initiated recovery for the same title. Keep the active Call cancellation in `onDestroy` and use tracker + call identity + Activity lifecycle guards.

Do not automatically loop when recovered data still has no playable URL; the recovered Activity shows an error and waits for user retry.

- [ ] **Step 4: Render player start failure and retry**

In `onClickStartError`, show a resource-backed player error. Retry hides the state and invokes the current player start logic for the same selected URL. `onPrepared` hides the state. Do not refetch metadata for a transport/player decode error unless the user invokes source recovery separately.

- [ ] **Step 5: Test, lint, build, commit**

Run Debug and Release unit tests, `lintDebug`, and Debug build. Commit: `feat: keep playback recovery errors on detail page`.

## Completion gate

- Missing-source recovery shows loading then content/error without automatic finish.
- Retry remains on the same detail context and cannot create callback loops.
- Destroyed/stale recovery callbacks cannot navigate or show UI.
- Player start error exposes retry; prepared playback hides the overlay.
- Debug/Release tests, `lintDebug`, and `assembleDebug` succeed.
