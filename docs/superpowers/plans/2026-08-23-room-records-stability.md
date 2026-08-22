# Room Records Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make history and favorites use one deterministic Room database, merge records stranded in the alternate legacy file without deleting it, and establish a non-destructive schema baseline.

**Architecture:** `play_record_db` becomes the canonical file because both entities already share one Room schema and playback commonly initializes it first. Both legacy accessors delegate to one singleton. A one-time importer opens `myStar_records` only when the file exists, copies missing rows by business key, then records completion in SharedPreferences; the legacy file remains untouched for recovery.

**Tech Stack:** Java, Room 2.6.1, Robolectric 4.13, JUnit 4.13.2, SharedPreferences

---

### Task 1: Use one canonical database and import the alternate legacy file

**Files:**
- Modify: `app/src/main/java/com/dlight/data/local/AppDatabase.java`
- Create: `app/src/main/java/com/dlight/data/local/LegacyRecordImporter.java`
- Create: `app/src/test/java/com/dlight/data/local/AppDatabaseTest.java`

- [ ] **Step 1: Write failing singleton and legacy import tests**

Under Robolectric, clear/close databases and the migration marker before every test. Cover:

- `getInstancePlayRecord` then `getInstanceMyStarRecord` returns the same object;
- reverse accessor order also returns the same object;
- a play record present only in legacy `myStar_records` is copied to canonical `play_record_db`;
- a favorite present only in legacy is copied;
- canonical rows with the same `(userId, vod_id)` win and are not duplicated;
- missing legacy file does not create it and still marks import complete;
- malformed/open failure leaves the marker unset so the next start can retry;
- a successful import leaves the legacy database file present and unchanged;
- a second initialization is idempotent.

Create file-backed fixtures directly with `Room.databaseBuilder(..., AppDatabase.class, fileName)` before invoking the static accessors. Run static initialization on a background executor so tests do not rely on `allowMainThreadQueries` production behavior.

- [ ] **Step 2: Run and verify red**

Expected failures: accessors currently depend on first call, no importer/marker/reset hook exists.

- [ ] **Step 3: Implement a single canonical singleton**

Use constants:

```java
static final String CANONICAL_DB_NAME = "play_record_db";
static final String LEGACY_DB_NAME = "myStar_records";
```

Both public accessors call one synchronized `getInstance(Context)`. Build only `CANONICAL_DB_NAME`, then invoke the importer before returning the instance.

Add a package-visible test hook that closes and nulls the singleton; production code must not call it.

- [ ] **Step 4: Implement non-destructive legacy import**

`LegacyRecordImporter.importIfNeeded(Context, AppDatabase canonical)` must:

1. Return when the SharedPreferences marker is true.
2. If `context.getDatabasePath(LEGACY_DB_NAME)` does not exist, mark success without creating the file.
3. Open the legacy file with a local Room instance using the same schema.
4. For each legacy play record, query canonical by `(userId, vod_id)`; when absent, set copied ID to `0` and insert.
5. Repeat for favorites.
6. Close the local legacy Room instance in `finally`.
7. Set the marker only after every read/copy succeeds.
8. Never delete, truncate, or write the legacy file.

Do not use `@Upsert` with copied primary IDs. Existing canonical business keys win; importer inserts only missing rows.

- [ ] **Step 5: Run tests, lint, and build**

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

- [ ] **Step 6: Commit Task 1**

```bash
git add app/src/main/java/com/dlight/data/local/AppDatabase.java \
  app/src/main/java/com/dlight/data/local/LegacyRecordImporter.java \
  app/src/test/java/com/dlight/data/local/AppDatabaseTest.java
git commit -m "fix: unify local history and favorite database"
```

### Task 2: Export Room schema and remove destructive fallback

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/java/com/dlight/data/local/AppDatabase.java`
- Create: `app/schemas/com.dlight.data.local.AppDatabase/1.json`
- Modify: `app/src/test/java/com/dlight/data/local/AppDatabaseTest.java`

- [ ] **Step 1: Configure the Room annotation processor schema directory**

Inside `android.defaultConfig`, add:

```groovy
javaCompileOptions {
    annotationProcessorOptions {
        arguments += ["room.schemaLocation": "$projectDir/schemas".toString()]
    }
}
```

Change `@Database(..., version = 1, exportSchema = true)`.

- [ ] **Step 2: Remove destructive migration behavior**

The canonical and legacy read builders must not call `fallbackToDestructiveMigration()`. Version 1 needs no migration today; future version changes must supply explicit `Migration` objects.

- [ ] **Step 3: Generate and inspect schema 1**

Run a clean app Java compile:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:clean :app:compileDebugJavaWithJavac --console=plain
```

Verify the generated JSON contains both `play_records` and `myStar_records`, their unique `(userId, vod_id)` indices, all current columns, and `version: 1`. Commit the schema file.

- [ ] **Step 4: Add a no-destructive-fallback regression test**

Create a version-2 incompatible fixture or inspect the builder through a package-visible factory so opening an unsupported newer schema throws instead of silently deleting rows. The test must prove existing data is not erased merely to make Room open.

- [ ] **Step 5: Run full verification**

Run unit tests, `lintDebug`, and `assembleDebug`. Run `git diff --check` and confirm no database file was added to Git.

- [ ] **Step 6: Commit Task 2**

```bash
git add app/build.gradle app/src/main/java/com/dlight/data/local/AppDatabase.java \
  app/src/test/java/com/dlight/data/local/AppDatabaseTest.java \
  app/schemas/com.dlight.data.local.AppDatabase/1.json
git commit -m "chore: establish Room schema baseline"
```

## Completion gate

- Both accessors return the same canonical `play_record_db` instance in either call order.
- Existing records from `myStar_records` are merged by business key and the legacy file is retained.
- Import is idempotent and retries after failure.
- Room schema version 1 is exported and tracked.
- No builder uses destructive fallback.
- Unit tests, `lintDebug`, and `assembleDebug` succeed.
- No device database, App data, or legacy file is deleted during verification.
