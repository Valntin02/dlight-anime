package com.dlight.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import com.dlight.BuildConfig;

import org.junit.Test;

import java.net.ProxySelector;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

public class HttpClientFactoryTest {
    @Test
    public void clients_areDistinctSingletonsUsingSystemProxySelector() {
        OkHttpClient apiClient = HttpClientFactory.apiClient();
        OkHttpClient imageClient = HttpClientFactory.imageClient();

        assertSame(apiClient, HttpClientFactory.apiClient());
        assertSame(imageClient, HttpClientFactory.imageClient());
        assertNotSame(apiClient, imageClient);
        assertSame(ProxySelector.getDefault(), apiClient.proxySelector());
        assertSame(ProxySelector.getDefault(), imageClient.proxySelector());
    }

    @Test
    public void clients_shareRequiredTransportSettings() {
        assertTransportSettings(HttpClientFactory.apiClient());
        assertTransportSettings(HttpClientFactory.imageClient());
    }

    @Test
    public void imageClient_hasNoHttpLoggingInterceptor() {
        assertEquals(0, loggingInterceptors(HttpClientFactory.imageClient()).size());
    }

    @Test
    public void apiClient_debugLoggingIsAtMostBasic() {
        List<HttpLoggingInterceptor> logging = loggingInterceptors(HttpClientFactory.apiClient());

        if (BuildConfig.DEBUG) {
            assertEquals(1, logging.size());
            assertEquals(HttpLoggingInterceptor.Level.BASIC, logging.get(0).getLevel());
        } else {
            assertEquals(0, logging.size());
        }
    }

    private static void assertTransportSettings(OkHttpClient client) {
        assertEquals(15_000, client.connectTimeoutMillis());
        assertEquals(30_000, client.readTimeoutMillis());
        assertEquals(15_000, client.writeTimeoutMillis());
        assertEquals(true, client.followRedirects());
        assertEquals(true, client.followSslRedirects());
        assertEquals(true, client.retryOnConnectionFailure());
    }

    private static List<HttpLoggingInterceptor> loggingInterceptors(OkHttpClient client) {
        return client.interceptors().stream()
                .filter(HttpLoggingInterceptor.class::isInstance)
                .map(HttpLoggingInterceptor.class::cast)
                .collect(java.util.stream.Collectors.toList());
    }
}
