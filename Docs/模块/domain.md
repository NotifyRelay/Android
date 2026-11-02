# `:domain`

KMP 领域层，全部位于 `commonMain`，只依赖 `:base`。包根 `notifyrelay.domain.*`。

| 源包 | 内容 |
|---|---|
| `feature/appslist` | `AppQueryMatch`、`LocalAppInfo`、`RemoteAppInfo`、`RemoteAppsPort`（+ `RemoteAppsPortRegistry`） |
| `feature/device/model` | `DeviceInfo` / `AuthInfo` / `PendingPairing`（`DeviceModels`）、`DeviceSnapshot`、`HandshakeRequest`、`SelectedDeviceInfo`、`DeviceNameCache` |
| `feature/device/service/contract` | `DevicePorts`（`HandshakeWaiterPort`、`AudioRelayPort` 等）、`DeviceQueryPort`、`DeviceAdminPort`、`DeviceMessagePort`、`DeviceChatPort`、`DevicePairingPort`、`DeviceAuthTablePort`、`DeviceSendPort`、`NotificationDataPort`、`DevicePortsProvider` |
| `feature/device/service/callback` | `HandshakeRequestHandler` |
| `feature/device/service/pairing` | `HandshakeWaiterRegistry` |
| `feature/device/pairing` | `PairingCorePort` |
| `feature/media` | `AudioRelayPort`（+ Registry）、`MediaControlPort`（+ Registry）、`MediaMessageReceiveMode` |
| `feature/notification/filter` | `FilterEntry`、`LocalFilterPort`（+ Registry）、`RemoteFilterPort`（+ Registry） |
| `feature/notification/history` | `NotificationHistoryPort`、`SuperIslandHistoryPort`（+ 各自 Registry）、`NotificationHistoryModels`、`AppIconInfo` |
| `feature/notification/superisland` | `SuperIslandConfigPort`（+ Registry）、`SpecInjectionMode` |

各 `*PortRegistry` 为端口注入点：平台侧启动时 `install` 实现，公共端 UI/ViewModel 经 `current` 取用。
