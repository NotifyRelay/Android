# Miuix v0.9.1 发布日志

> 发布日期：2026-05-14

## 亮点

* **升级至 Compose Multiplatform 1.11.0** — 跟进至最新稳定版。
* **移除 `miuix-shapes` 模块** — 视觉收益有限但性能成本较高。
* **新增：两级级联 ListPopup / DropdownMenu** — `OverlayCascadingListPopup`、`WindowCascadingListPopup`、`OverlayIconCascadingDropdownMenu`、`WindowIconCascadingDropdownMenu`。
* **新增：`miuix-blur` Highlight 支持** — `Modifier.drawBackdrop()` / `Modifier.textureBlur()` 新增 `highlight` 参数，可叠加高光效果。
* **`miuix-blur` API 变更** — DSL 中 `gaussianBlur(...)` 重命名为 `blur(...)`；`Modifier.textureBlur` 的 `blurRadius` 现按 dp 解释（自动密度换算）；Android `minSdk` 由 31 提升至 32。
* **Dropdown / Spinner 组件统一** — `SpinnerPopup` 合并至 `OverlayDropdownPopup`；逐项回调支持多选。
* **Compose 稳定性与渲染路径优化** — 覆盖 `Button`、`Surface`、`TextField`、`BasicComponent`、`TabRow`、`Snackbar`、`ColorPicker`、NavigationBar/Rail、Overscroll 等组件。

## ⚠️ 破坏性更改

### 1. 移除 `miuix-shapes` 模块

移除依赖，改用 `androidx.compose.foundation.shape`：

```kotlin
// 旧
implementation("top.yukonga.miuix.kmp:miuix-shapes:<version>")
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
val shape = SmoothRoundedCornerShape(16.dp)

// 新
import androidx.compose.foundation.shape.RoundedCornerShape
val shape = RoundedCornerShape(16.dp)
```

### 2. `miuix-blur`：`minSdk` 由 31 提升至 32

`miuix-blur` 现要求 Android API 32+，低于该版本的设备会自动回退至无模糊渲染。

### 3. `miuix-blur`：DSL 重命名 + `textureBlur` 半径单位改为 dp

`BackdropEffectScope.gaussianBlur(...)` DSL 重命名为 `blur(...)`，参数与单位不变（仍是像素 Float）：

```kotlin
// 旧
Modifier.drawBackdrop(...) { gaussianBlur(20f * density) }

// 新
Modifier.drawBackdrop(...) { blur(20f * density) }
```

`Modifier.textureBlur(blurRadius = ...)` 的类型仍为 `Float`，但含义由**像素**改为 **dp**（内部按屏幕密度自动换算）。数值需要相应调小：

```kotlin
// 旧 (v0.9.0)：60 为像素
Modifier.textureBlur(backdrop, shape, blurRadius = 60f)

// 新 (v0.9.1)：20 为 dp，在 3x 密度屏幕上约等于 60 px
Modifier.textureBlur(backdrop, shape, blurRadius = 20f)
```

`BlurDefaults.BlurRadius` 与 `BlurDefaults.MaxBlurRadius` 同样改为 dp（默认 `20f`、`150f`）。

### 4. `FloatingNavigationBar` 精简为图标模式

`FloatingNavigationBar` 已移除 `mode: FloatingNavigationBarDisplayMode` 参数，并整体删除 `FloatingNavigationBarDisplayMode` 枚举（含 `IconAndText` / `TextOnly` / `IconOnly` 三种取值）。组件现仅支持图标模式。如需 `IconAndText` 或 `TextOnly` 的展示效果，请改用普通 `NavigationBar` 或自行组合布局。

### 5. Dropdown / Spinner 合并

* `SpinnerEntry` 现为 `DropdownItem` 的废弃 `typealias`；`SpinnerColors` 同样标记为 `DropdownColors` 的废弃 `typealias`。
* `SpinnerDefaults` 标记 `@Deprecated`，请统一改用 `DropdownDefaults`。
* 原先的 `DropdownPopup` / `SpinnerPopup` 合并为单一 `OverlayDropdownPopup`（dialog 模式由 `OverlayDropdownDialog` 覆盖）；Window 一侧的对应实现位于 `WindowDropdownPopup` / `WindowDropdownDialog`。
* `WindowDropdownPreference` / `WindowSpinnerPreference`（以及对应的 `Overlay*` 版本）现统一通过 `DropdownEntry` / `DropdownItem` 管理选项。
* `miuix-preference` 新增下拉菜单包装：`OverlayDropdownMenu`、`OverlayIconDropdownMenu`、`WindowDropdownMenu`、`WindowIconDropdownMenu`，以及级联版本 `*IconCascadingDropdownMenu`。
* `DropdownItem` 新增 `selected: Boolean` 字段与可选的 `onClick: (() -> Unit)?` 回调，从而支持每行各自维护选中状态，实现多选。

## 更新内容

### 新功能

* library: 添加两级级联列表弹出支持 by @YuKongA
* library: CascadingListPopup: 添加预测性返回手势支持 by @YuKongA
* library: 添加分组式 DropdownPreference by @Miuzarte in #310
* library: 为 preference 组件补充缺失的 `startAction` 插槽 by @HChenX in #312
* library: feat: 为 Dropdown 和 Spinner 添加 `onExpandedChange(Boolean)` by @Miuzarte in #297
* library: 为 Card 添加 `holdDownState` 支持 by @YuKongA in #302
* feat: 支持滑动关闭 Snackbar by @AlexLiuDev233 in #292
* library: BasicComponent: 为无障碍添加 `role`/`onClickLabel` by @YuKongA
* library: refactor: NavigationBar/Rail 项改用 `Modifier.selectable` by @YuKongA
* library: 为 MiuixTheme 补充缺失的 `LocalContentColor` by @YuKongA
* library: miuix-blur: 添加 Highlight 支持 by @YuKongA
* library: miuix-blur: 添加 `setInputShader` by @YuKongA

### 破坏性 / API 变更

* library: 移除 miuix-shapes by @YuKongA
* library: miuix-blur: 将 minSdk 升级至 32 by @YuKongA
* library: miuix-blur: 简化 gaussianBlur 参数为 blur by @YuKongA
* library: 使用基于 dp 的模糊半径并自动密度换算 by @YuKongA
* library: 将 FloatingNavigationBar 精简为仅图标模式 by @YuKongA
* library: refactor: 统一 dropdown 和 spinner 组件 by @Miuzarte in #315

### 改进

**组件稳定性与渲染路径优化**

* library: 收紧 Compose 稳定性注解 by @YuKongA
* library: Button: 稳定修饰符并将主色参数化 by @YuKongA
* library: Surface: 将指示裁剪到形状并内联 SurfaceImpl by @YuKongA
* library: refactor: 提取 SurfaceImpl 并添加 SurfaceDefaults by @YuKongA
* library: TextField: 稳定 paddingModifier 的 remember 键 by @YuKongA
* library: TextField: 将 labelAnim 读取推迟到布局阶段 by @YuKongA
* library: ColorPicker: 将滑块指示值读取推迟到布局阶段 by @YuKongA
* library: TabRow: 移除冗余的 rememberUpdatedState by @YuKongA
* library: BasicComponent: 优化 Layout 测量阶段 by @YuKongA
* library: Snackbar: 提取硬编码默认值到 SnackbarDefaults by @YuKongA
* library: Overscroll: 将图层位移对齐到整像素 by @YuKongA
* library: Dropdown/ListPopup: 稳定渲染路径并优化无障碍 by @YuKongA
* library: Dropdown: 仅在弹窗全局行上添加首/末额外内边距 by @YuKongA

**miuix-blur**

* library: 重构模糊模块并刷新基线 by @YuKongA
* library: miuix-blur: 在效果热路径中复用临时缓冲区 by @YuKongA
* library: 优化 miuix-blur 在 sf=8/16 时的降采样级联 by @YuKongA
* library: 重构 miuix-blur 降采样与颜色控制 by @YuKongA
* library: miuix-blur: 将 Highlight 重构为 drawBackdrop 参数 by @YuKongA

**其他**

* library: 重构 miuix-icons by @YuKongA
* library: CascadingListPopup: 移除重复的进入触感反馈 by @YuKongA
* library: navigation3-ui: 同步 androidx v1.1.0 by @YuKongA

### Bug 修复

* library: ListPopup: 修复退出截断、手势竞态、过期捕获 by @YuKongA
* library: Dialog: 修复过期捕获、修饰符抖动、冗余暗化 by @YuKongA
* library: BottomSheet: 修复 IME 漂移、过期捕获、手势开销 by @YuKongA
* library: 用上限约束下限宽度以避免窄父容器崩溃 by @YuKongA
* library: 修复 HoldDown 泄露与无障碍语义 by @YuKongA
* library: fix: 防止 press 与 holdDown 指示重叠 by @YuKongA
* library: fix: TabRow 滚动状态持久化 by @Miuzarte in #300
* library: 移除 ContextMenuPositionProvider 的水平外边距 by @YuKongA
* example: 修复滚动动画回退中 versionCodeProgress 赋值缺失 by @wxxsfxyzm in #301

### 示例与文档

* example: android: 上传 baselineProfiles by @YuKongA
* example: 添加 textStyle 页面 by @HChenX in #320
* example: 补充缺失的 InnerShadow by @YuKongA
* example: 优化液态玻璃视觉效果 by @YuKongA
* example: 添加液态风格浮动导航栏 by @YuKongA
* example: 重构关于页背景效果并修复着色器条带 by @YuKongA
* example: 对 FloatingNavigationBar 应用模糊和高光 by @YuKongA
* example: 实现 `rememberNavBackStack` 和导航序列化 by @wxxsfxyzm in #306
* example: 添加 OS3 背景效果和配置 by @wxxsfxyzm in #303
* example: 为顶栏和导航栏添加模糊效果 by @YuKongA
* example: 清理背景效果着色器并修复横屏布局 by @YuKongA
* example: 清理效果着色器 by @YuKongA
* example: android: 排除未使用的原生库 by @YuKongA
* example: 更新 aboutlibraries.json by @YuKongA
* docs: 阐明 onDismissFinished 取消语义和 ListPopup 默认值 by @YuKongA
* misc: 更新 README.md by @YuKongA

### 构建

* build(gradle): android: 迁移到新的 r8 DSL by @YuKongA
* build: 通过生成的 xcconfig 路由 iOS 版本 by @YuKongA

### 依赖更新

* fix(deps): 更新 jetbrains.compose.multiplatform 到 v1.11.0 by @renovate in #325
* fix(deps): 更新 org.jetbrains.androidx.navigationevent:navigationevent-compose 到 v1.1.0 by @renovate in #324
* chore(deps): 更新 gradle 到 v9.5.1 by @renovate in #323
* chore(deps): 更新 org.jetbrains.compose.hot-reload 到 v1.1.1 by @renovate in #322
* fix(deps): 更新 about.libraries 到 v14.2.0 by @renovate in #319
* fix(deps): 更新 com.android.tools.build:gradle 到 v9.2.1 by @renovate in #318
* chore(deps): 更新 androidx.baselineprofile 到 v1.5.0-alpha06 by @renovate in #317
* fix(deps): 更新 about.libraries 到 v14.1.0 by @renovate in #316
* chore(deps): 更新 org.jetbrains.compose.hot-reload 到 v1.1.0 by @renovate in #314
* chore(deps): 更新 gradle 到 v9.5.0 by @renovate in #313
* fix(deps): 更新 io.nlopez.compose.rules:ktlint 到 v0.5.8 by @renovate in #311
* fix(deps): 更新 androidx.navigation3:navigation3-runtime 到 v1.1.1 by @renovate in #308
* fix(deps): 更新 kotlin monorepo 到 v2.3.21 by @renovate in #309
* fix(deps): 更新 com.android.tools.build:gradle 到 v9.2.0 by @renovate in #307
* chore(deps): 更新 actions/upload-pages-artifact 到 v5 by @renovate in #305
* fix(deps): 更新 com.android.tools.build:gradle 到 v9.1.1 by @renovate in #304
* fix(deps): 更新 about.libraries 到 v14.0.1 by @renovate in #296
* fix(deps): 更新 io.nlopez.compose.rules:ktlint 到 v0.5.7 by @renovate in #294
* fix(deps): 更新 androidx.navigation3:navigation3-runtime 到 v1.1.0 by @renovate in #293
* fix(deps): 更新 about.libraries 到 v14.0.0 by @renovate in #291
* fix(deps): 更新 about.libraries 到 v14.0.0-rc02 by @renovate in #290

### 新贡献者

* @Miuzarte 在 #297 中完成首次贡献
* @AlexLiuDev233 在 #292 中完成首次贡献

**完整更新日志**: https://github.com/compose-miuix-ui/miuix/compare/v0.9.0...v0.9.1
