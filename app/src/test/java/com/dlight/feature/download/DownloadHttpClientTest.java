package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;

public class DownloadHttpClientTest {
    @Test
    public void pinsSinglePolicyResolutionToTargetHostname() throws Exception {
        final AtomicInteger resolutions = new AtomicInteger();
        final List<InetAddress> resolved = new ArrayList<>();
        resolved.add(InetAddress.getByAddress("pinned", new byte[] {8, 8, 8, 8}));
        DownloadHttpClient.AddressResolver resolver =
                new DownloadHttpClient.AddressResolver() {
                    @Override
                    public List<InetAddress> resolve(URI uri, boolean allowPrivate)
                            throws IOException {
                        resolutions.incrementAndGet();
                        assertEquals("media.example.test", uri.getHost());
                        assertFalse(allowPrivate);
                        return resolved;
                    }
                };

        OkHttpClient client = DownloadHttpClient.clientFor(
                URI.create("https://media.example.test/playlist.m3u8"), false, resolver);
        resolved.clear();

        assertEquals(Collections.singletonList(
                        InetAddress.getByAddress("pinned", new byte[] {8, 8, 8, 8})),
                client.dns().lookup("media.example.test"));
        assertEquals(1, resolutions.get());
        assertEquals(15_000L, client.connectTimeoutMillis());
        assertEquals(15_000L, client.readTimeoutMillis());
        assertFalse(client.followRedirects());
        assertFalse(client.followSslRedirects());
        assertEquals(Proxy.NO_PROXY, client.proxy());
    }

    @Test
    public void pinnedDnsRejectsEveryOtherHostnameWithoutResolvingAgain() throws Exception {
        final AtomicInteger resolutions = new AtomicInteger();
        DownloadHttpClient.AddressResolver resolver =
                new DownloadHttpClient.AddressResolver() {
                    @Override
                    public List<InetAddress> resolve(URI uri, boolean allowPrivate)
                            throws IOException {
                        resolutions.incrementAndGet();
                        return Collections.singletonList(InetAddress.getLoopbackAddress());
                    }
                };
        OkHttpClient client = DownloadHttpClient.clientFor(
                URI.create("http://expected.example.test/video.ts"), true, resolver);
        assertNull(client.proxy());

        try {
            client.dns().lookup("other.example.test");
            fail("Expected UnknownHostException");
        } catch (UnknownHostException expected) {
            // Expected.
        }
        client.dns().lookup("EXPECTED.EXAMPLE.TEST");
        assertEquals(1, resolutions.get());
    }
}
