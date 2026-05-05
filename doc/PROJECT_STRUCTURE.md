# 次元之光 Anime Android 客户端 — 项目结构与功能总览

> 用途：帮助新进开发者 / AI 助手 在最短时间内建立项目心智模型。包含模块划分、关键类、数据流、已实现功能、注意事项。
> 维护：当目录结构、关键模块或后端接口发生变化时同步更新。

---

## 1. 项目定位

- **形态**：一款基于 Java 的 Android 动漫播放客户端（applicationId: `com.dlight`，versionName 由 `PROJ_VERSION` 注入，versionCode 26）。
- **后端**：自建 FastAPI（默认 baseUrl 由 `Param.java` 控制，联调阶段强制走 `http://127.0.0.1:8000/`，配合 `adb reverse tcp:8000 tcp:8000`；生产可切回 `https://113.45.243.38:10001`）。
- **核心库**：基于 [GSYVideoPlayer](https://github.com/CarGuo/GSYVideoPlayer) 多模块工程二次开发，主体业务集中在 `:app` 模块，其余 `:gsyVideoPlayer-*` 系列为播放器内核与解码器分包。

## 2. 顶层目录

```
dlight-anime/
├── app/                                ← 业务主模块（com.dlight）
├── gsyVideoPlayer/, gsyVideoPlayer-*/  ← GSY 播放器多内核（exo2/aliplay/ijk + abi 分包）
├── 16kpatch/                           ← 16KB Page Size SO 适配补丁
├── doc/                                ← 项目说明文档（包含本文件）
├── img/                                ← README 用图片资源
├── gradle/                             ← 依赖、版本统一脚本（dependencies.gradle 等）
├── settings.gradle / build.gradle      ← 多模块工程入口
├── module-lite*.sh                     ← 精简模块用脚本
├── README.md                           ← 上游 GSYVideoPlayer 自带说明
├── PROJECT_SUMMARY.md / MIGRATION_REPORT.md / REFACTOR_PLAN.md / ...
│                                       ← 历史改造与重构记录（可作背景了解）
└── release.jks                         ← 签名（debug/release 共用，密钥写死在 build.gradle）
```

## 3. `app` 模块结构

入口：`AndroidManifest.xml` 中以 `com.dlight.ui.activity.Main2Activity` 为 LAUNCHER；老的 `MainActivity` 是一套并行的导航壳（HomeFragment / ExploreFragment / SearchFragment / ProfileFragment），但当前未挂入启动入口。

```
app/src/main/java/com/dlight/
├── DlightApplication.java       ← MultiDex Application；初始化网络/播放器/Exo 数据源
├── CachedVideo.java                    ← 已下载视频条目模型
│
├── ui/
│   ├── activity/
│   │   ├── Main2Activity.java          ← 当前启动入口；BottomNav: 首页(MyMainFragment) + 我的(MyPageFragment)；onDestroy 触发收藏/播放记录同步
│   │   └── MainActivity.java           ← 备选导航壳（Home/Explore/Search/Profile）
│   ├── fragment/
│   │   ├── BaseFragment.java           ← 通用 Fragment 基类（getLayoutResId/initView/initData）
│   │   ├── BottomNavigationFragment.java
│   │   ├── MyMainFragment.java         ← 首页容器，承载 TopNavigation + FragmentHomePage / FragmentAnime 切换
│   │   ├── MyPageFragment.java         ← “我的”容器；按登录态加载 LoginFragment / UserFragment，实现登出回调
│   │   ├── HomeFragment.java / ExploreFragment.java / SearchFragment.java / ProfileFragment.java
│   │                                   ← 配合 MainActivity 的新版导航，目前只 HomeFragment 接通了 NetworkManager
│   ├── home/
│   │   ├── TopNavigationFragment.java  ← 顶部 “首页/动漫/搜索” 切换 + 跳转 SearchActivity
│   │   ├── FragmentHomePage.java       ← 首页内容容器（公告 + 今日更新 + 周更）
│   │   ├── AnnouncementBar.java        ← 公告滚动条，调用 /api/login/get-anno
│   │   ├── UpdateTodayFragment.java    ← 今日推荐（横向 RecyclerView，调用 get-today）
│   │   ├── WeeklyShow.java             ← 周更（按周几切换 + 内存缓存，调用 get-weekly）
│   │   ├── FragmentAnime.java          ← 动漫库分页 + 年份过滤（vodlist-page）
│   │   ├── VideoAdapter.java / YearIndexAdapter.java
│   ├── adapter/
│   │   └── VodAdapter.java             ← 新版 Vod 卡片适配器
│   └── player/
│       ├── DanmkuVideoActivity.java    ← ⭐ 主播放页：弹幕 + ViewPager2(详情/评论) + 播放记录写入
│       ├── DanmakuVideoPlayer.java     ← 继承 StandardGSYVideoPlayer，集成 DanmakuFlameMaster
│       ├── IntroFragment.java          ← 视频详情页（剧集按钮、收藏、下载、Picasso 封面）
│       ├── DetailPlayer.java           ← Media3/ExoPlayer 详情播放示例（轨道选择、PiP）
│       ├── PlayActivity.java / PlayTVActivity.java ← 单纯的播放示例（含切清晰度、共享元素过渡）
│       ├── SimplePlayer.java           ← 用于本地缓存视频回放（接收 file path）
│       ├── GSYExo2PlayerView.java      ← Exo2 自定义视图
│       └── ViewPagerAdapter.java
│
├── feature/
│   ├── user/
│   │   ├── LoginFragment.java          ← 登录/注册（/api/login/login, /api/login/register）
│   │   ├── UserFragment.java           ← 用户中心：头像上传、跳转历史/收藏/下载、退出登录
│   │   └── UserResModel / UserAvatarResModel / UserAvatarData
│   ├── search/
│   │   ├── SearchActivity.java         ← 搜索 + 历史 + 联想（/api/vodlist/suggest、search-vod）
│   │   ├── SearchResultAdapter.java
│   │   └── ReplyActivity.java          ← 评论二级回复弹窗页面
│   ├── comment/
│   │   ├── CommentFragment.java        ← 评论列表 + 输入框（评论 + 头像批量请求 + 点赞/举报）
│   │   ├── CommentBottomSheetFragment.java / ReplyDialogFragment.java
│   │   ├── CommentAdapter / CommentReplyAdapter / CommentData / CommentResModel
│   ├── danmaku/
│   │   ├── DanmakuOptionsFragment.java ← 底部弹窗：发送弹幕（颜色/类型/内容）
│   │   ├── DanmakuData / DanmakuReqModel / DanmakuResModel / DanmakuView_temp
│   └── download/
│       ├── ActvityDownVideo.java       ← 离线视频列表（读取 cover_map.json）
│       ├── AdapterDownVideo.java
│       ├── ServiceDownload.java        ← 前台 Service：拉取 m3u8 分片下载
│       └── VideoDownloader.java        ← 多线程下载器，进度回调通知栏
│
├── network/                            ← 新版网络层（推荐使用）
│   ├── NetworkManager.java             ← 单例：Retrofit + Gson + 日志/Token 拦截器 + SharedPreferences token 管理
│   ├── ApiConfig.java                  ← BASE_URL / 超时常量（已被 Param 动态 baseUrl 取代）
│   ├── api/
│   │   ├── VodApi.java                 ← today / weekly / search / suggest / vodlist-page
│   │   ├── AuthApi.java                ← login / register / get-anno / upload-avatar / sync-* / play-record / user-star
│   │   ├── CommentApi.java             ← comment-vod-id / comment-user-avatar / comment-reply / comment-up-down / comment-report
│   │   └── DanmakuApi.java             ← danmaku/get / danmaku/send-danmaku
│   ├── model/                          ← 新版 ApiResponse<T> + Vod / User / Comment / Danmaku
│   └── exosource/
│       ├── DlightDefaultHttpDataSource.java
│       └── DlightExoHttpDataSourceFactory.java   ← 自签证书/SSL 友好的 Exo 数据源
│
├── data/
│   ├── remote/                         ← 旧版网络层（仍被多数页面直接使用）
│   │   ├── RetrofitClient.java         ← 单例 Retrofit（基于 Param baseUrl）
│   │   ├── ApiService.java             ← 与 network/api 几乎重叠的“大杂烩”接口集合
│   │   ├── ApiClient.java              ← 通用 enqueue + ApiResponseCallback 包装
│   │   ├── NetworkHelper.java          ← OkHttpClient/Retrofit 构造（含证书放行）
│   │   ├── AuthHeaderUtil.java         ← bearer(token) 等 Header 帮助方法
│   │   └── GSYExoHttpDataSourceFactory.java
│   ├── model/
│   │   ├── VodData.java / VodResModel.java / VodPageResModel.java
│   │   ├── SwitchVideoModel.java       ← 多清晰度切换模型
│   │   └── JsonResModel.java           ← 旧版统一响应 (code / msg / data / isSuccessCode)
│   └── local/                          ← Room (本地数据库)
│       ├── AppDatabase.java            ← @Database({PlayRecord, MyStarRecord}, version=1)
│       ├── PlayRecord(.java/Dao/ResModel) + PlayRecordActivity / PlayRecordAdapter
│       ├── MyStarRecord(.java/Dao/ResModel) + MyStarActivity
│       └── ⚠ 注意：getInstancePlayRecord / getInstanceMyStarRecord 共用同一 instance 字段，
│         先调用谁就把 DB 文件名定死成谁（潜在 bug，新增表/库时要留意）
│
└── util/
    ├── Param.java                      ← ★ 单例：baseUrl/IP 获取/状态栏样式/模拟器探测
    ├── CommonUtil.java
    ├── NotificationUtils.java          ← 下载通知通道与构造
    ├── SmallVideoHelper.java           ← GSY 列表小窗辅助
    ├── SharedViewModel.java            ← Fragment 间共享 LiveData
    ├── BiliDanmukuParser.java / SakuraDanmukuParser.java ← 自定义弹幕解析器
    ├── LikeCacheManager.java
    ├── MyAppGlideModule.java           ← Glide 模块定义
    └── OnTransitionListener.java       ← 共享元素过渡空实现
```

### 资源（`app/src/main/res`）
- `layout/`：activity 与 fragment 布局；带 `activity_migrated_*` 前缀的是改造迁移过程中的新版本布局。
- `menu/`：底部导航 / 顶部菜单。
- `xml/network_security_config.xml`：允许明文流量与自签证书（配合联调环境）。
- `raw/`、`drawable/*`、`mipmap-*`、`color/*`：图标、配色、形状资源。

> 仓库中还存在 `app/src/main/app/src/main/...` 这种二级嵌套的同名目录（git status 中可见若干已修改/新增文件），属于历史误提交残留，新代码请只放在第一级 `app/src/main/...` 目录下。

## 4. 构建与依赖速查

- AGP 8.6.1，Java 编译目标见 `gradle/base.gradle` / `gradle/exported.gradle`。
- ABI 仅打包 `arm64-v8a`、`armeabi-v7a`、`x86`。
- ViewBinding 已开启；`debug` 与 `release` 都使用 `release.jks` 签名（密钥明文写在 `app/build.gradle`，注意不要外泄）。
- 关键依赖：
  - 网络：`com.squareup.retrofit2:retrofit:2.9.0`、`okhttp 4.12.0`、`okhttp logging-interceptor`、`zhy/okhttputils`
  - 图片：`Glide 4.14.0`、`Picasso 2.71828`
  - 数据库：`androidx.room 2.6.1`
  - UI：`com.google.android.material`、`androidx.viewpager2`、`recyclerview 1.4.0`、`appcompat`
  - 播放器：`:gsyVideoPlayer` + `:gsyVideoPlayer-aliplay`，弹幕 `DanmakuFlameMaster`
  - 权限：`permissionsdispatcher`
  - 调试：`leakcanary`（仅 debug）
  - 多 dex：`androidx.multidex`

## 5. 启动与运行流程

1. `Application.onCreate`（`DlightApplication`）
   - `Param.getInstance()` 解析 baseUrl（联调写死 `127.0.0.1:8000`，便于 `adb reverse`）。
   - `NetworkManager.getInstance(this)` 构造 Retrofit 单例并注入 Token 拦截器。
   - `PlayerFactory.setPlayManager(IjkPlayerManager.class)` 选择 IJK 播放内核。
   - `ExoSourceManager.setExoMediaSourceInterceptListener` 注入自签证书友好的 Http DataSource Factory。
2. `Main2Activity.onCreate`：BottomNav 切换 `MyMainFragment` ↔ `MyPageFragment`；onDestroy 在线程池中触发未同步收藏 / 播放记录回传后端。
3. `MyMainFragment` → `TopNavigationFragment` + `FragmentHomePage`（公告 / 今日 / 周更）/ `FragmentAnime`（分页+年份筛选）。
4. 视频卡片点击 → `DanmkuVideoActivity`（带 ViewPager2：`IntroFragment` 详情、`CommentFragment` 评论；播放器为 `DanmakuVideoPlayer`，发弹幕走 `DanmakuOptionsFragment` 底部表单）。
5. 收藏 / 历史先写 Room（`AppDatabase`），未同步条目在 `Main2Activity.onDestroy` 上传后端，下次进入若本地为空则从后端拉取一次。
6. 离线下载：`UserFragment` → `ActvityDownVideo` 列表 → `ServiceDownload` 前台服务 → `VideoDownloader.mulDownloadM3u8` 多线程下分片，完成后写入 `files/video/cover_map.json`，回放走 `SimplePlayer`。

## 6. 已实现的核心功能清单

| 模块 | 功能要点 | 关键文件 |
|---|---|---|
| 启动 / 框架 | MultiDex、网络/播放器/Exo 数据源初始化、状态栏沉浸 | `DlightApplication`, `Param.setStatusBarTransparent` |
| 首页 | 公告滚动栏、今日更新、按周几切换的周更（含内存缓存） | `FragmentHomePage`, `AnnouncementBar`, `UpdateTodayFragment`, `WeeklyShow` |
| 动漫库 | 分页加载、上拉触底、年份索引筛选 | `FragmentAnime`, `YearIndexAdapter`, `VodApi#getVodListPage` |
| 搜索 | 关键词联想、搜索历史本地存储、结果列表 | `SearchActivity`, `SearchResultAdapter` |
| 视频播放 | IJK 内核 + 弹幕；多清晰度切换；共享元素过渡；Picture-in-Picture（DetailPlayer） | `DanmkuVideoActivity`, `DanmakuVideoPlayer`, `DetailPlayer`, `PlayActivity`, `PlayTVActivity` |
| 弹幕 | 拉取 / 发送弹幕，自定义 `SakuraDanmukuParser`，颜色 + 类型选择 | `DanmakuApi`, `DanmakuOptionsFragment`, `SakuraDanmukuParser` |
| 评论 | 一级评论列表、二级回复、点赞/取消、举报；批量获取头像 | `CommentFragment`, `CommentApi`, `ReplyDialogFragment`, `CommentBottomSheetFragment` |
| 用户中心 | 登录/注册、头像上传(Multipart)、登出 | `LoginFragment`, `UserFragment`, `AuthApi#login/register/uploadAvatar` |
| 历史 / 收藏 | Room 本地双表，应用退出时同步未同步条目，进入页面时按需回拉 | `AppDatabase`, `PlayRecord*`, `MyStarRecord*`, `Main2Activity#syncPlayRecord/syncMyStarData` |
| 离线下载 | m3u8 分片多线程下载、前台 Service、通知栏进度、本地播放 | `ServiceDownload`, `VideoDownloader`, `ActvityDownVideo`, `SimplePlayer`, `NotificationUtils` |
| 公告 | 接口拉取 + 弹窗 / 滚动条 | `AnnouncementBar`, `AuthApi#getAnnouncement` |
| 网络层 | Retrofit + OkHttp、Token 拦截、Bearer Header 工具、自签证书 ExoDataSource | `NetworkManager`, `RetrofitClient`, `NetworkHelper`, `AuthHeaderUtil`, `Dlight*HttpDataSource*` |

## 7. 后端 API 速查（baseUrl 见 `Param`）

- 认证 / 用户：`/api/login/{login,register,get-anno,upload-avatar,sync-my-star,sync-play-record,play-record,user-star}`
- 视频：`/api/vodlist/{get-today,get-weekly,search-vod,suggest,vodlist-page}`
- 评论：`/api/comment/{comment-vod-id,comment-user-avatar,comment-reply,comment-up-down,comment-report}`
- 弹幕：`/api/danmaku/{get,send-danmaku}`

> 同一接口在 **新版 `network/api/*Api`** 与 **旧版 `data/remote/ApiService`** 中均有定义，新功能优先使用 `NetworkManager.getInstance(...).getXxxApi()`。

## 8. 注意事项 / 已知坑

1. **双套网络层并存**：`network/*` 是改造后版本（含 `ApiResponse<T>` 与统一 Token 拦截），但绝大多数老页面仍在用 `data/remote/RetrofitClient + ApiService + ApiClient`。新增功能尽量走新版；改老页面时注意 Token Header 取得方式不同。
2. **`Param.baseUrl` 写死了模拟器联调地址**：上线/真机调试时需要改 `Param` 构造或调用 `setBaseUrl(...)`。
3. **`AppDatabase` 单例混用**：`getInstancePlayRecord` 与 `getInstanceMyStarRecord` 共享 `instance`，先调用方决定数据库文件名（`play_record_db` 或 `myStar_records`），后续要拆分时记得分开持有。
4. **签名密钥明文**：`app/build.gradle` 中 `release.jks` 的口令为明文（仅适合开发环境），切勿对外公开仓库。
5. **目录嵌套残留**：`app/src/main/app/src/main/...` 是历史 commit 误嵌套，git status 仍显示这些路径下的修改；新代码请放在正确的一级目录。
6. **GitHub Maven 凭据明文**：`build.gradle` 中带有上游 GSYVideoPlayer 私库的 PAT，需要的话替换为本地凭据。
7. **构建脚本**：使用 Android Studio 自带 JDK 构建（Skill: `build-android`）；如需精简模块，可参考根目录 `module-lite*.sh`。
8. **历史改造文档**：根目录的 `PROJECT_SUMMARY.md`、`MIGRATION_REPORT.md`、`REFACTOR_PLAN.md`、`CLEANUP_COMPLETE.md`、`COMPILATION_FIX_REPORT.md`、`UNUSED_PACKAGES.md`、`TEST_PLAN.md`、`IMPLEMENTATION_PROGRESS.md` 记录了改造背景，做存量改动前可以翻一下。

## 9. 快速入口索引

- 想看启动入口 → `app/src/main/java/com/dlight/ui/activity/Main2Activity.java`
- 想加新接口 → `app/src/main/java/com/dlight/network/api/`（再到 `NetworkManager` 暴露 getter）
- 想动播放器 → `app/src/main/java/com/dlight/ui/player/DanmkuVideoActivity.java` 与 `DanmakuVideoPlayer.java`
- 想动数据库 → `app/src/main/java/com/dlight/data/local/AppDatabase.java` 及对应 `*Dao`
- 想换皮 / 颜色 → `app/src/main/res/values/colors.xml` + `styles.xml`
- 想改全局 baseUrl → `app/src/main/java/com/dlight/util/Param.java`
