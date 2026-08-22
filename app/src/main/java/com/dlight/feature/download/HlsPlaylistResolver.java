package com.dlight.feature.download;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HlsPlaylistResolver {
    private static final int NETWORK_TIMEOUT_MILLIS = 15000;

    private HlsPlaylistResolver() {
    }

    public static List<String> resolve(String playlistUrl) throws IOException {
        return resolve(playlistUrl, HlsPlaylistResolver::fetchOverNetwork, 0);
    }

    static List<String> resolve(String playlistUrl, PlaylistFetcher fetcher, int depth)
            throws IOException {
        if (depth > 3) {
            throw new IOException("播放列表嵌套层级过深");
        }

        URI playlistUri = toUri(playlistUrl);
        HlsPlaylistParser.parse("", playlistUri);
        String content = fetcher.fetch(playlistUrl);
        HlsPlaylistParser.Result result = HlsPlaylistParser.parse(content, playlistUri);

        if (!result.getSegments().isEmpty()) {
            return Collections.unmodifiableList(new ArrayList<>(result.getSegments()));
        }
        if (!result.getVariants().isEmpty()) {
            return resolve(result.getVariants().get(0).getUrl(), fetcher, depth + 1);
        }
        throw new IOException("播放列表中没有可下载的视频分片");
    }

    private static URI toUri(String playlistUrl) throws IOException {
        try {
            return URI.create(playlistUrl);
        } catch (IllegalArgumentException error) {
            throw new IOException("播放列表地址无效", error);
        }
    }

    private static String fetchOverNetwork(String playlistUrl) throws IOException {
        URLConnection connection = new URL(playlistUrl).openConnection();
        connection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
        connection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);

        StringBuilder content = new StringBuilder();
        char[] buffer = new char[8192];
        try (Reader reader = new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8)) {
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (content.length() > HlsPlaylistParser.MAX_CONTENT_CHARS - count) {
                    throw new IOException("播放列表内容过大");
                }
                content.append(buffer, 0, count);
            }
        }
        return content.toString();
    }
}

interface PlaylistFetcher {
    String fetch(String url) throws IOException;
}
