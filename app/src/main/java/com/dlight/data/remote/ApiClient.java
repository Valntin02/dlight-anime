package com.dlight.data.remote;

import android.util.Log;
import java.io.IOException;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class ApiClient {

    private static final String TAG = "ApiClient";

    // 简化方法，传入不同的 Call 对象
    public static <T> void requestData(Call<T> call, ApiResponseCallback<T> callback) {
        // 异步请求
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                if (response.isSuccessful()) {
                    T data = response.body();
                    if (data != null) {
                        // 回调成功，并传递数据
                        callback.onSuccess(data);
                    } else {
                        callback.onFailure("HTTP " + response.code() + " response body is null");
                    }
                } else {
                    String errorBody = "";
                    try {
                        if (response.errorBody() != null) {
                            errorBody = response.errorBody().string();
                        }
                    } catch (IOException ignored) {
                    }
                    String message = "HTTP " + response.code() + " " + response.message();
                    if (!errorBody.isEmpty()) {
                        message = message + " | " + errorBody;
                    }
                    Log.e(TAG, "Request failed: " + message);
                    callback.onFailure(message);

                }
            }

            @Override
            public void onFailure(Call<T> call, Throwable t) {
                //Log.e(TAG, "Error: " + t.getMessage());
                callback.onFailure("Error: " + t.getMessage());
            }
        });
    }

    // 定义一个 API 响应回调接口
    public interface ApiResponseCallback<R> {
        void onSuccess(R data);
        void onFailure(String error);

    }
}
