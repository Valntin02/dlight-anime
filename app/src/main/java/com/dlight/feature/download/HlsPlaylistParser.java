package com.dlight.feature.download;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class HlsPlaylistParser {
    static final int MAX_CONTENT_CHARS = 2 * 1024 * 1024;
    static final int MAX_LINE_CHARS = 8192;
    static final int MAX_LINES = 100000;
    static final int MAX_URI_ENTRIES = 20000;

    private static final String TAG_MAP = "#EXT-X-MAP";
    private static final String TAG_BYTERANGE = "#EXT-X-BYTERANGE";
    private static final String TAG_KEY = "#EXT-X-KEY";
    private static final String TAG_STREAM_INF = "#EXT-X-STREAM-INF";

    private HlsPlaylistParser() {
    }

    public static Result parse(String content, URI baseUri) throws IOException {
        validateHttpUri(baseUri, "播放列表地址无效");

        if (content == null) {
            return new Result(Collections.<String>emptyList(), Collections.<Variant>emptyList());
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            throw new IOException("播放列表内容过大");
        }
        if (content.trim().isEmpty()) {
            return new Result(Collections.<String>emptyList(), Collections.<Variant>emptyList());
        }

        List<String> segments = new ArrayList<>();
        List<Variant> variants = new ArrayList<>();
        boolean hasHeader = false;
        boolean hasMasterMarker = false;
        Long pendingBandwidth = null;
        int lineCount = 0;
        int uriEntryCount = 0;

        BufferedReader reader = new BufferedReader(new StringReader(content));
        String rawLine;
        while ((rawLine = reader.readLine()) != null) {
            lineCount++;
            if (lineCount > MAX_LINES) {
                throw new IOException("播放列表行数过多");
            }
            if (rawLine.length() > MAX_LINE_CHARS) {
                throw new IOException("播放列表行过长");
            }

            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            String upper = line.toUpperCase(Locale.ROOT);
            if ("#EXTM3U".equals(upper)) {
                hasHeader = true;
                continue;
            }
            if (isTag(upper, TAG_MAP)) {
                throw new IOException("暂不支持 fMP4 下载");
            }
            if (isTag(upper, TAG_BYTERANGE)) {
                throw new IOException("暂不支持字节范围分片");
            }
            if (isTag(upper, TAG_KEY)) {
                Map<String, String> attributes = parseAttributeList(attributesAfterColon(line));
                if (!"NONE".equalsIgnoreCase(attributes.get("METHOD"))) {
                    throw new IOException("暂不支持加密 HLS 下载");
                }
                continue;
            }
            if (hasTagAttributes(upper, TAG_STREAM_INF)) {
                hasMasterMarker = true;
                pendingBandwidth = parseBandwidth(attributesAfterColon(line));
                continue;
            }
            if (line.startsWith("#")) {
                continue;
            }

            boolean isVariant = pendingBandwidth != null;
            long bandwidth = isVariant ? pendingBandwidth : 0L;
            pendingBandwidth = null;
            if (upper.contains("ADJUMP")) {
                continue;
            }
            uriEntryCount++;
            if (uriEntryCount > MAX_URI_ENTRIES) {
                throw new IOException("播放列表 URI 条目过多");
            }

            String url = resolveUri(baseUri, line);
            if (isVariant) {
                variants.add(new Variant(url, bandwidth));
            } else {
                segments.add(url);
            }
        }

        if (!hasHeader) {
            return new Result(Collections.<String>emptyList(), Collections.<Variant>emptyList());
        }
        if (hasMasterMarker) {
            Collections.sort(variants, new Comparator<Variant>() {
                @Override
                public int compare(Variant left, Variant right) {
                    return Long.compare(right.getBandwidth(), left.getBandwidth());
                }
            });
            return new Result(Collections.<String>emptyList(), variants);
        }
        return new Result(segments, Collections.<Variant>emptyList());
    }

    private static boolean isTag(String upperLine, String tag) {
        return upperLine.equals(tag) || upperLine.startsWith(tag + ":");
    }

    private static boolean hasTagAttributes(String upperLine, String tag) {
        return upperLine.startsWith(tag + ":");
    }

    private static String attributesAfterColon(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? "" : line.substring(colon + 1);
    }

    private static long parseBandwidth(String attributeList) throws IOException {
        String value = parseAttributeList(attributeList).get("BANDWIDTH");
        if (value == null) {
            throw new IOException("播放列表 BANDWIDTH 无效");
        }
        try {
            long bandwidth = Long.parseLong(value);
            if (bandwidth <= 0) {
                throw new IOException("播放列表 BANDWIDTH 无效");
            }
            return bandwidth;
        } catch (NumberFormatException error) {
            throw new IOException("播放列表 BANDWIDTH 无效", error);
        }
    }

    private static Map<String, String> parseAttributeList(String attributes) throws IOException {
        Map<String, String> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (attributes.trim().isEmpty()) {
            return values;
        }

        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int index = 0; index < attributes.length(); index++) {
            char character = attributes.charAt(index);
            if (character == ',' && !inQuotes) {
                addAttributeToken(values, token.toString());
                token.setLength(0);
                continue;
            }

            token.append(character);
            if (escaped) {
                escaped = false;
            } else if (inQuotes && character == '\\') {
                escaped = true;
            } else if (character == '"') {
                inQuotes = !inQuotes;
            }
        }
        if (inQuotes) {
            throw new IOException("播放列表属性引号未闭合");
        }
        addAttributeToken(values, token.toString());
        return values;
    }

    private static void addAttributeToken(Map<String, String> values, String token)
            throws IOException {
        int equals = token.indexOf('=');
        if (equals < 0) {
            throw new IOException("播放列表属性格式无效");
        }

        String key = token.substring(0, equals).trim();
        if (key.isEmpty()) {
            throw new IOException("播放列表属性格式无效");
        }
        if (values.containsKey(key)) {
            throw new IOException("播放列表属性重复");
        }

        String value = token.substring(equals + 1).trim();
        if (value.length() >= 2 && value.charAt(0) == '"'
                && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1).trim();
        }
        values.put(key, value);
    }

    private static String resolveUri(URI baseUri, String value) throws IOException {
        final URI resolved;
        try {
            resolved = baseUri.resolve(value);
        } catch (IllegalArgumentException error) {
            throw new IOException("播放列表 URI 无效", error);
        }
        validateHttpUri(resolved, "播放列表 URI 无效");
        return resolved.toString();
    }

    private static void validateHttpUri(URI uri, String message) throws IOException {
        if (uri == null
                || !uri.isAbsolute()
                || uri.isOpaque()
                || uri.getScheme() == null
                || !("http".equalsIgnoreCase(uri.getScheme())
                        || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getHost().isEmpty()
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || uri.getPort() == 0
                || uri.getPort() < -1
                || uri.getPort() > 65535) {
            throw new IOException(message);
        }
    }

    public static final class Result {
        private final List<String> segments;
        private final List<Variant> variants;

        private Result(List<String> segments, List<Variant> variants) {
            this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
            this.variants = Collections.unmodifiableList(new ArrayList<>(variants));
        }

        public List<String> getSegments() {
            return segments;
        }

        public List<Variant> getVariants() {
            return variants;
        }
    }

    public static final class Variant {
        private final String url;
        private final long bandwidth;

        private Variant(String url, long bandwidth) {
            this.url = url;
            this.bandwidth = bandwidth;
        }

        public String getUrl() {
            return url;
        }

        public long getBandwidth() {
            return bandwidth;
        }
    }
}
