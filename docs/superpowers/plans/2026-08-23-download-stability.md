# Download Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse HLS playlists deterministically, avoid silently producing corrupt downloads, preserve verified segments across failures, and reconcile interrupted download tasks into a resumable state.

**Architecture:** Separate playlist text parsing from network fetching and file transfer. `HlsPlaylistParser` handles one document, `HlsPlaylistResolver` follows bounded nested variants, and `VideoDownloader` owns transfer/merge orchestration. Download state normalization remains in `DownloadTask`/`DownloadTaskStore`, not in UI code.

**Tech Stack:** Java, JUnit 4.13.2, Robolectric 4.13, `HttpURLConnection`, SharedPreferences

---

### Task 1: Parse HLS documents without network access

**Files:**
- Create: `app/src/main/java/com/dlight/feature/download/HlsPlaylistParser.java`
- Create: `app/src/test/java/com/dlight/feature/download/HlsPlaylistParserTest.java`

- [ ] **Step 1: Write failing media and master playlist tests**

Use a base URI `https://cdn.example.com/show/master.m3u8` and assert:

- comments and blank lines are ignored;
- relative and absolute segment URLs resolve in original order;
- media URIs without `.ts` extensions are accepted;
- URI lines containing `adjump` are excluded case-insensitively;
- `#EXT-X-STREAM-INF:BANDWIDTH=...` associates the next URI with a variant;
- variants sort by descending bandwidth with input order as the tie breaker;
- `#EXT-X-MAP`, `#EXT-X-BYTERANGE`, and encrypted `#EXT-X-KEY` fail with explicit `IOException` messages;
- `METHOD=NONE` is accepted;
- an empty/invalid document returns no segments or variants instead of inventing data.

Principal assertions:

```java
HlsPlaylistParser.Result media = HlsPlaylistParser.parse(
    "#EXTM3U\n#EXTINF:4,\nseg-1?id=7\n#EXTINF:4,\n/video/seg-2\n",
    URI.create("https://cdn.example.com/show/index.m3u8")
);
assertEquals(Arrays.asList(
    "https://cdn.example.com/show/seg-1?id=7",
    "https://cdn.example.com/video/seg-2"
), media.getSegments());
```

```java
HlsPlaylistParser.Result master = HlsPlaylistParser.parse(
    "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow.m3u8\n"
        + "#EXT-X-STREAM-INF:BANDWIDTH=2400000\nhigh.m3u8\n",
    URI.create("https://cdn.example.com/master.m3u8")
);
assertEquals("https://cdn.example.com/high.m3u8", master.getVariants().get(0).getUrl());
```

- [ ] **Step 2: Run and verify red**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest --tests 'com.dlight.feature.download.HlsPlaylistParserTest' --console=plain
```

Expected: compilation fails because the parser does not exist.

- [ ] **Step 3: Implement HlsPlaylistParser**

Create a final utility with immutable nested result types:

```java
public static Result parse(String content, URI baseUri) throws IOException

public static final class Result {
    public List<String> getSegments()
    public List<Variant> getVariants()
}

public static final class Variant {
    public String getUrl()
    public long getBandwidth()
}
```

Validate `baseUri` as hierarchical HTTP(S) with a host and no user info. Parse line-by-line. A `#EXT-X-STREAM-INF` line marks only the next non-comment URI as a variant. Resolve URIs with `baseUri.resolve`, accept only hierarchical HTTP(S), and reject invalid ports/user info/fragments.

When no master marker is pending, a non-comment URI is a media segment. Once the document contains master variants, return variants and no segment list so a malformed mixed document cannot be merged accidentally.

Reject unsupported constructs before returning:

```java
if (upper.startsWith("#EXT-X-MAP")) throw new IOException("暂不支持 fMP4 下载");
if (upper.startsWith("#EXT-X-BYTERANGE")) throw new IOException("暂不支持字节范围分片");
if (upper.startsWith("#EXT-X-KEY")) {
    // Parse the METHOD attribute exactly; substrings such as METHOD=NONEE or
    // METHOD=NONE inside a quoted URI must not bypass encryption rejection.
    String method = parseMethodAttribute(line);
    if (!"NONE".equalsIgnoreCase(method)) {
        throw new IOException("暂不支持加密 HLS 下载");
    }
}
```

- [ ] **Step 4: Run focused and full unit tests**

Run the focused command, then `:app:testDebugUnitTest`. Expected: all tests pass.

- [ ] **Step 5: Commit Task 1**

```bash
git add app/src/main/java/com/dlight/feature/download/HlsPlaylistParser.java \
  app/src/test/java/com/dlight/feature/download/HlsPlaylistParserTest.java
git commit -m "feat: add deterministic HLS playlist parser"
```

### Task 2: Resolve nested playlists and integrate VideoDownloader

**Files:**
- Create: `app/src/main/java/com/dlight/feature/download/HlsPlaylistResolver.java`
- Create: `app/src/test/java/com/dlight/feature/download/HlsPlaylistResolverTest.java`
- Modify: `app/src/main/java/com/dlight/feature/download/VideoDownloader.java`

- [ ] **Step 1: Write failing bounded-resolution tests**

Define a package-visible fetch contract:

```java
interface PlaylistFetcher {
    String fetch(String url) throws IOException;
}
```

Use an in-memory map fetcher and assert:

- a media playlist returns its segments;
- a master follows the highest-bandwidth variant;
- equal bandwidth keeps input order;
- nested master → master → media resolves correctly;
- more than three nested redirects throws `播放列表嵌套层级过深`;
- empty media throws `播放列表中没有可下载的视频分片`;
- a missing fake URL propagates an `IOException` without fallback to a lower variant.

- [ ] **Step 2: Run and verify red**

Run the focused resolver test. Expected: class-not-found compilation failure.

- [ ] **Step 3: Implement HlsPlaylistResolver**

Public production entry and package test entry:

```java
public static List<String> resolve(String playlistUrl) throws IOException {
    return resolve(playlistUrl, HlsPlaylistResolver::fetchOverNetwork, 0);
}

static List<String> resolve(String playlistUrl, PlaylistFetcher fetcher, int depth)
    throws IOException
```

Reject depth greater than three. Fetch UTF-8 text with 15-second connect/read timeouts, parse it with `HlsPlaylistParser`, return media segments, or recurse into `variants.get(0)`. Return an unmodifiable segment list.

- [ ] **Step 4: Replace VideoDownloader's private playlist traversal**

In `mulDownloadM3u8`, replace `loadSegmentUrls(m3u8Url, 0)` with `HlsPlaylistResolver.resolve(m3u8Url)` and delete the old private `loadSegmentUrls` method. Do not change transfer, progress, pause, merge, or cleanup behavior in this task.

- [ ] **Step 5: Run tests and build**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

- [ ] **Step 6: Commit Task 2**

```bash
git add app/src/main/java/com/dlight/feature/download/HlsPlaylistResolver.java \
  app/src/test/java/com/dlight/feature/download/HlsPlaylistResolverTest.java \
  app/src/main/java/com/dlight/feature/download/VideoDownloader.java
git commit -m "refactor: use tested HLS playlist resolution"
```

### Task 3: Make segment and merge writes atomic and resumable

**Files:**
- Modify: `app/src/main/java/com/dlight/feature/download/VideoDownloader.java`
- Create: `app/src/test/java/com/dlight/feature/download/VideoDownloaderFileTest.java`

- [ ] **Step 1: Write failing file-safety tests**

Using JUnit `TemporaryFolder` and local `file:` URLs, assert:

- a complete segment is written to the final segment path and no `.part` remains;
- a read failure removes only `.part` and never leaves a positive-length final segment;
- merge writes `title.ts.part`, renames only after all input segments are read, and leaves no final file after a missing segment failure;
- ordinary task failure preserves already completed segment files for retry;
- explicit `deletePartialDownload` still removes the task temp directory.

Expose only package-visible helpers required by tests; do not make transfer internals public API.

- [ ] **Step 2: Run tests and verify at least the partial-file cases fail**

Run the focused class. Expected: failure because current code writes directly to final segment and merge paths and deletes the temp directory on ordinary failure.

- [ ] **Step 3: Download into `.part` and rename atomically**

For each segment:

```java
File partFile = new File(destination.getParentFile(), destination.getName() + ".part");
deleteIfExists(partFile);
try (InputStream in = ...; FileOutputStream out = new FileOutputStream(partFile, false)) {
    // copy with pause checks
}
if (destination.exists() && !destination.delete()) throw new IOException(...);
if (!partFile.renameTo(destination)) {
    deleteIfExists(partFile);
    throw new IOException("无法保存完整视频分片");
}
```

Every failure and pause path deletes the `.part` file. Retry attempts start with a clean `.part`.

- [ ] **Step 4: Merge into `.part` and preserve verified segments on failure**

Merge into `<safeName>.ts.part`; rename to `<safeName>.ts` only after all segment reads and flush/close succeed. On merge failure, delete the merge `.part` and any incomplete final file, but retain completed numbered segments.

Change ordinary and interrupted task failure handling so it no longer deletes `videoTempDir`. Pause already preserves it. Only success and explicit user delete remove the temp directory.

- [ ] **Step 5: Run file tests, all tests, and build**

Expected: file tests and full suite pass; `assembleDebug` succeeds.

- [ ] **Step 6: Commit Task 3**

```bash
git add app/src/main/java/com/dlight/feature/download/VideoDownloader.java \
  app/src/test/java/com/dlight/feature/download/VideoDownloaderFileTest.java
git commit -m "fix: preserve verified download segments"
```

### Task 4: Test download task serialization and reconcile interrupted state

**Files:**
- Modify: `app/src/main/java/com/dlight/feature/download/DownloadTask.java`
- Modify: `app/src/main/java/com/dlight/feature/download/DownloadTaskStore.java`
- Modify: `app/src/main/java/com/dlight/DlightApplication.java`
- Create: `app/src/test/java/com/dlight/feature/download/DownloadTaskTest.java`
- Create: `app/src/test/java/com/dlight/feature/download/DownloadTaskStoreTest.java`

- [ ] **Step 1: Write DownloadTask behavior tests**

Cover JSON round-trip of every field, null-to-empty normalization, progress clamp, missing-field defaults, queued defaults, mutually consistent `isActive/isPaused/isCompleted`, and `updatedAt` monotonicity.

- [ ] **Step 2: Write Robolectric store and reconcile tests**

Cover upsert without duplication, descending update order, remove, malformed JSON without crash, skipping non-object/empty task IDs, reading old JSON with missing fields, and:

```java
DownloadTaskStore.reconcileInterruptedTasks(context);
```

Expected reconciliation:

- persisted `queued` or `downloading` → `paused`;
- progress, URL, cover, file path, and task ID remain unchanged;
- completed, failed, and already paused remain unchanged;
- reconciled tasks clear stale error text and receive a newer `updatedAt`.

- [ ] **Step 3: Run tests and verify reconcile is red**

Expected: DownloadTask tests may pass existing code; store test fails because reconcile does not exist.

- [ ] **Step 4: Implement reconciliation and call it once on process start**

Add synchronized `DownloadTaskStore.reconcileInterruptedTasks(Context)`. Read the stored tasks, mutate only active tasks to paused with no error, and perform one write only if something changed.

Call it in `DlightApplication.onCreate()` before a download service can be started. A redelivered Service intent will subsequently requeue the requested task; stale tasks without a redelivery become user-resumable paused entries.

- [ ] **Step 5: Run all tests, lint, and build**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

- [ ] **Step 6: Non-destructive device smoke**

Use only `adb install -r` when signatures match. Start a download, pause it, relaunch the App, and confirm the task remains resumable. Do not uninstall, clear App data, delete unrelated downloads, or mutate backend data. If the test cannot be completed safely, record it as unverified.

- [ ] **Step 7: Commit Task 4**

```bash
git add app/src/main/java/com/dlight/feature/download/DownloadTask.java \
  app/src/main/java/com/dlight/feature/download/DownloadTaskStore.java \
  app/src/main/java/com/dlight/DlightApplication.java \
  app/src/test/java/com/dlight/feature/download/DownloadTaskTest.java \
  app/src/test/java/com/dlight/feature/download/DownloadTaskStoreTest.java
git commit -m "fix: reconcile interrupted download tasks"
```

## Completion gate

- HLS media, master, nested, unsupported, and empty cases are tested.
- URI segments without `.ts` are no longer silently ignored.
- Unsupported encrypted/fMP4/byte-range playlists fail explicitly instead of producing corrupt files.
- Segment and merged outputs appear only after complete writes.
- Ordinary failure preserves verified segments; explicit delete removes them.
- Persisted active tasks become paused and resumable after process restart.
- All unit tests, `lintDebug`, and `assembleDebug` succeed.
- Device checks use only non-destructive install and task actions.
