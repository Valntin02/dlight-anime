package com.dlight.feature.download;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.net.URI;

public class DownloadUrlPolicyTest {
    @Test
    public void acceptsPublicLiteralWhenPrivateAddressesAreBlocked() throws Exception {
        DownloadUrlPolicy.validate(URI.create("https://8.8.8.8/video/segment.ts?token=1"), false);
    }

    @Test
    public void rejectsLoopbackLocalhostPrivateLinkLocalAndMulticastAddresses() throws Exception {
        assertBlocked("http://127.0.0.1/video.ts");
        assertBlocked("http://localhost/video.ts");
        assertBlocked("http://10.1.2.3/video.ts");
        assertBlocked("http://100.64.0.1/video.ts");
        assertBlocked("http://169.254.1.2/video.ts");
        assertBlocked("http://224.0.0.1/video.ts");
        assertBlocked("http://[fd12::1]/video.ts");
    }

    @Test
    public void allowPrivateOnlyAppliesSyntaxValidation() throws Exception {
        DownloadUrlPolicy.validate(URI.create("http://localhost/video.ts"), true);
        DownloadUrlPolicy.validate(URI.create("http://10.1.2.3/video.ts"), true);
        DownloadUrlPolicy.validate(URI.create("http://100.64.0.1/video.ts"), true);
        DownloadUrlPolicy.validate(URI.create("http://169.254.1.2/video.ts"), true);
        DownloadUrlPolicy.validate(URI.create("http://[fd12::1]/video.ts"), true);
    }

    @Test
    public void rejectsUnsafeOrMalformedUrisEvenWhenPrivateAddressesAreAllowed() throws Exception {
        assertInvalid("relative/video.ts");
        assertInvalid("ftp://8.8.8.8/video.ts");
        assertInvalid("https://user@8.8.8.8/video.ts");
        assertInvalid("https://8.8.8.8:0/video.ts");
        assertInvalid("https://8.8.8.8:70000/video.ts");
        assertInvalid("https://8.8.8.8/video.ts#fragment");
    }

    @Test
    public void dnsFailureIsReportedAsIOException() throws Exception {
        try {
            DownloadUrlPolicy.validate(
                    URI.create("http://[fe80::1%25definitely_missing_scope]/video.ts"), false);
            fail("Expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("主机"));
        }
    }

    private static void assertBlocked(String value) throws Exception {
        try {
            DownloadUrlPolicy.validate(URI.create(value), false);
            fail("Expected private address rejection: " + value);
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("私有或本地地址"));
        }
    }

    private static void assertInvalid(String value) throws Exception {
        try {
            DownloadUrlPolicy.validate(URI.create(value), true);
            fail("Expected invalid URI rejection: " + value);
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("下载地址无效"));
        }
    }
}
