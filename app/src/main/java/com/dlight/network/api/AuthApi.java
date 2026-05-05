package com.dlight.network.api;

import com.dlight.network.model.ApiResponse;
import com.dlight.network.model.User;

import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

/**
 * 认证相关的API接口
 */
public interface AuthApi {

    /**
     * 用户登录
     */
    @FormUrlEncoded
    @POST("/api/login/login")
    Call<ApiResponse<User>> login(
            @Field("username") String username,
            @Field("password") String password
    );

    /**
     * 用户注册
     */
    @FormUrlEncoded
    @POST("/api/login/register")
    Call<ApiResponse<User>> register(
            @Field("username") String username,
            @Field("password") String password
    );

    /**
     * 获取公告信息
     */
    @GET("/api/login/get-anno")
    Call<ApiResponse<Map<String, Object>>> getAnnouncement();

    /**
     * 上传用户头像
     */
    @Multipart
    @POST("/api/login/upload-avatar")
    Call<ApiResponse<Map<String, Object>>> uploadAvatar(
            @Part("url_flag") RequestBody urlFlag,
            @Part MultipartBody.Part file,
            @Header("Authorization") String token
    );

    /**
     * 同步收藏记录
     */
    @POST("/api/login/sync-my-star")
    Call<ApiResponse<Map<String, Object>>> syncStarRecords(
            @Body Map<String, Object> records,
            @Header("Authorization") String token
    );

    /**
     * 同步播放记录
     */
    @POST("/api/login/sync-play-record")
    Call<ApiResponse<Map<String, Object>>> syncPlayRecords(
            @Body Map<String, Object> records,
            @Header("Authorization") String token
    );

    /**
     * 获取同步播放记录
     */
    @GET("/api/login/play-record")
    Call<ApiResponse<Map<String, Object>>> getSyncPlayRecords(@Header("Authorization") String token);

    /**
     * 获取同步收藏记录
     */
    @GET("/api/login/user-star")
    Call<ApiResponse<Map<String, Object>>> getMyStars(@Header("Authorization") String token);
}
