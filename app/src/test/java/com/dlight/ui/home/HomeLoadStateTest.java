package com.dlight.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class HomeLoadStateTest {

    @Test
    public void nullAndEmptyListsHaveNoContent() {
        assertFalse(HomeLoadStatePolicy.hasContent(null));
        assertFalse(HomeLoadStatePolicy.hasContent(Collections.emptyList()));
        assertTrue(HomeLoadStatePolicy.hasContent(Collections.singletonList("video")));
    }

    @Test
    public void weeklyRejectsOlderSelectionAfterSwitch() {
        HomeLoadStatePolicy.WeeklyTracker<String> tracker =
            new HomeLoadStatePolicy.WeeklyTracker<>("一");

        HomeLoadStatePolicy.WeeklySelection<String> monday = tracker.select("一");
        HomeLoadStatePolicy.WeeklySelection<String> tuesday = tracker.select("二");

        assertFalse(tracker.accepts(monday));
        assertTrue(tracker.accepts(tuesday));
        assertEquals("二", tracker.selectedWeekday());
    }

    @Test
    public void weeklyInvalidateRejectsActiveCallback() {
        HomeLoadStatePolicy.WeeklyTracker<String> tracker =
            new HomeLoadStatePolicy.WeeklyTracker<>("日");
        HomeLoadStatePolicy.WeeklySelection<String> request = tracker.select("日");

        tracker.invalidate();

        assertFalse(tracker.accepts(request));
    }

    @Test
    public void weeklyCacheUsesDefensiveCopiesAndRemembersEmpty() {
        HomeLoadStatePolicy.WeeklyTracker<String> tracker =
            new HomeLoadStatePolicy.WeeklyTracker<>("日");
        HomeLoadStatePolicy.WeeklySelection<String> request = tracker.select("日");
        List<String> source = new ArrayList<>(Collections.singletonList("video"));
        tracker.cache(request, source);
        source.clear();

        HomeLoadStatePolicy.WeeklySelection<String> cached = tracker.select("日");
        assertTrue(cached.hasCachedValue());
        assertEquals(Collections.singletonList("video"), cached.cachedItems());
        cached.cachedItems().clear();
        assertEquals(
            Collections.singletonList("video"),
            tracker.select("日").cachedItems()
        );

        HomeLoadStatePolicy.WeeklySelection<String> emptyRequest = tracker.select("一");
        tracker.cache(emptyRequest, null);
        HomeLoadStatePolicy.WeeklySelection<String> empty = tracker.select("一");
        assertTrue(empty.hasCachedValue());
        assertTrue(empty.cachedItems().isEmpty());
    }

    @Test
    public void animeFirstFailureRetriesPageOne() {
        HomeLoadStatePolicy.AnimeTracker tracker = new HomeLoadStatePolicy.AnimeTracker();
        HomeLoadStatePolicy.AnimeRequest first = tracker.start(false);

        assertEquals(1, first.page());
        assertFalse(first.isPagination());
        assertTrue(tracker.fail(first));
        assertFalse(tracker.isRequesting());

        HomeLoadStatePolicy.AnimeRequest retry = tracker.start(false);
        assertEquals(1, retry.page());
    }

    @Test
    public void animeSuccessAdvancesPage() {
        HomeLoadStatePolicy.AnimeTracker tracker = new HomeLoadStatePolicy.AnimeTracker();
        HomeLoadStatePolicy.AnimeRequest first = tracker.start(false);

        assertTrue(tracker.succeed(first, 3));

        assertEquals(2, tracker.page());
        assertFalse(tracker.isRequesting());
        assertFalse(tracker.isExhausted());
    }

    @Test
    public void animePaginationFailureKeepsSamePageForRetry() {
        HomeLoadStatePolicy.AnimeTracker tracker = new HomeLoadStatePolicy.AnimeTracker();
        tracker.succeed(tracker.start(false), 3);
        HomeLoadStatePolicy.AnimeRequest pagination = tracker.start(true);

        assertTrue(pagination.isPagination());
        assertEquals(2, pagination.page());
        assertTrue(tracker.fail(pagination));
        assertEquals(2, tracker.page());

        HomeLoadStatePolicy.AnimeRequest retry = tracker.start(true);
        assertEquals(2, retry.page());
    }

    @Test
    public void animeYearResetInvalidatesRequestAndRestartsPageOne() {
        HomeLoadStatePolicy.AnimeTracker tracker = new HomeLoadStatePolicy.AnimeTracker();
        HomeLoadStatePolicy.AnimeRequest oldRequest = tracker.start(false);

        tracker.reset();

        assertFalse(tracker.accepts(oldRequest));
        assertEquals(1, tracker.page());
        assertEquals(1, tracker.start(false).page());
    }

    @Test
    public void animeStaleCallbackCannotChangeState() {
        HomeLoadStatePolicy.AnimeTracker tracker = new HomeLoadStatePolicy.AnimeTracker();
        HomeLoadStatePolicy.AnimeRequest stale = tracker.start(false);
        tracker.reset();
        HomeLoadStatePolicy.AnimeRequest current = tracker.start(false);

        assertFalse(tracker.succeed(stale, 5));
        assertEquals(1, tracker.page());
        assertTrue(tracker.isRequesting());
        assertTrue(tracker.accepts(current));
    }

    @Test
    public void animeInvalidateRejectsDestroyedViewCallback() {
        HomeLoadStatePolicy.AnimeTracker tracker = new HomeLoadStatePolicy.AnimeTracker();
        HomeLoadStatePolicy.AnimeRequest request = tracker.start(false);

        tracker.invalidate();

        assertFalse(tracker.accepts(request));
        assertFalse(tracker.succeed(request, 4));
        assertEquals(1, tracker.page());
        assertFalse(tracker.isRequesting());
    }
}
