# Room Structured Playback Sources Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve structured `vodPlayData` through history and favorite persistence while migrating existing Room v1 data without loss.

**Architecture:** Add one nullable JSON text column to each record table and a strict `Migration(1, 2)`. Entities own safe conversion back to `VodData`; corrupt/missing JSON becomes null so the tested legacy `vod_play_url` fallback remains available. The legacy file importer treats the new column as optional.

**Tech Stack:** Java, Room 2.6.1, Gson, Robolectric 4.13, JUnit 4.13.2

---

### Task 1: Persist and safely restore structured source JSON

**Files:**
- Modify: `app/src/main/java/com/dlight/data/local/PlayRecord.java`
- Modify: `app/src/main/java/com/dlight/data/local/MyStarRecord.java`
- Create: `app/src/main/java/com/dlight/network/ApiGsonFactory.java`
- Modify: `app/src/main/java/com/dlight/data/remote/RetrofitClient.java`
- Create: `app/src/test/java/com/dlight/data/local/RecordVodDataMappingTest.java`

- [ ] **Step 1: Write failing entity mapping tests**

Cover both record types:

- constructor stores `VodData.getVodPlayData().toString()`;
- `toVodData()` preserves all legacy fields and structured JSON;
- null structured data remains null;
- malformed stored JSON returns a `VodData` with null structured data and the original legacy URL;
- JSON object and array shapes both round-trip.

- [ ] **Step 2: Implement nullable `vod_play_data` and mapping**

Add a nullable `String vod_play_data` field with getter/setter to each entity. Constructors serialize only non-null JSON. Add:

```java
public VodData toVodData() {
    VodData data = new VodData(
        vod_id, vod_name, vod_pic, vod_play_url, vod_actor,
        vod_remarks, vod_year, vod_content, vod_total
    );
    if (vod_play_data != null && !vod_play_data.trim().isEmpty()) {
        try {
            data.setVodPlayData(JsonParser.parseString(vod_play_data));
        } catch (RuntimeException ignored) {
            data.setVodPlayData(null);
        }
    }
    return data;
}
```

Do not throw for old or malformed persisted JSON.

The new field is local persistence only. `ApiGsonFactory` must configure matching serialization and deserialization exclusion strategies for `vod_play_data` declared by `PlayRecord` and `MyStarRecord`, and `RetrofitClient` must use that Gson instance. Tests must prove sync JSON omits the field and backend array/object/string values under that key are ignored without changing other fields.

- [ ] **Step 3: Run focused/full tests and commit**

Do not treat this task as independently releasable until Task 2 migration is present; the entity and migration changes are one atomic delivery even if review commits remain separate.

Commit message: `fix: keep structured sources local to Room`.

### Task 2: Add explicit Room migration 1 to 2

**Files:**
- Modify: `app/src/main/java/com/dlight/data/local/AppDatabase.java`
- Modify: `app/src/main/java/com/dlight/data/local/LegacyRecordImporter.java`
- Modify: `app/src/test/java/com/dlight/data/local/AppDatabaseTest.java`
- Create: `app/schemas/com.dlight.data.local.AppDatabase/2.json`

- [ ] **Step 1: Write a failing v1 migration preservation test**

Create the exact exported v1 tables/indices in a file database, insert one play record and one favorite with non-default IDs, sync flags, episode, and legacy URLs, set `user_version=1`, then open through `AppDatabase` v2.

Assert:

- both rows remain with the same IDs and business keys;
- episode and sync flags remain;
- new `vod_play_data` columns are null;
- unique indices remain enforced;
- no destructive fallback occurs.

- [ ] **Step 2: Implement Migration 1 to 2**

Set `@Database(version = 2, exportSchema = true)` and define:

```java
static final Migration MIGRATION_1_2 = new Migration(1, 2) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("ALTER TABLE play_records ADD COLUMN vod_play_data TEXT");
        database.execSQL("ALTER TABLE myStar_records ADD COLUMN vod_play_data TEXT");
    }
};
```

Register it on the canonical builder. Keep destructive fallback absent.

- [ ] **Step 3: Keep legacy snapshot import compatible**

The importer must not require `vod_play_data`. When the optional column exists, copy it; otherwise set null. Required v1 columns remain unchanged.

- [ ] **Step 4: Generate schema 2 and verify migration**

Clean compile, verify `2.json` includes both nullable text columns and version 2, then run focused/full tests, lint, and Debug build.

- [ ] **Step 5: Commit**

Commit message: `feat: migrate Room records to structured sources`.

### Task 3: Use entity mapping at every record-to-VodData boundary

**Files:**
- Modify: `app/src/main/java/com/dlight/data/local/PlayRecordAdapter.java`
- Modify: `app/src/main/java/com/dlight/data/local/PlayRecordActivity.java`
- Modify: `app/src/main/java/com/dlight/data/local/MyStarActivity.java`
- Test: `app/src/test/java/com/dlight/data/local/RecordVodDataMappingTest.java`

- [ ] **Step 1: Replace manual reconstruction**

Replace every `new VodData(record.get...)` reconstruction with `record.toVodData()`. Do not change network synchronization or UI behavior.

- [ ] **Step 2: Prove no manual record reconstruction remains**

Use `rg` to confirm record-based `new VodData(...)` calls are gone from the three files. Run all tests, lint, and Debug build.

- [ ] **Step 3: Commit**

Commit message: `refactor: restore structured sources from records`.

## Completion gate

- Room v1 rows migrate to v2 without data loss.
- New history/favorite rows preserve structured source JSON.
- Old or malformed JSON safely falls back to the legacy URL.
- Legacy snapshot import supports v1 and v2 files.
- All record-to-`VodData` UI paths use entity mapping.
- Schema 1 and 2 are tracked; destructive fallback remains absent.
- Unit tests, `lintDebug`, and `assembleDebug` succeed.
