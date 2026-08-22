# Playback Source Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make playback source selection deterministic and testable, skip invalid high-priority sources, preserve legacy episode expansion, and make recovery matching choose a real candidate.

**Architecture:** Extract JSON source selection and recovery matching from `DanmkuVideoActivity` into two small pure Java utilities. The Activity remains responsible for navigation and player setup, but receives already validated URL lists and matched recovery data.

**Tech Stack:** Java, Gson, JUnit 4.13.2, Robolectric 4.13, GSYVideoPlayer

---

### Task 1: Extract and test playback source selection

**Files:**
- Create: `app/src/main/java/com/dlight/ui/player/PlaySourceSelector.java`
- Create: `app/src/test/java/com/dlight/ui/player/PlaySourceSelectorTest.java`

- [ ] **Step 1: Write failing priority and fallback tests**

Create fixtures with Gson JSON and assert:

```java
assertEquals(
    Arrays.asList("https://cdn.example.com/1.m3u8"),
    PlaySourceSelector.selectUrls(
        JsonParser.parseString("["
            + "{\"from\":\"lzm3u8\",\"episodes\":[{\"url\":\"undefined\"}]},"
            + "{\"from\":\"bfzym3u8\",\"episodes\":[{\"url\":\" https://cdn.example.com/1.m3u8 \"}]}"
            + "]"),
        null,
        null,
        null
    )
);
```

Add tests for:

- `lzm3u8` wins over generic non-bfzy m3u8, normal, and bfzy when it has a valid URL.
- A higher-priority source with only null/blank/`null`/`undefined`/non-HTTP URLs is skipped.
- An object-shaped `vodPlayData` works as one source.
- Episode order is preserved and surrounding whitespace is trimmed.
- Ordinary HTTPS and Debug HTTP URLs are accepted; missing host, ftp, javascript, blank, `null`, and `undefined` are rejected.
- When no structured source is playable, legacy `vod_play_url` is used.
- A legacy URL containing `第01集` expands to the count from remarks, then total, then one. Episode metadata extracts one number adjacent to `集`/`话`/`期` (or an all-digit total) and clamps expansion to 2,000 URLs so dates and malformed large values cannot cause OOM.
- Invalid structured and legacy values produce an empty list.

- [ ] **Step 2: Run the test and verify the red state**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest --tests 'com.dlight.ui.player.PlaySourceSelectorTest' --console=plain
```

Expected: compilation fails because `PlaySourceSelector` does not exist.

- [ ] **Step 3: Implement PlaySourceSelector**

Create a final utility with this public surface:

```java
public static List<String> selectUrls(
    JsonElement vodPlayData,
    String legacyUrl,
    String remarks,
    String total
)

static boolean isPlayableUrl(String rawUrl)
static int parseEpisodeCount(String remarks, String total)
```

Implementation rules:

```java
private static int sourcePriority(JsonObject source) {
    String name = sourceName(source).toLowerCase(Locale.ROOT);
    if ("lzm3u8".equals(name)) return 0;
    if (name.contains("m3u8") && !name.contains("bfzy")) return 1;
    if (name.contains("bfzy")) return 3;
    return 2;
}
```

For array data, keep source objects in input order, sort stably by priority, extract valid URLs from each source, and return the first non-empty URL list. Do not select a source merely because its episode array is non-empty.

Validate URLs with `java.net.URI`: only hierarchical `http`/`https`, a non-null host, no user info, no fragment, and a port of `-1` or `1..65535`. Query parameters are allowed because signed media URLs commonly use them.

Legacy expansion must use the existing behavior:

```java
int count = parseEpisodeCount(remarks, total);
for (int episode = 1; episode <= count; episode++) {
    String number = String.format(Locale.ROOT, "%02d", episode);
    urls.add(trimmed.replaceAll("第\\d+集", "第" + number + "集"));
}
```

Return `Collections.emptyList()` for no result and an unmodifiable copy for successful results.

- [ ] **Step 4: Run focused and full tests**

Run the command from Step 2, then:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest --console=plain
```

Expected: all tests pass; task is not `NO-SOURCE`.

- [ ] **Step 5: Commit Task 1**

```bash
git add app/src/main/java/com/dlight/ui/player/PlaySourceSelector.java \
  app/src/test/java/com/dlight/ui/player/PlaySourceSelectorTest.java
git commit -m "feat: add deterministic playback source selection"
```

### Task 2: Integrate the selector into DanmkuVideoActivity

**Files:**
- Modify: `app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java`

- [ ] **Step 1: Replace Activity-owned selection logic**

Replace `dealVideourls()` with:

```java
private void dealVideourls() {
    videourls = new ArrayList<>(PlaySourceSelector.selectUrls(
        videoData.getVodPlayData(),
        videoData.getVod_play_url(),
        videoData.getVod_remarks(),
        videoData.getVod_total()
    ));
}
```

Remove these private methods from the Activity:

- `appendPreferredUrlsFromPlayData`
- `getSourcePriority`
- `getSourceName`
- `hasPlayableEpisodes`
- `appendUrlsFromSource`
- `parseEpisodeCount`
- `extractPositiveNumber`

Replace the selected URL guard with `PlaySourceSelector.isPlayableUrl(playUrl)`. Remove Gson imports that become unused, but preserve Gson types still used elsewhere in the file.

- [ ] **Step 2: Prove selection logic is no longer duplicated**

```bash
rg -n 'appendPreferredUrlsFromPlayData|getSourcePriority|hasPlayableEpisodes|parseEpisodeCount' \
  app/src/main/java/com/dlight/ui/player
```

Expected: matches exist only in test descriptions if any; production selection lives in `PlaySourceSelector`.

- [ ] **Step 3: Run tests and Debug build**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Perform a non-destructive emulator smoke check**

Install with `adb install -r` only if the currently installed package is signature-compatible. Do not uninstall or clear App data. If it is incompatible, report the device check as blocked and retain build/test evidence.

Open an item whose first source is invalid but a later source is valid. Verify the Activity remains open, selected episode is clamped into range, and the player attempts the later valid URL. A DNS/media-host failure must be reported separately from selection failure.

- [ ] **Step 5: Commit Task 2**

```bash
git add app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java
git commit -m "refactor: use tested playback source selector"
```

### Task 3: Extract and test recovery candidate matching

**Files:**
- Create: `app/src/main/java/com/dlight/ui/player/VodRecoveryMatcher.java`
- Create: `app/src/test/java/com/dlight/ui/player/VodRecoveryMatcherTest.java`
- Modify: `app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java`

- [ ] **Step 1: Write failing candidate-order tests**

Create `VodData` fixtures and assert:

- matching positive `vod_id` wins even when another item has the same name;
- exact non-null name wins when IDs differ;
- when neither matches, the first non-null candidate is returned;
- a leading null candidate is skipped;
- null/empty candidates or null current item return null.

The principal assertion is:

```java
assertSame(byId, VodRecoveryMatcher.findBest(current, Arrays.asList(null, byName, byId)));
```

- [ ] **Step 2: Run and verify red**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest --tests 'com.dlight.ui.player.VodRecoveryMatcherTest' --console=plain
```

Expected: compilation fails because `VodRecoveryMatcher` does not exist.

- [ ] **Step 3: Implement and integrate the matcher**

Create:

```java
public final class VodRecoveryMatcher {
    private VodRecoveryMatcher() {
    }

    public static VodData findBest(VodData current, List<VodData> candidates) {
        if (current == null || candidates == null || candidates.isEmpty()) return null;
        if (current.getVod_id() > 0) {
            for (VodData item : candidates) {
                if (item != null && item.getVod_id() == current.getVod_id()) return item;
            }
        }
        String name = current.getVod_name();
        if (name != null) {
            for (VodData item : candidates) {
                if (item != null && name.equals(item.getVod_name())) return item;
            }
        }
        for (VodData item : candidates) {
            if (item != null) return item;
        }
        return null;
    }
}
```

Replace the Activity's `findBestMatchedVod(...)` call with `VodRecoveryMatcher.findBest(videoData, ...)` and delete the private matcher.

- [ ] **Step 4: Run all tests and build**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

Expected: tests, lint task, and Debug build succeed.

- [ ] **Step 5: Commit Task 3**

```bash
git add app/src/main/java/com/dlight/ui/player/VodRecoveryMatcher.java \
  app/src/test/java/com/dlight/ui/player/VodRecoveryMatcherTest.java \
  app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java
git commit -m "refactor: test playback recovery matching"
```

## Completion gate

- New selector and recovery tests run and pass.
- A high-priority source with no valid URLs no longer blocks a lower-priority valid source.
- Legacy URL expansion behavior remains covered.
- `DanmkuVideoActivity` no longer owns JSON priority or recovery matching rules.
- `testDebugUnitTest`, `lintDebug`, and `assembleDebug` succeed.
- Device smoke uses non-destructive install only; no uninstall, clear-data, or backend mutation.
