package com.dlight.feature.download;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class DownloadHttpClient {
    enum Purpose {
        PLAYLIST(15),
        SEGMENT(30);

        private final int readTimeoutSeconds;

        Purpose(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }
    }

    interface AddressResolver {
        List<InetAddress> resolve(URI uri, boolean allowPrivate) throws IOException;
    }

    private static final AddressResolver POLICY_RESOLVER =
            DownloadUrlPolicy::resolveAllowedAddresses;
    private static final OkHttpClient BASE_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private DownloadHttpClient() {
    }

    static Response execute(URI uri, boolean allowPrivate, Purpose purpose) throws IOException {
        OkHttpClient client = clientFor(uri, allowPrivate, purpose, POLICY_RESOLVER);
        Request request = new Request.Builder().url(uri.toString()).build();
        return client.newCall(request).execute();
    }

    static OkHttpClient clientFor(URI uri, boolean allowPrivate, Purpose purpose,
            AddressResolver resolver) throws IOException {
        final String expectedHost = uri.getHost();
        final List<InetAddress> resolved = resolver.resolve(uri, allowPrivate);
        if (expectedHost == null || expectedHost.isEmpty() || resolved == null
                || resolved.isEmpty()) {
            throw new IOException("下载地址主机解析失败");
        }
        final List<InetAddress> pinned = Collections.unmodifiableList(
                new ArrayList<>(resolved));

        Dns pinnedDns = new Dns() {
            @Override
            public List<InetAddress> lookup(String hostname) throws UnknownHostException {
                if (!expectedHost.equalsIgnoreCase(hostname)) {
                    throw new UnknownHostException("拒绝解析非目标下载主机: " + hostname);
                }
                return pinned;
            }
        };
        OkHttpClient.Builder builder = BASE_CLIENT.newBuilder()
                .dns(pinnedDns)
                .readTimeout(purpose.readTimeoutSeconds, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS));
        if (!allowPrivate) {
            builder.proxy(Proxy.NO_PROXY);
        }
        return builder.build();
    }
}
