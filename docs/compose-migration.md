# Jetpack Compose 与紫白 Material 3 界面

2026-10-05：原主界面使用 Java 动态创建 View，并无 XML 布局文件。本次将首页、设置、黑白名单编辑、授权面板、确认弹窗、任务结果与辅助功能任务浮条迁移到 Jetpack Compose。

## 设计

- 固定紫白 MD3 色板：主色 `#6750A4`，浅紫容器 `#EADDFF`，背景 `#FFFBFE`；深色模式使用对应的紫色与深灰色板。
- 首页采用状态卡片、应用类型 FilterChip、搜索输入框和底部操作区，优先展示可停止的应用。
- 设置使用分组容器；授权使用 ModalBottomSheet；选择与确认使用 Material 3 AlertDialog。
- 底部导航在“应用”和“设置”之间切换，列表整体可滚动，避免小屏与键盘遮挡固定列表。
- 图标沿用应用卡片与停止符号的轮廓，改为同一套紫色配色；未加入 slogan。
- 界面文字继续来自中英资源，语言跟随系统或手动指定。

[诊断版首页预览](compose-home-zh.png)

## 结构

- `MainActivity.kt`：AppCompat 宿主，负责语言、系统设置跳转、生命周期与 Compose 内容。
- `KillAppsViewModel.kt`：通过 StateFlow 发布 UI 状态；异步读取应用，保存原有 preferences 键，保留启动前复查、任务取消与授权检查。
- `KillAppsUi.kt`：各页面及弹窗。
- `KillAppsTheme.kt`：明暗主题、色板与圆角。
- `StopTaskOverlay.kt`：为辅助功能服务创建独立的 ComposeView、LifecycleOwner 和 SavedStateRegistryOwner；移除窗口时销毁 composition。
- Root、应用保护与 Android 16 确认按钮的既有 Java 业务逻辑保留。

Activity 重建时 ViewModel 保存任务与选择状态，Compose 保存页面和搜索词；进程重新启动仍读取原有名单与设置。不会把搜索结果范围当成任务范围：开始按钮的总选择数始终包含搜索之外的已选应用。

## 构建

采用 Kotlin 2.0.21 及同版本 Compose Compiler 插件，Compose BOM 2025.01.01、Activity Compose 1.10.1、Lifecycle 2.8.7。为兼容现有 SDK 35 / AGP 8.7.3 / Gradle 8.9，未升级到要求更新 SDK 与 AGP 的 Compose 版本。最低 Android 版本仍为 8.0。

```powershell
./gradlew.bat :app:assembleDebug :app:assembleRelease
```

用户完成测试后，已移除临时诊断构建开关及诊断名称资源。Debug 和 Release 都使用正式包名 `com.android.killapps`、名称 KillApps；Release 按 arm32、arm64、universal 生成三包。底部“应用”标签使用四格圆角实心矢量图标，与圆角实心设置齿轮保持一致。

图标微调：底部四格图标从 24dp 缩小为 22dp，平衡与齿轮的视觉重量。“任务结果”在菜单和设置中统一使用带勾的报告图标，“名单模式”使用筛选漏斗图标；颜色仍由 Material 3 主题提供。

Compose 的依赖引入 `libandroidx.graphics.path.so`，因此新的 ABI 包会分别包含对应原生库，已不再是初始 Release 的纯 Java 包。

## 检查与测试范围

Compose 迁移期间诊断版编译、11 项已有单元测试和 Debug Lint 通过，Release 三种 ABI APK 编译成功。界面在 Android 16 / LineageOS 23.2 上运行后，用户自行完成使用测试并反馈未发现明显问题，因此恢复正式包名构建。

遇到旧版 Compose Lint 对 produceState 内赋值的误报，改为 remember + LaunchedEffect 异步加载图标，没有关闭 Lint。搜索框关闭自动纠错，以便输入包名。

参考：[Material Design 3](https://m3.material.io/)、[Compose Compiler 配置](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)。
