package com.dlight.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.dlight.data.local.MyStarRecord;
import com.dlight.data.local.PlayRecord;
import com.google.gson.Gson;

import org.junit.Test;

public class ApiGsonFactoryTest {

    @Test
    public void serializationOmitsLocalStructuredDataFromBothRecordTypes() {
        Gson gson = ApiGsonFactory.create();
        PlayRecord play = new PlayRecord();
        play.setVod_id(101);
        play.setVod_play_url("legacy-play-url");
        play.setVod_play_data("{\"local\":true}");
        MyStarRecord star = new MyStarRecord();
        star.setVod_id(202);
        star.setVod_play_url("legacy-star-url");
        star.setVod_play_data("[\"local\"]");

        String playJson = gson.toJson(play);
        String starJson = gson.toJson(star);

        assertFalse(playJson.contains("vod_play_data"));
        assertTrue(playJson.contains("\"vod_id\":101"));
        assertTrue(playJson.contains("\"vod_play_url\":\"legacy-play-url\""));
        assertFalse(starJson.contains("vod_play_data"));
        assertTrue(starJson.contains("\"vod_id\":202"));
        assertTrue(starJson.contains("\"vod_play_url\":\"legacy-star-url\""));
    }

    @Test
    public void deserializationIgnoresEveryBackendStructuredDataShape() {
        Gson gson = ApiGsonFactory.create();
        String[] structuredValues = {
            "[\"array\"]",
            "{\"kind\":\"object\"}",
            "\"string\""
        };

        for (String structuredValue : structuredValues) {
            String json = "{\"vod_id\":303,\"vod_name\":\"backend\","
                + "\"vod_play_url\":\"legacy-url\",\"vod_play_data\":"
                + structuredValue + "}";

            PlayRecord play = gson.fromJson(json, PlayRecord.class);
            MyStarRecord star = gson.fromJson(json, MyStarRecord.class);

            assertNull(play.getVod_play_data());
            assertEquals(303, play.getVod_id());
            assertEquals("backend", play.getVod_name());
            assertEquals("legacy-url", play.getVod_play_url());
            assertNull(star.getVod_play_data());
            assertEquals(303, star.getVod_id());
            assertEquals("backend", star.getVod_name());
            assertEquals("legacy-url", star.getVod_play_url());
        }
    }
}
