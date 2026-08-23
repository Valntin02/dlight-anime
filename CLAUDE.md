# CLAUDE.md

> 给 AI 助手与协作开发者使用的当前项目索引。详细结构、运行链路和验证要求见 [doc/PROJECT_STRUCTURE.md](doc/PROJECT_STRUCTURE.md)。根目录其他改造文档主要记录历史过程，不能替代当前代码与本索引。

## 当前架构约定

- Android 业务代码位于 `app/src/main/java/com/dlight/`；`Main2Activity` 是唯一 LAUNCHER。
- 业务接口只定义在 `app/src/main/java/com/dlight/data/remote/ApiService.java`。
- `RetrofitClient` 创建唯一 Retrofit 实例，`NetworkConfig` 读取并校验环境 baseUrl，`HttpClientFactory` 提供 API/图片 OkHttp 客户端。不要再建平行 Retrofit stack。
- API baseUrl 必须是 origin 根地址：只能包含 scheme、host 和可选 port，不能包含业务 path、query 或 fragment。Debug 用 `-PDLIGHT_DEBUG_API_BASE_URL=...`，Release 用 HTTPS 的 `-PDLIGHT_RELEASE_API_BASE_URL=...`。
- 图片统一通过 `ImageLoader`/Glide 加载。
- Room 使用 `AppDatabase` v2、单一 canonical database、显式 1→2 migration 与 schema 导出；旧收藏库由 `LegacyRecordImporter` 通过稳定快照一次性导入。
- 下载链路使用 `DownloadUrlPolicy`、`DownloadHttpClient`、`DownloadPreflight`、`DownloadProgressMetrics` 与 `DownloadTaskStore`；不要绕过 URL/DNS 校验或任务状态持久化。

## 安全与构建

- 主变体禁止 cleartext，Debug 的专用 network security config 才允许本地 HTTP；API、图片、Exo 和下载均使用平台 TLS 信任。
- Release 签名仅从 `DLIGHT_RELEASE_STORE_FILE`、`DLIGHT_RELEASE_STORE_PASSWORD`、`DLIGHT_RELEASE_KEY_ALIAS`、`DLIGHT_RELEASE_KEY_PASSWORD` 读取；未完整提供时生成 unsigned Release 产物。
- `app` 配置 `lint { abortOnError true }`。涉及 `app/src/main/res/**`、Manifest、Java/Kotlin 或 Gradle 的改动，完成前至少验证相应单测、`:app:lintDebug` 和 `:app:assembleDebug`；Release 改动还要提供 HTTPS API 参数验证 Release。
- 仅修改 Markdown 时无需 Android 构建，但必须运行 `git diff --check` 并核对文档中的路径仍存在。

## 当前未闭环项

- 根工程聚合 lint 仍会命中 vendored `gsyVideoPlayer-java` 的 `RestrictedApi`；`app` 自身 lint gate 已开启。
- 首页、搜索、播放恢复、图片和下载等关键路径已有自动化覆盖，但尚未完成所有目标设备上的端到端 smoke 验证。

## 维护约定

- 目录结构、接口、环境配置、安全策略或构建门禁变化时，同步更新 `doc/PROJECT_STRUCTURE.md`。
- 只做需求直接要求的修改；不要顺手清理 vendored 播放器或历史文档。
