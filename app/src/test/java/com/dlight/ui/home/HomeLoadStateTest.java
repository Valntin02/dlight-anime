package com.dlight.ui.home;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

public class HomeLoadStateTest {

    @Test
    public void nullAndEmptyListsHaveNoContent() {
        assertFalse(HomeLoadStatePolicy.hasContent(null));
        assertFalse(HomeLoadStatePolicy.hasContent(Collections.emptyList()));
    }

    @Test
    public void nonEmptyListHasContent() {
        assertTrue(HomeLoadStatePolicy.hasContent(Collections.singletonList("video")));
    }

    @Test
    public void errorIsShownOnlyWithoutOldContent() {
        assertTrue(HomeLoadStatePolicy.shouldShowError(false));
        assertFalse(HomeLoadStatePolicy.shouldShowError(true));
    }

    @Test
    public void weeklyCallbackMustMatchGenerationAndWeekday() {
        assertTrue(HomeLoadStatePolicy.isCurrentWeeklyRequest(3, "日", 3, "日"));
        assertFalse(HomeLoadStatePolicy.isCurrentWeeklyRequest(2, "日", 3, "日"));
        assertFalse(HomeLoadStatePolicy.isCurrentWeeklyRequest(3, "六", 3, "日"));
    }

    @Test
    public void paginationErrorPreservesExistingContent() {
        assertTrue(HomeLoadStatePolicy.shouldPreserveContentOnError(true, true));
        assertFalse(HomeLoadStatePolicy.shouldPreserveContentOnError(true, false));
        assertFalse(HomeLoadStatePolicy.shouldPreserveContentOnError(false, true));
    }
}
