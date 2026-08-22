package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

public class HlsPlaylistParserTest {
    @Test
    public void parsesMediaUrisInOrderAndSkipsAdsCommentsAndBlankLines() throws Exception {
        HlsPlaylistParser.Result result = HlsPlaylistParser.parse(
                "#EXTM3U\n"
                        + "\n"
                        + "# a comment\n"
                        + "#EXTINF:4,\n"
                        + "seg-1?id=7\n"
                        + "#EXTINF:4,\n"
                        + "/video/seg-2\n"
                        + "https://media.example.net/segment?id=3\n"
                        + "skip-ADJUMP-segment\n",
                URI.create("https://cdn.example.com/show/index.m3u8"));

        assertEquals(Arrays.asList(
                "https://cdn.example.com/show/seg-1?id=7",
                "https://cdn.example.com/video/seg-2",
                "https://media.example.net/segment?id=3"), result.getSegments());
        assertTrue(result.getVariants().isEmpty());
    }

    @Test
    public void parsesAndStablySortsMasterVariantsByDescendingBandwidth() throws Exception {
        HlsPlaylistParser.Result result = HlsPlaylistParser.parse(
                "#EXTM3U\n"
                        + "#EXT-X-STREAM-INF:BANDWIDTH=800000\n"
                        + "low.m3u8\n"
                        + "#EXT-X-STREAM-INF:BANDWIDTH=2400000\n"
                        + "# variant comment\n"
                        + "high-first.m3u8\n"
                        + "#EXT-X-STREAM-INF:BANDWIDTH=2400000\n"
                        + "high-second.m3u8\n",
                URI.create("https://cdn.example.com/show/master.m3u8"));

        assertTrue(result.getSegments().isEmpty());
        assertEquals(3, result.getVariants().size());
        assertEquals("https://cdn.example.com/show/high-first.m3u8",
                result.getVariants().get(0).getUrl());
        assertEquals(2400000L, result.getVariants().get(0).getBandwidth());
        assertEquals("https://cdn.example.com/show/high-second.m3u8",
                result.getVariants().get(1).getUrl());
        assertEquals("https://cdn.example.com/show/low.m3u8",
                result.getVariants().get(2).getUrl());
    }

    @Test
    public void masterPlaylistNeverReturnsMediaLines() throws Exception {
        HlsPlaylistParser.Result result = HlsPlaylistParser.parse(
                "#EXTM3U\nsegment-before.ts\n"
                        + "#EXT-X-STREAM-INF:BANDWIDTH=10\nvariant.m3u8\n"
                        + "segment-after.ts\n",
                URI.create("https://cdn.example.com/master.m3u8"));

        assertTrue(result.getSegments().isEmpty());
        assertEquals(1, result.getVariants().size());
    }

    @Test
    public void rejectsUnsupportedMapByteRangeAndEncryptedKey() throws Exception {
        assertUnsupported("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\nsegment.m4s\n",
                "暂不支持 fMP4 下载");
        assertUnsupported("#EXTM3U\n#EXT-X-BYTERANGE:1000@0\nsegment.ts\n",
                "暂不支持字节范围分片");
        assertUnsupported("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\nsegment.ts\n",
                "暂不支持加密 HLS 下载");
        assertUnsupported("#EXTM3U\n#EXT-X-KEY:URI=\"key\"\nsegment.ts\n",
                "暂不支持加密 HLS 下载");
        assertUnsupported("#EXTM3U\n#EXT-X-KEY:METHOD=NONEE\nsegment.ts\n",
                "暂不支持加密 HLS 下载");
        assertUnsupported(
                "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key?METHOD=NONE\"\nsegment.ts\n",
                "暂不支持加密 HLS 下载");
        assertUnsupported(
                "#EXTM3U\n#EXT-X-KEY:URI=\"https://cdn/key,METHOD=NONE,tail\"\nsegment.ts\n",
                "暂不支持加密 HLS 下载");
        assertUnsupported(
                "#EXTM3U\n#EXT-X-KEY:URI=\"https://cdn/key\\\",METHOD=NONE,tail\"\n"
                        + "segment.ts\n",
                "暂不支持加密 HLS 下载");
        assertUnsupported(
                "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,"
                        + "URI=\"https://cdn/key,METHOD=NONE,tail\"\nsegment.ts\n",
                "暂不支持加密 HLS 下载");
    }

    @Test
    public void acceptsMethodNone() throws Exception {
        HlsPlaylistParser.Result result = HlsPlaylistParser.parse(
                "#EXTM3U\n#ext-x-key:method=none,"
                        + "URI=\"https://cdn/key,with-comma\"\nsegment.ts\n",
                URI.create("https://cdn.example.com/master.m3u8"));

        assertEquals(Collections.singletonList("https://cdn.example.com/segment.ts"),
                result.getSegments());
    }

    @Test
    public void rejectsDuplicateOrMalformedKeyAttributes() throws Exception {
        assertParseFails(
                "#EXTM3U\n#EXT-X-KEY:METHOD=NONE,method=AES-128\nsegment.ts\n",
                "播放列表属性重复");
        assertParseFails(
                "#EXTM3U\n#EXT-X-KEY:METHOD=NONE,URI=\"unterminated\nsegment.ts\n",
                "播放列表属性引号未闭合");
        assertParseFails(
                "#EXTM3U\n#EXT-X-KEY:METHOD=NONE,BROKEN\nsegment.ts\n",
                "播放列表属性格式无效");
        assertParseFails(
                "#EXTM3U\n#EXT-X-KEY:METHOD=NONE,=value\nsegment.ts\n",
                "播放列表属性格式无效");
    }

    @Test
    public void parsesBandwidthOnlyFromExactValidatedAttribute() throws Exception {
        HlsPlaylistParser.Result result = HlsPlaylistParser.parse(
                "#EXTM3U\n"
                        + "#EXT-X-STREAM-INF:AUDIO=\"group,BANDWIDTH=999999\",BANDWIDTH=10\n"
                        + "variant.m3u8\n",
                URI.create("https://cdn.example.com/master.m3u8"));

        assertEquals(10L, result.getVariants().get(0).getBandwidth());
        assertParseFails(
                "#EXTM3U\n#EXT-X-STREAM-INF:AUDIO=\"group,BANDWIDTH=9\"\nv.m3u8\n",
                "播放列表 BANDWIDTH 无效");
        assertParseFails("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=0\nv.m3u8\n",
                "播放列表 BANDWIDTH 无效");
        assertParseFails("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=-1\nv.m3u8\n",
                "播放列表 BANDWIDTH 无效");
        assertParseFails("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=fast\nv.m3u8\n",
                "播放列表 BANDWIDTH 无效");
        assertParseFails(
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=999999999999999999999\nv.m3u8\n",
                "播放列表 BANDWIDTH 无效");
    }

    @Test
    public void ignoresTagsThatOnlyShareANamePrefix() throws Exception {
        HlsPlaylistParser.Result result = HlsPlaylistParser.parse(
                "#EXTM3U\n"
                        + "#EXT-X-MAPPING:URI=\"init.mp4\"\n"
                        + "#EXT-X-BYTERANGE-FOO:100\n"
                        + "#EXT-X-KEYFORMAT:METHOD=AES-128\n"
                        + "#EXT-X-STREAM-INFORMATION:BANDWIDTH=10\n"
                        + "#EXT-X-STREAM-INF\n"
                        + "segment.ts\n",
                URI.create("https://cdn.example.com/master.m3u8"));

        assertEquals(Collections.singletonList("https://cdn.example.com/segment.ts"),
                result.getSegments());
        assertTrue(result.getVariants().isEmpty());
    }

    @Test
    public void rejectsOversizedContentAndLines() throws Exception {
        assertParseFails(repeat('x', HlsPlaylistParser.MAX_CONTENT_CHARS + 1),
                "播放列表内容过大");
        assertParseFails("#EXTM3U\n" + repeat('x', HlsPlaylistParser.MAX_LINE_CHARS + 1),
                "播放列表行过长");

        StringBuilder tooManyLines = new StringBuilder("#EXTM3U\n");
        for (int index = 0; index < HlsPlaylistParser.MAX_LINES; index++) {
            tooManyLines.append("#\n");
        }
        assertParseFails(tooManyLines.toString(), "播放列表行数过多");
    }

    @Test
    public void rejectsTooManyUriEntries() throws Exception {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        for (int index = 0; index <= HlsPlaylistParser.MAX_URI_ENTRIES; index++) {
            playlist.append("segment\n");
        }

        assertParseFails(playlist.toString(), "播放列表 URI 条目过多");
    }

    @Test
    public void emptyOrInvalidDocumentReturnsEmptyResult() throws Exception {
        HlsPlaylistParser.Result empty = HlsPlaylistParser.parse(
                " \n# just a comment\n", URI.create("https://cdn.example.com/master.m3u8"));
        HlsPlaylistParser.Result invalid = HlsPlaylistParser.parse(
                "segment.ts\n", URI.create("https://cdn.example.com/master.m3u8"));

        assertTrue(empty.getSegments().isEmpty());
        assertTrue(empty.getVariants().isEmpty());
        assertTrue(invalid.getSegments().isEmpty());
        assertTrue(invalid.getVariants().isEmpty());
    }

    @Test
    public void rejectsUnsafeBaseUris() throws Exception {
        assertInvalidBase("relative/master.m3u8");
        assertInvalidBase("ftp://cdn.example.com/master.m3u8");
        assertInvalidBase("https://user@cdn.example.com/master.m3u8");
        assertInvalidBase("https://cdn.example.com:70000/master.m3u8");
        assertInvalidBase("https://cdn.example.com/master.m3u8#fragment");
    }

    @Test
    public void rejectsUnsafeResolvedUris() throws Exception {
        assertInvalidChild("ftp://cdn.example.com/segment.ts");
        assertInvalidChild("https://user@cdn.example.com/segment.ts");
        assertInvalidChild("https://cdn.example.com:70000/segment.ts");
        assertInvalidChild("segment.ts#fragment");
    }

    @Test
    public void resultCollectionsAreImmutable() throws Exception {
        HlsPlaylistParser.Result media = HlsPlaylistParser.parse(
                "#EXTM3U\nsegment.ts\n", URI.create("https://cdn.example.com/master.m3u8"));
        HlsPlaylistParser.Result master = HlsPlaylistParser.parse(
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nvariant.m3u8\n",
                URI.create("https://cdn.example.com/master.m3u8"));

        try {
            media.getSegments().add("https://cdn.example.com/other.ts");
            fail("segments should be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
        try {
            master.getVariants().clear();
            fail("variants should be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void assertUnsupported(String content, String expectedMessage) throws Exception {
        try {
            HlsPlaylistParser.parse(content, URI.create("https://cdn.example.com/master.m3u8"));
            fail("Expected IOException");
        } catch (IOException error) {
            assertEquals(expectedMessage, error.getMessage());
        }
    }

    private static void assertParseFails(String content, String expectedMessage) throws Exception {
        try {
            HlsPlaylistParser.parse(content, URI.create("https://cdn.example.com/master.m3u8"));
            fail("Expected IOException");
        } catch (IOException error) {
            assertEquals(expectedMessage, error.getMessage());
        }
    }

    private static String repeat(char character, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, character);
        return new String(characters);
    }

    private static void assertInvalidBase(String base) throws Exception {
        try {
            HlsPlaylistParser.parse("#EXTM3U\nsegment.ts\n", URI.create(base));
            fail("Expected IOException for base " + base);
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("播放列表地址"));
        }
    }

    private static void assertInvalidChild(String child) throws Exception {
        try {
            HlsPlaylistParser.parse("#EXTM3U\n" + child + "\n",
                    URI.create("https://cdn.example.com/master.m3u8"));
            fail("Expected IOException for child " + child);
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("播放列表 URI"));
        }
    }
}
