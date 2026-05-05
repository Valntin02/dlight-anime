package com.dlight.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.dlight.network.api.VodApi;
import com.dlight.network.api.AuthApi;
import com.dlight.network.api.DanmakuApi;
import com.dlight.network.api.CommentApi;
import com.dlight.network.model.User;
import com.dlight.util.Param;

/**
 * 网络管理器 - 单例模式
 * 负责管理所有网络请求，包括Retrofit实例、Token管理、拦截器等
 */
public class NetworkManager {
    private static final String TAG = "NetworkManager";
    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";

    private static NetworkManager instance;
    private Retrofit retrofit;
    private Context context;

    private VodApi vodApi;
    private AuthApi authApi;
    private DanmakuApi danmakuApi;
    private CommentApi commentApi;

    private NetworkManager(Context context) {
        this.context = context.getApplicationContext();
        initRetrofit();
        initApis();
    }

    /**
     * 获取NetworkManager单例
     */
    public static synchronized NetworkManager getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkManager(context);
        }
        return instance;
    }

    /**
     * 初始化Retrofit
     */
    private void initRetrofit() {
        String baseUrl = Param.getInstance().getBaseUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        // 创建Gson实例
        Gson gson = new GsonBuilder()
                .setLenient()  // 宽松解析
                .create();

        // 日志拦截器 - Debug模式输出详细日志
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message -> {
            Log.d(TAG, message);
        });
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        // Token拦截器 - 自动添加Authorization头
        Interceptor tokenInterceptor = chain -> {
            Request originalRequest = chain.request();
            String token = getToken();

            if (token != null && !token.isEmpty()) {
                Request newRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer " + token)
                        .build();
                return chain.proceed(newRequest);
            }

            return chain.proceed(originalRequest);
        };

        // 创建OkHttpClient
        try {
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .addInterceptor(tokenInterceptor)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)  // 连接失败自动重试
                    .build();

            // 创建Retrofit
            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();

            Log.d(TAG, "NetworkManager initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize NetworkManager", e);
            throw e;
        }
    }

    /**
     * 初始化API服务
     */
    private void initApis() {
        vodApi = retrofit.create(VodApi.class);
        authApi = retrofit.create(AuthApi.class);
        danmakuApi = retrofit.create(DanmakuApi.class);
        commentApi = retrofit.create(CommentApi.class);
    }

    // ====== Token管理 ======

    /**
     * 获取保存的Token
     */
    private String getToken() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_TOKEN, "");
    }

    /**
     * 保存Token
     */
    public void saveToken(String token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_TOKEN, token).apply();
        Log.d(TAG, "Token saved");
    }

    /**
     * 清除Token（退出登录）
     */
    public void clearToken() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_TOKEN).apply();
        Log.d(TAG, "Token cleared");
    }

    /**
     * 检查是否已登录
     */
    public boolean isLoggedIn() {
        return !getToken().isEmpty();
    }

    // ====== 用户信息管理 ======

    /**
     * 保存用户信息
     */
    public void saveUserInfo(int userId, String userName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_USER_ID, userId)
                .putString(KEY_USER_NAME, userName)
                .apply();
    }

    /**
     * 获取用户ID
     */
    public int getUserId() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_USER_ID, 0);
    }

    /**
     * 获取用户名
     */
    public String getUserName() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_NAME, "");
    }

    /**
     * 清除用户信息
     */
    public void clearUserInfo() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_USER_ID)
                .remove(KEY_USER_NAME)
                .apply();
    }

    /**
     * 退出登录（清除Token和用户信息）
     */
    public void logout() {
        clearToken();
        clearUserInfo();
        Log.d(TAG, "User logged out");
    }

    // ====== API服务Getter ======

    /**
     * 获取VodApi
     */
    public VodApi getVodApi() {
        return vodApi;
    }

    /**
     * 获取AuthApi
     */
    public AuthApi getAuthApi() {
        return authApi;
    }

    /**
     * 获取DanmakuApi
     */
    public DanmakuApi getDanmakuApi() {
        return danmakuApi;
    }

    /**
     * 获取CommentApi
     */
    public CommentApi getCommentApi() {
        return commentApi;
    }
}
