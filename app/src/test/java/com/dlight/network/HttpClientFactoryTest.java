package com.dlight.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import com.dlight.BuildConfig;

import org.junit.Test;

import java.net.ProxySelector;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

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
    public void clients_haveNoHttpLoggingInterceptor() {
        assertFalse(hasHttpLoggingInterceptor(HttpClientFactory.apiClient()));
        assertFalse(hasHttpLoggingInterceptor(HttpClientFactory.imageClient()));
    }

    @Test
    public void onlyDebugApiClient_hasOneSafeRequestLoggingInterceptor() {
        List<SafeRequestLoggingInterceptor> apiLogging = interceptors(
                HttpClientFactory.apiClient(), SafeRequestLoggingInterceptor.class);
        List<SafeRequestLoggingInterceptor> imageLogging = interceptors(
                HttpClientFactory.imageClient(), SafeRequestLoggingInterceptor.class);

        if (BuildConfig.DEBUG) {
            assertEquals(1, apiLogging.size());
        } else {
            assertEquals(0, apiLogging.size());
        }
        assertEquals(0, imageLogging.size());
    }

    private static void assertTransportSettings(OkHttpClient client) {
        assertEquals(15_000, client.connectTimeoutMillis());
        assertEquals(30_000, client.readTimeoutMillis());
        assertEquals(15_000, client.writeTimeoutMillis());
        assertEquals(true, client.followRedirects());
        assertEquals(true, client.followSslRedirects());
        assertEquals(true, client.retryOnConnectionFailure());
    }

    private static <T extends Interceptor> List<T> interceptors(OkHttpClient client, Class<T> type) {
        return client.interceptors().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .collect(java.util.stream.Collectors.toList());
    }

    private static boolean hasHttpLoggingInterceptor(OkHttpClient client) {
        return client.interceptors().stream()
                .anyMatch(interceptor -> interceptor.getClass().getName()
                        .contains("HttpLoggingInterceptor"));
    }
}
