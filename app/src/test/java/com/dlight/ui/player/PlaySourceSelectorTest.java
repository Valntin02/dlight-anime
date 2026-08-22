package com.dlight.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class PlaySourceSelectorTest {
    @Test
    public void selectUrls_prefersLzm3u8OverOtherSources() {
        JsonElement playData = json("["
            + source("normal", "https://cdn.example.com/normal.mp4") + ","
            + source("bfzym3u8", "https://cdn.example.com/bfzy.m3u8") + ","
            + source("otherm3u8", "https://cdn.example.com/generic.m3u8") + ","
            + source("lzm3u8", "https://cdn.example.com/lz.m3u8")
            + "]");

        assertEquals(
            Collections.singletonList("https://cdn.example.com/lz.m3u8"),
            PlaySourceSelector.selectUrls(playData, null, null, null)
        );
    }

    @Test
    public void selectUrls_ordersRemainingPrioritiesAndKeepsEqualPriorityStable() {
        assertEquals(
            Collections.singletonList("https://cdn.example.com/generic.m3u8"),
            PlaySourceSelector.selectUrls(
                json("["
                    + source("bfzym3u8", "https://cdn.example.com/bfzy.m3u8") + ","
                    + source("normal", "https://cdn.example.com/normal.mp4") + ","
                    + source("otherm3u8", "https://cdn.example.com/generic.m3u8")
                    + "]"),
                null,
                null,
                null
            )
        );
        assertEquals(
            Collections.singletonList("https://cdn.example.com/normal.mp4"),
            PlaySourceSelector.selectUrls(
                json("["
                    + source("bfzym3u8", "https://cdn.example.com/bfzy.m3u8") + ","
                    + source("normal", "https://cdn.example.com/normal.mp4")
                    + "]"),
                null,
                null,
                null
            )
        );
        assertEquals(
            Collections.singletonList("https://cdn.example.com/first.m3u8"),
            PlaySourceSelector.selectUrls(
                json("["
                    + source("alpham3u8", "https://cdn.example.com/first.m3u8") + ","
                    + source("betam3u8", "https://cdn.example.com/second.m3u8")
                    + "]"),
                null,
                null,
                null
            )
        );
    }

    @Test
    public void selectUrls_skipsHigherPrioritySourceWithoutPlayableUrls() {
        JsonElement playData = json("["
            + "{\"from\":\"lzm3u8\",\"episodes\":["
            + "{\"url\":null},{\"url\":\" \"},{\"url\":\"null\"},"
            + "{\"url\":\"undefined\"},{\"url\":\"ftp://cdn.example.com/1.m3u8\"},"
            + "{\"url\":\"javascript:alert(1)\"}]},"
            + "{\"from\":\"bfzym3u8\",\"episodes\":["
            + "{\"url\":\" https://cdn.example.com/1.m3u8 \"}]}"
            + "]");

        assertEquals(
            Collections.singletonList("https://cdn.example.com/1.m3u8"),
            PlaySourceSelector.selectUrls(playData, null, null, null)
        );
    }

    @Test
    public void selectUrls_acceptsObjectShapedPlayData() {
        JsonElement playData = json(source("normal", "https://cdn.example.com/movie.mp4"));

        assertEquals(
            Collections.singletonList("https://cdn.example.com/movie.mp4"),
            PlaySourceSelector.selectUrls(playData, null, null, null)
        );
    }

    @Test
    public void selectUrls_preservesEpisodeOrderAndTrimsWhitespace() {
        JsonElement playData = json("{\"from\":\"normal\",\"episodes\":["
            + "{\"url\":\" https://cdn.example.com/2.mp4 \"},"
            + "{\"url\":\"https://cdn.example.com/1.mp4?token=abc\"}]}");

        assertEquals(
            Arrays.asList(
                "https://cdn.example.com/2.mp4",
                "https://cdn.example.com/1.mp4?token=abc"
            ),
            PlaySourceSelector.selectUrls(playData, null, null, null)
        );
    }

    @Test
    public void isPlayableUrl_acceptsHttpAndHttpsAndRejectsInvalidValues() {
        assertTrue(PlaySourceSelector.isPlayableUrl("https://cdn.example.com/a.m3u8?token=abc"));
        assertTrue(PlaySourceSelector.isPlayableUrl("http://10.0.2.2:8080/debug.mp4"));
        assertTrue(PlaySourceSelector.isPlayableUrl("https://cdn.example.com:65535/a.mp4"));

        assertFalse(PlaySourceSelector.isPlayableUrl(null));
        assertFalse(PlaySourceSelector.isPlayableUrl(" "));
        assertFalse(PlaySourceSelector.isPlayableUrl("null"));
        assertFalse(PlaySourceSelector.isPlayableUrl("undefined"));
        assertFalse(PlaySourceSelector.isPlayableUrl("https:///missing-host.mp4"));
        assertFalse(PlaySourceSelector.isPlayableUrl("ftp://cdn.example.com/a.mp4"));
        assertFalse(PlaySourceSelector.isPlayableUrl("javascript:alert(1)"));
        assertFalse(PlaySourceSelector.isPlayableUrl("https://user:secret@cdn.example.com/a.mp4"));
        assertFalse(PlaySourceSelector.isPlayableUrl("https://cdn.example.com/a.mp4#episode"));
        assertFalse(PlaySourceSelector.isPlayableUrl("https://cdn.example.com:0/a.mp4"));
        assertFalse(PlaySourceSelector.isPlayableUrl("https://cdn.example.com:65536/a.mp4"));
    }

    @Test
    public void selectUrls_usesLegacyUrlWhenStructuredSourcesAreNotPlayable() {
        JsonElement playData = json("[{\"from\":\"lzm3u8\",\"episodes\":["
            + "{\"url\":\"undefined\"}]}]");

        assertEquals(
            Collections.singletonList("https://legacy.example.com/movie.mp4"),
            PlaySourceSelector.selectUrls(
                playData,
                " https://legacy.example.com/movie.mp4 ",
                null,
                null
            )
        );
    }

    @Test
    public void selectUrls_expandsLegacyEpisodeUrlUsingRemarksThenTotalThenOne() {
        assertEquals(
            Arrays.asList(
                "https://legacy.example.com/第01集.mp4",
                "https://legacy.example.com/第02集.mp4",
                "https://legacy.example.com/第03集.mp4"
            ),
            PlaySourceSelector.selectUrls(
                null,
                "https://legacy.example.com/第01集.mp4",
                "更新至3集",
                "9"
            )
        );
        assertEquals(
            Arrays.asList(
                "https://legacy.example.com/第01集.mp4",
                "https://legacy.example.com/第02集.mp4"
            ),
            PlaySourceSelector.selectUrls(
                null,
                "https://legacy.example.com/第01集.mp4",
                "完结",
                "共2集"
            )
        );
        assertEquals(
            Collections.singletonList("https://legacy.example.com/第01集.mp4"),
            PlaySourceSelector.selectUrls(
                null,
                "https://legacy.example.com/第01集.mp4",
                null,
                null
            )
        );
    }

    @Test
    public void selectUrls_returnsEmptyForInvalidStructuredAndLegacyValues() {
        JsonElement playData = json("[{\"from\":\"lzm3u8\",\"episodes\":["
            + "{\"url\":\"https:///missing-host.mp4\"}]}]");

        assertEquals(
            Collections.emptyList(),
            PlaySourceSelector.selectUrls(playData, "undefined", "12", "12")
        );
    }

    @Test(expected = UnsupportedOperationException.class)
    public void selectUrls_returnsUnmodifiableSuccessfulResult() {
        List<String> urls = PlaySourceSelector.selectUrls(
            null,
            "https://legacy.example.com/movie.mp4",
            null,
            null
        );

        urls.add("https://legacy.example.com/other.mp4");
    }

    @Test
    public void parseEpisodeCount_extractsEpisodeUnitWithoutJoiningOtherNumbers() {
        assertEquals(
            12,
            PlaySourceSelector.parseEpisodeCount("更新至12集（2026-08-23）", "24")
        );
        assertEquals(12, PlaySourceSelector.parseEpisodeCount("第2季 更新至12集", "24"));
        assertEquals(8, PlaySourceSelector.parseEpisodeCount("更新至8话", "24"));
        assertEquals(6, PlaySourceSelector.parseEpisodeCount("全6期", "24"));
    }

    @Test
    public void parseEpisodeCount_acceptsPureNumericTotal() {
        assertEquals(24, PlaySourceSelector.parseEpisodeCount("完结", " 24 "));
        assertEquals(24, PlaySourceSelector.parseEpisodeCount("20260823", "24"));
        assertEquals(24, PlaySourceSelector.parseEpisodeCount("12", "24"));
        assertEquals(1, PlaySourceSelector.parseEpisodeCount(null, "0"));
    }

    @Test
    public void parseEpisodeCount_clampsLargePositiveCount() {
        assertEquals(2000, PlaySourceSelector.parseEpisodeCount("更新至999999集", "24"));

        List<String> urls = PlaySourceSelector.selectUrls(
            null,
            "https://legacy.example.com/第01集.mp4",
            "999999集",
            null
        );

        assertEquals(2000, urls.size());
        assertEquals("https://legacy.example.com/第2000集.mp4", urls.get(1999));
    }

    @Test
    public void parseEpisodeCount_fallsBackAfterOverflow() {
        assertEquals(
            18,
            PlaySourceSelector.parseEpisodeCount(
                "更新至999999999999999999999999集",
                "18"
            )
        );
        assertEquals(
            1,
            PlaySourceSelector.parseEpisodeCount(null, "999999999999999999999999")
        );
    }

    private static JsonElement json(String value) {
        return new JsonParser().parse(value);
    }

    private static String source(String from, String url) {
        return "{\"from\":\"" + from + "\",\"episodes\":[{\"url\":\"" + url + "\"}]}";
    }
}
