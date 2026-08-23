package com.dlight.network;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NetworkConfigTest {
    @Test
    public void normalizeBaseUrl_trimsAndAddsTrailingSlash() {
        assertEquals("https://example.com/", NetworkConfig.normalizeBaseUrl("  https://example.com  "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsNonRootPath() {
        NetworkConfig.normalizeBaseUrl("https://example.com/api");
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsTrailingSlashOnNonRootPath() {
        NetworkConfig.normalizeBaseUrl("https://example.com/backend/");
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsEncodedPath() {
        NetworkConfig.normalizeBaseUrl("https://example.com/%61pi");
    }

    @Test
    public void normalizeBaseUrl_acceptsHighestValidPort() {
        assertEquals("https://example.com:65535/", NetworkConfig.normalizeBaseUrl("https://example.com:65535"));
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

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsQuery() {
        NetworkConfig.normalizeBaseUrl("https://example.com/api?format=json");
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsFragment() {
        NetworkConfig.normalizeBaseUrl("https://example.com/api#section");
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsPortZero() {
        NetworkConfig.normalizeBaseUrl("https://example.com:0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsPortAboveRange() {
        NetworkConfig.normalizeBaseUrl("https://example.com:65536");
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsFarAboveRangePort() {
        NetworkConfig.normalizeBaseUrl("https://example.com:99999");
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalizeBaseUrl_rejectsControlCharacters() {
        NetworkConfig.normalizeBaseUrl("https://example.com/\n");
    }
}
