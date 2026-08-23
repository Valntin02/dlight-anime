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
        if (rawValue.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("API base URL must not contain control characters");
        }

        String value = rawValue.trim();
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("API base URL is invalid: " + value, error);
        }

        String scheme = uri.getScheme();
        int port = uri.getPort();
        String rawPath = uri.getRawPath();
        if (scheme == null || uri.getHost() == null
            || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
            || uri.getRawQuery() != null || uri.getRawFragment() != null
            || !(rawPath == null || rawPath.isEmpty() || "/".equals(rawPath))
            || port < -1 || port == 0 || port > 65535) {
            throw new IllegalArgumentException(
                "API base URL must use http or https, include a host and valid port, omit query and fragment, and use only the root path"
            );
        }

        return value.endsWith("/") ? value : value + "/";
    }
}
