# `:superislandui`

源集分布：平台无关代码（UI 主体、model、parser、builder（含 `SuperIslandImageSpec`/`SuperIslandExtrasCore`）、diff、`json/JsonCompat`、`common/IslandSummary`（`miui.focus.param` 摘要纯解析）、`common/BitmapUtils`（尺寸/缩放/缓存策略）、`common/CapsuleScrollManager`、`common/TextSplitter`、`model/core/SuperIslandData`、`SuperIslandImageUtil` 声明、`currentTimeMillis` 薄壳）位于 `superislandui/src/commonMain/`；Android 专属代码位于 `superislandui/src/androidMain/`，共 5 个文件：3 处为公共源集声明的平台实现（`PlatformBitmap`、`ImageCodec`、`SuperIslandExtras` 门面），2 处为平台特性保留项（`SuperIslandManager`、`SuperIslandImageUtil` 的 actual）：

| androidMain 文件 | 作用 |
|---|---|
| `common/PlatformBitmap.android.kt` | `PlatformBitmap` 的平台位图（actual typealias → `android.graphics.Bitmap`） |
| `common/ImageCodec.android.kt` | 绘制端口 `ImageCodec` 的 Android 实现（Paint 度量 + Canvas） |
| `builder/SuperIslandExtras.kt` | `SuperIslandExtrasCore` 的 Bundle 门面（常量转发 + `BundleExtrasWriter`） |
| `common/SuperIslandManager.kt` | 依赖 `Context`/`StatusBarNotification` 的通知解析 |
| `floating/common/SuperIslandImageUtil.android.kt` | `rememberSuperIslandImagePainter` 的 actual（data URL 解码委托 `:base` 的 `ImageUtils`） |

`BitmapUtils` 返回类型经 `PlatformBitmap` typealias 在 Android 侧仍为 `android.graphics.Bitmap`；`SuperIslandExtras.writePicMap` 仍接收 `android.os.Bundle`。日志统一使用 `notifyrelay.base.log.Logger`。

| 类 | 职责 |
|---|---|
| `SuperIslandManager` | 悬浮岛启用检测、协议版本读取、权限判断、通知 extras 提取（JSON 摘要解析委托 `extractIslandSummary`） |
| `IslandSummary` / `extractIslandSummary` | `miui.focus.param` 摘要纯解析：title/text 提取优先级与 param_v2/param_island/bigIslandArea raw 文本 |
| `DiffSystem` | State 展示数据类 |
| `TextSplitter` | 文本按宽度/规则分割 |
| `CapsuleScrollManager` | 胶囊滚动管理 |
| `BitmapUtils` | Bitmap 处理（缩放、圆角、缓存等） |
| `PreviewData` | 预览数据 |
| `SuperIslandTextComponents` | 文本组件：`AutoFitText`、`AutoScrollText`（超宽进入滚动并占满容器宽，未超宽随容器对齐规则放置） |
| `AParser` / `BParser` | 小岛 A/B 区组件解析器 |
| `SuperIslandImageUtil` | 图片加载与变换 |
| `FocusIconResolver` | 焦点图标解析 |
| `CommonCompose` | 通用 Compose 组件 |
| `ParamV2` / `ParamIsland` / `SuperIslandData` | 核心数据模型 |
| `BaseInfo` / `ChatInfo` / `HighlightInfo` / `HintInfo` / `PicInfo` | 模板数据模型 |
| `ActionInfo` / `AnimTextInfo` / `MediaSessionData` / `MultiProgressInfo` / `ProgressInfo` / `TextButton` / `TimerInfo` | 组件数据模型 |
| `BigIslandArea` / `ACompose` / `BCompose` / `BigIslandCollapsedCompose` | 小岛区域与折叠态渲染 |
| `bigisland/components/*Compose` | 大岛 14 个具体组件渲染 |
