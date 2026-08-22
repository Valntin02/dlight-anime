package com.dlight.network;

import com.dlight.BuildConfig;

import java.net.URI;

public final class NetworkConfig {
    private NetworkConfig() {
    }

    public static String apiBaseUrl() {
        return normalizeBaseUrl(BuildConfig.API_BASE_URL);
    }

    static String normalizeBaseUrl(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw new IllegalArgumentException("API base URL is empty");
        }

        String value = rawValue.trim();
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("API base URL is invalid: " + value, error);
        }

        String scheme = uri.getScheme();
        if (scheme == null || uri.getHost() == null
            || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("API base URL must use http or https and include a host");
        }

        return value.endsWith("/") ? value : value + "/";
    }
}
