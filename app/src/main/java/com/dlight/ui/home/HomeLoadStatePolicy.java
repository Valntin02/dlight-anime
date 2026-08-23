package com.dlight.ui.home;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class HomeLoadStatePolicy {

    private HomeLoadStatePolicy() {
    }

    static boolean hasContent(List<?> items) {
        return items != null && !items.isEmpty();
    }

    static boolean shouldShowError(boolean hasContent) {
        return !hasContent;
    }

    static final class WeeklyTracker<T> {
        private final Map<String, List<T>> cache = new HashMap<>();
        private int generation;
        private String selectedWeekday;

        WeeklyTracker(String initialWeekday) {
            selectedWeekday = initialWeekday;
        }

        WeeklySelection<T> select(String weekday) {
            selectedWeekday = weekday;
            generation++;
            boolean hasCachedValue = cache.containsKey(weekday);
            List<T> cachedItems = hasCachedValue
                ? copy(cache.get(weekday))
                : Collections.emptyList();
            return new WeeklySelection<>(
                generation,
                weekday,
                hasCachedValue,
                cachedItems
            );
        }

        boolean accepts(WeeklySelection<T> selection) {
            return selection != null
                && selection.generation == generation
                && selection.weekday.equals(selectedWeekday);
        }

        void cache(WeeklySelection<T> selection, List<T> items) {
            if (accepts(selection)) {
                cache.put(selection.weekday, copy(items));
            }
        }

        String selectedWeekday() {
            return selectedWeekday;
        }

        void invalidate() {
            generation++;
        }

        private List<T> copy(List<T> items) {
            return items == null ? new ArrayList<>() : new ArrayList<>(items);
        }
    }

    static final class WeeklySelection<T> {
        private final int generation;
        private final String weekday;
        private final boolean hasCachedValue;
        private final List<T> cachedItems;

        private WeeklySelection(
            int generation,
            String weekday,
            boolean hasCachedValue,
            List<T> cachedItems
        ) {
            this.generation = generation;
            this.weekday = weekday;
            this.hasCachedValue = hasCachedValue;
            this.cachedItems = cachedItems;
        }

        String weekday() {
            return weekday;
        }

        boolean hasCachedValue() {
            return hasCachedValue;
        }

        List<T> cachedItems() {
            return new ArrayList<>(cachedItems);
        }
    }

    static final class AnimeTracker {
        private int page = 1;
        private int generation;
        private boolean requesting;
        private boolean exhausted;

        AnimeRequest start(boolean hasContent) {
            if (requesting || exhausted) return null;
            requesting = true;
            generation++;
            return new AnimeRequest(generation, page, hasContent);
        }

        boolean accepts(AnimeRequest request) {
            return request != null
                && requesting
                && request.generation == generation
                && request.page == page;
        }

        boolean succeed(AnimeRequest request, int totalPage) {
            if (!accepts(request)) return false;
            requesting = false;
            page++;
            exhausted = page > totalPage;
            return true;
        }

        boolean fail(AnimeRequest request) {
            if (!accepts(request)) return false;
            requesting = false;
            return true;
        }

        void reset() {
            generation++;
            page = 1;
            requesting = false;
            exhausted = false;
        }

        void invalidate() {
            generation++;
            requesting = false;
        }

        int page() {
            return page;
        }

        boolean isRequesting() {
            return requesting;
        }

        boolean isExhausted() {
            return exhausted;
        }
    }

    static final class AnimeRequest {
        private final int generation;
        private final int page;
        private final boolean pagination;

        private AnimeRequest(int generation, int page, boolean pagination) {
            this.generation = generation;
            this.page = page;
            this.pagination = pagination;
        }

        int page() {
            return page;
        }

        boolean isPagination() {
            return pagination;
        }
    }

}
