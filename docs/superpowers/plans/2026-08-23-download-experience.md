# Download Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show useful download speed/ETA and prevent accidental or impossible downloads on offline, cellular, or critically low-storage devices.

**Architecture:** A pure `DownloadProgressMetrics` computes throttled snapshots from bytes/progress/time. Optional metrics persist on `DownloadTask`. A separate `DownloadPreflight` class classifies connectivity and storage; UI owners decide whether to show confirmation.

**Tech Stack:** Java, Android ConnectivityManager/StatFs, JUnit/Robolectric, existing download Service

---

### Task 1: Compute, persist, and display download metrics

**Files:**
- Create: `app/src/main/java/com/dlight/feature/download/DownloadProgressMetrics.java`
- Create: `app/src/test/java/com/dlight/feature/download/DownloadProgressMetricsTest.java`
- Modify: `app/src/main/java/com/dlight/feature/download/DownloadTask.java`
- Modify: `app/src/test/java/com/dlight/feature/download/DownloadTaskTest.java`
- Modify: `app/src/main/java/com/dlight/feature/download/VideoDownloader.java`
- Modify: `app/src/main/java/com/dlight/feature/download/ServiceDownload.java`
- Modify: `app/src/main/java/com/dlight/feature/download/AdapterDownVideo.java`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Test a pure clock-driven metrics calculator**

Inject a `Clock`. Cover zero/unknown, bytes per second, ETA from progress, non-decreasing bytes, reset, no divide-by-zero, long overflow, and one-second emission throttling.

- [ ] **Step 2: Add backward-compatible task fields**

Add `bytesDownloaded`, `bytesPerSecond`, and `etaSeconds` as non-negative longs. JSON missing fields default to zero/zero/-1. Old JSON remains readable. Reset metrics when requeueing; paused retains bytes but clears stale speed/ETA; completed ETA is zero.

- [ ] **Step 3: Report bytes without changing transfer semantics**

Count actual bytes written in the existing segment copy loop. Track per-attempt in-flight bytes separately from total transferred bytes: valid progress/ETA uses completed plus current in-flight bytes, while a failed attempt discards its in-flight contribution without decreasing the persisted transferred byte count. Add a default callback method carrying progress, downloaded bytes, speed, and ETA so existing callback implementations remain source-compatible. Emit at most once per second and at segment completion; do not emit after terminal callback.

- [ ] **Step 4: Persist/service-render metrics**

Service updates task metrics and publishes at the throttled cadence. Adapter uses resource strings and formats IEC/SI-consistent speed plus exact `mm:ss`/`h:mm:ss` ETA for downloading; paused keeps percentage but not stale speed; failed shows error.

- [ ] **Step 5: Verify and commit**

Run Debug/Release tests, app lint/build. Commit: `feat: show download speed and remaining time`.

### Task 2: Add download network and storage preflight

**Files:**
- Create: `app/src/main/java/com/dlight/feature/download/DownloadPreflight.java`
- Create: `app/src/test/java/com/dlight/feature/download/DownloadPreflightTest.java`
- Modify: `app/src/main/java/com/dlight/ui/player/IntroFragment.java`
- Modify: `app/src/main/java/com/dlight/feature/download/ActvityDownVideo.java`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Test network/storage decisions**

Classify offline and validated internet, and use `ConnectivityManager.isActiveNetworkMetered()` so cellular or VPN-over-cellular networks require confirmation while unmetered validated networks are ready. Require at least 256 MiB free reserve in the internal video directory. Unknown capabilities fail closed as offline; storage API/runtime failure returns a visible preflight error, not a crash.

- [ ] **Step 2: Add one preflight API**

Return a result enum/value object: `READY`, `CONFIRM_CELLULAR`, `OFFLINE`, `LOW_STORAGE`, `ERROR`. Do not show UI from the utility.

- [ ] **Step 3: Gate new and resumed downloads**

Before `startForegroundService`, run preflight. READY starts. Metered network shows one lifecycle-owned, resource-backed confirmation dialog. Positive action reruns network and storage checks with metered consent before starting; disconnects or low storage during the dialog still block. Dismiss dialogs on Fragment view/Activity destruction and prevent duplicates. OFFLINE/LOW_STORAGE/ERROR show actionable messages. Apply to detail-page new downloads and download-list resume/retry. Pause/play/delete are unaffected.

`ServiceDownload` must use `START_NOT_STICKY`; Android must not redeliver a previously confirmed start Intent after network/storage conditions change. Persisted active tasks reconcile to paused on the next process start and require a new user resume/preflight.

- [ ] **Step 4: Verify and commit**

Run Debug/Release tests, app lint/build. Commit: `feat: confirm cellular downloads and storage`.

## Completion gate

- Active downloads show progress, speed, and ETA without callback spam.
- Old persisted tasks remain readable.
- Terminal callbacks prevent late metrics.
- Offline and critically low storage prevent start.
- Cellular requires explicit confirmation for new/resumed tasks.
- Debug/Release tests, `lintDebug`, and `assembleDebug` succeed.
