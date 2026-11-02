---
name: notifyrelay-trim-cot-leakage
description: 审计或修复读起来像泄漏的推理记录的散文时使用——已死的设计会话引用，如 (decision N)、审计条目代号、未提交草稿的 §N；变更叙述，如 "used to"、"no longer"、"this cut"；栈或评审视角（"a later PR in this stack"、"rejected in review"）；面向评审者的论证；控制流叙述；或注释、JSDoc、文档与 Agent Note 中留有余地的规划残留。
---

# 精简思维链泄漏

思维链泄漏的散文，其视角是编写会话而不是仓库：它引用只有那个会话能看到的制品，叙述变更而不是状态，或者与已经离场的评审者争论。当段落承载事实分句时，修复从不只是删除——把每个分句重述成在 HEAD 上成立，然后删掉周围的记录；不承载任何事实分句的段落（审计代号、控制流叙述）直接删除。**必需背景：** [notifyrelay-prose-standard](../notifyrelay-prose-standard/SKILL.md) 拥有本技能所套用的完整命题规则；归档的 committed-artifact-citations 记录保存引用规则的历史理由。这是指引，不是脚本。

<a id="the-one-test"></a>

## 唯一判据

对每段可疑文字问：**一个处在 HEAD、无法访问任何会话记录、PR 讨论串或未提交草稿的读者，能否解析每个引用并验证每个主张？** 不能，就从仓库的视角重述存活的事实并删除其余部分。能，它就不是泄漏，无论听起来多么历史——但可解析性只满足本技能的门槛：在当前状态面（README、文档、JSDoc）上，可解析的变更故事仍属变更叙述，由第 3 类把它路由到其获准的归属地。

<a id="taxonomy"></a>

## 分类法

1. **已死的设计会话引用** —— `(decision 7)`、`(audit C2)`、`design §4.7`、`plan §1.4`、阶段标签（`T4`、`W3`、`P-I`）、"the design ledger"、"(B ruling)"。若该决策有已提交的所有者，按其名称与路径引用；否则删除引用，并重述其事实分句使之自足。
2. **栈与 PR 视角** —— "a later PR in this stack"、"this PR adds"、"the previous commit"。陈述已交付的机制或扩展点；推迟的工作移到 `TODO` 标记或 issue 引用。
3. **变更叙述与版本印记** —— "used to"、"no longer"、"the old X"，以及与过去状态形成对照的索引式印记（"v1"、"this cut"、"today"、"now"）。陈述当前行为；已修复的回归写成现在时反事实（"without X, Y happens"），绝不写成仓库历史（"used to Y"）。
4. **评审编排** —— "Rejected in review:"、"the reviewer confirmed"、草稿序数（"v5 of this note"）、轮次归属。把存活的决策与理由作为普通事实保留；删除「谁在何时说的」。
5. **面向评审者的论证** —— "the cast is safe — it simply…"、"this is correct because…"。为自身正确性辩护的注释面向评审者，不面向维护者。陈述使代码安全的不变量；若代码已展示该不变量，就删除注释。
6. **复述与推导记录** —— 控制流叙述（"first we X, then we Y"）、测试走查、显然分支的证明。删除；只保留非显然的契约或不变量。
7. **含糊措辞与规划残留** —— "probably fine for now"、"should be enough"、没有标记的推迟。提升为 `TODO`/`FIXME`，或重述为实际的边界；删除含糊措辞。
8. **编写语言残留** —— 在其余语言为英文的散文中混入未翻译的工作语言片段（端、设计稿、`---- 私有 ----` 分隔线），或在中文对侧反过来。翻译或删除。

<a id="what-is-not-leakage"></a>

## 什么不算泄漏

无辅助的引用清理在两个方向上都会失败：既删掉了持久引用，也留下了已死引用。以下保留规则按字面执行；[示例](references/examples.md)逐条校准：

- **Issue 引用** —— `#1470`、`TODO(name):`、"issue #N owns the follow-up" 在 HEAD 上可解析；在任何面上都保留，包括 README。不要把它们挪进 Agent Note。
- **Agent Note 与事故复盘中的已合并 PR 与 issue 引用** —— 按[文档标准](../../../Docs/AGENTS.md)的变更故事路由，这是获准的证据。
- **抑制说明** —— `oxlint-disable … -- reason`、覆盖率忽略原因、空 catch 的说明都是必需的散文；修正错误的理由，绝不删除它。
- **现在时反事实回归钉** —— "without X, Y happens"、"a naive X would…"。
- **实测边界** —— "(measured: 512 nests ≈ 0.15s)" 用于校准某个常量；证据词 "measured" 是承重的。
- **运行时的旧/新状态** —— "the old connection drains before the new one accepts" 是运行时生命周期，不是变更历史。
- **记录变更故事章节中的历史阶段名** —— "the first cut shipped X" 在该处对当前状态是安全的；索引式印记（"this cut"）在任何地方都禁用。
- **按设计在仓库外解析的外部引用** —— 标准章节（RFC 9110 §10.1.5）、Figma 画板名；§ 禁令针对未提交的内部草稿，不针对外部标准或拥有自身 § 编号的已提交文档。
- **项目声音与体裁形式** —— 作为项目声音的「我们」；记录中的 Alternatives considered 章节。

<a id="workflow"></a>

## 工作流

1. 范围与排除按 [notifyrelay-prose-standard](../notifyrelay-prose-standard/SKILL.md)：要求显式范围；绝不触碰 `vendor/` 或 `.agents/notes/archived/`。录制 fixture 与快照是派生物，不是散文目标：改动拥有它们的源或场景，并且只有授权行为变更需要新证据时才重新生成。
2. 先做只读审计：运行[召回题库](references/recall-batteries.md)（带 `--hidden` 以便搜到 `.agents/`），用已知正例与近似负例校准每个探针，再信任其输出，然后按语义判断每个命中。题库是探针而不是定义——原清理的每一轮评审都发现了题库漏掉的案例，所以还要在没有具体模式的情况下，读范围内最密集的散文（模块 JSDoc、README、Agent Note）。
3. 按面先修所有者：生成的目录 → 追踪每个消费方，修源 JSDoc 或生成器模板，再重新生成所有派生物；类型等价围栏 → 修源 JSDoc，再重新粘贴两个双语页（`verify-type-equiv` 钉住它们）；双语散文 → 最小化更新对侧，并经由[轻量例行流程](../../../Docs/AGENTS.md#writing-rules)重新记录；双语围栏 → 把修正后的逐字块逐字节复制到两侧，再重新记录配对；面向模型或用户的字符串 → 经 [notifyrelay-prose-standard](../notifyrelay-prose-standard/SKILL.md) 路由，且只有拿到所属行为证据时才改动，否则保持不变并报告推迟。
4. 删除任何内容之前，枚举该段落的命题（prose-standard 规则），并对照[过度纠正陷阱](references/examples.md#overcorrection-traps)：那些把义务翻转成认可、把假设提升为已交付特性、删掉真实事实，或丢掉证据来源的精简。
5. 校验：重跑题库，预期只剩获准保留项、本技能自身目录，以及所属记录中被引用的证据；确认每个剩余引用在 HEAD 上可解析；对触及的面运行门禁（文档用 `doc-sync`，以及 `verify-type-equiv`、`verify-translation-pairing`）。
