# PR #48 CodeRabbit 评审 → 修复计划

## 验证结论汇总

| # | 问题 | 验证结果 | 处置 |
|---|------|---------|------|
| 1 | registerReceiver flags 兼容性 | **部分真实**：minSdk=31，但三参数重载 API 26+ 已存在，**不会抛 NoSuchMethodError**；`RECEIVER_NOT_EXPORTED`(值4) 语义仅 API 33+ 生效，API 31/32 上等效 exported。注册的全是受保护系统广播（SCREEN_OFF/SCREEN_ON/USER_PRESENT/BATTERY_CHANGED），无实际崩溃/安全风险 | 做防御性修复（API 分支） |
| 2 | runBlocking 桥接 + 重复查询 | **真实**（getEnabledFilterEntries 每条通知查库 2 次；多个同步调用方） | ✅ 用户已确认：**全面 suspend 化** |
| 3 | 内置条目开关无效 | **真实**：`getEnabledFilterEntries` 强制加回内置条目（BackendLocalFilter.kt:78-80），注释"恒启用"但 UI 提供开关 | ✅ 用户已确认：**允许用户禁用内置条目** |
| 4 | 电量不带符号 | **真实**：`syncHeartbeatMode` 与 `startDiscovery` 均传正值，远端会误判全部设备为"充电中"（Rust 侧 battery>0=充电；BatteryReceiver 路径才是带符号的） | 修复两处 |
| 5 | scrcpy server URI 未保存 | **真实**：回调仅 takePersistableUriPermission，未写入 `CUSTOM_SERVER_URI`；且 `ScrcpyUiViewModel` 是**单例**（getInstance），只写 StorageManager 不会让 UI/连接生效 | 修复（app 模块内即可，通过 viewModel setter 双写） |
| 6 | 远程过滤开关 UI 不刷新 | **真实**：`entryEnabled` 读非 Compose 状态；`mutableStateOf` 对 data class 结构相等不触发重组 | 修复（enabledKeys 状态） |
| 7 | 开发者模式入口不刷新 | **真实**；但 CodeRabbit 建议的 ON_RESUME 方案**在本导航架构下无效**（Navigator 是 `rememberSaveable`+栈内重组，路由 push/pop 不改变 Activity lifecycle） | 修复（改用 `LaunchedEffect(backStack.size)` 或全局状态） |
| 8 | 迁移失败后不再重试 | **真实**：catch→return 导致 Room 提交版本 8，数据静默丢失 | 修复（抛出异常回滚） |
| 9 | 自定义组命名负数 + 启用错配 | **真实**：`"自定义组${idx+1-3}"` 对 idx=0 生成"自定义组-2"（与 savePackageGroups 的"自定义组1"不一致）；Set 解析顺序不可靠 | 修复（parseJsonList + 命名修正） |
| 10 | 替换操作无事务 | **真实**：clear+insert 非原子（FilterListDao 已有 replacePackageGroups 的 @Transaction 先例） | 修复（照搬模板） |
| 11 | scrcpy 子模块提交不存在 | ~~疑似误报/已过时~~ | ✅ **已在 PR CI 时修复，无需处理** |
| 12 | Docstring 覆盖率 26.92% | 与项目现状不符（全库无 docstring 强制约定） | 跳过 |
| 13 | PR 标题"Build"过于笼统 | 属实 | 合并时改标题（提示项） |

---

## 修复任务清单

### A. 通知过滤后端 suspend 化（问题 2，含问题 3 决策）

**A1. `app/src/main/java/com/xzyht/notifyrelay/feature/notification/backend/BackendLocalFilter.kt`**
- `getFilterEntries`、`getEnabledFilterEntries` 改为 `suspend`：合并为**一次查询**，复用行集；删除 `getFilterEntries` 内的二次查询与 `getEnabledFilterEntries` 内的重复查询（L74-77）
- **删除 L78-80 内置条目强制加入逻辑**（问题 3：允许用户禁用）
- `setFilterEntryEnabled`、`addFilterEntry`、`removeFilterEntry` 改为 `suspend`
- `shouldForward` 改为 `suspend`；其内部热路径只调用 suspend 过滤方法（消除每次通知 2 次 runBlocking 查询）
- 同步契约保留：`NotifyRelayNotificationListenerService.kt:457` 等非协程回调处，用 `CoroutineScope(Dispatchers.IO).launch` + 结果回传同步化，或保留一个内部 `runBlocking` 薄包装（实现时按调用上下文选择，保持服务回调语义不变）

**A2. `app/src/main/java/com/xzyht/notifyrelay/feature/notification/backend/BackendRemoteFilter.kt`（RemoteFilterConfig）**
- `load`、`loadFilterLists`、`loadPackageGroups`、`save`、`saveFilterLists`、`savePackageGroups` 改为 `suspend`，`runBlocking` 替换为 `withContext(Dispatchers.IO)`（save 内的 `syncToRust` 调用保留）
- `filterRemoteNotification`（L75，同步契约，被消息处理链调用）：内部由 runBlocking 包 suspend load 改为——若调用链可挂起则挂起，否则保留最小 runBlocking 兜底并注释原因

**A3. 调用方（app 模块）**
- `UILocalFilter.kt`：L42-43 的组合期同步初始化 → `LaunchedEffect` 异步加载（加 loading 态）；L144-157 回调 → `rememberCoroutineScope().launch`
- `UIRemoteFilter.kt`：L65 已有 LaunchedEffect 可挂起；L100-321 各处 `save` → 协程回调
- `NotificationHistoryViewModel.kt:66` → `viewModelScope.launch`
- `DeviceForwardScreen.kt:47` → 所在 Effect/作用域适配
- `NotifyRelayNotificationListenerService.kt:457` → 见 A1 同步化方案

### B. UI 状态类修复（问题 6、7）

**B1. `app/src/main/java/com/xzyht/notifyrelay/ui/pages/UIRemoteFilter.kt`（问题 6）**
- 新增 `enabledKeys` 状态（SnapshotStateSet 或 `mutableStateOf<Set<String>>`），键为 `"$packageName|$keyword"`
- `entryEnabled` 改读 `enabledKeys`；在 LaunchedEffect、onEntryEnabledChange、onAddEntry、onRemoveEntry、模式切换后同步刷新（与 `filterItems` 刷新一致）
- `setFilterEntryEnabled` 持久化逻辑保持不变

**B2. `app/src/main/java/com/xzyht/notifyrelay/ui/screen/SettingsScreen.kt`（问题 7）**
- 放弃 ON_RESUME 方案（已证无效）
- 改为 `LaunchedEffect(navigator.backStack.size)`：栈变化（从"关于"返回）时重新读取 `StorageManager.getBoolean(context, "developer_mode_enabled", false)` 并更新 `isDeveloperModeEnabled`
- 备选（实现时取更简洁者）：与 `DeveloperModeActivity.DEBUG_UI_ENABLED` 同模式，提升为全局 `MutableState`，由 `UIAbout` 激活时同步更新——但需新增状态容器并改 UIAbout

### C. 收发与同步修复（问题 1、4、5）

**C1. `app/src/main/java/com/xzyht/notifyrelay/feature/device/service/DeviceConnectionManager.kt`（问题 1）**
- L615 与 L643-649 两处：`if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, RECEIVER_NOT_EXPORTED) else registerReceiver(r, f)`
- 保留现有 try/catch

**C2. `app/src/main/java/com/xzyht/notifyrelay/sync/ConnectionDiscoveryManager.kt`（问题 4）**
- `syncHeartbeatMode` 与 `startDiscovery` 两处：按 BatteryReceiver 约定读取带符号电量（`isCharging ? level : -level`）
- 建议提取私有辅助如 `getSignedBatteryLevel()`，两处共用

**C3. `app/src/main/java/com/xzyht/notifyrelay/ui/activity/MainActivity.kt`（问题 5）**
- serverPicker 回调：`takePersistableUriPermission` 成功后将 `uri.toString()` 同时写入：
  1. `StorageManager`（`PrefsType.SCRCPY`、`ScrcpyPreferenceKeys.CUSTOM_SERVER_URI`）
  2. `ScrcpyUiViewModel.getInstance(app).customServerUri = uri.toString()`（setter 内部自动持久化+更新单例状态，使设置页展示与实际连接都生效）
- `runCatching` 失败改为 `Logger.e` 记录，不再静默
- 保留 `uri == null` 直接返回

### D. 数据库层修复（问题 8、9、10）

**D1. `data/src/main/java/notifyrelay/data/database/AppDatabase.kt` 迁移（问题 8）**
- MIGRATION_7_8 的 catch 中 `return`（L365）改为 `throw e`，使 Room 回滚事务、下次启动重试迁移（保留 Logger.e 诊断）

**D2. `AppDatabase.kt` migratePackageGroups（问题 9）**
- 新增 `parseJsonList`（`TypeToken<List<String>>`）保留 JSON 数组顺序
- L479 改用它；L488 命名改为 `"自定义组${idx + 1}"`（与 `savePackageGroups` 一致），移除默认组数量偏移
- `parseJsonSet` 保留给其他迁移（黑白名单用 contains 匹配，顺序无关）

**D3. `FilterListDao.kt` + `DatabaseRepository.kt`（问题 10）**
- DAO 新增 `@Transaction` 默认方法：`replaceBlackList`、`replaceWhiteList`、`replaceFilterEntries`（clear + insert，照搬 L77-87 `replacePackageGroups` 模板）
- Repository L503-506 / L512-515 / L521-524 三个方法改为委托给对应 DAO 事务方法

### E. 验证与收尾

**E1. 构建验证**：按 AGENTS.md 规范首次构建禁止筛选输出；验证 `:app` 与 `:data` 编译通过、单元测试（若有）通过

**E2. PR 标题**：合并时改为描述性标题（如"过滤配置迁移数据库 + 设置子页面 + TCP 备用心跳"），Docstring 检查跳过

---

## 明确不做

- 问题 11（scrcpy 子模块）：已在 PR CI 时修复，不涉及代码修改
- Docstring 覆盖率补全（不符合本项目约定）
- 迁移逻辑之外的表结构改动