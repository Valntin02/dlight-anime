package com.dlight.network;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NetworkConfigTest {
    @Test
    public void normalizeBaseUrl_trimsAndAddsTrailingSlash() {
        assertEquals("https://example.com/", NetworkConfig.normalizeBaseUrl("  https://example.com  "));
    }

    @Test
    public void normalizeBaseUrl_preservesPath() {
        assertEquals("https://example.com/api/", NetworkConfig.normalizeBaseUrl("https://example.com/api"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsBlankValue() {
        NetworkConfig.normalizeBaseUrl("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsFtpScheme() {
        NetworkConfig.normalizeBaseUrl("ftp://example.com");
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsMissingHost() {
        NetworkConfig.normalizeBaseUrl("https:///api");
    }
}
