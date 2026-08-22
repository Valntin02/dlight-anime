package com.dlight.util;

import com.dlight.network.NetworkConfig;

import java.net.URI;
import java.util.Locale;

public final class ImageUrlResolver {
    private ImageUrlResolver() {
    }

    public static String resolve(String raw) {
        try {
            return resolve(raw, NetworkConfig.apiBaseUrl());
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    static String resolve(String raw, String base) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        try {
            URI imageUri = URI.create(raw.trim());
            URI baseUri = null;
            if (!imageUri.isAbsolute()) {
                baseUri = validBaseUri(base);
                if (baseUri == null) {
                    return null;
                }
                imageUri = baseUri.resolve(imageUri);
            }

            String scheme = imageUri.getScheme();
            if (scheme == null) {
                return null;
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!isSupportedScheme(normalizedScheme) || imageUri.isOpaque()) {
                return null;
            }
            boolean isHttp = "http".equals(normalizedScheme) || "https".equals(normalizedScheme);
            if (isHttp && (imageUri.getRawUserInfo() != null
                || imageUri.getHost() == null || !hasValidPort(imageUri))) {
                return null;
            }
            if (isHttp && isLoopback(imageUri.getHost())) {
                if (baseUri == null) {
                    baseUri = validBaseUri(base);
                }
                return baseUri == null ? null : replaceOrigin(imageUri, baseUri);
            }
            return withLowercaseScheme(imageUri, normalizedScheme);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static URI validBaseUri(String base) {
        if (base == null) {
            return null;
        }
        URI baseUri = URI.create(base);
        String scheme = baseUri.getScheme();
        if (scheme == null || baseUri.isOpaque() || baseUri.getRawUserInfo() != null
            || baseUri.getHost() == null || !hasValidPort(baseUri)
            || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return null;
        }
        return baseUri;
    }

    private static boolean isSupportedScheme(String scheme) {
        return "http".equals(scheme)
            || "https".equals(scheme)
            || "file".equals(scheme)
            || "content".equals(scheme)
            || "android.resource".equals(scheme);
    }

    private static boolean hasValidPort(URI uri) {
        int port = uri.getPort();
        return port == -1 || (port > 0 && port <= 65535);
    }

    private static boolean isLoopback(String host) {
        return "127.0.0.1".equalsIgnoreCase(host)
            || "localhost".equalsIgnoreCase(host)
            || "0.0.0.0".equalsIgnoreCase(host);
    }

    private static String withLowercaseScheme(URI uri, String normalizedScheme) {
        String value = uri.toString();
        return normalizedScheme + value.substring(value.indexOf(':'));
    }

    private static String replaceOrigin(URI imageUri, URI baseUri) {
        StringBuilder result = new StringBuilder()
            .append(baseUri.getScheme().toLowerCase(Locale.ROOT))
            .append("://")
            .append(baseUri.getRawAuthority());
        if (imageUri.getRawPath() != null) {
            result.append(imageUri.getRawPath());
        }
        if (imageUri.getRawQuery() != null) {
            result.append('?').append(imageUri.getRawQuery());
        }
        if (imageUri.getRawFragment() != null) {
            result.append('#').append(imageUri.getRawFragment());
        }
        return URI.create(result.toString()).toString();
    }
}
