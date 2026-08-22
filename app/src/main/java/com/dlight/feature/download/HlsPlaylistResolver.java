package com.dlight.feature.download;

import com.dlight.BuildConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HlsPlaylistResolver {
    private static final int NETWORK_TIMEOUT_MILLIS = 15000;
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
        DownloadUrlPolicy.validate(playlistUri, allowPrivate);
        FetchedPlaylist fetched = fetcher.fetch(playlistUrl);
        DownloadUrlPolicy.validate(fetched.getFinalUri(), allowPrivate);
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
            DownloadUrlPolicy.validate(currentUri, allowPrivate);
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) currentUri.toURL().openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(NETWORK_TIMEOUT_MILLIS);
                connection.setReadTimeout(NETWORK_TIMEOUT_MILLIS);

                int status = connection.getResponseCode();
                if (isRedirect(status)) {
                    closeResponseBody(connection);
                    if (redirectCount >= MAX_REDIRECTS) {
                        throw new IOException("播放列表重定向次数过多");
                    }
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) {
                        throw new IOException("播放列表重定向缺少 Location");
                    }

                    final URI nextUri;
                    try {
                        nextUri = currentUri.resolve(location);
                    } catch (IllegalArgumentException error) {
                        throw new IOException("播放列表重定向地址无效", error);
                    }
                    DownloadUrlPolicy.validate(nextUri, allowPrivate);
                    if (!visited.add(nextUri)) {
                        throw new IOException("播放列表重定向循环");
                    }
                    currentUri = nextUri;
                    redirectCount++;
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    closeResponseBody(connection);
                    throw new IOException("播放列表请求失败，HTTP 状态码: " + status);
                }
                return new FetchedPlaylist(readContent(connection), currentUri);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    private static String readContent(HttpURLConnection connection) throws IOException {
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

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308;
    }

    private static void closeResponseBody(HttpURLConnection connection) {
        InputStream stream = connection.getErrorStream();
        if (stream == null) {
            try {
                stream = connection.getInputStream();
            } catch (IOException ignored) {
                return;
            }
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // The connection is disconnected by the caller's finally block.
        }
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
