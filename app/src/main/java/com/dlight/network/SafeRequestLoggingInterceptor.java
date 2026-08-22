package com.dlight.network;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class SafeRequestLoggingInterceptor implements Interceptor {
    public interface Sink {
        void log(String message);
    }

    private final Sink sink;

    public SafeRequestLoggingInterceptor(Sink sink) {
        this.sink = Objects.requireNonNull(sink);
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String requestSummary = formatRequest(request.method(), request.url());
        sink.log(requestSummary);

        long startedAtNanos = System.nanoTime();
        Response response = chain.proceed(request);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        sink.log(formatResponse(response.code(), elapsedMillis, requestSummary));
        return response;
    }

    static String formatRequest(String method, HttpUrl url) {
        String host = url.host().contains(":") ? "[" + url.host() + "]" : url.host();
        StringBuilder summary = new StringBuilder()
                .append(method)
                .append(' ')
                .append(url.scheme())
                .append("://")
                .append(host);
        if (url.port() != HttpUrl.defaultPort(url.scheme())) {
            summary.append(':').append(url.port());
        }
        return summary.append(url.encodedPath()).toString();
    }

    static String formatResponse(int code, long elapsedMillis, String requestSummary) {
        return code + " " + elapsedMillis + "ms " + requestSummary;
    }
}
