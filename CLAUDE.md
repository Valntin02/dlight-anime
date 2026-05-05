# CLAUDE.md

> 给 Claude / 协作开发者使用的项目上下文索引。详细的模块说明、数据流、已实现功能与注意事项请直接阅读下方链接的结构总览文档。

## 项目结构与功能总览
- 主文档：[doc/PROJECT_STRUCTURE.md](doc/PROJECT_STRUCTURE.md)
  - 顶层目录、`app` 模块各包职责、关键类清单
  - 启动 / 运行流程、已实现功能矩阵
  - 后端 API 速查（baseUrl 由 `app/.../util/Param.java` 控制）
  - 注意事项与已知坑（双套网络层、AppDatabase 单例混用、目录嵌套残留等）

## 历史改造背景（按需查阅）
- `PROJECT_SUMMARY.md`、`MIGRATION_REPORT.md`、`REFACTOR_PLAN.md`
- `CLEANUP_COMPLETE.md`、`COMPILATION_FIX_REPORT.md`、`UNUSED_PACKAGES.md`
- `TEST_PLAN.md`、`IMPLEMENTATION_PROGRESS.md`

## 上游播放器文档
- `doc/USE.md`、`doc/DECODERS.md`、`doc/DEPENDENCIES.md`、`doc/BUILD_SO.md` 等：来自 GSYVideoPlayer 上游，用于了解播放器内核层。

## 维护约定
- 当目录结构、关键模块、后端接口、构建配置发生变化时，请同步更新 `doc/PROJECT_STRUCTURE.md`。
- 新增业务接口优先放在 `app/src/main/java/com/dlight/network/api/`，并通过 `NetworkManager` 暴露 getter。
- 构建 Android 使用 Android Studio 自带 JDK（可用 `build-android` skill）。

## 完工自检（强约束）
- **只要本轮改动涉及 `app/src/main/res/**`、`AndroidManifest.xml`、`*.java`、`*.kt` 或任一 `*.gradle` 文件**，在向用户回报"完成"之前，必须主动调用 `build-android` skill 跑一次 `./gradlew assembleDebug`，确认 `BUILD SUCCESSFUL` 才算结束。
- 构建失败时直接读取错误日志原地修复（典型坑：XML 注释里出现 `--`、color/drawable 引用未定义、style parent 在当前依赖里不存在），修完再跑一次，循环到通过为止。
- 仅改 markdown / 注释 / `doc/**` 时不需要构建。
- 主题、状态栏色、Activity/Fragment 切换等"视觉正确性"无法靠编译验证，构建通过后仍需提示用户在真机/模拟器跑一遍对照截图确认。
