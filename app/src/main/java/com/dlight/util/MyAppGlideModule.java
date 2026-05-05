package com.dlight.util;

import android.content.Context;
import com.bumptech.glide.Glide;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.Registry;
import com.dlight.data.remote.NetworkHelper;

import java.io.InputStream;

import okhttp3.OkHttpClient;
//自己定义一个glide 使用自己定义的okhttpclient,这里设置的是glide 和单独使用okhttpclient没有关系
@com.bumptech.glide.annotation.GlideModule
public class MyAppGlideModule extends AppGlideModule {
    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        OkHttpClient okHttpClient = NetworkHelper.getOkHttpClient(); // 获取自定义的 OkHttpClient
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory((okhttp3.Call.Factory) okHttpClient));
    }
}
