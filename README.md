<p align="center">
  <img src="app/src/main/res/drawable-nodpi/logo_dlight.png" alt="次元之光 Logo" width="220" />
</p>

<h1 align="center">次元之光 · Dlight</h1>

<p align="center">
  一款基于 <a href="https://github.com/CarGuo/GSYVideoPlayer">GSYVideoPlayer</a> 二次开发的 Android 动漫播放客户端
</p>

---

## 功能特性

- **首页**：公告滚动条、今日更新、按周几切换的周更（带内存缓存）
- **动漫库**：分页加载、上拉触底、年份索引筛选
- **搜索**：关键词联想、本地搜索历史、结果列表
- **播放器**：IJK 内核 + 弹幕，多清晰度切换、共享元素过渡、Picture-in-Picture
- **弹幕**：拉取/发送、自定义解析器、颜色与类型选择
- **评论**：一级评论、二级回复、点赞、举报、批量获取头像
- **用户中心**：登录/注册、头像上传、登出，独立设置页
- **历史 / 收藏**：Room 本地双表，退出应用时同步未上传条目，进入页面按需回拉
- **离线下载**：m3u8 分片多线程下载、前台 Service、通知栏进度、本地回放

## 技术栈

| 类别 | 选型 |
|---|---|
| 语言 / 构建 | Java，AGP 8.6.1 |
| 网络 | Retrofit 2.9.0、OkHttp 4.12.0 |
| 图片 | Glide 4.14.0、Picasso 2.71828 |
| 数据库 | Room 2.6.1 |
| 播放器 | GSYVideoPlayer（IJK 主用，备用 Exo2 / Aliyun） |
| 弹幕 | DanmakuFlameMaster |
| UI | Material Components、ViewPager2、ViewBinding |

## 快速开始

### 环境要求

- Android Studio（推荐 Hedgehog 或更新版本），使用其内置 JDK 构建
- Android SDK Platform 35（compileSdk）；最低支持 Android 8.0（minSdk 26）
- 联调时本地需运行 FastAPI 后端，监听 `:8000`

### 克隆 & 构建

```bash
git clone <repo-url> dlight-anime
cd dlight-anime
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

### 后端 baseUrl 配置

后端地址由 `app/src/main/java/com/dlight/util/Param.java` 控制，按运行环境选择：

| 场景 | baseUrl |
|---|---|
| 模拟器 | `http://10.0.2.2:8000/`（默认；无需 `adb reverse`）|
| 真机同网段 | 改 `Param` 中的 `DEVICE_BASE_URL` 为路由器内网 IP |
| 生产环境 | 运行时调用 `Param.getInstance().setBaseUrl(...)` 切换 |

> 模拟器内的 `127.0.0.1` 指模拟器自身，访问宿主机服务必须走 `10.0.2.2`。

## 项目结构

详细模块划分、关键类清单、数据流、已知坑请参考：

- [doc/PROJECT_STRUCTURE.md](doc/PROJECT_STRUCTURE.md) — 项目结构与功能总览
- [CLAUDE.md](CLAUDE.md) — 协作开发上下文索引（含构建约束与维护约定）

## 致谢

- [GSYVideoPlayer](https://github.com/CarGuo/GSYVideoPlayer) — 播放器内核框架
- [DanmakuFlameMaster](https://github.com/bilibili/DanmakuFlameMaster) — 弹幕引擎
- 以及其他被使用的优秀开源项目

## License

[Apache License 2.0](LICENSE)

## 免责声明

本项目基于 Apache License 2.0 开源发布，使用本软件即表示您已知悉并同意以下条款：

- **按现状提供**：本软件以"原样"（AS IS）提供，不附带任何明示或暗示的担保，包括但不限于适销性、特定用途适用性、无侵权等。作者及贡献者不对因使用本软件所导致的任何直接、间接、偶然、特殊、惩罚性或后果性损害负责。
- **用户风险自负**：您使用本软件的所有行为及后果由您自行承担，包括但不限于数据丢失、系统崩溃、法律风险或第三方纠纷。请在测试充分、风险可控的前提下使用。
- **不保证持续支持**：作者及贡献者无义务提供更新、修复、维护或技术支持。社区贡献和使用者需自行承担延伸使用、二次开发与部署所引发的问题。
- **禁止非法使用**：严禁将本项目用于任何违法、侵权、恶意或破坏性用途。若用户将本软件用于违反当地法律法规的用途，由此产生的一切后果将由用户自行承担。
- **第三方组件使用**：本项目可能依赖于第三方开源库或服务，其许可与维护责任归各自原始作者所有。本项目不对第三方组件的变动、失效或兼容性问题负责。
