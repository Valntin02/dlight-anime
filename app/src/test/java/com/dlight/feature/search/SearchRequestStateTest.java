package com.dlight.feature.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.dlight.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class SearchRequestStateTest {
    @Test
    public void layoutOverlaysLoadStateOnWeightedResultsFrame() {
        View layout = LayoutInflater.from(RuntimeEnvironment.getApplication())
            .inflate(R.layout.activity_search, null);
        View results = layout.findViewById(R.id.recyclerViewResults);
        View loadState = layout.findViewById(R.id.search_load_state);

        assertTrue(results.getParent() instanceof FrameLayout);
        assertSame(results.getParent(), loadState.getParent());
        assertEquals(View.GONE, results.getVisibility());
        assertEquals(View.GONE, loadState.getVisibility());
        assertEquals(View.VISIBLE, layout.findViewById(R.id.textHistory).getVisibility());
        assertEquals(View.VISIBLE, layout.findViewById(R.id.historyContainer).getVisibility());
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
            ((FrameLayout) results.getParent()).getLayoutParams();
        assertEquals(0, params.height);
        assertEquals(1f, params.weight, 0f);
    }

    @Test
    public void oldResponseIsIgnoredAndNewestResponseIsAccepted() {
        SearchRequestTracker tracker = new SearchRequestTracker();

        SearchRequestTracker.Request oldRequest = tracker.begin(" old ");
        SearchRequestTracker.Request newRequest = tracker.begin("new");

        assertEquals(SearchRequestTracker.State.LOADING, oldRequest.getState());
        assertEquals("old", oldRequest.getKeyword());
        assertTrue(oldRequest.shouldRequest());
        assertEquals(
            SearchRequestTracker.State.IGNORED,
            tracker.onSuccess(oldRequest.getGeneration(), 200, Collections.singletonList("old"))
        );
        assertEquals(
            SearchRequestTracker.State.CONTENT,
            tracker.onSuccess(newRequest.getGeneration(), 200, Collections.singletonList("new"))
        );
    }

    @Test
    public void successSeparatesContentFromNullAndEmptyResults() {
        SearchRequestTracker tracker = new SearchRequestTracker();

        SearchRequestTracker.Request content = tracker.begin("content");
        assertEquals(
            SearchRequestTracker.State.CONTENT,
            tracker.onSuccess(content.getGeneration(), 200, Arrays.asList("one", "two"))
        );

        SearchRequestTracker.Request empty = tracker.begin("empty");
        assertEquals(
            SearchRequestTracker.State.EMPTY,
            tracker.onSuccess(empty.getGeneration(), 200, Collections.emptyList())
        );

        SearchRequestTracker.Request nullContent = tracker.begin("null");
        assertEquals(
            SearchRequestTracker.State.EMPTY,
            tracker.onSuccess(nullContent.getGeneration(), 200, null)
        );
    }

    @Test
    public void businessAndTransportErrorsRetryTheSameTrimmedKeyword() {
        SearchRequestTracker tracker = new SearchRequestTracker();

        SearchRequestTracker.Request business = tracker.begin("  retry me  ");
        assertEquals(
            SearchRequestTracker.State.ERROR,
            tracker.onSuccess(business.getGeneration(), 500, Collections.singletonList("ignored"))
        );

        SearchRequestTracker.Request businessRetry = tracker.retry();
        assertTrue(businessRetry.shouldRequest());
        assertEquals("retry me", businessRetry.getKeyword());

        assertEquals(
            SearchRequestTracker.State.ERROR,
            tracker.onFailure(businessRetry.getGeneration())
        );
        SearchRequestTracker.Request transportRetry = tracker.retry();
        assertEquals("retry me", transportRetry.getKeyword());
        assertTrue(transportRetry.getGeneration() > businessRetry.getGeneration());
    }

    @Test
    public void blankKeywordInvalidatesRequestAndRestoresHistory() {
        SearchRequestTracker tracker = new SearchRequestTracker();
        SearchRequestTracker.Request oldRequest = tracker.begin("old");

        SearchRequestTracker.Request blank = tracker.begin("  \t  ");

        assertFalse(blank.shouldRequest());
        assertEquals(SearchRequestTracker.State.HISTORY, blank.getState());
        assertEquals("", blank.getKeyword());
        assertEquals(
            SearchRequestTracker.State.IGNORED,
            tracker.onFailure(oldRequest.getGeneration())
        );
    }

    @Test
    public void destroyInvalidatesCallbacksAndDisablesRetry() {
        SearchRequestTracker tracker = new SearchRequestTracker();
        SearchRequestTracker.Request request = tracker.begin("anime");

        tracker.destroy();

        assertEquals(
            SearchRequestTracker.State.IGNORED,
            tracker.onSuccess(request.getGeneration(), 200, Collections.singletonList("late"))
        );
        assertEquals(SearchRequestTracker.State.IGNORED, tracker.retry().getState());
        assertFalse(tracker.retry().shouldRequest());
    }
}
