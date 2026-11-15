---
name: notifyrelay-doc
description: 创建、重构、评审、审计或迁移 NotifyRelay 的 Markdown 文档：采用读者优先的层级、摘要与目录导航、从用户到开发者的渐进细节、已执行操作的事实核查与仓库校验。用于新增或修订 NotifyRelay 文档、文档树组织与文档质量审计。
---

<a id="NotifyRelay-documentation"></a>

# NotifyRelay 文档

<a id="summary"></a>

## 摘要

NotifyRelay 文档标准：让每一页都可检索、新人可读，并且精确到足以让 agent 与维护者使用。先执行仓库 `AGENTS.md` 与已落地的门禁，再用本工作流处理页面层级、渐进细节与语料库审计。每个事实只有一个所有者：源码、测试、生成的清单、包 README、指南、Agent Note 与 scratch 各自持有自己那类真相。`范例 README` README 对是格式的参考范例。

<a id="table-of-contents"></a>

## 目录

- [工作流](#workflow)
- [事实核查流程：实测，不要假定](#fact-check-procedure-test-do-not-assume)
- [语气规则](#voice-rules)
- [质量标准](#quality-criteria)
- [审计语料库](#audit-the-corpus)
- [词数预算](#wordcount-budgets)
- [详细参考页](#detailed-references)
- [校验](#validation)
- [Dev Note](#dev-note)

<a id="workflow"></a>

## 工作流

对每个请求的范围按此顺序执行。让常见读者路径保持简短，但不要仅为减少字数而删掉失败、归属、限制或其他必需的契约。

1. 读取根目录与更具体的 `AGENTS.md`、[文档标准](../../../Docs/AGENTS.md)、目标页、其源码与测试以及导航所有者。
2. 按主要职责与读者给页面分类：产品快速上手、用户任务指南、贡献者教程、架构概览、包或子系统参考页、生成参考、agent 指令、决策记录或 scratch。
3. 把页面放到最近的所有者处。包契约与包代码放在一起；`docs/` 用于跨包的学习、用户、开发者、架构、讨论与会过期的 scratch 材料。
4. 写细节之前先界定读者的起点状态、可观察结果、可能的失败、恢复路径与下一个有用的深度。
5. 按该顺序写 `Summary`、`Table of Contents`、面向用户的内容、面向开发者的内容、可选的 `Further Exploration` 与最后的 `Dev Note`（文档类型允许时）。
6. 校验页面与其声明的所有者一致：标题、列表、表格、代码与链接的布局保持稳定，改动不超出该页范围。
7. 对照源码、测试、生成器、包元数据或当前决策所有者校验每条断言，并按下方事实核查流程实际运行页面所指示的操作。先更新所有者，再改任何派生产物。
8. 先跑聚焦检查，再跑 `pnpm run test:docs`、`pnpm run verify-all`、`pnpm run lint` 与 `git diff --check`；完整复读 diff，先查正确性，再查简洁度与仓库契合度。

<a id="fact-check-procedure-test-do-not-assume"></a>

## 事实核查流程：实测，不要假定

文档陈述产品今天的行为；操作类断言的唯一可采信证据是实际运行过。每份新文档、以及每个声称操作、命令、默认值、错误或平台差异的新段落，都必须走本流程。

1. **写安装指引前先判定对象类型。** 读事实，绝不看文件夹名：`package.json` 里是否有 `notifyrelay.bundle.patch` 声明，入口文件是什么插件形态（`apply` 导出或默认服务导出是插件；朴素模块 API 是库）。bundle 用 `notifyrelay plugin --profile <name> add <package>` 安装，且只有这一种包形态会让该命令激活 profile 层；plugin 以 `cordis.yml` 行挂载；库是依赖，没有自己的安装路径。特殊形态的包（库、bundle）用自己的 README 模板——绝不为插件写不适用的安装指引。
2. **在当前检出上实际运行每个声称的操作。** 逐条执行文档将要展示的 CLI 命令、配置片段、profile 或 patch 示例；只记录你观察到的内容，包括确切输出、告警与失败形态。若某条断言依赖你没有的密钥或网络，说明这一点并点明验证所有者，而不是断言其行为。
3. **删掉无法复现的内容。** 绝不凭记忆、类比或相邻包 README 搬运命令、字段、默认值或行为。断言无法复现时，改正断言——不是改正测试。
4. **对照最新 master 检查旧文档。** 修订既有页面前先 `git fetch origin`，把该节与 `origin/master` 对比；配对 sidecar 可恢复任一侧最后确认的文本。master 上的过期陈述仍然是错的：按代码改正，而不是按旧散文。
5. **每次编辑后跑门禁。** 每次改动都重跑 `pnpm run verify-all`，让机械检查覆盖新的内容。

<a id="voice-rules"></a>

## 语气规则

这些规则决定一节可以说什么。它们适用于每一份面向人编写的页面。

- **摘要说明主体能做什么。** 开篇 `Summary` 与面向用户的各节描述用户或 agent 能用该主体做什么——结果、收益、何时选用、主要代价——绝不描述其角色、类型或内部身份。包 Summary 中的「它是什么」仅指读者可见的能力，而非其 Cordis 角色、注册项或内部组件。除读者会直接在配置、命令或公共 API 中使用外，省略源码标识符。「该 seam 注册 `ctx.x` 并追加 `x/event` 记录」是身份叙述；「你可以为每条消息保存一条笔记，且它在重启后仍在」才是它做的事。
- **开发者各节解释，绝不罗列。** 折叠的实现内容覆盖总体设计概念、架构与粗线条数据流——足以理解该包如何工作——并链接代码以取精确细节。折叠区内不放完整 API 清单、穷尽列清单、事件载荷枚举或 JSDoc 复述。
- **Dev Note 是唯一的杂物区。** 半成品想法、草稿、未定方向、实测产物与工作假设只存在于文件末尾的 Dev Note，并明确标注为非权威。其他各节都是打磨过的、描述当前状态的散文。
- **只写当前状态。** 普通文档描述当前代码库；Agent Note 与事故复盘保留各自的历史范围。
- **使用受控技术英语。** 当歧义会改变行为时，让每个句子有明确的主体与一个主动作。每个概念复用一个术语，偏好直接动词，拆开叠加的指令与条件，保留情态与例外。按[页面风格参考页](references/style.md#controlled-technical-english)中的非认证、受 ASD-STE100 启发的纪律执行。缩短句子会损失精确性时，不要强行缩短。

<a id="quality-criteria"></a>

## 质量标准

评审时使用这些定义。每节在子节或穷尽细节之前，先用一段简短的定向段落开场。

- **简短：** 常见路径只含其所需的事实；穷尽真相始终只隔一条直接链接或一层细节。
- **直观：** 前置概念先于依赖它的概念，下一个动作显而易见，标题使用读者会搜索的词。
- **友好：** 读者能认出成功、在动手前理解风险、从可能的失败中恢复，并自行决定是否继续深入。
- **准确：** 每条持久断言都有一个所有者，以及与其风险相称的验证路径。
- **agent 可读：** 元数据、稳定标题、锚点、术语、归属与当前或待定状态支持定向检索，无需加载整个语料库。
- **新人可完整重建：** 无仓库背景的职业工程师通过三到五个互链页面即可重建相关架构或特性。

不要对穷尽型参考页施加统一字数上限。衡量入口路径长度、为一次查找而扫过的无关材料、最大一节、标题数与页面大小；检索成本高时按既有领域所有者拆分。

<a id="audit-the-corpus"></a>

## 审计语料库

阅读，不要复述，这些所有者的契约：[Docs/AGENTS.md](../../../Docs/AGENTS.md) 负责层级、教程与参考页形态、分类、预算与 slop 检查清单；[.agents/notes/README.md](../../notes/README.md) 负责 Agent Note 生命周期；根 AGENTS.md 负责常驻指令。审计与编辑都排除 `.agents/notes/archived/`——归档记录是冻结的历史。

对范围内每份面向人的文档（不含 Agent Note）套用标准的写作顺序：定位文档并陈述其自身主题；确定允许的细节层级，把更深的解释连同链接移到拥有它的后代；按预期用途而非路径判定教程或参考页；教程按前置关系与难度排列概念；内容可观的混合形态拆开。然后检查放置约束：生成的清单从不手改；移动是原子的，且同一变更里修好所有入站链接。

结构检查之后，用最便宜的探针先追 slop 检查清单。用 [notifyrelay-trim-cot-leakage](../notifyrelay-trim-cot-leakage/SKILL.md) 处理推理记录泄漏，grep 有特点的短语找出重复规则，把手写的清单与状态盘点换成其权威所有者，并从已实现的 Agent Note 中删除迁移计划与将来时的规格话术。用 `pnpm run verify-doc-budgets --list` 与词数扫描衡量异常值；若删除散文改变的是被承诺的行为而非其解释，先提出行为变更（遵循 [notifyrelay-find-simplifications](../notifyrelay-find-simplifications/SKILL.md)）。保留每条承重规则，最好写成一到三行加一条指向其理由的链接；不要只为安置可丢弃的推理而新造一段解释。

<a id="wordcount-budgets"></a>

## 词数预算

`pnpm run verify-doc-budgets` 把常驻文档与 scripts/doc-budgets.manifest.json 中的上限比较；门禁变红时按 [Docs/AGENTS.md](../../../Docs/AGENTS.md#wordcount-budgets) 中搬迁、压缩、上调的有序策略处理。上限是护栏，不是压缩目标：处于或低于目标时保留至少 5% 余量；只有内容确实需要空间时才上调上限，并在 PR 中说明清单 diff 的理由。

<a id="detailed-references"></a>

## 详细参考页

只加载任务所需的那份参考页。每份参考页都从本文件直接链接，因此技能没有深层引用链。

- [元数据与链接](references/metadata-links.md)：README frontmatter、description 语义与仓库路径。
- [页面结构与层级](references/structure-hierarchy.md)：必需章节顺序、章节摘要、从用户到开发者的推进、docs 树放置、小规则文件、Further Exploration 与 Dev Note 归属。
- [页面风格](references/style.md)：简短摘要、`-----` 章节分隔线、可折叠内容节与强调纪律。
- [评审标准](references/review.md)：新人测试、证据检查、参考范例与校验命令。

句子级契约覆盖与编辑判断用 [notifyrelay-prose-standard](../notifyrelay-prose-standard/SKILL.md)。

<a id="validation"></a>

## 校验

校验受影响的格式，而不只是 Markdown 语法。强承诺需要一份聚焦的有效 fixture，以及一份证明顶层门禁会变红的无效 fixture。

- 页面结构：校验章节顺序、链接目标与术语一致性。
- 教程：走通文档所写的入口路径，或点明一个明确的人工验证所有者。
- 生成的参考页：运行确定性新鲜度检查并报告检索规模指标。
- 技能：运行仓库的技能调用元数据检查。

在完整跑 `pnpm run verify-all` 之前，先用 `pnpm run test:docs` 跑快速综合文档检查（换行、链接、预算、技能元数据、Agent Note 门禁）。

<a id="dev-note"></a>

## Dev Note

None.
