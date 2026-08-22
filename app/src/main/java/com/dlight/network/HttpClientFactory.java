package com.dlight.network;

import android.util.Log;

import com.dlight.BuildConfig;

import java.net.ProxySelector;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public final class HttpClientFactory {
    private static final String TAG = "ApiRequest";
    private static final OkHttpClient API_CLIENT = createApiClient();
    private static final OkHttpClient IMAGE_CLIENT = baseBuilder().build();

    private HttpClientFactory() {
    }

    public static OkHttpClient apiClient() {
        return API_CLIENT;
    }

    public static OkHttpClient imageClient() {
        return IMAGE_CLIENT;
    }

    private static OkHttpClient createApiClient() {
        OkHttpClient.Builder builder = baseBuilder();
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(new SafeRequestLoggingInterceptor(
                    message -> Log.d(TAG, message)));
        }
        return builder.build();
    }

    private static OkHttpClient.Builder baseBuilder() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .proxySelector(ProxySelector.getDefault());
    }
}
