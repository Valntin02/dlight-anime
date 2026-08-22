package com.dlight.feature.download;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HlsPlaylistParser {
    private static final Pattern BANDWIDTH_PATTERN = Pattern.compile(
            "(?:^|,)\\s*BANDWIDTH\\s*=\\s*(\\d+)\\s*(?:,|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_METHOD_PATTERN = Pattern.compile(
            "(?:^|,)\\s*METHOD\\s*=\\s*([^,\\s]+)",
            Pattern.CASE_INSENSITIVE);

    private HlsPlaylistParser() {
    }

    public static Result parse(String content, URI baseUri) throws IOException {
        validateHttpUri(baseUri, "播放列表地址无效");

        if (content == null || content.trim().isEmpty()) {
            return new Result(Collections.<String>emptyList(), Collections.<Variant>emptyList());
        }

        List<String> segments = new ArrayList<>();
        List<Variant> variants = new ArrayList<>();
        boolean hasHeader = false;
        boolean hasMasterMarker = false;
        Long pendingBandwidth = null;

        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            String upper = line.toUpperCase(Locale.ROOT);
            if ("#EXTM3U".equals(upper)) {
                hasHeader = true;
                continue;
            }
            if (upper.startsWith("#EXT-X-MAP")) {
                throw new IOException("暂不支持 fMP4 下载");
            }
            if (upper.startsWith("#EXT-X-BYTERANGE")) {
                throw new IOException("暂不支持字节范围分片");
            }
            if (upper.startsWith("#EXT-X-KEY") && !hasNoEncryptionMethod(line)) {
                throw new IOException("暂不支持加密 HLS 下载");
            }
            if (upper.startsWith("#EXT-X-STREAM-INF")) {
                hasMasterMarker = true;
                pendingBandwidth = parseBandwidth(line);
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

    private static long parseBandwidth(String streamInfLine) {
        int colon = streamInfLine.indexOf(':');
        String attributes = colon >= 0 ? streamInfLine.substring(colon + 1) : "";
        Matcher matcher = BANDWIDTH_PATTERN.matcher(attributes);
        if (!matcher.find()) {
            return 0L;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static boolean hasNoEncryptionMethod(String keyLine) {
        int colon = keyLine.indexOf(':');
        if (colon < 0) {
            return false;
        }
        Matcher matcher = KEY_METHOD_PATTERN.matcher(keyLine.substring(colon + 1));
        return matcher.find() && "NONE".equalsIgnoreCase(matcher.group(1));
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
