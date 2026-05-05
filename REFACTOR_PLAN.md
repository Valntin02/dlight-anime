# 次元之光Android客户端改造计划

## 📋 项目目标

将次元之光Android客户端改造为现代化的视频应用，对齐后端API接口并应用与前端一致的主题设计。

---

## 🎯 第一阶段：网络层改造

### 1.1 创建统一的API配置

**文件**: `app/src/main/java/com/dlight/network/ApiConfig.java`

```java
package com.dlight.network;

/**
 * API配置类 - 统一管理后端接口地址
 */
public class ApiConfig {
    // 基础URL - 根据后端文档
    public static final String BASE_URL = "http://your-backend-url/api/";

    // 超时设置
    public static final long CONNECT_TIMEOUT = 15L;  // 连接超时15秒
    public static final long READ_TIMEOUT = 30L;      // 读取超时30秒
    public static final long WRITE_TIMEOUT = 15L;     // 写入超时15秒

    // API端点路径
    public static class Endpoints {
        // 用户认证
        public static final String LOGIN = "login/login";
        public static final String REGISTER = "login/register";
        public static final String UPLOAD_AVATAR = "login/upload-avatar";
        public static final String GET_PROFILE = "login/get-anno";

        // 视频相关
        public static final String GET_TODAY = "vodlist/get-today";
        public static final String GET_WEEKLY = "vodlist/get-weekly";
        public static final String SEARCH_VOD = "vodlist/search-vod";
        public static final String SUGGEST = "vodlist/suggest";
        public static final String VODLIST_PAGE = "vodlist/vodlist-page";
        public static final String GET_BY_IDS = "vodlist/get-by-ids";

        // 弹幕相关
        public static final String GET_DANMAKU = "danmaku/get";
        public static final String SEND_DANMAKU = "danmaku/send-danater";

        // 评论相关
        public static final String GET_COMMENTS = "comment/comment-vod-id";
        public static final String SEND_COMMENT = "comment/comment-reply";
        public static final String LIKE_COMMENT = "comment/comment-up-down";
        public static final String REPORT_COMMENT = "comment/comment-report";

        // 用户相关
        public static final String TOGGLE_STAR = "login/toggle-star";
        public static final String ADD_PLAY_RECORD = "login/add-play-record";
        public static final String GET_PLAY_RECORD = "login/play-record";
        public static final String GET_USER_STAR = "login/user-star";
    }
}
```

### 1.2 创建统一响应模型

**文件**: `app/src/main/main/java/com/dlight/network/model/ApiResponse.java`

```java
package com.dlight.network.model;

import com.google.gson.annotations.SerializedName;

/**
 * 统一API响应格式
 * 对应后端响应: { "code": 200, "message": "...", "data": ... }
 */
public class ApiResponse<T> {
    @SerializedName("code")
    private int code;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private T data;

    @SerializedName("msg")
    private String msg;

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message != null ? message : msg;
    }

    public T getData() {
        return data;
    }

    public boolean isSuccess() {
        return code == 200;
    }
}
```

### 1.3 创建数据模型

**视频模型**: `app/src/main/java/com/dlight/network/model/Vod.java`

```java
package com.dlight.network.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Vod {
    @SerializedName("vod_id")
    private int vodId;

    @SerializedName("vod_name")
    private String vodName;

    @SerializedName("vod_sub")
    private String vodSub;

    @SerializedName("vod_pic")
    private String vodPic;

    @SerializedName("vod_content")
    private String vodContent;

    @SerializedName("vod_play_url")
    private String vodPlayUrl;

    @SerializedName("type_id")
    private int typeId;

    @SerializedName("type_name")
    private String typeName;

    @SerializedName("vod_time")
    private String vodTime;

    @SerializedName("vod_remarks")
    private String vodRemarks;

    // Getters and Setters
    public int getVodId() { return vodId; }
    public String getVodName() { return vodName; }
    public String VodPic() { return vodPic; }
    public String getVodPlayUrl() { return vodPlayUrl; }
    // ...
}
```

**用户模型**: `app/src/main/java/com/dlight/network/model/User.java`

```java
package com.dlight.network.model;

import com.google.gson.annotations.SerializedName;

public class User {
    @SerializedName("user_id")
    private int userId;

    @SerializedName("user_name")
    private String userName;

    @SerializedName("user_portrait")
    private String userPortrait;

    @SerializedName("user_email")
    private String userEmail;

    @SerializedName("token")
    private String token;

    // Getters and Setters
    public int getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getUserPortrait() { return userPortrait; }
    public String getToken() { return token; }
}
```

**弹幕模型**: `app/src/main/java/com/dlight/network/model/Danmaku.java`

```java
package com.dlight.network.model;

import com.google.gson.annotations.SerializedName;

public class Danmaku {
    @SerializedName("text")
    private String text;

    @SerializedName("color")
    private int color;  // 后端返回整数颜色，如 16777215

    @SerializedName("time")
    private float time;

    @SerializedName("type")
    private int type;

    // Getters and Setters
    public String getText() { return text; }
    public int getColor() { return color; }
    public float getTime() { return time; }
    public int getType() { return type; }
}
```

### 1.4 创建API服务接口

**文件**: `app/src/main/java/com/dlight/network/api/VodApi.java`

```java
package com.dlight.network.api;

import com.dlight.network.model.ApiResponse;
import com.dlight.network.model.Vod;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * 视频API服务接口
 */
public interface VodApi {
    /**
     * 获取今日推荐视频
     */
    @GET(ApiConfig.Endpoints.GET_TODAY)
    Call<ApiResponse<List<Vod>>> getTodayVods();

    /**
     * 根据星期获取视频列表
     */
    @GET(ApiConfig.Endpoints.GET_WEEKLY)
    Call<ApiResponse<List<Vod>>> getWeeklyVods(@Query("vod_weekday") String weekday);

    /**
     * 搜索视频
     */
    @GET(ApiConfig.Endpoints.SEARCH_VOD)
    Call<ApiResponse<List<Vod>>> searchVod(@Query("input_search") String keyword);

    /**
     * 视频搜索建议
     */
    @GET(ApiConfig.Endpoints.SUGGEST)
    Call<ApiResponse<List<String>>> suggestVod(@Query("input_search") String keyword);

    /**
     * 分页获取视频列表
     */
    @GET(ApiConfig.Endpoints.VODLIST_PAGE)
    Call<ApiResponse<Object>> getVodListPage(
            @Query("page") int page,
            @Query("limit") int limit,
            @Query("type_name") String typeName,
            @Query("year") String year,
            @Query("status") Integer status
    );

    /**
     * 根据ID获取视频详情
     */
    @GET(ApiConfig.Endpoints.GET_BY_IDS)
    Call<ApiResponse<List<Vod>>> getVodByIds(@Query("vod_ids") String vodIds);
}
```

**文件**: `app/src/main/java/com/dlight/network/api/AuthApi.java`

```java
package com.dlight.network.api;

import com.dlight.network.model.ApiResponse;
import com.dlight.network.model.User;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * 用户认证API服务接口
 */
public interface AuthApi {
    /**
     * 用户登录
     */
    @POST(ApiConfig.Endpoints.LOGIN)
    @FormUrlEncoded
    Call<ApiResponse<User>> login(
            @Field("username") String username,
            @Field("password") String password
    );

    /**
     * 用户注册
     */
    @POST(ApiConfig.Endpoints.REGISTER)
    @FormUrlEncoded
    Call<ApiResponse<User>> register(
            @Field("username") String username,
            @Field("password") String password
    );

    /**
     * 上传头像
     */
    @Multipart
    @POST(ApiConfig.Endpoints.UPLOAD_AVATAR)
    Call<ApiResponse<String>> uploadAvatar(
            @Part MultipartBody.Part file,
            @Part("url_flag") RequestBody urlFlag
    );

    /**
     * 切换收藏状态
     */
    @POST(ApiConfig.Endpoints.TOGGLE_STAR)
    @FormUrlEncoded
    Call<ApiResponse<Object>> toggleStar(
            @Field("vodId") int vodId
    );
}
```

### 1.5 创建网络管理器

**文件**: `app/src/main/java/com/dlight/network/NetworkManager.java`

```java
package com.dlight.network;

import com.dlight.network.api.AuthApi;
import com.dlight.network.api.VodApi;
import com.dlight.network.api.DanmakuApi;
import com.dlight.network.api.CommentApi;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.TimeUnit;

/**
 * 网络管理器 - 单例模式
 */
public class NetworkManager {
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

    public static synchronized NetworkManager getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkManager(context);
        }
        return instance;
    }

    private void initRetrofit() {
        // 创建Gson实例
        Gson gson = new GsonBuilder()
                .setLenient()  // 宽松解析
                .create();

        // 日志拦截器
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
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
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(tokenInterceptor)
                .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
                .build();

        // 创建Retrofit
        retrofit = new Retrofit.Builder()
                .baseUrl(ApiConfig.BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
    }

    private void initApis() {
        vodApi = retrofit.create(VodApi.class);
        authApi = retrofit.create(AuthApi.class);
        danmakuApi = retrofit.create(DanmakuApi.class);
        commentApi = retrofit.create(CommentApi.class);
    }

    // Token管理
    private String getToken() {
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        return prefs.getString("token", "");
    }

    public void saveToken(String token) {
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("token", token).apply();
    }

    public void clearToken() {
        SharedPreferences prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    // Getter方法
    public VodApi getVodApi() {
        return vodApi;
    }

    public AuthApi getAuthApi() {
        return authApi;
    }

    public DanmakuApi getDanmakuApi() {
        return danmakuApi;
    }

    public CommentApi getCommentApi() {
        return commentApi;
    }
}
```

---

## 🎨 第二阶段：主题系统改造

### 2.1 更新colors.xml

**文件**: `app/src/main/res/values/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ====== 极光蓝品牌色系 ====== -->
    <color name="brand_50">#E0FBFF</color>
    <color name="brand_100">#B3F5FF</color>
    <color name="brand_200">#80EFFF</color>
    <color name="brand_300">#4DE9FF</color>
    <color name="brand_400">#26E4FF</color>
    <color name="brand_500">#00D1FF</color>  <!-- 核心品牌色 -->
    <color name="brand_600">#00ADDB</color>
    <color name="brand_700">#0089B7</color>
    <color name="brand_800">#006593</color>
    <color name="brand_900">#004A7A</color>

    <!-- ====== 深色主题背景 ====== -->
    <color name="dark_bg">#0A0E14</color>        <!-- 极深星空蓝 - 主背景 -->
    <color name="dark_card">#151921</color>      <!-- 卡片背景 -->
    <color name="dark_lighter">#1C212C</color>   <!-- 悬浮/交互状态 -->
    <color name="dark_border">#21262D</color>     <!-- 边框色 -->

    <!-- ====== 辅助颜色 ====== -->
    <color name="accent_500">#7000FF</color>     <!-- 深空紫 -->

    <!-- ====== 文本颜色 ====== -->
    <color name="text_primary">#E6EDF3</color>  <!-- 主要文本 -->
    <color name="text_secondary">#8B949E</color> <!-- 次要文本 -->
    <color name="text_tertiary">#6E7681</color> <!-- 三级文本 -->
    <color name="text_disabled">#484F58</color>  <!-- 禁用文本 -->

    <!-- ====== 状态颜色 ====== -->
    <color name="success">#52C41A</color>
    <color name="warning">#FAAD14</color>
    <color name="error">#FF4D4F</color>
    <color name="info">#1890FF</color>

    <!-- ====== 底部导航栏 ====== -->
    <color name="nav_bg">#14161F</color>
    <color name="nav_item_icon_color">#8B949E</color>
    <color name="nav_item_icon_color_selected">#00D1FF</color>
    <color name="nav_item_text_color">#8B949E</color>
    <color name="nav_item_text_color_selected">#00D1FF</color>

    <!-- ====== 其他 ====== -->
    <color name="transparent">#00000000</color>
    <color name="btn_rect_alpha">#56b3b3b3</color>
    <color name="comment_item_reply">#1C212C</color>
</resources>
```

### 2.2 更新styles.xml

**文件**: `app/src/main/res/values/styles.xml`

```xml
<resources xmlns:tools="http://schemas.android.com/tools">

    <!-- ====== 应用主主题 ====== -->
    <style name="AppTheme" parent="Theme.Material3.Dark.NoActionBar">
        <!-- 核心颜色 -->
        <item name="colorPrimary">@color/brand_500</item>
        <item name="colorPrimaryDark">@color/brand_700</item>
        <item name="colorAccent">@color/brand_500</item>

        <!-- 背景颜色 -->
        <item name="android:windowBackground">@color/dark_bg</item>
        <item name="android:colorBackground">@color/dark_bg</item>

        <!-- 文本颜色 -->
        <item name="android:textColorPrimary">@color/text_primary</item>
        <item name="android:textColorSecondary">@color/text_secondary</item>

        <!-- 状态栏 -->
        <item name="android:statusBarColor">@color/dark_bg</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">false</item>

        <!-- 导航栏 -->
        <item name="android:navigationBarColor">@color/dark_bg</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="m">false</item>

        <!-- 对话框主题 -->
        <item name="alertDialogTheme">@style/AlertDialogCustom</item>
    </style>

    <!-- ====== 底部导航栏主题 ====== -->
    <style name="BottomNavTheme" parent="Widget.Material3.BottomNavigationView">
        <item name="itemBackground">@color/transparent</item>
        <item name="itemIconTint">@color/nav_item_icon_color</item>
        <item name="itemTextColor">@color/nav_item_text_color</item>
        <item name="itemRippleColor">@color/brand_500</item>
    </style>

    <!-- ====== 卡片样式 ====== -->
    <style name="CardStyle" parent="Widget.Material3.CardView.Elevated">
        <item name="cardBackgroundColor">@color/dark_card</item>
        <item name="cardCornerRadius">12dp</item>
        <item name="cardElevation">2dp</item>
        <item name="strokeColor">@color/dark_border</item>
        <item name="strokeWidth">1dp</item>
    </style>

    <!-- ====== 按钮样式 ====== -->
    <style name="PrimaryButton" parent="Widget.Material3.Button">
        <item name="android:backgroundTint">@color/brand_500</item>
        <item name="android:textColor">@android:color/white</item>
        <item name="cornerRadius">8dp</item>
    </style>

    <style name="OutlinedButton" parent="Widget.Material3.Button.OutlinedButton">
        <item name="strokeColor">@color/brand_500</item>
        <item name="android:textColor">@color/brand_500</item>
        <item name="cornerRadius">8dp</item>
    </style>

    <!-- ====== 编辑框样式 ====== -->
    <style name="EditTextStyle" parent="Widget.Material3.TextInputLayout.OutlinedBox">
        <item name="boxBackgroundColor">@color/dark_card</item>
        <item name="boxStrokeColor">@color/dark_border</item>
        <item name="hintTextColor">@color/text_tertiary</item>
        <item name="android:textColor">@color/text_primary</item>
    </style>

    <!-- ====== 其他样式 ====== -->
    <style name="Theme.AppCompat.Translucent" parent="AppTheme">
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowDisablePreview">true</item>
    </style>

    <style name="Theme.AppCompat.Transitions" parent="AppTheme">
        <item name="android:windowContentTransitions">true</item>
    </style>

    <style name="Theme.AppCompat.Full" parent="Theme.AppCompat.Translucent">
        <item name="android:windowFullscreen">true</item>
    </style>

    <style name="dialog_style" parent="@android:style/Theme.Dialog">
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowNoTitle">true</item>
    </style>

    <style name="BottomNavTextAppearance" parent="TextAppearance.MaterialComponents.Caption">
        <item name="android:paddingTop">15dp</item>
        <item name="android:textSize">12sp</item>
    </style>
</resources>
```

### 2.3 创建通用组件样式

**文件**: `app/src/main/res/layout/item_vod_card.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="8dp"
    style="@style/CardStyle">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">

        <!-- 视频封面 -->
        <ImageView
            android:id="@+id/vod_image"
            android:layout_width="match_parent"
            android:layout_height="180dp"
            android:scaleType="centerCrop"
            android:background="@color/dark_lighter" />

        <!-- 视频标题 -->
        <TextView
            android:id="@+id/vod_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="视频标题"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:maxLines="2"
            android:ellipsize="end" />

        <!-- 视频副标题/分类 -->
        <TextView
            android:id="@+id/vod_subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="分类名称"
            android:textColor="@color/text_secondary"
            android:textSize="12sp" />

    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

---

## 📱 第三阶段：UI组件改造

### 3.1 创建MainActivity（带底部导航）

### 3.2 创建首页Fragment（今日推荐）

### 3.3 创建探索页Fragment（分类浏览）

### 3.4 创建搜索页Fragment

### 3.5 创建个人中心Fragment

### 3.6 创建视频详情Activity

---

## 🧪 第四阶段：测试计划

### 4.1 单元测试

- 网络层测试
- 数据模型测试
- 工具类测试

### 4.2 集成测试

- API接口调用测试
- 数据流测试

### 4.3 UI测试

- 界面显示测试
- 交互测试
- 播放器集成测试

---

## 📅 实施时间表

| 阶段 | 任务 | 预计时间 |
|------|------|----------|
| 第一阶段 | 网络层改造 | 2-3天 |
| 第二阶段 | 主题系统改造 | 1-2天 |
| 第三阶段 | UI组件改造 | 3-5天 |
| 第四阶段 | 测试与优化 | 2-3天 |

---

## 📝 注意事项

1. **保持向后兼容**: 改造过程中尽量保留现有功能
2. **渐进式改造**: 分模块逐步改造，每个阶段可独立测试
3. **代码规范**: 遵循Android开发最佳实践
4. **性能优化**: 注意内存泄漏、网络优化等
5. **文档更新**: 同步更新代码注释和项目文档

---

## 🚀 下一步行动

1. 创建网络层包结构
2. 实现NetworkManager
3. 更新主题资源文件
4. 创建测试用例
5. 开始逐步改造
