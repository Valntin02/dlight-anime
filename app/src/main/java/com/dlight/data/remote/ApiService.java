package com.dlight.data.remote;

import com.dlight.data.local.MyStarRecord;
import com.dlight.data.local.MyStarResModel;
import com.dlight.data.local.PlayRecord;
import com.dlight.data.local.PlayRecordResModel;
import com.dlight.data.model.JsonResModel;
import com.dlight.data.model.VodPageResModel;
import com.dlight.data.model.VodResModel;
import com.dlight.feature.comment.CommentData;
import com.dlight.feature.comment.CommentResModel;
import com.dlight.feature.danmaku.DanmakuResModel;
import com.dlight.feature.user.UserAvatarResModel;
import com.dlight.feature.user.UserResModel;

import java.util.List;
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
import retrofit2.http.Query;

public interface ApiService {

    @FormUrlEncoded
    @POST("/api/danmaku/get")
    Call<DanmakuResModel> requestData(
        @Field("vod_id") int vodId,
        @Field("vod_nid") int vodNid
    );

    @FormUrlEncoded
    @POST("/api/danmaku/send-danmaku")
    Call<JsonResModel> sendDanmaku(
        @Field("vodId") int vodId,
        @Field("vodNid") int vodNid,
        @Field("content") String content,
        @Field("time") float time,
        @Field("color") int color,
        @Field("type") int type,
        @Header("Authorization") String token
    );

    @GET("/api/vodlist/get-weekly")
    Call<VodResModel> requestVodData(@Query("vod_weekday") String vod_weekday);

    @GET("/api/vodlist/get-today")
    Call<VodResModel> requestVodData();

    @FormUrlEncoded
    @POST("/api/login/login")
    Call<UserResModel> login(
        @Field("username") String username,
        @Field("password") String password
    );

    @FormUrlEncoded
    @POST("/api/login/register")
    Call<UserResModel> register(
        @Field("username") String username,
        @Field("password") String password
    );

    @Multipart
    @POST("/api/login/upload-avatar")
    Call<JsonResModel> requestUploadAvatar(
        @Part("url_flag") RequestBody urlFlag,
        @Part MultipartBody.Part file,
        @Header("Authorization") String token
    );

    @GET("/api/login/get-anno")
    Call<JsonResModel> requestAnnoData();

    @GET("/api/comment/comment-vod-id")
    Call<CommentResModel> requestComment(@Query("comment_rid") int comment_rid);

    @FormUrlEncoded
    @POST("/api/comment/comment-user-avatar")
    Call<UserAvatarResModel> requestUserAvatar(@Field("userIds") String userIds);

    @POST("/api/comment/comment-reply")
    Call<JsonResModel> requestReplyVod(@Body CommentData body,@Header("Authorization") String token);

    @FormUrlEncoded
    @POST("/api/comment/comment-up-down")
    Call<JsonResModel> requestCommentUpDown(
        @Field("commentId") int commentId,
        @Field("isLiked") boolean isLiked,
        @Header("Authorization") String token
    );

    @FormUrlEncoded
    @POST("/api/comment/comment-report")
    Call<JsonResModel> requestCommentReport(
        @Field("commentId") int commentId,
        @Header("Authorization") String token
    );

    @GET("/api/vodlist/suggest")
    Call<Map<String, Object>> requestSuggestData(@Query("input_search") String wd);

    @GET("/api/vodlist/search-vod")
    Call<VodResModel> requestRearchVodData(@Query("input_search") String wd);

    @GET("/api/vodlist/vodlist-page")
    Call<VodPageResModel> requestVideoPage(@Query("page") int page, @Query("limit") int limit, @Query("year") String year);

    @POST("/api/login/sync-my-star")
    Call<JsonResModel> syncStarRecords(@Body List<MyStarRecord> records, @Header("Authorization") String token);

    @POST("/api/login/sync-play-record")
    Call<JsonResModel> syncPlayRecords(@Body List<PlayRecord> records, @Header("Authorization") String token);

    @GET("/api/login/play-record")
    Call<PlayRecordResModel> requestPlayRecords(@Header("Authorization") String token);

    @GET("/api/login/user-star")
    Call<MyStarResModel> requestMyStars(@Header("Authorization") String token);
}
