package com.dlight.ui.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PlaySourceSelector {
    private PlaySourceSelector() {
    }

    public static List<String> selectUrls(
        JsonElement vodPlayData,
        String legacyUrl,
        String remarks,
        String total
    ) {
        List<String> structuredUrls = selectStructuredUrls(vodPlayData);
        if (!structuredUrls.isEmpty()) {
            return immutableCopy(structuredUrls);
        }

        if (!isPlayableUrl(legacyUrl)) {
            return Collections.emptyList();
        }

        String trimmed = legacyUrl.trim();
        List<String> urls = new ArrayList<>();
        if (trimmed.matches(".*第\\d+集.*")) {
            int count = parseEpisodeCount(remarks, total);
            for (int episode = 1; episode <= count; episode++) {
                String number = String.format(Locale.ROOT, "%02d", episode);
                urls.add(trimmed.replaceAll("第\\d+集", "第" + number + "集"));
            }
        } else {
            urls.add(trimmed);
        }
        return immutableCopy(urls);
    }

    static boolean isPlayableUrl(String rawUrl) {
        if (rawUrl == null) {
            return false;
        }
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()
            || "null".equalsIgnoreCase(trimmed)
            || "undefined".equalsIgnoreCase(trimmed)) {
            return false;
        }

        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            int port = uri.getPort();
            return !uri.isOpaque()
                && scheme != null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && uri.getHost() != null
                && uri.getUserInfo() == null
                && uri.getFragment() == null
                && (port == -1 || (port >= 1 && port <= 65535));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
    }

    static int parseEpisodeCount(String remarks, String total) {
        int fromRemarks = extractPositiveNumber(remarks);
        if (fromRemarks > 0) {
            return fromRemarks;
        }
        int fromTotal = extractPositiveNumber(total);
        return fromTotal > 0 ? fromTotal : 1;
    }

    private static List<String> selectStructuredUrls(JsonElement vodPlayData) {
        if (vodPlayData == null || vodPlayData.isJsonNull()) {
            return Collections.emptyList();
        }
        if (vodPlayData.isJsonObject()) {
            return extractUrls(vodPlayData.getAsJsonObject());
        }
        if (!vodPlayData.isJsonArray()) {
            return Collections.emptyList();
        }

        List<JsonObject> sources = new ArrayList<>();
        for (JsonElement sourceElement : vodPlayData.getAsJsonArray()) {
            if (sourceElement != null && sourceElement.isJsonObject()) {
                sources.add(sourceElement.getAsJsonObject());
            }
        }
        Collections.sort(sources, Comparator.comparingInt(PlaySourceSelector::sourcePriority));
        for (JsonObject source : sources) {
            List<String> urls = extractUrls(source);
            if (!urls.isEmpty()) {
                return urls;
            }
        }
        return Collections.emptyList();
    }

    private static List<String> extractUrls(JsonObject source) {
        JsonElement episodesElement = source.get("episodes");
        if (episodesElement == null || !episodesElement.isJsonArray()) {
            return Collections.emptyList();
        }

        List<String> urls = new ArrayList<>();
        JsonArray episodes = episodesElement.getAsJsonArray();
        for (JsonElement episodeElement : episodes) {
            if (episodeElement == null || !episodeElement.isJsonObject()) {
                continue;
            }
            JsonElement urlElement = episodeElement.getAsJsonObject().get("url");
            if (urlElement == null || !urlElement.isJsonPrimitive()) {
                continue;
            }
            String url = urlElement.getAsString();
            if (isPlayableUrl(url)) {
                urls.add(url.trim());
            }
        }
        return urls;
    }

    private static int sourcePriority(JsonObject source) {
        String name = sourceName(source).toLowerCase(Locale.ROOT);
        if ("lzm3u8".equals(name)) return 0;
        if (name.contains("m3u8") && !name.contains("bfzy")) return 1;
        if (name.contains("bfzy")) return 3;
        return 2;
    }

    private static String sourceName(JsonObject source) {
        JsonElement from = source.get("from");
        if (from != null && from.isJsonPrimitive()) {
            return from.getAsString();
        }
        JsonElement label = source.get("label");
        return label != null && label.isJsonPrimitive() ? label.getAsString() : "";
    }

    private static int extractPositiveNumber(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String number = text.replaceAll("[^0-9]", "");
        if (number.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<String> immutableCopy(List<String> urls) {
        return Collections.unmodifiableList(new ArrayList<>(urls));
    }
}
