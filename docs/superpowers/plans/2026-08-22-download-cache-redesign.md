# Download Cache Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide reliable current-episode downloads with persistent in-app progress, failure/completion states, retry, and mirrored foreground notifications.

**Architecture:** Add a small persistent task model/store shared by the download service and both UIs. Serialize top-level download jobs in `ServiceDownload`, keep segment downloads parallel inside `VideoDownloader`, and propagate persisted state through package-scoped broadcasts.

**Tech Stack:** Java, Android Views, SharedPreferences/JSON, foreground Service, RecyclerView, Gradle.

---

### Task 1: Persistent download task state

**Files:**
- Create `app/src/main/java/com/dlight/feature/download/DownloadTask.java`
- Create `app/src/main/java/com/dlight/feature/download/DownloadTaskStore.java`
- Create `app/src/main/java/com/dlight/feature/download/DownloadContract.java`

- [ ] Define deterministic task IDs and four states: queued, downloading, completed, failed.
- [ ] Implement JSON serialization with backward-safe defaults.
- [ ] Implement synchronized get/list/upsert/remove operations and newest-first ordering.

### Task 2: Reliable service and downloader

**Files:**
- Modify `app/src/main/java/com/dlight/feature/download/ServiceDownload.java`
- Modify `app/src/main/java/com/dlight/feature/download/VideoDownloader.java`
- Modify `app/src/main/java/com/dlight/util/NotificationUtils.java`
- Modify `app/src/main/AndroidManifest.xml`

- [ ] Accept full task metadata and persist queued state.
- [ ] Execute tasks through one service-level queue and use one foreground notification safely.
- [ ] Persist/broadcast every meaningful state update.
- [ ] Make HLS download completion synchronous, single-callback, and task-isolated.
- [ ] Declare foreground-service permission explicitly.

### Task 3: Detail-page progress

**Files:**
- Modify `app/src/main/java/com/dlight/ui/player/IntroFragment.java`
- Modify `app/src/main/res/layout/fragment_intro.xml`

- [ ] Bind the click listener regardless of notification permission.
- [ ] Start the service with `videourls.get(currentPlayingIndex)` and episode `currentPlayingIndex + 1`.
- [ ] Add progress/status views and refresh them from persisted state plus broadcasts.
- [ ] Refresh download state whenever the selected episode changes.

### Task 4: Cache-page task list

**Files:**
- Modify `app/src/main/java/com/dlight/feature/download/ActvityDownVideo.java`
- Modify `app/src/main/java/com/dlight/feature/download/AdapterDownVideo.java`
- Modify `app/src/main/res/layout/item_downvideo.xml`
- Modify `app/src/main/res/layout/activity_downvideo.xml`

- [ ] Render queued/downloading/failed/completed tasks with progress.
- [ ] Play completed files, retry failed tasks, and delete task/file on long press.
- [ ] Preserve visibility of legacy completed files not yet represented in the task store.
- [ ] Register/unregister the update receiver with the Activity lifecycle.

### Task 5: Verification

- [ ] Run ID/static reference checks and `git diff --check`.
- [ ] Run `:app:assembleDebug --rerun-tasks` and `:app:lintDebug` with Android Studio JBR.
- [ ] Install the debug APK on `emulator-5554` and inspect logcat for service/UI crashes.
- [ ] Review the complete diff against the design and commit only intended files.
