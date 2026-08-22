package com.dlight.feature.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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
                URI.create("https://media.example.test/playlist.m3u8"), false,
                DownloadHttpClient.Purpose.PLAYLIST, resolver);
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
                URI.create("http://expected.example.test/video.ts"), true,
                DownloadHttpClient.Purpose.SEGMENT, resolver);
        assertNull(client.proxy());
        assertEquals(30_000L, client.readTimeoutMillis());

        try {
            client.dns().lookup("other.example.test");
            fail("Expected UnknownHostException");
        } catch (UnknownHostException expected) {
            // Expected.
        }
        client.dns().lookup("EXPECTED.EXAMPLE.TEST");
        assertEquals(1, resolutions.get());
    }

    @Test
    public void eachPinnedClientHasAnIsolatedConnectionPool() throws Exception {
        DownloadHttpClient.AddressResolver resolver =
                new DownloadHttpClient.AddressResolver() {
                    @Override
                    public List<InetAddress> resolve(URI uri, boolean allowPrivate) {
                        return Collections.singletonList(InetAddress.getLoopbackAddress());
                    }
                };

        OkHttpClient first = DownloadHttpClient.clientFor(
                URI.create("http://expected.example.test/one"), true,
                DownloadHttpClient.Purpose.PLAYLIST, resolver);
        OkHttpClient second = DownloadHttpClient.clientFor(
                URI.create("http://expected.example.test/two"), true,
                DownloadHttpClient.Purpose.PLAYLIST, resolver);

        assertNotSame(first.connectionPool(), second.connectionPool());
    }

    @Test
    public void closedResponseLeavesNoPinnedSocketInThePool() throws Exception {
        LocalHttpServer server = LocalHttpServer.start();
        try {
            final InetAddress loopback = InetAddress.getByName("127.0.0.1");
            DownloadHttpClient.AddressResolver resolver =
                    new DownloadHttpClient.AddressResolver() {
                        @Override
                        public List<InetAddress> resolve(URI uri, boolean allowPrivate) {
                            return Collections.singletonList(loopback);
                        }
                    };
            URI uri = URI.create("http://pinned.example.test:" + server.port() + "/video");
            OkHttpClient client = DownloadHttpClient.clientFor(uri, false,
                    DownloadHttpClient.Purpose.SEGMENT, resolver);

            try (Response response = client.newCall(
                    new Request.Builder().url(uri.toString()).build()).execute()) {
                assertEquals(200, response.code());
                response.body().bytes();
            }

            assertEquals(0, client.connectionPool().idleConnectionCount());
            awaitNoConnections(client);
            assertEquals(0, client.connectionPool().connectionCount());
        } finally {
            server.stop();
        }
    }

    private static void awaitNoConnections(OkHttpClient client) throws InterruptedException {
        for (int attempt = 0; attempt < 100
                && client.connectionPool().connectionCount() != 0; attempt++) {
            Thread.sleep(10);
        }
    }

    private static final class LocalHttpServer {
        private final Object server;
        private final Class<?> serverClass;

        private LocalHttpServer(Object server, Class<?> serverClass) {
            this.server = server;
            this.serverClass = serverClass;
        }

        static LocalHttpServer start() throws Exception {
            Class<?> serverClass = Class.forName("com.sun.net.httpserver.HttpServer");
            Class<?> handlerClass = Class.forName("com.sun.net.httpserver.HttpHandler");
            final Class<?> exchangeClass = Class.forName("com.sun.net.httpserver.HttpExchange");
            Object server = serverClass.getMethod("create", InetSocketAddress.class, int.class)
                    .invoke(null, new InetSocketAddress("127.0.0.1", 0), 0);
            Object handler = java.lang.reflect.Proxy.newProxyInstance(
                    handlerClass.getClassLoader(), new Class<?>[] {handlerClass},
                    (proxy, method, arguments) -> {
                        if ("handle".equals(method.getName())) {
                            Object exchange = arguments[0];
                            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                            exchangeClass.getMethod("sendResponseHeaders", int.class, long.class)
                                    .invoke(exchange, 200, (long) body.length);
                            try (OutputStream output = (OutputStream) exchangeClass
                                    .getMethod("getResponseBody").invoke(exchange)) {
                                output.write(body);
                            }
                        }
                        return null;
                    });
            serverClass.getMethod("createContext", String.class, handlerClass)
                    .invoke(server, "/video", handler);
            serverClass.getMethod("start").invoke(server);
            return new LocalHttpServer(server, serverClass);
        }

        int port() throws Exception {
            InetSocketAddress address = (InetSocketAddress) serverClass
                    .getMethod("getAddress").invoke(server);
            return address.getPort();
        }

        void stop() throws Exception {
            serverClass.getMethod("stop", int.class).invoke(server, 0);
        }
    }
}
