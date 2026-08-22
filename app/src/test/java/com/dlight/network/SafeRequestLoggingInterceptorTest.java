package com.dlight.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import okhttp3.HttpUrl;

public class SafeRequestLoggingInterceptorTest {
    private static final HttpUrl SENSITIVE_URL = HttpUrl.get(
            "https://user:secret@example.com/search?input_search=私密词");

    @Test
    public void requestSummary_containsOnlyAllowedUrlParts() {
        String summary = SafeRequestLoggingInterceptor.formatRequest("GET", SENSITIVE_URL);

        assertEquals("GET https://example.com/search", summary);
        assertContainsNoSensitiveData(summary);
    }

    @Test
    public void responseSummary_containsCodeDurationAndSameSafeRequest() {
        String requestSummary = SafeRequestLoggingInterceptor.formatRequest("GET", SENSITIVE_URL);
        String summary = SafeRequestLoggingInterceptor.formatResponse(200, 12, requestSummary);

        assertEquals("200 12ms GET https://example.com/search", summary);
        assertContainsNoSensitiveData(summary);
    }

    private static void assertContainsNoSensitiveData(String summary) {
        assertFalse(summary.contains("user"));
        assertFalse(summary.contains("secret"));
        assertFalse(summary.contains("input_search"));
        assertFalse(summary.contains("私密词"));
        assertFalse(summary.contains("?"));
    }
}
