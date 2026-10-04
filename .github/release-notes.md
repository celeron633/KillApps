## KillApps

支持 Android 8.0+，包名 `com.android.killapps`。提供 Root 优先／辅助功能批量停止、用户与系统应用筛选、黑白名单、中英双语及 MD3 明暗主题。

### 下载 / Downloads

| APK | 适用设备 / Devices |
| --- | --- |
| `*-arm32.apk` | 32 位 ARM / armeabi-v7a |
| `*-arm64.apk` | 64 位 ARM / arm64-v8a |
| `*-universal.apk` | 通用版，不确定架构时选这个 / Choose this if unsure |

下载 APK 后安装即可，`SHA256SUMS.txt` 用于核对文件完整性。界面使用 Jetpack Compose 和紫白 Material 3 主题；框架的原生依赖按 APK 架构打包。

Release 构建未开启 debuggable，本次使用 Android 测试证书签名。CI 签名与本机开发版可能不同，覆盖安装提示签名不一致时需要先卸载旧版；卸载会清除本机设置和名单。

Release builds are non-debuggable and signed with an Android test certificate. If an existing installation has a different certificate, uninstall it before installing; this clears local settings and lists. The UI uses Jetpack Compose and a purple and white Material 3 theme, with framework native dependencies packaged for each ABI.

首次启动请授权 Root，或开启使用情况访问权限和辅助功能。无 Root 时使用近期活动候选，不能完整枚举运行进程。强行停止可能中断通知，直到再次打开目标应用。

On first launch, authorize root or enable usage access and accessibility. Without root, the app uses recent activity as candidates rather than a complete running-process list. Force-stopped apps may not receive notifications until reopened.
