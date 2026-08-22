package com.dlight.ui.home;

import java.util.List;

final class HomeLoadStatePolicy {

    private HomeLoadStatePolicy() {
    }

    static boolean hasContent(List<?> items) {
        return items != null && !items.isEmpty();
    }

    static boolean shouldShowError(boolean hasContent) {
        return !hasContent;
    }

    static boolean isCurrentWeeklyRequest(
        int callbackGeneration,
        String callbackWeekday,
        int currentGeneration,
        String selectedWeekday
    ) {
        return callbackGeneration == currentGeneration
            && callbackWeekday != null
            && callbackWeekday.equals(selectedWeekday);
    }

    static boolean shouldPreserveContentOnError(boolean pagination, boolean hasContent) {
        return pagination && hasContent;
    }
}
