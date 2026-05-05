package com.dlight.network.api;

import com.dlight.network.model.ApiResponse;
import com.dlight.network.model.Danmaku;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * 弹幕相关的API接口
 */
public interface DanmakuApi {

    /**
     * 获取弹幕数据
     */
    @FormUrlEncoded
    @POST("/api/danmaku/get")
    Call<ApiResponse<List<Danmaku>>> getDanmaku(
            @Field("vod_id") int vodId,
            @Field("vod_nid") int vodNid
    );

    /**
     * 发送弹幕
     */
    @FormUrlEncoded
    @POST("/api/danmaku/send-danmaku")
    Call<ApiResponse<Map<String, Object>>> sendDanmaku(
            @Field("vodId") int vodId,
            @Field("vodNid") int vodNid,
            @Field("content") String content,
            @Field("time") float time,
            @Field("color") int color,
            @Field("type") int type,
            @Header("Authorization") String token
    );
}
