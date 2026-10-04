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
bash ./gradlew --no-daemon --stacktrace :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease
```

`KillApps-debug-<run number>` 和 `KillApps-release-<run number>`：各包含 arm32、arm64、universal 三个独立可安装 APK 与 `SHA256SUMS.txt`，保留 30 天。APK 文件名包含应用版本号。通过 Gradle ABI splits 真正生成三份产物，不复制同一个 APK 冒充分包。当前无原生库，三包大小接近。

Release 变体未开启 debuggable，暂用 Android Debug 证书签名，无需 Secrets。每个干净 runner 的测试证书可能不同，与本机或其他 CI 运行的 APK 不一定能直接覆盖安装；正式发布和稳定升级需要独立的固定发布签名配置。

`KillApps-reports-<run number>`：JUnit XML、单元测试 HTML 和 Lint 报告，保留 14 天。构建失败时仍上传已经生成的报告；取消任务时不上传。

Gradle 缓存由官方 setup-gradle 管理，默认只向默认分支写入缓存，同时验证 Gradle Wrapper。构建 job 只授予 `contents: read`；Action 固定到已核对的发布 commit。

## 标签发布

推送 `v*` 标签（首次为 `v1.0`）后，release job 等待构建成功，下载同一运行的三份 Release APK，校验数量、架构文件名及 SHA-256，创建草稿并上传全部附件后再发布。该 job 单独授予 `contents: write`，通过 GitHub 内置 token 创建 Release，无需本机 GitHub API token。版本标签来自明确的 git push。

已发布的 Release 不会被自动覆盖；失败留下的草稿可以通过重新运行同一工作流继续上传并发布。发布说明放在 `.github/release-notes.md`。Release 附件不受 Actions artifact 的 30 天保留期限限制。

## 验证范围

本地验证工作流语法及同样的 Gradle 构建、测试、Lint 命令。工作流提交并推送到 GitHub 后才能执行云端构建；实际运行状态和下载入口见仓库 Actions 页面。CI 不执行依赖真实设备、root 或辅助功能授权的真机测试。

## 参考

- [Java setup](https://github.com/actions/setup-java)
- [Android SDK setup](https://github.com/android-actions/setup-android)
- [Gradle setup](https://github.com/gradle/actions/tree/main/setup-gradle)
- [Artifact upload](https://github.com/actions/upload-artifact)
