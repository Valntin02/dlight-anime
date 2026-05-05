package com.dlight.data.remote;

import com.dlight.util.Param;

import retrofit2.Retrofit;

public class RetrofitClient {
    private static Retrofit retrofit;
    //Retrofit 本身是线程安全的。它的设计是允许多个线程并发地使用同一个 Retrofit 实例来发起不同的网络请求。

    // 私有化构造函数，确保单例
    private RetrofitClient() {}

    public static Retrofit getRetrofitInstance() {
        if (retrofit == null) {
            synchronized (RetrofitClient.class) { // synchronized 用于保证线程安全
                if (retrofit == null) {
                    retrofit = NetworkHelper.getRetrofitInstance(Param.getInstance().getBaseUrl());
                }
            }
        }
        return retrofit;
    }


}

