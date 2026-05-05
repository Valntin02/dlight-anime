package com.dlight.data.remote;

import androidx.annotation.Nullable;

public final class AuthHeaderUtil {

    private AuthHeaderUtil() {
    }

    public static String bearer(@Nullable String token) {
        if (token == null) {
            return "";
        }
        String normalized = token.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String value = normalized.substring(7).trim();
            return value.isEmpty() ? "" : "Bearer " + value;
        }
        return "Bearer " + normalized;
    }
}
