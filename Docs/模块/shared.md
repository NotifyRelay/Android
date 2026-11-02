# `:shared`

KMP 公共界面层，全部代码位于 `commonMain`（android 与 jvm 双目标，无平台源集）。

包根为 `notifyrelay.shared.*`，分为四类：

| 源包 | 内容 |
|---|---|
| `ui/navigation` | `Navigator`、`Route`（含 `SettingsDeveloperMode`）、`rememberNavigator` |
| `ui/screen` | `MainScreen`、`DeviceListScreen`、`HistoryScreen`、`DeviceForwardScreen`、`SettingsScreen` 及其 Port（`MainScreenPort`、`SettingsPort`、`DeviceListPort`） |
| `ui/pages`、`ui/dialog`、`ui/guide`、`ui/common` | 各功能页、对话框与公共组件（`ScrollableTopAppBarPage`、`DoubleClickConfirmButton`、`NotifyRelayTheme`、`deviceIconFontFamily`、`DebugFlags` 等）；`pages/history/SuperIslandHistoryScreen`、`pages/superisland/SuperIslandHistoryCards`+`SuperIslandHistoryFormatters`、`pages/chat/ChatTest`、`pages/SuperIslandSettings`、`ui/screen/DeveloperModeScreen` 均为公共页面，平台专属动作经槽位注入 |
| `ui/viewmodel` | `RemoteFilterViewModel`、`NotificationHistoryViewModel`、`SuperIslandHistoryViewModel`、`DevicePortsViewModel`、`AppIconViewModel`、`LocalAppsViewModel`、`RemoteAppsViewModel`、`AppPickerViewModel`、`AudioRelayViewModel`、`MediaControlViewModel`、`SuperIslandSettingsViewModel` |
| `ui/port` | 界面能力端口：`ToastPort`、`ClipboardPort`、`IntentLaunchPort`、`ThemePort`、`SystemBarPort`、`BitmapPort`、`PreferencePort`、`ImageDecodePort` |
| `feature/appslist` | `AppIconPort`、`LocalAppsPort`、`PinnedAppsPort` 及其 Registry，`SharedAppIcon` |
| `feature/device/pairing` | `PairingViewModel`、`PairingDependencies` |
| `feature/notification/superisland` | `ComposeFloatingView`、`ReplicaDisplayCache`、`LocalSuperIslandTracker` |

ViewModel 构造依赖平台端口，默认值统一由 `ui/viewmodel/ViewModelFactories.kt` 经端口注册表装配，调用方使用 `= xxxViewModel()` 即可。

判定口径：不含 `android.*`、`java.*`、`Context`、`org.json`；允许依赖 `:data` 的仓储门面（Room 与序列化细节不暴露给 UI）。

资源：Compose 资源位于 `shared/src/commonMain/composeResources/`（`drawable/app_icon.svg` 矢量图标、`font/segsmdl2.ttf`）；图标在桌面端按尺寸栅格化、安卓端由 Compose 资源绘制，不再维护像素图标。MDL2 字体为设备列表电池图标的渲染来源，`BatteryIconConverter` 输出的私有区字符必须配该字体，Android 与 JVM 共用同一份。
