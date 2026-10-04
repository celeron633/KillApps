# 验证记录

日期：2026-10-04。设备：Redmi Note 7，LineageOS，Android 11 / API 30，Magisk 30.7。

## 构建与自动检查

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

结果：BUILD SUCCESSFUL，Android Lint **0 errors / 10 warnings**。保留的提示包括固定版本依赖有新版本、兼容属性与 RecyclerView 列表刷新建议等，未关闭整个 Lint。

5 个单元测试通过：白名单排除、黑名单仅包含、保护优先级、用户／系统筛选独立、中英字符串键与格式参数一一对应。

## 真机验证

| 项目 | 操作及结果 |
| --- | --- |
| 安装启动 | Debug APK 安装成功，名称 KillApps，包名 com.android.killapps |
| 首次启动 | 显示授权说明；锁屏期间首次 su 被 Magisk 拒绝，解锁并通过 Magisk 授权后重新检测成功 |
| root 检测 | 自动模式显示运行中来源，列表包含测试应用及真实子进程关联应用 |
| root 停止 | 仅选择 `com.android.killapps.fixture`，结果成功 1 / 失败 0；`pidof` 无进程、`dumpsys package` 为 `stopped=true` |
| 使用情况授权 | 从 APP 跳转目标应用的系统使用情况访问页，开启后返回显示“已开启” |
| 辅助功能授权 | 显示中文用途说明，进入英文系统服务页面并确认授权，返回显示“已开启” |
| 白名单 | 添加测试包，首页显示“按名单保留”，不可选择；搜索包名可编辑名单 |
| 黑名单 | 同一测试包同时保存在两份名单，切换黑名单后只选中该包；模式切换不清空名单 |
| 辅助功能停止 | 在“仅辅助功能”模式，中文 APP + 英文系统页面点击 Force stop 和确认；成功 1 / 失败 0，验证 `stopped=true`，自动返回 KillApps |
| 取消 | 详情操作开始前点击浮条取消，结果停止 0 / 失败 0 / 跳过 1；目标仍为 `stopped=false` |
| 中英切换 | 默认英文、切换中文即时重建界面；首页、设置、授权、确认、结果均显示中文；任务结果使用资源 ID 在当前语言重新格式化 |
| 明暗主题 | 使用系统夜间模式分别验证中文首页，卡片、文本、按钮与状态栏切换；测试后恢复浅色 |
| 持久化 | 重启进程后语言、停止方式、两份名单及模式保留；本次临时选择与任务记录不保留 |
| 权限失效 | 对 KillApps 自身执行测试用 force-stop 后系统禁用服务，再次启动自动展示授权面板；已授权但未连接与未授权分开显示 |
| 默认保护 | KillApps 自身、Magisk、已启用的参考 APP 辅助功能服务均不能勾选；系统筛选关闭 |

所有停止操作使用项目的可丢弃测试模块，不关闭设备上已有的业务应用。测试结束移除测试 APK 与名单条目，恢复自动优先 root、白名单模式、仅用户应用。

## 截图

- [中文设置](settings-zh.png)
- [中文首页](home-zh.png)
- [深色中文首页](home-dark-zh.png)
- [英文 root 结果](root-result-en.png)
- [中文辅助功能浮条](accessibility-running-zh.png)
- [中文辅助功能结果](accessibility-result-zh.png)
- [中文取消结果](cancel-zh.png)

## 验证中发现并修正

1. WindowInsetsController 在 DecorView 初始化前访问会导致 Android 11 启动崩溃，已调整为 setContentView 后获取。
2. 就绪状态每 400ms 重写 TextView 会产生连续辅助功能事件，导致 UIAutomator 无法进入空闲；只在任务运行或完成状态变化时轮询更新。
3. `uiautomator dump` 默认会抑制其他辅助功能服务，不可在停止任务期间使用；任务验证改用 `screencap`、`dumpsys window` 与 `dumpsys package`。最后一个结果完成后立即结束会话，避免完成间隙被误记为取消。
4. 测试用 force-stop 自身会让 Android 将辅助功能服务标为失效。需要在系统设置关闭后重新开启；APP 默认保护自身及所有已启用服务，实际批处理不触发此行为。

## 尚未覆盖

仅 API 30 进行了真机运行验证；minSdk 26 的 API 兼容性已通过 Lint，但未在 Android 8 或最新 Android 真机运行。尚未覆盖其他厂商 ROM、多用户设备实际操作、其他语言系统页面及大量系统应用批处理。侧载应用在部分 Android 版本可能需要先在系统应用详情里允许受限设置，再启用辅助功能。
