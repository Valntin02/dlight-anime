# 可以删除的未使用包清理计划

## 📊 分析结果

从AndroidManifest.xml中，发现项目注册了**50+个Activity**，大部分是GSYVideoPlayer的示例和演示代码。

---

## ✅ 建议保留的包

### 核心包（必须保留）
```
com.dlight.network/          # 新的网络层（我们创建的）
com.dlight.ui/                 # 新的UI层（我们创建的）
com.example.gsyvideoplayer.video/   # 播放器核心（可能需要保留部分）
com.example.gsyvideoplayer.GSYApplication  # Application类
```

### 可能需要保留的播放器组件
```
com.example.gsyvideoplayer.video/
  ├── DanmakuVideoPlayer.java        # 弹幕播放器（参考用）
  ├── SampleVideo.java               # 基础播放器示例
  └── CustomRenderVideoPlayer.java   # 自定义渲染播放器（参考用）
```

---

## ❌ 建议删除的包

### 纯别 1：示例Activity包（肯定可以删除）
```
com.example.gsyvideoplayer.simple/      # 包含20+个示例Activity
com.example.gsyvideoplayer.switchplay/ # 切换播放示例
com.example.gsyvideoplayerexo/         # Exo播放器示例
```

### 级别 2：工具和演示包（可以删除）
```
com.example.gsyvideoplayer.utils/
  ├── floatUtil/                   # 浮动工具示例
  └── JumpUtils.java              # 跳转工具
com.example.gsyvideoplayer.effet/       # 效果演示
com.example.gsyvideoplayer.adapter/      # 示例适配器
com.example.gsyvideoplayer.holder/       # 示例ViewHolder
com.example.gsyvideoplayer.model/        # 示例数据模型
```

### 级别 3：其他演示包（可以删除）
```
com.example.gsyvideoplayer.common/       # 公共组件（示例用）
com.example.gsyvideoplayer.fragment/     # 示例Fragment
com.example.gsyvideoplayer.listener/     # 示例监听器
com.example.gsyvideoplayer.mediacodec/ # 媒体编解码示例
com.example.gsyvideoplayer.view/        # 自定义View示例
```

---

## 🗑️ 清理计划

### 阶段 1：删除simple包（最大，最安全）
```
删除：com/example/gsyvideoplayer/simple/
影响：约20个Activity + Service
风险：低（完全示例代码）
```

### 阶段 2：删除switchplay和exo包
```
删除：
  - com/example/gsyvideoplayer/switchplay/
  - com/example/gsyvideoplayer/exo/
影响：约4个Activity
风险：低（示例代码）
```

### 阶段 3：删除工具和演示包
```
删除：
  - com/example/gsyvideoplayer/utils/
  - com/example/gsyvideoplayer/effet/
  - com/example/gsyvideoplayer/adapter/
  - com/example/gsyvideoplayer/holder/
  - com/example/gsyvideoplayer/model/
  - com/example/gsyvideoplayer/common/
  - com/example/gsyvideoplayer/fragment/
  - com/example/gsyvideoplayer/listener/
  - com/example/gsyvideoplayer/mediacodec/
  - com/example/gsyvideoplayer/view/
影响：大量辅助类
风险：中（需要检查是否有其他Activity依赖）
```

### 阶段 4：清理主Activity层的示例代码
```
保留：
  - MainActivity.java（重写为新的主入口）
  - PlayActivity.java（参考或重写）
  - DanmakuVideoActivity.java（参考弹幕功能）

删除：
  - ViewPager2Activity
  - ViewPagerDemoActivity
  - RecyclerView2Activity
  - RecyclerView3Activity
  - ListVideoActivity
  - ListVideo2Activity
  - ListMultiVideoActivity
  - ListADVideoActivity
  - ListADVideoActivity2
  - DetailPlayer
  - DetailListPlayer
  - DetailMoreTypeActivity
  - DetailControlActivity
  - DetailFilterActivity
  - DetailADPlayer
  - DetailADPlayer2
  - DetailNormalActivityPlayer2
  - DetailDownloadPlayer
  - DetailDownloadExoPlayer
  - AudioDetailPlayer
  - PlayTVActivity
  - PlayEmptyControlActivity
  - PlayPickActivity
  - AutoPlayRecyclerViewActivity
  - ScrollingActivity
  - WindowActivity
  - EmptyActivity
  - InputUrlDetailActivity
  - FragmentVideoActivity
  - WebDetailActivity
  - RecyclerViewActivity
  - exo.DetailExoListPlayer
  - exosubtitle.GSYExoSubTitleDetailPlayer
  - utils.floatUtil.FloatActivity
```

---

## 📝 清理后的项目结构

```
app/src/main/java/
├── com/example/gsyvideoplayer/
│   ├── GSYApplication.java          # 保留
│   ├── MainActivity.java              # 重写
│   ├── PlayActivity.java              # 保留/重写
│   └── video/                      # 部分保留
│       ├── DanmakuVideoPlayer.java  # 保留（参考）
│       └── [其他播放器参考]
├── com/dlight/
│   ├── network/                    # 新网络层
│   │   ├── ApiConfig.java
│   │   ├── NetworkManager.java
│   │   ├── api/
│   │   └── model/
│   └── ui/                        # 新UI层
│       ├── activity/
│       │   └── MainActivity.java
│       ├── fragment/
│       │   ├── BaseFragment.java
│       │   ├── HomeFragment.java
│       │   ├── ExploreFragment.java
│       │   ├── SearchFragment.java
│       │   └── ProfileFragment.java
│       └── adapter/
│           └── VodAdapter.java
```

---

## 🚀 执行清理

### 步骤 1：备份项目
```bash
cp -r /path/to/dlight-anime /path/to/dlight-anime-backup
```

### 步骤 2：删除simple包（约30个文件）
```bash
rm -rf app/src/main/java/com/example/gsyvideoplayer/simple/
```

### 步骤 3：删除switchplay包
```bash
rm -rf app/src/main/java/com/example/gsyvideoplayer/switchplay/
```

### 步骤 4：删除exo包
```bash
rm -rf app/src/main/java/com/example/gsyvideoplayer/exo/
```

### 步骤 5：删除工具和演示包
```bash
rm -rf app/src/main/java/com/example/gsyvideoplayer/utils/
rm -rf app/src/main/java/com/example/gsyvideoplayer/effet/
rm -rf app/src/main/java/com/example/gsyvideoplayer/adapter/
rm -rf app/src/main/java/com/example/gsyvideoplayer/holder/
rm -rf app/src/main/java/com/example/gsyvideoplayer/model/
rm -rf app/src/main/java/com/example/gsyvideoplayer/common/
rm -rf app/src/main/java/com/example/gsyvideoplayer/fragment/
rm -rf app/src/main/java/com/example/gsyvideoplayer/listener/
rm -rf app/src/main/java/com/example/gsyvideoplayer/mediacodec/
rm -rf app/src/main/java/com/example/gsyvideoplayer/view/
```

### 步骤 6：清理AndroidManifest.xml
删除所有示例Activity的声明，保留：
- GSYApplication
- MainActivity（新的）
- 可能保留的PlayActivity

### 步骤 7：测试编译
```bash
./gradlew clean build
```

---

## ⚠️ 注意事项

1. **先备份** - 删除前务必备份整个项目
2. **分步删除** - 建议分多次提交，每步后测试编译
3. **检查依赖** - 删除后检查是否有编译错误
4. **保留参考** - 如果有需要的播放器功能，从备份中复制
5. **测试功能** - 确保删除后GSYVideoPlayer库仍能正常工作

---

## 📊 预计效果

删除后：
- **减少代码量**: 约150-200个Java文件
- **减少Activity数量**: 从50+个减少到2-3个
- **简化项目结构**: 大幅简化代码层级
- **减少APK大小**: 去除示例代码后APK体积减小

---

**准备好开始清理了吗？我将按照上述计划逐步执行。**
