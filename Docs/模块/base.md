# `:base`

KMP 基础设施层。平台无关部分（`Logger`、`Clock`、`TimeZone`、`DigestUtils`、`TextUtils`、`ColorUtils`、`DataUrlUtils`、`MeasureTime`、`TimestampFormatter`、`BatteryIconConverter`、`FilterConfigDefaults`、`SuperIslandStorageKeys`、`PlatformInfo`）位于 `base/src/commonMain/`；Android 平台工具（依赖 `Context`/`PackageManager`/`Bitmap`/`Window`/`Keystore`/系统版本串判定等）位于 `base/src/androidMain/`；JVM 侧 `actual` 位于 `base/src/jvmMain/`。全仓日志统一使用 `notifyrelay.base.log.Logger`。

| 文件 | 类/对象 | 主要公开方法 |
|---|---|---|
| `AppListHelper.kt` | `enum class InstalledAppsFilter` + `object AppListHelper` | `InstalledAppsFilter.accepts(context, appInfo)`；`queryInstalledApplications(context, flags, filter)`, `getInstalledApplications(context)`, `canQueryApps(context)`, `getApplicationLabel(context, packageName)`, `getApplicationLabel(context, appInfo)` |
| `BatteryIconConverter.kt` | `object BatteryIconConverter` | `getBatteryIcon(...)`, `getBatteryColor(batteryLevel)` |
| `BatteryUtils.kt` | `object BatteryUtils` | `getBatteryLevel(context)`, `isCharging(context)` |
| `ClipboardUtils.kt` | `object ClipboardUtils` | `copyText(context, label, text)`（写系统剪贴板，失败返回 false） |
| `DeviceUtils.kt` | `object DeviceUtils` | `isTablet(context)`, `getLocalDeviceName(context)`（`sanitizeDisplayName` 为私有） |
| `GuidePermissionRequester.kt` | `class GuidePermissionRequester` | `requestPostNotifications()`, `requestQueryApps()`, `requestLocalNetwork()`, `requestBluetoothConnect()` |
| `IntentUtils.kt` | `object IntentUtils` | `startActivity(...)`(2 重载), `createIntent<T>()`, `createImplicitIntent(action)` |
| `PermissionHelper.kt` | `object PermissionHelper` | `checkAllPermissions()`, `detectMiuiOrPengpai(context)`, `requestSensitiveNotificationPermission()`, `checkBluetoothConnectPermission()`, `checkOverlayPermission()`, `requestOverlayPermission()`, `checkBackgroundUnlimitedPermission()`, `checkNotificationListenerServiceCanStart()`, `getDeclaredPermissions()`, `isLocalNetworkPermissionRequired()`, `checkLocalNetworkPermission()`, `getApplicableDeclaredPermissions()`, `openAppDetailsSettings()`, `checkManageExternalStoragePermission()`, `requestManageExternalStoragePermission()`, `getDetailedOsVersion()`, `isVersionGreaterThan(...)`, `isDeviceLocked()`, `isAppInForeground()`；内部 `object AppForegroundDetector` 提供 `initialize()`, `isForeground()`, `addListener()`, `removeListener()` |
| `SecureKeyStorage.kt` | `object SecureKeyStorage` | `encryptAndStore(...)`, `retrieveAndDecrypt(...)`, `removeKey(...)` |
| `ServiceManager.kt` | `object ServiceManager` | `startNotificationListenerService(context)`, `startAllServices(context)`（服务类名以常量 `NOTIFICATION_LISTENER_SERVICE_CLASS` 持有） |
| `SystemBarUtils.kt` | `object SystemBarUtils` | `setStatusBarColor(window, color, animate)`, `setNavigationBarColor(window, color, animate)`（反射降级，纯 Android 平台工具） |
| `ThemeSettingsManager.kt` | `object ThemeSettingsManager` | `fun interface ThemeChangeListener`, `getThemeBaseIndex(context)`, `setThemeBaseIndex(...)`, `addThemeChangeListener(...)`, `removeThemeChangeListener(...)` |
| `SuperIslandImageUtils.kt` | `object SuperIslandImageUtils` | `resolveFallbackIconUrl(picMap)`, `parseSimpleHtmlToAnnotatedString(html)`（纯函数；`:superislandui` 与本模块共用） |
| `VersionUtils.kt` | `object VersionUtils` | `isVersionGreaterThan(version, target)`（纯字符串比较；`PermissionHelper` 同名方法委托到此） |
| `ToastUtils.kt` | `object ToastUtils` | `showShortToast(...)`, `showLongToast(...)` |
| `image/ImageUtils.kt` | `object ImageUtils` + 顶层扩展 | `decodeDataUrlToBitmap(...)`, `bitmapToBase64(bitmap)`, `bitmapToDataUri(bitmap)`, `bytesToDataUrl(bytes, mime)`, `loadBitmap(...)`, `scaleDown(bitmap, maxDimension)`；顶层扩展 `Drawable.toBitmapOrDefault(fallbackSize)`, `Bitmap.toPngByteArray()`。`isDataUrl`/`cleanDataUrl`/`parseColor`/`unescapeHtml` 的实现位于 `:shared`（`DataUrlUtils`/`ColorUtils`/`TextUtils`），此处保留同名入口做单行委托 |
