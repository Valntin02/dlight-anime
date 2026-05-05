---
allowed-tools: Bash(./gradlew:*)
description: 构建Android项目，使用Android Studio自带的JDK
---

## 构建步骤

1. **清理构建缓存**
   ```bash
   ./gradlew clean
   ```

2. **执行构建**
   ```bash
   JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
   ```

3. **检查构建结果**
   - 查找生成的APK文件：`app/build/outputs/apk/debug/app-debug.apk`
   - 显示APK文件信息（大小、修改时间）

4. **报告构建状态**
   - 如果成功：显示APK路径和大小
   - 如果失败：显示错误信息

## 常见问题处理

如果遇到以下错误，请自动修复：

- **Java环境未配置**：设置 `JAVA_HOME` 指向Android Studio的JDK
- **权限错误**：执行 `chmod +x gradlew`
- **资源冲突**：删除重复的资源文件
- **缺失资源**：在 `values/dimens.xml` 和 `values/strings.xml` 中添加缺失的资源
- **R类找不到**：检查包名配置是否匹配

请执行上述步骤，自动构建Android项目并报告结果。
