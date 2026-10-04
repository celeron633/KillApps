# GitHub Actions 自动构建

工作流：`.github/workflows/build.yml`，名称 **Android CI**。

## 触发与环境

- 任意分支推送、PR 创建／更新、Actions 页手动 Run workflow。
- Ubuntu 24.04，Temurin JDK 17，SDK Platform 35，Build Tools 34.0.0。
- 使用仓库内 Gradle 8.9 Wrapper；通过 `bash ./gradlew` 执行，不依赖脚本的可执行权限。
- `.gitattributes` 固定 Linux 脚本及 YAML 的 LF 换行，避免 Windows 提交后 Linux 构建失败。
- 同一 ref 的新任务会取消正在运行的旧任务；单次构建最长 30 分钟。

## 执行与产物

```sh
bash ./gradlew --no-daemon --stacktrace :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

`KillApps-debug-<run number>`：安装包 `KillApps-debug.apk` 与 `SHA256SUMS.txt`，保留 30 天。使用 Android Debug 签名，无需 Secrets。每个干净 runner 的 Debug 签名可能不同，与本机或其他 CI 运行的 APK 不一定能直接覆盖安装；正式发布和稳定升级需要独立的固定发布签名配置。

`KillApps-reports-<run number>`：JUnit XML、单元测试 HTML 和 Lint 报告，保留 14 天。构建失败时仍上传已经生成的报告；取消任务时不上传。

Gradle 缓存由官方 setup-gradle 管理，默认只向默认分支写入缓存，同时验证 Gradle Wrapper。GitHub token 只授予 `contents: read`；Action 固定到已核对的发布 commit。

## 验证范围

本地验证工作流语法及同样的 Gradle 构建、测试、Lint 命令。工作流提交并推送到 GitHub 后才能执行云端构建；实际运行状态和下载入口见仓库 Actions 页面。CI 不执行依赖真实设备、root 或辅助功能授权的真机测试。

## 参考

- [Java setup](https://github.com/actions/setup-java)
- [Android SDK setup](https://github.com/android-actions/setup-android)
- [Gradle setup](https://github.com/gradle/actions/tree/main/setup-gradle)
- [Artifact upload](https://github.com/actions/upload-artifact)
