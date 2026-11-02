# `:desktop`

KMP 桌面端入口，仅 `jvmMain`（jvm 单目标 + Compose Desktop）。包根 `notifyrelay.desktop.*`。

文件用途、类职责与公开方法按包拆在子页：

- [入口与数据层](desktop/入口与数据层.md)：`Main.kt`、`DataLayerDesktop.kt`、`CoreSelfCheck.kt`
- [设备与配对](desktop/设备与配对.md)：`device/` 的设备列表、报文入站与配对依赖注入
- [端口实现](desktop/端口实现.md)：`platform/` 中为 `:shared` 提供各端口的实现
- [窗口与打包](desktop/窗口与打包.md)：自绘窗口、托盘、单实例、自启、系统媒体中心、可分发包与图标生成
- [叠加层](desktop/叠加层.md)：`overlay/` 的媒体卡、超级岛卡片、弹幕与四类扩展点

窗口、托盘、主题、配对、电量、打包与叠加层的当前契约见[桌面端](../桌面端.md)。
