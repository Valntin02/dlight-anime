package com.dlight.network.api;

import com.dlight.network.model.ApiResponse;
import com.dlight.network.model.Vod;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * 视频相关的API接口
 */
public interface VodApi {

    /**
     * 获取今日推荐视频
     */
    @GET("/api/vodlist/get-today")
    Call<ApiResponse<List<Vod>>> getTodayVods();

    /**
     * 获取周推荐视频
     */
    @GET("/api/vodlist/get-weekly")
    Call<ApiResponse<List<Vod>>> getWeeklyVods(@Query("vod_weekday") String vodWeekday);

    /**
     * 搜索视频
     */
    @GET("/api/vodlist/search-vod")
    Call<ApiResponse<List<Vod>>> searchVods(@Query("input_search") String keyword);

    /**
     * 获取建议/搜索提示
     */
    @GET("/api/vodlist/suggest")
    Call<Map<String, Object>> getSearchSuggestion(@Query("input_search") String keyword);

    /**
     * 分页获取视频列表
     */
    @GET("/api/vodlist/vodlist-page")
    Call<ApiResponse<List<Vod>>> getVodListPage(
            @Query("page") int page,
            @Query("limit") int limit
    );
}
