package com.dlight.network.api;

import com.dlight.network.model.ApiResponse;
import com.dlight.network.model.Comment;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * 评论相关的API接口
 */
public interface CommentApi {

    /**
     * 获取视频评论
     */
    @GET("/api/comment/comment-vod-id")
    Call<ApiResponse<List<Comment>>> getComments(@Query("comment_rid") int commentRid);

    /**
     * 请求用户头像
     */
    @FormUrlEncoded
    @POST("/api/comment/comment-user-avatar")
    Call<ApiResponse<Map<String, Object>>> getUserAvatar(@Field("userIds") String userIds);

    /**
     * 回复视频评论
     */
    @POST("/api/comment/comment-reply")
    Call<ApiResponse<Map<String, Object>>> replyComment(
            @Body Comment comment,
            @Header("Authorization") String token
    );

    /**
     * 评论点赞/取消点赞
     */
    @FormUrlEncoded
    @POST("/api/comment/comment-up-down")
    Call<ApiResponse<Map<String, Object>>> commentUpDown(
            @Field("commentId") int commentId,
            @Field("isLiked") boolean isLiked,
            @Header("Authorization") String token
    );

    /**
     * 举报评论
     */
    @FormUrlEncoded
    @POST("/api/comment/comment-report")
    Call<ApiResponse<Map<String, Object>>> reportComment(
            @Field("commentId") int commentId,
            @Header("Authorization") String token
    );
}
