# KillApps

Android 8.0+ 应用管理工具，包名 `com.android.killapps`。采用 Material Design 3，支持明暗主题、中英双语、用户／系统应用筛选、白名单和黑名单。

- 自动模式优先使用 root，以 `am force-stop` 停止应用。
- 无 root 时使用使用情况访问权限生成近期活动候选，并由辅助功能依次操作系统“强行停止”按钮。
- 名单独立持久保存；支持本次勾选、搜索、开始前复查、取消队列和逐项结果。
- 默认关闭系统应用筛选，自动保护核心应用。不含广告、统计或网络权限。

## 构建与安装

使用 Android Studio 打开目录，设置 Gradle JDK 为 **17**。本项目使用 SDK 35、AGP 8.7.3、Gradle 8.9；`local.properties` 指向本机 SDK。

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

设备测试可另外构建 `:test-fixture:assembleDebug`；该模块是可丢弃的停止目标，不会打包进 KillApps。

首次启动请根据提示授予 root 或使用情况访问权限和辅助功能。在设置中可切换语言、停止方式、名单模式并编辑名单。白名单中的应用不关闭；黑名单模式只关闭名单内应用。

## 文档

- [产品流程与筛选规则](docs/product-flow.md)
- [开发记录与平台限制](docs/development.md)
- [真机验证记录](docs/testing.md)

无 root 的近期活动候选不等于完整运行进程列表。辅助功能兼容中英系统文案；不同 ROM 设置结构需要逐机验证。当前任务结果保存在进程内存，名单和设置保存在本机。
