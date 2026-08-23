# 次元之光 Anime Android 客户端 — 当前架构与功能总览

> 本文描述当前代码，不记录迁移过程。根目录的 `PROJECT_SUMMARY.md`、`MIGRATION_REPORT.md`、`REFACTOR_PLAN.md` 等属于历史材料；发生冲突时以当前代码和本文为准。

## 1. 项目定位

- Java Android 动漫播放客户端，applicationId 为 `com.dlight`，业务集中在 `:app`。
- 基于 vendored GSYVideoPlayer 多模块源码；当前 `settings.gradle` 包含 `gsyVideoPlayer`、`gsyVideoPlayer-java`、`gsyVideoPlayer-base`、`gsyVideoPlayer-exo_player2`、代理缓存与三个 ABI 模块。
- `Main2Activity` 是 Manifest 中唯一 LAUNCHER，承载首页与“我的”两页导航。
- 后端为 FastAPI；所有业务接口集中在 `data/remote/ApiService.java`。

## 2. 顶层目录

```text
dlight-anime/
├── .github/workflows/android.yml       Debug/Release 自动质量门禁
├── app/                                业务应用、单测、Room schema
├── gsyVideoPlayer/                     播放器聚合模块
├── gsyVideoPlayer-base/                播放器基础 API
├── gsyVideoPlayer-java/                播放器 Java 实现
├── gsyVideoPlayer-exo_player2/         Exo2 支持
├── gsyVideoPlayer-proxy_cache/         代理缓存
├── gsyVideoPlayer-armv64/              arm64-v8a native 包
├── gsyVideoPlayer-armv7a/              armeabi-v7a native 包
├── gsyVideoPlayer-x86/                 x86 native 包
├── gradle/                             共享构建与依赖定义
├── doc/                                当前项目与上游播放器文档
├── docs/superpowers/                   设计与实施计划记录
├── build.gradle                        根构建配置
└── settings.gradle                     模块清单
```

## 3. `app` 代码结构

以下只列当前存在且承担主要职责的文件。

```text
app/src/main/java/com/dlight/
├── DlightApplication.java              恢复中断下载状态；初始化播放器与 Exo 数据源
├── CachedVideo.java                    本地视频条目
├── data/
│   ├── remote/
│   │   ├── ApiService.java             唯一业务 Retrofit 接口定义
│   │   ├── RetrofitClient.java         唯一 Retrofit 实例
│   │   ├── ApiClient.java              Call enqueue/callback 包装
│   │   └── AuthHeaderUtil.java         Bearer header 构造
│   ├── model/
│   │   ├── VodData.java
│   │   ├── VodResModel.java
│   │   ├── VodPageResModel.java
│   │   ├── JsonResModel.java
│   │   └── SwitchVideoModel.java
│   └── local/
│       ├── AppDatabase.java            Room v2 canonical database
│       ├── LegacyRecordImporter.java   旧收藏库稳定快照导入
│       ├── PlayRecord.java
│       ├── PlayRecordDao.java
│       ├── MyStarRecord.java
│       └── MyStarRecordDao.java
├── network/
│   ├── NetworkConfig.java              BuildConfig API origin 校验
│   ├── HttpClientFactory.java          API/图片 OkHttpClient
│   ├── ApiGsonFactory.java             Gson 配置
│   ├── SafeRequestLoggingInterceptor.java  Debug 安全请求摘要
│   ├── LocalOnly.java
│   ├── model/                          UI/播放器仍使用的网络模型
│   └── exosource/
│       ├── DlightDefaultHttpDataSource.java
│       └── DlightExoHttpDataSourceFactory.java
├── feature/
│   ├── user/
│   │   ├── LoginFragment.java
│   │   ├── UserFragment.java
│   │   └── SettingsActivity.java
│   ├── search/
│   │   ├── SearchActivity.java
│   │   ├── SearchRequestTracker.java
│   │   └── SearchResultAdapter.java
│   ├── comment/
│   │   ├── CommentFragment.java
│   │   ├── CommentBottomSheetFragment.java
│   │   ├── CommentSheetLayout.java
│   │   ├── CommentAdapter.java
│   │   └── ReplyDialogFragment.java
│   ├── danmaku/
│   │   ├── DanmakuOptionsFragment.java
│   │   ├── DanmakuData.java
│   │   └── DanmakuResModel.java
│   └── download/
│       ├── ActvityDownVideo.java        下载任务列表
│       ├── ServiceDownload.java         前台下载 Service
│       ├── VideoDownloader.java         HLS 下载执行器
│       ├── HlsPlaylistResolver.java     主/媒体播放列表解析
│       ├── DownloadUrlPolicy.java       URL 与地址范围策略
│       ├── DownloadHttpClient.java      平台 TLS + 固定 DNS 结果
│       ├── DownloadPreflight.java       网络/计费网络/空间预检
│       ├── DownloadProgressMetrics.java 进度、速度、ETA
│       ├── DownloadTask.java
│       └── DownloadTaskStore.java       任务持久化与中断恢复
├── ui/
│   ├── activity/Main2Activity.java     唯一启动 Activity
│   ├── fragment/
│   │   ├── BaseFragment.java
│   │   ├── MyMainFragment.java
│   │   └── MyPageFragment.java
│   ├── home/
│   │   ├── FragmentHomePage.java
│   │   ├── UpdateTodayFragment.java
│   │   ├── WeeklyShow.java
│   │   ├── FragmentAnime.java
│   │   └── HomeLoadStatePolicy.java
│   ├── player/
│   │   ├── DanmkuVideoActivity.java
│   │   ├── DanmakuVideoPlayer.java
│   │   ├── IntroFragment.java
│   │   ├── PlayerLoadStateController.java
│   │   ├── PlayerRecoveryTracker.java
│   │   ├── PlaySourceSelector.java
│   │   └── VodRecoveryMatcher.java
│   └── widget/LoadStateView.java        loading/empty/error/retry 通用状态视图
└── util/
    ├── ImageLoader.java                 Glide-only 图片入口
    ├── ImageUrlResolver.java
    ├── MyAppGlideModule.java            Glide OkHttp 集成
    ├── NotificationUtils.java
    ├── Param.java                       状态栏、设备/IP 等杂项；不是 API baseUrl 来源
    └── SakuraDanmukuParser.java
```

相关配置与持久化文件：

```text
app/src/main/AndroidManifest.xml
app/src/debug/AndroidManifest.xml
app/src/main/res/xml/network_security_config.xml
app/src/debug/res/xml/network_security_config.xml
app/schemas/com.dlight.data.local.AppDatabase/1.json
app/schemas/com.dlight.data.local.AppDatabase/2.json
```

## 4. 核心运行链路

### API

1. 页面从 `RetrofitClient.getRetrofitInstance()` 获取唯一 Retrofit。
2. `RetrofitClient` 使用 `NetworkConfig.apiBaseUrl()`、`ApiGsonFactory` 和 `HttpClientFactory.apiClient()`。
3. 页面创建 `ApiService` 并通过 `ApiClient` 处理异步回调；鉴权 header 由 `AuthHeaderUtil` 构造。
4. 新业务接口直接加入 `ApiService.java`，不要复制 Retrofit/client/config 职责。

### API 环境

- Debug 默认 origin 为 `http://10.0.2.2:8000/`，可用 `-PDLIGHT_DEBUG_API_BASE_URL=...` 覆盖。
- Release 必须提供 `-PDLIGHT_RELEASE_API_BASE_URL=https://host[:port]/`。
- Gradle 与 `NetworkConfig` 都只接受 origin 根地址：无业务 path、query、fragment 或非法 port。接口注解负责追加 `/api/...` 路径。

### 首页、搜索与播放恢复

- `LoadStateView` 统一 loading、empty、error 和 retry 表现。
- `HomeLoadStatePolicy` 管理今日、周更和动漫分页的缓存、重试、空态及过期响应。
- `SearchRequestTracker` 用 generation 忽略旧请求响应，避免搜索结果回退。
- 播放详情使用 `PlayerLoadStateController` 展示源恢复状态；`PlayerRecoveryTracker`、`PlaySourceSelector` 与 `VodRecoveryMatcher` 控制一次自动恢复、用户重试和过期回调。

### 下载

- `DownloadPreflight` 在启动/恢复前检查有效网络、计费网络确认和至少 256 MiB 可用空间。
- `DownloadProgressMetrics` 汇总已下载字节、分片进度、速度和 ETA，并节流进度通知。
- `DownloadTaskStore` 持久化任务；应用启动时把意外中断的 active 任务协调为 paused，等待显式恢复。
- `DownloadUrlPolicy` 校验 playlist/segment URL。Release 拒绝本地、私有、链路本地、组播、IPv6 ULA 与 CGNAT 地址；Debug 允许本地联调。
- `DownloadHttpClient` 使用平台 TLS，不安装自定义 trust manager；同一次请求使用策略校验后的固定 DNS 地址列表，保留原 hostname 供 HTTPS/SNI 校验，并禁用自动重定向。

## 5. 图片、TLS 与网络安全

- 图片统一由 `ImageLoader` 使用 Glide；`MyAppGlideModule` 将 Glide 接到 `HttpClientFactory.imageClient()`。
- `IntroFragment`、首页、搜索、评论头像、用户头像、播放缩略图和下载封面都走同一图片入口。
- `app/src/main/res/xml/network_security_config.xml` 设置 `cleartextTrafficPermitted="false"`；主/Release 不接受明文 HTTP。
- `app/src/debug/res/xml/network_security_config.xml` 才设置 `cleartextTrafficPermitted="true"`，用于模拟器本地 API。
- API、图片、Exo 和下载链路均依赖平台 TLS 信任，没有全局 trust-all 或跳过 hostname 校验。

## 6. Room 数据

- `AppDatabase` 当前 schema version 为 2，包含 `PlayRecord` 与 `MyStarRecord` 两张表，统一存储在 `play_record_db`。
- `MIGRATION_1_2` 为两张表增加可空的 `vod_play_data`；schema 1/2 均导出到 `app/schemas/`。
- 两个公开 getter 都返回同一个 canonical singleton，不再按调用顺序选择不同数据库。
- `LegacyRecordImporter` 检测旧 `myStar_records` 文件，连同 WAL/SHM 生成带指纹验证的稳定只读快照，在 canonical transaction 中去重导入；源文件不被删除，完成标志确保一次性执行。

## 7. 构建、安全配置与依赖

- AGP 8.6.1；`app` 使用 Java、ViewBinding、MultiDex、Room 2.6.1、Retrofit 2.9.0、OkHttp 4.12.0 和 Glide 4.14.0。
- 图片栈为 Glide-only；封面与头像应继续通过 `ImageLoader`。
- Release API origin 必须显式传入 HTTPS 值，否则 `preReleaseBuild` 失败。
- Release signing 只读取环境变量：`DLIGHT_RELEASE_STORE_FILE`、`DLIGHT_RELEASE_STORE_PASSWORD`、`DLIGHT_RELEASE_KEY_ALIAS`、`DLIGHT_RELEASE_KEY_PASSWORD`。四项完整时签名；缺失时 Release 保持 unsigned。
- `app` 使用 `lint { abortOnError true }`；`:app:lintDebug`/`:app:lintRelease` 的错误会阻断构建。
- `.github/workflows/android.yml` 在 push/PR 执行 Debug/Release 单测、lint、构建及基础敏感信息/过期依赖扫描；不发布或签名 APK。

常用验证命令（使用 Android Studio JBR 与本机 Android SDK）：

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease \
  -PDLIGHT_RELEASE_API_BASE_URL=https://example.com/
```

## 8. 当前功能

| 模块 | 当前能力 | 关键入口 |
|---|---|---|
| 首页 | 公告、今日、周更、动漫分页；加载/空/错/重试状态 | `FragmentHomePage`, `HomeLoadStatePolicy`, `LoadStateView` |
| 搜索 | 历史、联想、结果；忽略过期响应 | `SearchActivity`, `SearchRequestTracker` |
| 播放 | IJK/GSY、弹幕、详情、播放源恢复 | `DanmkuVideoActivity`, `PlayerRecoveryTracker`, `IntroFragment` |
| 评论 | 列表、回复、点赞/举报、头像批量加载 | `CommentFragment`, `CommentAdapter` |
| 用户 | 登录注册、头像、历史、收藏、设置 | `LoginFragment`, `UserFragment`, `SettingsActivity` |
| 本地记录 | Room v2 收藏/历史、显式 migration、旧库一次性导入 | `AppDatabase`, `LegacyRecordImporter` |
| 下载 | HLS 解析、任务恢复、预检、速度/ETA、URL/DNS 防护 | `VideoDownloader`, `DownloadTaskStore`, `DownloadPreflight` |

## 9. 当前未闭环项

1. **根工程聚合 lint**：vendored `gsyVideoPlayer-java` 仍报告 `RestrictedApi`；这是播放器源码模块问题。`app` 自身 lint gate 已启用并应单独保持通过。
2. **设备 smoke 覆盖**：自动化测试、App lint 和构建覆盖已补齐，但首页、搜索、播放恢复、图片、下载与 legacy database 导入尚未在所有目标设备/API 级别完成端到端 smoke。

## 10. 快速入口

- 新增/修改业务接口：`app/src/main/java/com/dlight/data/remote/ApiService.java`
- 修改 Retrofit 构造：`app/src/main/java/com/dlight/data/remote/RetrofitClient.java`
- 修改 API origin 规则：`app/src/main/java/com/dlight/network/NetworkConfig.java` 与 `app/build.gradle`
- 修改 OkHttp：`app/src/main/java/com/dlight/network/HttpClientFactory.java`
- 修改图片加载：`app/src/main/java/com/dlight/util/ImageLoader.java`
- 修改 Room：`app/src/main/java/com/dlight/data/local/AppDatabase.java`、`LegacyRecordImporter.java` 与 `app/schemas/`
- 修改下载：`app/src/main/java/com/dlight/feature/download/`
- 修改加载状态：`app/src/main/java/com/dlight/ui/widget/LoadStateView.java`
- 修改播放器恢复：`app/src/main/java/com/dlight/ui/player/PlayerRecoveryTracker.java`
