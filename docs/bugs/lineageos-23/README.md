# Android 16 / LineageOS 23.2 辅助功能停止失败

日期：2026-10-05。设备：PKG110，Android 16（API 36），LineageOS `23.2-20260721-NIGHTLY-giuliac`。原版本：GitHub Release 1.0，应用版本 1.0.0。

## 复现与原因

辅助功能和使用情况权限均已开启，服务正常连接。仅选择可丢弃的 `com.android.killapps.fixture`，点击开始后能打开系统详情并点击“强行停止”，但停在确认弹窗。10 秒后记失败，目标仍为 `stopped=false`。

新版系统设置的 Compose 确认按钮没有 `android:id/button1`，也没有其他资源 ID。“确定”文本节点不可点击，其父节点才是可点击的 `android.view.View`。旧实现只找 `android:id/button1`，无法点击新版确认按钮。

- [确认弹窗](before-dialog.png)
- [原版本失败结果](before-result.png)

## 修复

- 保留 `android:id/button1`，同时支持 Settings 包名下的 `:id/button1`。
- 无 ID 时按中英确认文字或 contentDescription 完整匹配，查找可点击父节点。
- 必须处于已验证的系统设置窗口，且此前已点击目标应用的强行停止、存在停止警告，才查找确认按钮。
- 排除隐藏或禁用节点，不采用坐标点击，也不把含有“OK”的普通说明当成按钮。
- 保持 `FLAG_STOPPED` 验证，确认点击后才进入停止状态检查。
- 应用版本更新为 1.0.1（versionCode 2）；About 版本号改为来自 BuildConfig。

## 验证

加入实际设备确认弹窗的无 ID XML 作为回归样本，并覆盖旧版 ID、Settings 包名 ID、英文 contentDescription、禁用父节点、隐藏按钮和说明文字等情况。11 项单元测试通过，诊断版构建与 Lint 通过。用户已在连接的设备上测试并确认修复可用。

设备原安装包使用 CI 生成的签名，无法被本机签名直接覆盖。为保留原包设置和白名单，使用 `-Pdiagnostic` 构建独立包 `com.android.killapps.diagnostic` 验证，辅助功能入口显示“KillApps 诊断版”。常规构建仍为 `com.android.killapps`。

```powershell
./gradlew.bat -Pdiagnostic :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

参考：[AOSP Compose 强行停止按钮及确认弹窗](https://android.googlesource.com/platform/packages/apps/Settings/+/refs/heads/main/src/com/android/settings/spa/app/appinfo/AppForceStopButton.kt)。
