package com.dlight;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.media3.datasource.DataSink;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.multidex.MultiDexApplication;

import com.dlight.network.NetworkManager;
import com.dlight.network.exosource.DlightExoHttpDataSourceFactory;
import com.dlight.util.Param;

import java.io.File;
import java.util.Map;

import tv.danmaku.ijk.media.exo2.ExoMediaSourceInterceptListener;
import tv.danmaku.ijk.media.exo2.ExoSourceManager;
import com.shuyu.gsyvideoplayer.player.IjkPlayerManager;
import com.shuyu.gsyvideoplayer.player.PlayerFactory;

/**
 * 次元之光Application类
 * 负责应用初始化
 */
public class DlightApplication extends MultiDexApplication {
    private static final String TAG = "DlightApp";
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();

        Log.d(TAG, "DlightApplication started");
        Log.d(TAG, "API baseUrl resolved: " + Param.getInstance().getBaseUrl());

        // 初始化网络管理器
        initNetworkManager();

        // 初始化视频播放器配置

        initPlayerConfig();

        // 配置Exo数据源
        initExoDataSource();
    }

    /**
     * 初始化网络管理器
     */
    private void initNetworkManager() {
        try {
            NetworkManager.getInstance(this);
            Log.d(TAG, "NetworkManager initialized");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize NetworkManager", e);
        }
    }

    /**
     * 初始化播放器配置
     */
    private void initPlayerConfig() {
        // 使用IJK播放器
        PlayerFactory.setPlayManager(IjkPlayerManager.class);

        // 代理缓存模式配置已移除，使用默认配置

        Log.d(TAG, "Player config initialized");
    }

    /**
     * 初始化Exo数据源
     */
    private void initExoDataSource() {
        ExoSourceManager.setExoMediaSourceInterceptListener(new ExoMediaSourceInterceptListener() {
            @Override
            public MediaSource getMediaSource(String dataSource, boolean preview, boolean cacheEnable,
                                              boolean isLooping, File cacheDir) {
                // 返回null使用默认配置
                return null;
            }

            /**
             * 自定义Http数据源工厂
             * 用于配置SSL证书和其他网络设置
             */
            @Override
            public DataSource.Factory getHttpDataSourceFactory(String userAgent, @Nullable TransferListener listener,
                                                                int connectTimeoutMillis, int readTimeoutMillis,
                                                                Map<String, String> mapHeadData, boolean allowCrossProtocolRedirects) {
                // 使用自定义的数据源工厂，支持自签证书
                DlightExoHttpDataSourceFactory factory = new DlightExoHttpDataSourceFactory(
                        userAgent, listener, connectTimeoutMillis, readTimeoutMillis, allowCrossProtocolRedirects);
                factory.setDefaultRequestProperties(mapHeadData);
                return factory;
            }

            @Override
            public DataSink.Factory cacheWriteDataSinkFactory(String CachePath, String url) {
                return null;
            }
        });
    }

    /**
     * 获取应用上下文
     */
    public static Context getAppContext() {
        return context;
    }
}
