# PR #53 CodeRabbit 二次评审 → 修复计划

> 本文件替换了原 PR #52 修复计划（已由 PR #53 实施完成），现归档 PR #53 的二次评审修复计划。

**基线**：分支 `Agent_dev_fix-pr52` @ `cbdb912`（PR #53 HEAD） | **评审来源**：CodeRabbit review（PR #53） | **共 6 条评论**：4 条 Major 修复 + 1 条 Minor 修复 + 1 条 Major 用户决策跳过

## 验证结论汇总

| # | 评论 | 验证结果 | 处置 |
|---|------|---------|------|
| 1 | AudioRelayPlayer 非阻塞停止后旧 AudioRecord 悬空（L196-207） | **有效**：`stopSendCapture()` 只 `cancel()`，`audioRecord` 要等旧协程 `finally`（read 阻塞 20ms 级）才清空，期间 `startSendCapture` 因 `audioRecord != null` 被拒（`MainActivity.handleScreenCaptureReady` 先停后启场景失败）；且旧 `finally` 无条件操作**字段**，与新实例交错时释放并置 null 新实例（L190） | ✅ 修 |
| 2 | BackendLocalFilter.rowsCached 未持锁（L123-135） | **有效**：读协程未锁查库 → 写协程持锁提交 + `invalidateCache()` → 读协程把旧行集写回 `cachedRows`，缓存污染至下次写入，新建/删除/禁用规则不生效 | ✅ 修 |
| 3 | MediaProjectionForegroundService 失败后仍 markReady（L31-42） | **有效**：`catch { stopSelf() }` 不阻断当前流程进入 `onStartCommand`，`markReady()` 仍触发 `onForegroundReady` → 外部回调后 `getMediaProjection()` 在 Android 14+ 被拒 | ✅ 修 |
| 4 | MessageSender 裸路径图片无范围/大小限制（L255-280） | 风险真实但**无实际触发面**：pics 主流值为 base64 data URI / 网络 URL / 数字 imageId，`/` 绝对路径分支极少触发 | ⏭️ 用户确认跳过 |
| 5 | SuperIslandProcessor contentHash 去重键无法被终止路径清除（L113-115） | **有效**：登记键为 5 段（含 contentHash，L115），终止路径全部用 3/4 段键（L129/L141/L158）或终止包自身 hash（L168），均不匹配；TTL（300s）内重新收到相同内容被 `dedupCheck` 丢弃 | ✅ 修 |
| 6 | plan.md:5 评审统计数字错误 | **有效**：原文"33 有效 + 2 部分有效 + 1 不成立 = 36"≠35 | ✅ 修 |

---

## 修复任务清单

### 1. `app/.../feature/audio/AudioRelayPlayer.kt` — 捕获生命周期原子化（评论 1）

**1.1 `startSendCapture`（L138-192）**
- `AudioRecord` 创建后，协程内以**局部引用**捕获（`val record = audioRecord`），循环内 `record.read(...)`、静默帧发送均用局部实例
- 协程 `finally`（L183-191）：仅对**局部实例** `stop()`/`release()`；字段清空仅当 `audioRecord === 局部实例` 时执行

**1.2 `stopSendCapture`（L195-198）**
- 原子移除：`val rec = audioRecord; audioRecord = null; try { rec?.stop() } catch {}`（`stop()` 立即解除 `read()` 阻塞），再 `captureJob?.cancel()`、`captureJob = null`
- `stopSendCaptureAndJoin`（L200-203）语义保持不变（协程调用方可 join）

**1.3 `stop()`（L205-225）**
- 同样原子移除并 `stop()` 旧 AudioRecord（现实现未触碰 audioRecord，悬空窗口同样存在，`stop()` 后 `startSendCapture` 可能被拒）

### 2. `app/.../feature/notification/backend/BackendLocalFilter.kt` — rowsCached 入锁（评论 2）

**2.1 `rowsCached`（L123-135）**
- 整个函数体包 `saveLock.withLock { ... }`：缓存命中、DB 读取、内置条目初始化、`cachedRows` 赋值全部在锁内（照 CodeRabbit committable suggestion）
- 现有写入路径（`setFilterEntryEnabled`/`addFilterEntry`/`removeFilterEntry`）已持同一锁并 `invalidateCache()`，无需改动

### 3. `app/.../servers/MediaProjectionForegroundService.kt` — 前台启动成功标志（评论 3）

**3.1 `onCreate`（L31-35）**
- 新增 `private var foregroundStarted = false`
- `try { startForeground(1002, notification); foregroundStarted = true } catch { stopSelf() }`

**3.2 `onStartCommand`（L38-43）**
- `if (!foregroundStarted) return START_NOT_STICKY`；仅成功后调用 `markReady()`

### 4. `app/.../sync/notification/SuperIslandProcessor.kt` — 按 sourceKey 清除全部登记键（评论 5）

**4.1 新增登记簿**
- `private val registeredDedupKeys = ConcurrentHashMap<String, MutableSet<String>>()`（sourceKey → 登记过的 dedupKey 集合）

**4.2 登记点（L189 首次 dedupCheck 处）**
- `dedupCheck` 未命中（首次处理）时：`registeredDedupKeys.computeIfAbsent(sourceKey) { mutableSetOf() }.add(dedupKey)`

**4.3 新增 `clearDedupBySource(manager, sourceKey)`**
- 遍历该 sourceKey 全部登记键逐个 `dedupClear`，随后 `remove(sourceKey)`

**4.4 终止路径统一替换**
- L127-131（显式完整 sourceId 分支）：→ `clearDedupBySource(manager, sourceKey)`（featureId=explicitFeatureKey 时 sourceKey 即完整键）
- L135-145（显式后缀匹配分支）：→ 按 `rid.substringAfterLast("|")` 匹配登记簿键后清除
- L151-162（前缀匹配分支）：→ 按 rid 段匹配登记簿条目清除
- L168（兜底）：→ `clearDedupBySource(manager, sourceKey)`
- 合并失败路径（如有同类 dedupClear）一并走登记簿

### 5. `plan.md` — 统计修正与归档（评论 6）

- 本条即本文件：PR #52 评审统计修正为 **30 条有效 + 3 条部分有效（M11/O1/O5）+ 1 条结论不成立（I1）+ 1 条用户决策跳过（O3）= 35 条**
- PR #52 修复清单已由 PR #53 实施完成（历史内容见 git 记录）

## 明确不做

- **评论 4（裸路径读取限制）**：用户确认——pics 传入均为实际数据 base64/网络 URL/系统内置资源键（均有对应回退逻辑），无常规路径输入，不做范围/大小限制
- **PR #53 标题**：用户确认不改（保留原标题）
- **Docstring 覆盖检查**：项目为 Kotlin/Rust，检查不适用（沿用既有结论）

## 验证步骤

1. **构建验证**：`.\gradlew.bat compileDebugKotlin`（首次构建禁止筛选输出）
2. **功能抽查**：
   - 音频中继：`handleScreenCaptureReady` 先停止后启动捕获（评论 1 场景）、连续启停捕获
   - 过滤：并发切换开关 + 添加条目后 UI 刷新生效（评论 2）
   - 超级岛：锁屏通知 → 结束包 → 重新收到相同内容可再次通知（评论 5）
   - 投影：startForeground 失败（后台启动限制）时不触发 `onForegroundReady` 回调（评论 3）
3. 推送触发 PR 检查工作流，确认 CI 通过