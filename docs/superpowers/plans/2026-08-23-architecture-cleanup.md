# Targeted Architecture Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the disconnected duplicate Retrofit stack and make app lint errors fail the build after fixing the known layout error.

**Architecture:** Keep `RetrofitClient/ApiService` as the only active API boundary. Delete only files proven unreachable. Preserve network model classes still referenced by UI/player code.

**Tech Stack:** Java, Gradle, Android Lint, ConstraintLayout

---

### Task 1: Remove the unused duplicate API stack

**Files:**
- Delete: `app/src/main/java/com/dlight/network/NetworkManager.java`
- Delete: `app/src/main/java/com/dlight/network/ApiConfig.java`
- Delete: `app/src/main/java/com/dlight/network/api/AuthApi.java`
- Delete: `app/src/main/java/com/dlight/network/api/CommentApi.java`
- Delete: `app/src/main/java/com/dlight/network/api/DanmakuApi.java`
- Delete: `app/src/main/java/com/dlight/network/api/VodApi.java`
- Modify: `app/build.gradle`
- Modify: `app/src/test/java/com/dlight/network/HttpClientFactoryTest.java`

- [ ] Confirm no production/test references outside the deletion set.
- [ ] Delete the stack; do not delete `network/model` classes.
- [ ] Remove `logging-interceptor` dependency. Change the client test to assert interceptor class names do not contain `HttpLoggingInterceptor`, without importing that library.
- [ ] Run Debug/Release unit tests and builds plus dependency insight proving logging-interceptor is absent.
- [ ] Commit: `refactor: remove unused duplicate API stack`.

### Task 2: Fix app lint error and enforce lint failures

**Files:**
- Modify: `app/src/main/res/layout/fragment_comment_bottom_sheet.xml`
- Modify: `app/build.gradle`

- [ ] Add complete start/end/top/bottom constraints for title, recycler, and included input layout while preserving the existing 500dp recycler intent.
- [ ] Replace deprecated `lintOptions { abortOnError false }` with `lint { abortOnError true }`.
- [ ] Run `:app:lintDebug` and `:app:lintRelease`; both must succeed with zero lint errors in their reports. Warnings may remain.
- [ ] Run Debug/Release tests and builds.
- [ ] Commit: `chore: enforce app lint error gate`.

## Completion gate

- Only one Retrofit/API boundary remains active.
- No logging-interceptor dependency remains.
- Referenced network model classes remain intact.
- App lint reports zero errors and aborts future builds on new errors.
- Debug/Release tests, lint, and builds succeed.
