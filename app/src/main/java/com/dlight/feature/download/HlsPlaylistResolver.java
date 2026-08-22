package com.dlight.feature.download;

import com.dlight.BuildConfig;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Response;
import okhttp3.ResponseBody;

public final class HlsPlaylistResolver {
    private static final int MAX_REDIRECTS = 5;

    private HlsPlaylistResolver() {
    }

    public static List<String> resolve(String playlistUrl) throws IOException {
        final boolean allowPrivate = BuildConfig.DEBUG;
        return resolve(playlistUrl,
                url -> fetchOverNetwork(url, allowPrivate), 0, allowPrivate);
    }

    static List<String> resolve(String playlistUrl, PlaylistFetcher fetcher, int depth,
            boolean allowPrivate) throws IOException {
        if (depth > 3) {
            throw new IOException("播放列表嵌套层级过深");
        }

        URI playlistUri = toUri(playlistUrl);
        FetchedPlaylist fetched = fetcher.fetch(playlistUrl);
        HlsPlaylistParser.Result result = HlsPlaylistParser.parse(
                fetched.getContent(), fetched.getFinalUri());

        if (!result.getSegments().isEmpty()) {
            for (String segment : result.getSegments()) {
                DownloadUrlPolicy.validate(toUri(segment), allowPrivate);
            }
            return Collections.unmodifiableList(new ArrayList<>(result.getSegments()));
        }
        if (!result.getVariants().isEmpty()) {
            return resolve(result.getVariants().get(0).getUrl(), fetcher, depth + 1,
                    allowPrivate);
        }
        throw new IOException("播放列表中没有可下载的视频分片");
    }

    private static URI toUri(String playlistUrl) throws IOException {
        try {
            return URI.create(playlistUrl);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IOException("播放列表地址无效", error);
        }
    }

    private static FetchedPlaylist fetchOverNetwork(String playlistUrl, boolean allowPrivate)
            throws IOException {
        URI currentUri = toUri(playlistUrl);
        Set<URI> visited = new HashSet<>();
        visited.add(currentUri);
        int redirectCount = 0;

        while (true) {
            try (Response response = DownloadHttpClient.execute(currentUri, allowPrivate)) {
                int status = response.code();
                if (isRedirect(status)) {
                    if (redirectCount >= MAX_REDIRECTS) {
                        throw new IOException("播放列表重定向次数过多");
                    }
                    String location = response.header("Location");
                    if (location == null || location.trim().isEmpty()) {
                        throw new IOException("播放列表重定向缺少 Location");
                    }

                    final URI nextUri;
                    try {
                        nextUri = currentUri.resolve(location);
                    } catch (IllegalArgumentException error) {
                        throw new IOException("播放列表重定向地址无效", error);
                    }
                    if (!visited.add(nextUri)) {
                        throw new IOException("播放列表重定向循环");
                    }
                    currentUri = nextUri;
                    redirectCount++;
                    continue;
                }
                if (status != 200) {
                    throw new IOException("播放列表请求失败，HTTP 状态码: " + status);
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("播放列表响应内容为空");
                }
                return new FetchedPlaylist(readContent(body), currentUri);
            }
        }
    }

    private static String readContent(ResponseBody body) throws IOException {
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[8192];
        try (Reader reader = new InputStreamReader(
                body.byteStream(), StandardCharsets.UTF_8)) {
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

    private static boolean isRedirect(int status) {
        return status == 301
                || status == 302
                || status == 303
                || status == 307
                || status == 308;
    }
}

interface PlaylistFetcher {
    FetchedPlaylist fetch(String url) throws IOException;
}

final class FetchedPlaylist {
    private final String content;
    private final URI finalUri;

    FetchedPlaylist(String content, URI finalUri) {
        this.content = content;
        this.finalUri = finalUri;
    }

    String getContent() {
        return content;
    }

    URI getFinalUri() {
        return finalUri;
    }
}
