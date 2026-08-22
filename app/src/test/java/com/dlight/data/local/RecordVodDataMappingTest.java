package com.dlight.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.dlight.data.model.VodData;
import com.google.gson.JsonParser;

import org.junit.Test;

public class RecordVodDataMappingTest {

    @Test
    public void playRecordObjectRoundTripPreservesAllFields() {
        VodData original = vodData("legacy-play-url", "{\"source\":\"object\"}");

        PlayRecord record = new PlayRecord(7, 3, original);
        VodData restored = record.toVodData();

        assertEquals(original.getVodPlayData().toString(), record.getVod_play_data());
        assertLegacyFields(original, restored);
        assertEquals(original.getVodPlayData(), restored.getVodPlayData());
    }

    @Test
    public void playRecordArrayRoundTripPreservesStructuredData() {
        VodData original = vodData("legacy-play-url", "[\"first\",{\"url\":\"second\"}]");

        VodData restored = new PlayRecord(7, 3, original).toVodData();

        assertEquals(original.getVodPlayData(), restored.getVodPlayData());
    }

    @Test
    public void playRecordNullStructuredDataRemainsNull() {
        VodData original = vodData("legacy-play-url", null);

        PlayRecord record = new PlayRecord(7, 3, original);

        assertNull(record.getVod_play_data());
        assertNull(record.toVodData().getVodPlayData());
        assertEquals("legacy-play-url", record.toVodData().getVod_play_url());
    }

    @Test
    public void playRecordMalformedStructuredDataKeepsLegacyUrl() {
        PlayRecord record = new PlayRecord(7, 3, vodData("legacy-play-url", null));
        record.setVod_play_data("{not-json");

        VodData restored = record.toVodData();

        assertNull(restored.getVodPlayData());
        assertEquals("legacy-play-url", restored.getVod_play_url());
    }

    @Test
    public void starRecordArrayRoundTripPreservesAllFields() {
        VodData original = vodData("legacy-star-url", "[\"first\",{\"url\":\"second\"}]");

        MyStarRecord record = new MyStarRecord(8, original);
        VodData restored = record.toVodData();

        assertEquals(original.getVodPlayData().toString(), record.getVod_play_data());
        assertLegacyFields(original, restored);
        assertEquals(original.getVodPlayData(), restored.getVodPlayData());
    }

    @Test
    public void starRecordObjectRoundTripPreservesStructuredData() {
        VodData original = vodData("legacy-star-url", "{\"source\":\"object\"}");

        VodData restored = new MyStarRecord(8, original).toVodData();

        assertEquals(original.getVodPlayData(), restored.getVodPlayData());
    }

    @Test
    public void starRecordNullStructuredDataRemainsNull() {
        VodData original = vodData("legacy-star-url", null);

        MyStarRecord record = new MyStarRecord(8, original);

        assertNull(record.getVod_play_data());
        assertNull(record.toVodData().getVodPlayData());
        assertEquals("legacy-star-url", record.toVodData().getVod_play_url());
    }

    @Test
    public void starRecordMalformedStructuredDataKeepsLegacyUrl() {
        MyStarRecord record = new MyStarRecord(8, vodData("legacy-star-url", null));
        record.setVod_play_data("[not-json");

        VodData restored = record.toVodData();

        assertNull(restored.getVodPlayData());
        assertEquals("legacy-star-url", restored.getVod_play_url());
    }

    @Test
    public void blankStoredStructuredDataKeepsLegacyUrls() {
        PlayRecord play = new PlayRecord(7, 3, vodData("legacy-play-url", null));
        play.setVod_play_data("   ");
        MyStarRecord star = new MyStarRecord(8, vodData("legacy-star-url", null));
        star.setVod_play_data("\t");

        assertNull(play.toVodData().getVodPlayData());
        assertEquals("legacy-play-url", play.toVodData().getVod_play_url());
        assertNull(star.toVodData().getVodPlayData());
        assertEquals("legacy-star-url", star.toVodData().getVod_play_url());
    }

    private static VodData vodData(String legacyUrl, String structuredJson) {
        VodData data = new VodData(
            101, "name", "pic", legacyUrl, "actor",
            "remarks", "2026", "content", "12"
        );
        if (structuredJson != null) {
            data.setVodPlayData(new JsonParser().parse(structuredJson));
        }
        return data;
    }

    private static void assertLegacyFields(VodData expected, VodData actual) {
        assertEquals(expected.getVod_id(), actual.getVod_id());
        assertEquals(expected.getVod_name(), actual.getVod_name());
        assertEquals(expected.getVod_pic(), actual.getVod_pic());
        assertEquals(expected.getVod_play_url(), actual.getVod_play_url());
        assertEquals(expected.getVod_actor(), actual.getVod_actor());
        assertEquals(expected.getVod_remarks(), actual.getVod_remarks());
        assertEquals(expected.getVod_year(), actual.getVod_year());
        assertEquals(expected.getVod_content(), actual.getVod_content());
        assertEquals(expected.getVod_total(), actual.getVod_total());
    }
}
