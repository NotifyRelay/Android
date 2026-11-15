# 泄漏示例（少样本）

提炼自 2026-08 的全仓清理及其多轮评审。用它们识别主导原则，而不是当作文本模板。本文件刻意引用泄漏原文作为校准材料——[召回题库](recall-batteries.md)排除本技能目录，其措辞在其他地方不构成许可。

被引用的英文原句是泄漏示例本体，逐字保留；点评与说明使用中文。

<a id="dead-citations"></a>

## 已死的引用

<a id="decision-ordinal-with-a-committed-owner"></a>

### 有已提交所有者的决策序数

**泄漏：** "Slash input resolves against the visible catalog (decision 21)."

**修复：** "Slash input resolves against the visible catalog — the plain-text-reference decision, owned by the web input-machine note."

序数在 HEAD 上无处可解析；该决策的名称与所属记录路径可以。每个文件至少一次点明所属记录的路径——面支持链接时用链接——之后可以用可搜索的名称单独提及。

<a id="decision-ordinal-without-an-owner"></a>

### 没有所有者的决策序数

**泄漏：** "The registry rejects duplicate names (decision 7: names are flat, no namespacing)."

**修复：** "The registry rejects duplicate names; names are flat, with no namespacing."

没有已提交制品拥有 "decision 7"，所以引用被删除——但它的事实分句（扁平命名）被重述为自足陈述，而不是随引用一起删掉。

<a id="audit-item-codes"></a>

### 审计条目代号

**泄漏：** "Rendering is pure: same snapshot, same string (audit R3)."

**修复：** "Rendering is pure: same snapshot, same string."

仓库中没有审计文档；该代号是纯会话速记，不承载任何命题。

<a id="section-numbers-of-uncommitted-drafts"></a>

### 未提交草稿的章节号

**泄漏：** "Layering follows the design (v2 §3.2): `src/core/` is the pure core."

**修复：** "Layering: `src/core/` is the pure core."

无人提交的草稿的 `§N` 无法解析。对照："escapes per RFC 9110 §10.1.5" 保留——外部标准按设计在仓库外解析，拥有自身 § 编号的已提交文档可以按章节引用。

<a id="plan-phase-labels"></a>

### 计划阶段标签

**泄漏：** "`src/client/` is the shell (T4); the P-I migration owns the adapters."

**修复：** "`src/client/` is the shell; the adapters live in `src/client/adapters/`."

阶段标签索引的是一份从未落地的计划。用该阶段产出的东西替换标签。

<a id="stack-and-pr-vantage"></a>

## 栈与 PR 视角

<a id="stack-position-in-durable-prose"></a>

### 持久散文中的栈位置

**泄漏：** "A future remote backend implements this interface (the sandbox backend is a later PR in this stack)."

**修复：** "A remote backend can implement this interface without changing the render layer."

持久散文看不到栈。保留扩展点契约；待办工作的归宿是 PR 本身、一个 `TODO` 或一个 issue。

<a id="this-pr-in-a-readme"></a>

### README 中的 "This PR"

**泄漏：** "This PR adds cursor-based pagination to the session list."

**修复：** "The session list paginates by cursor."

README 比任何 PR 都活得久；把机制陈述为当前事实。

<a id="change-narration-and-version-stamps"></a>

## 变更叙述与版本印记

<a id="war-story-with-a-pr-number"></a>

### 带 PR 编号的战争故事

**泄漏：** "Colors used to come from `--widget-*` tokens, which nothing defined, so it always rendered the fallbacks; the alias tokens fixed that (PR #88)."

**修复：** "Colors come from the alias tokens; an undefined token renders the fallbacks."

两个活事实都存活——当前机制与仍然成立的失败行为——以现在时重述。该 bug 的传记属于其 PR 与 Agent Note。

<a id="removal-narration"></a>

### 移除叙述

**泄漏：** "The `probe` field is gone with the removal cut; badges ride the generic projection pair now."

**修复：** "Badges use the generic projection pair."

从没见过 `probe` 的读者从它的缺席里学不到东西。与被删除的过去形成对照的 "now" 是版本印记。

<a id="fixed-regression--counterfactual-present"></a>

### 已修复的回归 → 现在时反事实

**泄漏：** "This used to double-encode multibyte labels."

**修复：** "Without the byte-length guard, multibyte labels double-encode."

回归钉作为现在时反事实存活，并点明该护栏；"used to" 反而把它钉进仓库考古。

<a id="indexical-version-stamps"></a>

### 索引式版本印记

**泄漏：** "Batch rendering is synchronous this cut; the async path is roadmap work."

**修复：** "Batch rendering is synchronous."（推迟事项留在调用点的 `TODO(widget-batch):`。）

"This cut" / "v1" / "today" 在合并的那一刻就开始过期。Agent Note 变更故事章节里的历史阶段名（"the first cut shipped X"）对当前状态是安全的；索引式写法从来不是。

<a id="review-choreography"></a>

## 评审编排

<a id="review-verdicts-as-prose"></a>

### 评审结论写进散文

**泄漏：** "Rejected in review: caching the resolved spec. We keep resolution per-call."

**修复（写在 Agent Note 的 Alternatives considered 中）：** "**Caching the resolved spec.** Rejected: the spec depends on per-call cwd, so a cache keyed by request would serve stale roots."

alternatives-considered 体裁是获准的归属地；评审者与轮次不属于理由。

<a id="draft-ordinals"></a>

### 草稿序数

**泄漏：** "As of v5 of this note, the loader also validates manifests."

**修复：** "The loader validates manifests."

已实施的记录陈述已交付的现实；它自身的修订历史在 git 里。

<a id="reviewer-addressed-justification"></a>

## 面向评审者的论证

<a id="arguing-a-cast"></a>

### 为一次类型断言辩护

**泄漏：** "The cast is safe — the SDK constructed the object, it simply doesn't declare the optionals strictly enough."

**修复：** "The SDK constructs this object with every optional populated; the declared type is looser than the runtime guarantee."

陈述维护者不得破坏的不变量。"It simply…" 是一种在回应 HEAD 上无人提出的异议的口吻。若不变量在代码里已经可见，就改为删除该注释。

<a id="appeal-to-review-authority"></a>

### 援引评审权威

**泄漏：** "This is correct because the reviewer confirmed the wrapping order."

**修复：**（已删除；包装顺序写在函数的 `@returns` 中。）

正确性主张引用不变量或测试，绝不引用人。

<a id="restatement-and-derivation"></a>

## 复述与推导

<a id="control-flow-narration"></a>

### 控制流叙述

**泄漏：** "First we normalize the label, then we truncate it, then we wrap it."

**修复：**（已删除。）

注释下方三行代码用代码说了同样的事。

<a id="test-walkthrough"></a>

### 测试走查

**泄漏：** "This test creates a session, sends two messages, waits for the second reply, and then asserts the log has four entries."

**修复：** "Two round-trips must produce exactly four log entries — the projection dedupes the shared prefix."

只保留非显然的断言理由；走查在复述测试体。

<a id="hedges-and-planning-residue"></a>

## 含糊措辞与规划残留

<a id="unmarked-deferral"></a>

### 无标记的推迟

**泄漏：** "Probably fine to render eagerly for now."

**修复：**（已删除；推迟事项已经有其 `TODO(widget-batch):` 标记。）

没有所有者的含糊措辞是规划残留。若没有标记，就写一个（`TODO(name): coalesce per animation frame`），而不是留着含糊措辞。

<a id="vague-sizing"></a>

### 含糊的尺寸说明

**泄漏：** "A 64 KiB buffer should be enough for most cases."

**修复：** "64 KiB holds the largest observed frame (48 KiB) with headroom; a larger frame fails loudly in `decode`."

用实际的边界和超界时的失败行为替换含糊措辞。

<a id="authoring-language-slips"></a>

## 编写语言残留

**泄漏：** "The renderer runs on the client 端; see the 设计稿 for spacing. ---- 私有 ----"

**修复：** "The renderer runs on the client side; spacing follows the Figma frame `widget-badges`."

工作语言片段与会话分隔线是转录残留。Figma 画板名保留：那是按设计在仓库外解析的外部来源。

<a id="authoring-language-slip-inside-a-verbatim-fence"></a>

### 逐字围栏内的编写语言残留

**泄漏：** 逐字代码块内的 `// 更新这里 before returning`。

**修复：** `// Update this before returning.`

逐字围栏只有一份可编辑的源：修正代码块本身，生成物随之更新。

<a id="behavior-visible-candidates"></a>

## 行为可见的候选

**可疑：** 一句导出的 JSDoc 写着 "available for now"，而生成器把这句话复制进面向模型的目录。

**错误：** 在纯散文清理中只改源句子，或只手工编辑生成的目录。

**正确：** 追踪该源的生成扇出，更新所有者，重新生成每个派生物，并更新所属的可运行快照。对 GUI 字符串，还要包含仓库要求的行为证据。若授权范围内没有所属场景，保持不变并报告推迟。

<a id="keeps"></a>

## 保留项

<a id="issue-references-are-durable-on-every-surface"></a>

### Issue 引用在任何面上都持久

**保留：** "The cap applies to the complete rendered value, wrappers included (issue #1470 owns the follow-up)."

一次无辅助的清理删掉了这句，理由是 issue 引用属于 Agent Note。方向错了：issue 在任何面上都能于 HEAD 解析，且 "#N owns the follow-up" 是 README 中推迟工作的获准归属地。Agent Note 与事故复盘额外获准的是引用*已合并 PR* 作为证据。

<a id="dead-name-drops-are-not-naming-the-owner"></a>

### 已死的点名不等于「点明所有者」

**删除：** "Badge renderer over the widget seam (see the widget-rendering RFC)."

一次无辅助的清理把这句当作 "naming the owning document by topic"（按主题点明所属文档）保留了下来。判据是可解析性，不是形式：没有任何已提交文件对得上 "the widget-rendering RFC"，所以该指针已死。若存在已提交所有者就重新指向它；否则删除。

<a id="suppression-justifications"></a>

### 抑制说明

**保留（修正后）：** `// oxlint-disable-next-line no-non-null-assertion -- the one-element literal guarantees index 0.`

理由分句是必需的散文。当所述理由为假时（原文写的是 "the loop guard above proves a frame exists"，却看不到任何循环），修正理由；绝不删除它。

<a id="measured-bounds"></a>

### 实测边界

**保留：** "Depth cap (measured: 512 nests ≈ 0.15s synchronous; 4096 blocks the loop)."

该测量钉住常量，防止无依据的重新调参，而 "measured" 表明这是数据而非猜测。

<a id="runtime-oldnew-is-not-change-history"></a>

### 运行时的旧/新不是变更历史

**保留：** "The old connection drains before the new one accepts."

这里的「旧」与「新」命名的是交接期间两个存活的运行时对象，不是仓库状态。变更叙述禁令针对仓库历史，不针对生命周期词汇。

<a id="runtime-natural-time-is-not-a-version-stamp"></a>

### 运行时自然时间不是版本印记

**保留：** "What is today's date?"

该提示问的是运行时时钟；"today" 并不与仓库状态对照。由于文本会抵达模型，任何改写仍需要其所属的行为证据。

<a id="overcorrection-traps"></a>

## 过度纠正陷阱

下列每个陷阱都曾在原清理中交付，并在评审中被抓住。精简一段之前，先枚举它的命题。

<a id="flipping-an-obligation-into-an-endorsement"></a>

### 把义务翻转成认可

**原始：** "These direct registrations are exceptions pending migration to slots."

**过度纠正：** "These direct registrations are sanctioned exceptions."

**正确：** "These direct registrations are exceptions pending migration to slots."

"Pending migration" 是一项义务；"sanctioned" 则祝福了现状。这次精简在缩短句子的同时把句子的情态反转了。

<a id="promoting-a-hypothetical-to-a-shipped-feature"></a>

### 把假设提升为已交付特性

**原始：** "A future IPC-based shell subclasses the executor and overrides `spawn`."

**过度纠正：** "An IPC-based shell subclasses the executor and overrides `spawn`."

**正确：** "A hypothetical IPC-based shell — no such shell exists — would subclass the executor and override `spawn`."

只删掉未来标记，就把一个设计示意变成了该类已交付的主张。要显式标记假设，而不是只去掉未来标记。

<a id="deleting-a-true-fact-with-the-transcript-around-it"></a>

### 连同周围的记录一起删掉真实事实

**原始：** "The gate notice narrates the check order; the notice text is also what `verify-doc-typecheck` compiles against."

**过度纠正：** "…"（整句作为叙述被删除。）

**正确：** "The notice text is what `verify-doc-typecheck` compiles against."

句子一半是叙述，另一半是承重的耦合。当命题共享一行时，删除分句，不是删除整句。

<a id="dropping-the-measurement-source-while-keeping-the-number"></a>

### 保留数字却丢掉测量来源

**原始：** "The 4 MiB ceiling is measured: the largest generated `py-types` module is 3.1 MiB."

**过度纠正：** "The ceiling is 4 MiB; the largest generated `py-types` module is 3.1 MiB."

**正确：** 保留 "measured"。

没有 "measured"，3.1 MiB 读起来是定义而不是观察，于是没人会在调高上限前重新测量。
