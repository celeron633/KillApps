# 开发记录

## 环境

2026-10-04：Windows PowerShell；SDK `C:/Users/dengxh/AppData/Local/Android/Sdk`，compile/target SDK 35，min SDK 26。连接设备 Redmi Note 7 / LineageOS / Android 11（API 30），adb root shell 可通过 Magisk `su` 获得 UID 0。

项目从空目录建立。使用 Java 17、Android Gradle Plugin 8.7.3、Gradle 8.9、AppCompat 1.7.0、Material Components 1.12.0、RecyclerView 1.3.2。

## 遇到的问题

1. Android Studio 附带 JDK 25。Gradle 8.9 运行产生 `Unsupported class file major version 69`。为构建单独下载 JDK 17 到 `.tools/jdk17`，不修改全局 Java 设置。
2. 标准 Android 的 `pm help` 没有 kill 子命令。按 Force stop 的需求采用 ActivityManager 的 `am force-stop`，而非不等价的后台 `am kill`。
3. 无 root 时 UsageStats 只能提供近期使用信息，不能声称精确识别后台存活进程；界面区分数据来源。
4. 用户要求双语：去除业务/UI 中文硬编码，英文默认资源与 `values-zh` 资源一一对应；辅助功能的系统匹配词也放资源数组，独立于 APP 语言以支持中文 APP + 英文系统。
5. 避免 Java 9 的 InputStream.transferTo：Android 8 上不支持该 API，使用缓冲区读取输出。
6. PACKAGE_USAGE_STATS 通过系统设置中的 AppOps 授权；此处对该权限单独抑制 ProtectedPermissions 提示。QUERY_ALL_PACKAGES 是枚举无桌面入口的后台／系统应用的必要条件，使用说明注释与局部 Lint 抑制，不关闭全局检查。
7. APP 语言切换需要两份资源在发布包中同时存在，禁用 App Bundle 的语言拆分；Android 13+ 同时声明 localeConfig 支持系统的按应用语言设置。

## 参考

- 参考产品：https://play.google.com/store/apps/details?id=com.tafayor.killall
- 使用情况 API：https://developer.android.com/reference/android/app/usage/UsageStatsManager
- 辅助功能服务：https://developer.android.com/guide/topics/ui/accessibility/service
- ActivityManager 命令：https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/am/ActivityManagerShellCommand.java
- APP 语言：https://developer.android.com/guide/topics/resources/app-languages

## 构建

图标更新：采用深绿渐变底、三层应用卡片和圆角停止符号的原创矢量设计。使用 Android 8+ Adaptive Icon 适配桌面形状，并为 Android 13+ 提供 monochrome 主题图标资源，无位图缩放失真。

2026-10-04 界面调整：移除首页 slogan 及说明文字，同时删除对应的中英字符串资源。首页从应用状态卡片开始。完善 `.gitignore`，排除构建产物、本机 SDK 配置、下载工具、安装包、IDE 配置及签名凭据；保留 Gradle Wrapper、源码和 docs。

在 Android Studio 中选择 JDK 17，或 PowerShell 设置本次会话的 `JAVA_HOME` 后执行 `./gradlew.bat assembleDebug testDebugUnitTest lintDebug`。SDK 路径保存在不提交的 `local.properties`。

设备验证记录见 `testing.md`（验证完成后填写）。
