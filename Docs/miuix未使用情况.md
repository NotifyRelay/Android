
对 `Android/` 全量 **388** 个 Kotlin 文件（其中非子模块 **323** 个：`:app` 213 / `:data` 32 / `:base` 18 / `:superislandui` 60；子模块 `:scrcpy` 53、`:checkupdata` 10，另 `nativecore/`（`:core`）2 个）做了逐类扫描 + 逐文件精读。**原审计全程只读，未创建/修改/删除任何文件**（任务前后 `git status` 均为空，唯一临时日志目录 `.tmp-ui-audit` 已清理）。

> **修订记录（工具方法审计重构后复核）**
> 本文原基于重构前基线（全量 384 个 `.kt`）撰写。此后工具方法审计重构已落地
> （`整理项目` @ `96e70aab`，即 PR #96「统一共享的公用工具，同时移除过时的数据层代码」），本次按**当前代码**重新扫描复核，
> 仅更新受重构影响的部分：**文件计数**、因增删行而**漂移的行号引用**，以及被重构**顺带解决**的结论（在对应处标注「重构后」）。
> 其余结论经复核仍然成立，未做改动。

一句话结论：**`app` 模块的 Miuix 迁移相当干净（material3 使用 0 处），问题集中在 `superislandui` 库模块（material3 误用 18 文件）、`scrcpy` 子模块（M2 图标 + material3 IconButton），以及 `app` 层少量手搓基础组件与硬编码颜色。**

| 模块 | material3/M2 组件误用 | 手搓基础组件 | 硬编码色/字号 | 评级 |
|---|---|---|---|---|
| `:app` | **0** | 13 处（多为小面积） | 11 处 | 良好 |
| `:superislandui` | **18 文件 / 25 处** | 6 类 | 32 色 + 40 字号 | 需整改 |
| `:scrcpy`（子模块） | 1 处 material3 + 6 文件 M2 图标 | — | — | 需在子模块仓库处理 |
| `:base` `:data` `:checkupdata` `:core` | 0 | 0 | 0 | 合规 |

---

## 一、严重级：误用 material3 / Material2

### 1.1 `:superislandui`（18 个文件，25 处）

| 文件:行 | 现状 | 应改用 | 说明 |
|---|---|---|---|
| [CommonCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/common/CommonCompose.kt):15 | `import androidx.compose.material3.Card` + `CardDefaults`；调用点 :292、:295、:305 | Miuix `Card` / `CardDefaults.defaultColors` | 超级岛根卡片，黑底 0.92 / 红底 0.92 为设计所需，色值可保留，**组件来源应换 Miuix** |
| [ActionCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/ActionCompose.kt):6 | `material3.Button`，调用 :28，且 `onClick = { /* TODO */ }` 空实现 | Miuix `Button` 或 `TextButton` | 组件来源错误 + 点击逻辑未实现 |
| [ActionInfoButton.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/common/ActionInfoButton.kt):73 | 全限定 `androidx.compose.material3.Text(` | Miuix `Text` | 该组件是按钮组件 2/3/5 的公共底座，被 [HintInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/HintInfoCompose.kt):150 与 [HighlightInfoV3Compose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/HighlightInfoV3Compose.kt):121 复用，单点替换收益高 |
| [HighlightInfoV3Compose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/HighlightInfoV3Compose.kt):67, :77, :103 | 3 处全限定 `androidx.compose.material3.Text(` | Miuix `Text` | |
| [SuperIslandTextComponents.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/common/SuperIslandTextComponents.kt):8 | `material3.Text`（3 个调用点） | Miuix `Text` | `AutoFitText` / `AutoScrollText` 是全局文本基座，影响面最大 |
| 其余 12 个文件 | 各 1 处 `import androidx.compose.material3.Text`：[AnimTextInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/AnimTextInfoCompose.kt):9、[BaseInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/BaseInfoCompose.kt):14、[ChatInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/ChatInfoCompose.kt):13、[CoverInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/CoverInfoCompose.kt):10、[DefaultCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/DefaultCompose.kt):11、[HighlightInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/HighlightInfoCompose.kt):10、[HintInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/HintInfoCompose.kt):14、[IconTextInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/IconTextInfoCompose.kt):10、[MediaIslandCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/MediaIslandCompose.kt):16、[MultiProgressCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/MultiProgressCompose.kt):15、[PicInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/PicInfoCompose.kt):8、[TextButtonCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/TextButtonCompose.kt):7、[TimerInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/TimerInfoCompose.kt):5 | Miuix `Text` | 已核对：这些 `Text` 调用**全部显式传了 `color` 与 `fontSize`/`style`**，唯一的例外是 `ActionCompose.kt:32`（只传 `color`，未传 style/字号）。因此换成 Miuix `Text` 时默认样式差异被显式参数覆盖，**视觉风险低**；`ActionCompose.kt:32` 需补 style |

⚠ 关键判断依据：Miuix `Text` 支持 `color` + `fontSize` + `fontWeight` + `style` 全参数（已通过 miuix-mcp 核实 `Text` 组件文档），所以「岛屿固定白字」不是保留 material3 的理由。

### 1.2 `:scrcpy`（子模块，非本仓直接修改）

| 文件:行 | 现状 | 说明 |
|---|---|---|
| [ReorderableList.kt](Android/scrcpy/src/main/java/io/github/miuzarte/scrcpyforandroid/widgets/ReorderableList.kt):12 | `import androidx.compose.material3.IconButton`，调用 :117 | 同文件 :66/:153 已在用 Miuix `Card`、:108 用 Miuix `Checkbox`、:83/:129 用 Miuix `Icon`——**混用**，此处明显漏换 |
| 6 个文件共 29 处 | `androidx.compose.material.icons.*`（M2 图标，含 `material-icons-extended` 依赖） | 见 1.3 |

### 1.3 M2 图标体系（`scrcpy` 独有）

29 处 M2 图标导入，**`:scrcpy` 内 `MiuixIcons.*` 使用为 0**：

| 文件 | M2 图标导入数 |
|---|---|
| [VirtualButtons.kt](Android/scrcpy/src/main/java/io/github/miuzarte/scrcpyforandroid/widgets/VirtualButtons.kt):13-25 | 14 |
| [DeviceWidgets.kt](Android/scrcpy/src/main/java/io/github/miuzarte/scrcpyforandroid/widgets/DeviceWidgets.kt):27-31 | 6 |
| [SettingsPage.kt](Android/scrcpy/src/main/java/io/github/miuzarte/scrcpyforandroid/pages/SettingsPage.kt):12-13 | 3 |
| [DevicePage.kt](Android/scrcpy/src/main/java/io/github/miuzarte/scrcpyforandroid/pages/DevicePage.kt):63 | 2 |
| [VirtualButtonOrderPage.kt](Android/scrcpy/src/main/java/io/github/miuzarte/scrcpyforandroid/pages/VirtualButtonOrderPage.kt):7 | 2 |
| [ReorderableList.kt](Android/scrcpy/src/main/java/io/github/miuzarte/scrcpyforandroid/widgets/ReorderableList.kt):11 | 2 |

对比：`:app` 与 `:superislandui` 的 M2 图标使用均为 **0**，`app` 用 `MiuixIcons`（10 处导入 + `icon.extended.*` 若干）。`scrcpy` 是唯一保留 M2 图标体系的模块，且 `scrcpy/build.gradle.kts:64` 为此专门引入了 `material-icons-extended`。

### 1.4 Material2 XML 主题（跨模块、存量）

| 文件:行 | 现状 | 影响 |
|---|---|---|
| [themes.xml](Android/app/src/main/res/values/themes.xml):3, :18, :31 | `parent="Theme.MaterialComponents.DayNight.*"` | 主应用 + 开发者模式 + 透明 Activity 主题都继承 M2 主题 |
| [themes.xml (night)](Android/app/src/main/res/values-night/themes.xml):3 | 同上 | |
| [AndroidManifest.xml](Android/app/src/main/AndroidManifest.xml):163 | `@style/Theme.MaterialComponents.DayNight.NoActionBar` | |
| [themes.xml](Android/scrcpy/src/main/res/values/themes.xml):3 | 同上（子模块） | |

⚠ 这是**存量**且属 Compose 之外的原生主题层：Compose 侧已由 `MiuixTheme` 接管（[Theme.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/common/Theme.kt):20），XML 主题目前只承担 windowBackground / 状态栏等宿主职责。要不要改是设计决策，**不建议在本次范围内动**，仅登记。

---

## 二、第二级：自组 UI（该用 Miuix 却手搓）

### 2.1 `:app`（13 处）

| # | 位置 | 现状 | 应改用 | 影响面 |
|---|---|---|---|---|
| 1 | [GuideComponents.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/guide/GuideComponents.kt):209-245 | `Column + clickable` 手写标题+摘要+状态文案+状态圆点 | `preference.BasicComponent(startAction/endActions)` | **最大收益点**：是 [GuideRequiredPermissionPage.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/guide/GuideRequiredPermissionPage.kt) 与 [GuideOptionalPermissionPage.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/guide/GuideOptionalPermissionPage.kt) 的公共底座，2 页共 6 个权限项 |
| 2 | [GuideWelcomePage.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/guide/GuideWelcomePage.kt):105-120 | `Box.size(68.dp).clip(CircleShape).background(primary).clickable` 手搓圆形主按钮 | `IconButton(minWidth=68.dp, minHeight=68.dp, cornerRadius=34.dp)` | 引导页首屏，缺标准按压反馈 |
| 3 | [GuideAgreementPage.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/guide/GuideAgreementPage.kt):155-177 | `Row + Checkbox + Text+clickable`，勾选态与文案热区手工双向绑定 | `preference.CheckboxPreference` | 引导页法律同意项，热区不一致风险 |
| 4 | [DeviceListButtons.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/screen/devicelist/DeviceListButtons.kt):205-227 | `Row + Text + Switch`（Switch 本身是 Miuix） | `preference.SwitchPreference` | 丢失整行点击区 |
| 5 | [MusicControlPage.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/MusicControlPage.kt):250-274, :277-301 | 同上，2 处 | `SwitchPreference` | |
| 6 | [SuperIslandSettings.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/SuperIslandSettings.kt):231-262 | 同上（默认镜像包名） | `SwitchPreference` | |
| 7 | [SuperIslandSettings.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/SuperIslandSettings.kt):278-323 | 同上 + :306 用 `Button(size 32.dp)`（`Modifier.size(32.dp)` 在 :315）当删除按钮 | `SwitchPreference` + `IconButton` | :306 应为 IconButton；同文件 :44-50 已导入 `MiuixIcons.Delete`，说明图标本来可得 |
| 8 | [SuperIslandHistory.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/SuperIslandHistory.kt):119-139 | `Row + Text + Switch` 手搓开关行 | `SwitchPreference` | |
| 9 | [DisplayNavigationBar.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/remoteapps/DisplayNavigationBar.kt):40-91 | `Card + Row + Column.clip.background.clickable` 手搓显示设备导航条（图标+两行文字+选中态） | `NavigationBar` / `NavigationRail` | 与 [MainScreen.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/screen/MainScreen.kt):129-169 的 `NavigationBar` 风格不统一 |
| 10 | [AppGridItems.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/remoteapps/AppGridItems.kt):65-70, :125-130 | `Box.clip(RoundedCornerShape(12.dp)).background(surfaceVariant)` 手搓图标底板 | Miuix `Surface` | 色值已走 colorScheme，仅容器来源 |
| 11 | [SuperIslandHistoryImages.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/superisland/SuperIslandHistoryImages.kt):105-109, :177-183 | 同上 | Miuix `Surface` | 同上 |
| 12 | [UIRemoteFilter.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/UIRemoteFilter.kt):254-277 | 用 `Button + Text("+")` / `Text("×")` 当 28.dp 图标按钮 | `IconButton` + `MiuixIcons.Add/Delete` | 文字当图标 |
| 13 | [SuperIslandSettings.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/SuperIslandSettings.kt):120-121 | 组件内再套 `Scaffold { Surface(background) }` | 删除内层 Scaffold | **嵌套 Scaffold**：该组件已被 [SettingsScreen.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/screen/SettingsScreen.kt):136-141 的 `ScrollableTopAppBarPage`（内部即 Scaffold）与 [GuideBasicSettingsPage.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/guide/GuideBasicSettingsPage.kt):237-239 嵌入，会重复消费 insets |

**判定为「合理、不建议改」的手搓项**（列出以免误改）：
- [AppPickerDialog.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/dialog/AppPickerDialog.kt):193-220 应用列表项（图标+主副标题+分隔线）——Miuix 无「带图标+双行+可点击」的列表项原语，`BasicComponent` 无法一次容纳异步图标与两行文本的组合，属合理自组；
- [ChatTest.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/ChatTest.kt):98-128 聊天气泡——无对应组件；
- [AppGridItems.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/remoteapps/AppGridItems.kt):52-59, :112-119 网格磁贴——Miuix 无网格单元组件；
- [GuideComponents.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/guide/GuideComponents.kt):53-119 Canvas 极光背景——纯装饰绘制。

### 2.2 `:superislandui`（6 类）

| 位置 | 现状 | 建议 |
|---|---|---|
| [BaseInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/BaseInfoCompose.kt):208-222 | `ContentDivider`：`Spacer + height(1.dp) + background(Color(0xFFDDDDDD))` 手搓分割线 | 换 `HorizontalDivider` 可行；但固定灰是岛屿分格线设计，属**设计特征**（保持亦可，登记） |
| [MediaIslandCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/MediaIslandCompose.kt):154-191 | 3 个 `Box + clickable + padding` 手搓「上一首/播放暂停/下一首」（文字按钮） | 换 `TextButton` 更符合规范，且能获得标准按压反馈 |
| [TextButtonCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/TextButtonCompose.kt):28-40 | `Row + clickable` 手搓文本按钮，`onClick` 为 `// TODO: handle action` | 换 `TextButton`；**且点击逻辑本身未实现（功能性缺陷）** |
| [ActionInfoButton.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/common/ActionInfoButton.kt):53-61 | `Row + clip(16.dp) + background(bgColor)` 手搓胶囊按钮 | ⚠ **不建议换**：`bgColor`/`titleColor` 来自协议数据（`action.actionBgColor` / `actionTitleColor`），由对端下发，必须原样渲染，与主题色无关 |
| [HighlightInfoV3Compose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/HighlightInfoV3Compose.kt):108-113 | `clip(4.dp)+background(labelBgColor)` 手搓标签 | 同上，数据驱动色值，保持 |
| [BaseInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/BaseInfoCompose.kt):54, :64, [ChatInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/ChatInfoCompose.kt):63, :76, [CoverInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/CoverInfoCompose.kt):63, [IconTextInfoCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/IconTextInfoCompose.kt):67, [MediaIslandCompose.kt](Android/superislandui/src/main/java/github/xzynine/superislandui/floating/bigisland/components/MediaIslandCompose.kt):84, :99 | 图片圆角容器（`Image + clip`） | 图片本身需圆角裁切，`Surface` 包裹收益有限，保持 |

---

## 三、第三级：原生 View / 传统 View 体系

结论：**全仓无 `res/layout` XML 布局、无 RecyclerView/ListView/FrameLayout/LinearLayout 手写 UI、无 `findViewById`/`setContentView`**，唯一的 View 层只有两处，且均为**技术必需、不算违规**：

| 位置 | 现状 | 判定 |
|---|---|---|
| [FloatingComposeContainer.kt](Android/app/src/main/java/com/xzyht/notifyrelay/feature/notification/superisland/floating/FloatingComposeContainer.kt):33 | `: AbstractComposeView`，作为 `WindowManager.addView` 的宿主，内部已用 `MiuixTheme(darkColorScheme())`（:221） | **必需保留**（悬浮窗必须有 View 宿主）；内部 :222 已正确走 Miuix |
| [DeviceWidgets.kt](Android/scrcpy/src/main/java/io/github/miuzarte/scrcpyforandroid/widgets/DeviceWidgets.kt):1040 | `AndroidView` 承载 `ScrcpyInputSurfaceView`（视频 Surface） | **必需保留**（SurfaceView 无法用 Compose 替代） |

唯一 `android.widget.*` 使用是 `Toast`。**重构后**：原先直接 `Toast.makeText` 而未走项目 `ToastUtils` 的 4 个文件（`ParamIslandCompose.kt`、`UIAbout.kt`、`SuperIslandHistoryFormatters.kt`、`ChatTest.kt`）**已全部改走 `ToastUtils`，风格不统一问题已解决**；现仅剩 1 处直用——[FloatingReplicaListModeManager.kt](Android/app/src/main/java/com/xzyht/notifyrelay/feature/notification/superisland/replica/FloatingReplicaListModeManager.kt):194-196（`switchNotificationInList`，`Toast` 与 `.makeText` 跨行书写，故按单行 grep `Toast.makeText` 不会命中），建议后续一并改走 `ToastUtils`。`base` 的 [ToastUtils.kt](Android/base/src/main/java/notifyrelay/base/util/ToastUtils.kt):19、:31 是封装本体，属预期。

---

## 四、第四级：硬编码颜色与字号

### 4.1 颜色

| 模块/位置 | 数量 | 判定 |
|---|---|---|
| `:superislandui` 10 个文件 | 32 处 | **多为设计特征**：超级岛是仿小米原生岛屿视觉，黑底 `0xEE000000`/`0x92` 透明黑、固定白字 `Color.White`、`0x80FFFFFF` 次级白——这些是岛屿规范，改成主题色反而失真。仅 `BitmapUtils.kt:152` 附近 2 处属工具类残余 |
| [FloatingWindowContainer.kt](Android/app/src/main/java/com/xzyht/notifyrelay/feature/notification/superisland/floating/FloatingWindowContainer.kt):244, :307, :367, :376 + [ParamIslandCompose.kt](Android/app/src/main/java/com/xzyht/notifyrelay/feature/notification/superisland/floating/bigisland/ParamIslandCompose.kt):58, :67, :106, :115 + [ComposeFloatingView.kt](Android/app/src/main/java/com/xzyht/notifyrelay/feature/notification/superisland/floating/bigisland/ComposeFloatingView.kt):29, :45, :52, :58 | 12 处 | 同上，属岛屿风格（`ParamIslandCompose` 的行号已按重构后更新：剪贴板/Toast 改走 `:base` 的 `ClipboardUtils`/`ToastUtils`，删去 4 个 import 后上移 2 行） |
| [DoubleClickConfirm.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/common/DoubleClickConfirm.kt):80 | `ButtonDefaults.buttonColors(color = Color.Red)` | **真违规**：应为 `colorScheme.error`。同项目 [DeviceListButtons.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/screen/devicelist/DeviceListButtons.kt):428 已显式传 `colorScheme.error`，证明这是漏网 |
| [DoubleClickConfirm.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/common/DoubleClickConfirm.kt):109, :111 | `confirmTextColor ?: Color.White` / `textColor ?: Color.White` | **真违规且默认分支实际生效**：核对全部 5 个调用点——`ClipboardSyncPage.kt:172`（[来源](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/ClipboardSyncPage.kt):172）、`SuperIslandHistory.kt:98`（[来源](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/SuperIslandHistory.kt):98）都**未传** `textColor`，只有 `NotificationHistoryScaffold.kt:61` 与 `DeviceListButtons.kt:418` 传了。应为 `onPrimary` / `onError` |
| [UpdateDialog.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/dialog/UpdateDialog.kt):46 | `Color(0xFF43A047)` 成功绿 | 次要：Miuix 无 success 语义色，同文件 :45 已有注释说明，建议**登记为已知例外**而非新增语义色 |
| [AppGridItems.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/remoteapps/AppGridItems.kt):155 | `Color.Black.copy(alpha = 0.5f)` 遮罩 | 次要：无 Miuix scrim 语义色，可用 `onSurface.copy(alpha)` |
| [DisplayNavigationBar.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/remoteapps/DisplayNavigationBar.kt):64 | `Color.Transparent` + `primary.copy(alpha=0.1f)` | 次要：`Transparent` 无问题，选中底色可换语义色 |
| 跨模块间接：`BatteryIconConverter.kt` | [来源](Android/base/src/main/java/notifyrelay/base/util/BatteryIconConverter.kt) 返回硬编码电池色（`0xFF00FFE1`/`0xFFFF8C00`/`0xFFFF0000`），被 [DeviceListButtons.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/screen/devicelist/DeviceListButtons.kt):403 使用 | 属业务语义色（电量分档），非主题违规 |

### 4.2 字号

| 位置 | 现状 | 判定 |
|---|---|---|
| `:superislandui` 全局 | 40 处 `fontSize = N.sp` | 岛屿固定排版，设计特征 |
| [PairingCodeDialog.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/dialog/PairingCodeDialog.kt):138 | `36.sp` | 次要（Miuix `title1` 上限 32sp，配对码放大属刻意） |
| [PairingCodeInputField.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/dialog/PairingCodeInputField.kt):77 | `24.sp` 等宽 | 次要，6 格配对码输入属合理特例 |
| [AppGridItems.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/remoteapps/AppGridItems.kt):96, :186 | `11.sp` | 次要 |
| [DisplayNavigationBar.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/remoteapps/DisplayNavigationBar.kt):80, :85 | `12.sp` / `10.sp` | 次要 |
| [DeviceListButtons.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/screen/devicelist/DeviceListButtons.kt):402 | `16.sp` | **合理例外**：渲染 Segoe MDL2 图标字形，字号即图标尺寸 |

---

## 五、附带发现：未使用的依赖声明（死依赖）

Gradle 声明了但代码零引用：

| 模块 | 文件:行 | 声明 | 代码使用 |
|---|---|---|---|
| `:app` | [build.gradle.kts](Android/app/build.gradle.kts):177, :192 | `libs.androidx.material3` + `androidx.compose.material3:material3` | **0**（app 全模块无任何 material3 引用） |
| `:app` | [build.gradle.kts](Android/app/build.gradle.kts):175, :193 | `libs.material`（M2）+ `androidx.compose.material:material` | **0** |
| `:app` | [build.gradle.kts](Android/app/build.gradle.kts):220 | `libs.miuix.navigation3.ui` | **0**（app 导航全部走 `androidx.navigation3` 原生 + `navigationevent`） |
| `:superislandui` | [build.gradle.kts](Android/superislandui/build.gradle.kts):76 | `libs.miuix.preference` | **0** |
| `:superislandui` | [build.gradle.kts](Android/superislandui/build.gradle.kts):77 | `libs.miuix.icons` | **0** |
| `:superislandui` | [build.gradle.kts](Android/superislandui/build.gradle.kts):52 | `libs.material`（M2） | **0** |
| `:scrcpy` | [build.gradle.kts](Android/scrcpy/build.gradle.kts):70 | `libs.miuix.navigation3.ui` | **0** |
| `:scrcpy` | [build.gradle.kts](Android/scrcpy/build.gradle.kts):63, :64, :66 | `material3` / `material-icons-extended` / M2 material | material3 仅 1 处、icons 29 处、M2 material 0 处 |
| `:base` / `:data` | 各自 `build.gradle.kts` | `libs.material`（M2） | **0** |

> `:superislandui` 实际只用了 miuix 的 4 个符号：`CircularProgressIndicator`、`LinearProgressIndicator`、`ProgressIndicatorDefaults`、`MiuixTheme`（共 7 处，全在 `CommonCompose.kt`/`MediaIslandCompose.kt`）——该模块对 Miuix 的接入度远低于 `:app`，这也是它为何大面积停留 material3。

---

## 六、建议修复优先级（仅建议，本次未改动）

**P0（明确的规范违规，改动面小、收益直接）**
1. [DoubleClickConfirm.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/common/DoubleClickConfirm.kt):80, :109, :111 —— 3 处硬编码色，其中 `Color.White` 默认分支有 2 个调用方实际命中。
2. [ReorderableList.kt](Android/scrcpy/src/main/java/io/github/miuzarte/scrcpyforandroid/widgets/ReorderableList.kt):12 —— material3 `IconButton` → Miuix `IconButton`（同文件其余组件已全 Miuix，属漏换）。

**P1（结构性问题）**
3. [SuperIslandSettings.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/SuperIslandSettings.kt):120-121 删除内层 `Scaffold`（嵌套 Scaffold，重复消费 insets）。
4. [GuideComponents.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/guide/GuideComponents.kt):209-245 → `BasicComponent`（单点覆盖 2 页 6 个权限项）。
5. `:superislandui` 统一 `material3.Text` → Miuix `Text`（18 文件，逐文件机械替换；已核实这些调用都显式传了 color/fontSize，视觉风险低）。建议**先只换 Text，保留 `Card`/`Button` 的岛屿定制色**，分批提交。

**P2（体验一致性）**
6. 手搓开关行（6 处）→ `SwitchPreference`；[DisplayNavigationBar.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/pages/remoteapps/DisplayNavigationBar.kt):58-66 → `NavigationBar`。
7. [GuideWelcomePage.kt](Android/app/src/main/java/com/xzyht/notifyrelay/ui/guide/GuideWelcomePage.kt):105-120 手搓圆按钮 → `IconButton`。

**P3（清理，非必要）**
8. 移除上表的死依赖声明；`scrcpy` 的 M2 图标体系替换（须在 `ScrcpyForAndroid` 子模块仓库进行，并调用 `submodule-modifier` 流程）。

**建议保持现状（已核实为设计/技术必需）**：`superislandui` 的固定黑底白字与固定字号、`ActionInfoButton`/`HighlightInfoV3` 的数据驱动色值、`FloatingComposeContainer` 的 `AbstractComposeView`、`DeviceWidgets` 的 `AndroidView`、M2 XML 宿主主题。

---

## 七、方法与合规说明

- **取证方式**：`grep`/`glob` 全量模式扫描（material3/M2 导入、`AndroidView`、`android.widget.*`、`com.clickable`、`combinedClickable`、`Divider`、`height(1.dp)`、`fontSize = N.sp`、`Color(0x…)`），再对命中文件用 `read` 逐行核对真实行号；Miuix 可用组件与 API 签名通过 **miuix-mcp** 核实（`get_all_components`、`get_component_doc` for Text/Card/Button/Surface/BasicComponent、`get_best_practices_doc`）。
- **基线构建**：`Android` 目录下 `gradlew.bat :app:compileDebugKotlin --rerun-tasks --no-build-cache` → **BUILD SUCCESSFUL，Kotlin 警告 26 条**（与 `memory/2026-09-19.md` 记录的基线 26 条一致）。因本次为纯读取审计，无需构建前后对比。
- **未修改任何文件**（原审计）：任务前后 `git -C Android status --porcelain` 均为空；临时日志目录 `.tmp-ui-audit` 已删除。
- **重构后复核（本次修订）**：对本文引用的 **61 个文件**逐一核对存在性与行号（全部存在），其中 **8 个**被该重构改动过——`ParamIslandCompose.kt`、`Theme.kt`、`GuideComponents.kt`、`GuideOptionalPermissionPage.kt`、`GuideRequiredPermissionPage.kt`、`ChatTest.kt`、`SuperIslandHistory.kt`、`SuperIslandSettings.kt`；前 2 个与后 4 个的漂移行号已按当前代码重定位，`Guide*PermissionPage.kt` 仅影响「影响面」描述（不涉行号）。
- **重构后构建复核**：`gradlew.bat :app:compileDebugKotlin --rerun-tasks --no-build-cache` → **BUILD SUCCESSFUL，Kotlin 警告 26 条**（与重构前基线一致、零新增），`60 actionable tasks: 60 executed`、`FROM-CACHE 0`。`build.gradle.kts` 未被重构改动，故 §五 死依赖的行号与结论不变。
- **关于 mermaid**：本次为静态只读调查，不涉及任何时序/并发行为变更，故按 AGENTS.md「涉及时序才需 sequenceDiagram」的口径未绘制时序图。若后续进入修复阶段，涉及 `SnackbarHostState` 投递、`AnchoredDraggable` 滑动删除、悬浮窗 `addView`/`updateViewLayout` 三处的改动会补时序图。

**本文即该审计报告**，已落盘为 `Android/Docs/miuix未使用情况.md`。需要的话可按 P0–P2 直接进入修复。