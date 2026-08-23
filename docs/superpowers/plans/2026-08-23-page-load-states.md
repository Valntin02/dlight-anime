# Page Load States Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give homepage sections and search explicit loading, empty, error, and retry states while preventing stale network callbacks from updating destroyed or superseded screens.

**Architecture:** A small reusable `LoadStateView` renders status UI. Each page keeps ownership of its Retrofit `Call` and request generation. Homepage sections remain independent; pagination errors preserve existing content.

**Tech Stack:** Java, Android Views/XML, Retrofit, Robolectric, JUnit, Espresso-compatible view IDs

---

### Task 1: Build and test LoadStateView

**Files:**
- Create: `app/src/main/java/com/dlight/ui/widget/LoadStateView.java`
- Create: `app/src/test/java/com/dlight/ui/widget/LoadStateViewTest.java`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/ids.xml`

- [ ] **Step 1: Write failing Robolectric view-state tests**

Cover initial `GONE`, loading spinner/message/no retry, empty message/no retry, error message/retry visible, retry click callback, and `hide()`.

- [ ] **Step 2: Implement the programmatic view**

Create a vertical centered `LinearLayout` containing a `ProgressBar`, message `TextView`, and retry `Button`. Use string resources, stable child IDs, existing colors and spacing, dark text on the brand retry background for sufficient contrast, a minimum 48dp retry target, and a polite accessibility live region that announces attached state changes. Provide these methods:

```java
public void showLoading(String message)
public void showEmpty(String message)
public void showError(String message)
public void hide()
public void setOnRetryListener(OnClickListener listener)
```

Default messages are used when input is null/blank. Do not add a general state framework.

- [ ] **Step 3: Run tests/build and commit**

Commit: `feat: add reusable page load state view`.

### Task 2: Add independent states to homepage sections

**Files:**
- Modify: `app/src/main/res/layout/fragment_update_today.xml`
- Modify: `app/src/main/res/layout/activity_get_data.xml`
- Modify: `app/src/main/res/layout/fragment_anime.xml`
- Modify: `app/src/main/java/com/dlight/ui/home/UpdateTodayFragment.java`
- Modify: `app/src/main/java/com/dlight/ui/home/WeeklyShow.java`
- Modify: `app/src/main/java/com/dlight/ui/home/FragmentAnime.java`
- Create: `app/src/test/java/com/dlight/ui/home/HomeLoadStateTest.java`

- [ ] **Step 1: Wrap each content list in a FrameLayout**

Keep current list dimensions/weights, add a sibling `LoadStateView` overlay with IDs:

- `today_load_state`
- `weekly_load_state`
- `anime_load_state`

- [ ] **Step 2: Implement Today lifecycle and state transitions**

Store the active `Call<VodResModel>`. Before first request show loading. Success with null/empty list shows empty; non-empty replaces data and hides state. Failure shows error only when no existing data. Retry repeats the request. Cancel and null the call in `onDestroyView`; callbacks first verify `isAdded()` and current view fields are non-null.

- [ ] **Step 3: Implement Weekly request identity and cache states**

Track `activeCall`, `requestGeneration`, and `selectedWeekday`. A new weekday cancels the old call and increments generation. A callback updates UI only when its captured generation and weekday still match. Cached non-empty/empty values render content/empty without a request. Retry repeats the selected weekday. Cancel in `onDestroyView`.

- [ ] **Step 4: Implement Anime first-page and pagination states**

First page/filter reset with no content shows loading. Empty first page shows empty. First-page failure shows retry error. Pagination failure keeps existing content visible and must not clear the adapter; show a resource-backed Snackbar with an explicit retry action for the same page, and dismiss it on success, filter reset, or view destruction. Guard null response lists. Track/cancel the active call on view destruction.

- [ ] **Step 5: Test state decisions**

Use production state trackers that the Fragments call as their single source for Weekly generation/cache and Anime page/request state. Tests must cover null list, empty, content, error with/without old content, Weekly A→B stale callback/destroy invalidation/cache defensive copy, and Anime first failure retry, pagination same-page retry, success increment, filter reset, and stale callback rejection.

- [ ] **Step 6: Run tests/lint/build and commit**

Commit: `feat: show homepage loading and error states`.

### Task 3: Add race-safe search states

**Files:**
- Modify: `app/src/main/res/layout/activity_search.xml`
- Modify: `app/src/main/java/com/dlight/feature/search/SearchActivity.java`
- Create: `app/src/test/java/com/dlight/feature/search/SearchRequestStateTest.java`

- [ ] **Step 1: Wrap search results and add LoadStateView**

Place `recyclerViewResults` and `search_load_state` in a weighted `FrameLayout`. History remains separate and visible before a search.

- [ ] **Step 2: Track the active request and generation**

Store `Call<VodResModel> activeSearchCall`, integer generation, and last trimmed keyword. A new search cancels the prior call, increments generation, hides history, shows loading, and leaves the result recycler ready. Ignore callbacks whose generation is stale or Activity is finishing/destroyed.

- [ ] **Step 3: Render result states and retry**

- HTTP/business success + non-empty list: update adapter, show list, hide state.
- Success + null/empty list: clear adapter, show empty.
- Business error or transport failure: clear adapter, show error with retry for the same keyword.
- Blank keyword: do not request; restore history.

Cancel search and suggestion calls in `onDestroy`. Replace all string identity checks (`input == ""`) with trimmed emptiness checks.

- [ ] **Step 4: Test generation and state transitions**

Extract a small `SearchRequestState` if needed. Cover old response ignored, newest response accepted, empty, error/retry keyword, blank restores history, and destroy ignores callbacks.

- [ ] **Step 5: Run Debug and Release tests, lint, build, then commit**

Run both `testDebugUnitTest` and `testReleaseUnitTest` with the Release URL property. Commit: `feat: show reliable search result states`.

## Completion gate

- Homepage sections independently show loading/content/empty/error.
- Retry works without recreating the Activity.
- Weekly and search stale responses cannot overwrite newer choices.
- Null response lists do not crash.
- Pagination failure preserves existing anime content.
- Destroyed views cancel calls and ignore callbacks.
- Debug and Release unit tests, `lintDebug`, and `assembleDebug` succeed.
