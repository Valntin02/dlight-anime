package com.dlight.feature.search;

import java.util.Collection;

final class SearchRequestTracker {
    enum State {
        HISTORY,
        LOADING,
        CONTENT,
        EMPTY,
        ERROR,
        IGNORED
    }

    static final class Request {
        private final int generation;
        private final String keyword;
        private final State state;

        private Request(int generation, String keyword, State state) {
            this.generation = generation;
            this.keyword = keyword;
            this.state = state;
        }

        int getGeneration() {
            return generation;
        }

        String getKeyword() {
            return keyword;
        }

        State getState() {
            return state;
        }

        boolean shouldRequest() {
            return state == State.LOADING;
        }
    }

    private int generation;
    private String lastKeyword = "";
    private boolean destroyed;

    Request begin(String input) {
        generation++;
        if (destroyed) {
            return new Request(generation, "", State.IGNORED);
        }

        String keyword = input == null ? "" : input.trim();
        if (keyword.isEmpty()) {
            lastKeyword = "";
            return new Request(generation, "", State.HISTORY);
        }

        lastKeyword = keyword;
        return new Request(generation, keyword, State.LOADING);
    }

    Request retry() {
        if (destroyed || lastKeyword.isEmpty()) {
            return new Request(generation, "", State.IGNORED);
        }
        return begin(lastKeyword);
    }

    State onSuccess(int requestGeneration, int code, Collection<?> content) {
        if (!isCurrent(requestGeneration)) {
            return State.IGNORED;
        }
        if (code != 200) {
            return State.ERROR;
        }
        return content == null || content.isEmpty() ? State.EMPTY : State.CONTENT;
    }

    State onFailure(int requestGeneration) {
        return isCurrent(requestGeneration) ? State.ERROR : State.IGNORED;
    }

    boolean isCurrent(int requestGeneration) {
        return !destroyed && requestGeneration == generation;
    }

    void destroy() {
        destroyed = true;
        generation++;
    }
}
