# `:overlay-api`

叠加层扩展点契约（jvm 单目标），只放数据模型与能力接口，无实现、无平台依赖；由 `:desktop` 实现与消费，渲染侧不反向引用实现类型。包根 `notifyrelay.overlay.api`。新增能力只需实现 `OverlayFeature` 并注册到 `OverlayFeatureRegistry`，不必修改本模块核心结构。

| 文件 | 类/接口 | 职责 |
|---|---|---|
| `OverlayFeature.kt` | `OverlayStyle`、`OverlayFeature`、`OverlayFeatureRegistry` | 状态元素外观配置（缩放、颜色、描边）；扩展点统一生命周期契约（id / available / start / stop / syncSwitch）；注册表（register / unregister / all） |
| `ClockConfig.kt` | `ClockConfig`、`ClockSource` | 时间浮窗配置与数据源契约；实现侧纯 JVM（`java.time`），不使用 JNA |
| `Balance.kt` | `BalanceHistoryItem`、`BalanceSnapshot`、`BalanceSource` | 余额数据源契约：快照与历史查询、启停轮询、立即查询、清空历史、订阅变更 |
| `LogiBattery.kt` | `LogiBatteryDevice`、`LogiBatterySource` | 罗技电池设备快照与枚举数据源契约（可用性、快照、启停、立即刷新、设备名覆盖、订阅） |
| `Keyboard.kt` | `KeyboardStateSnapshot`、`KeyMapping`、`KeyboardSource` | 键盘状态与映射规则，以及键盘数据源契约（安装/卸载钩子、映射读写、状态与提示订阅）；`KeyboardSource.keyName` 提供虚拟键码到展示名的映射 |
