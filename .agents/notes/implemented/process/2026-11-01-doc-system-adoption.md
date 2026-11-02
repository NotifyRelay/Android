# Agent Note: 接入 DSH 文档系统并停用 .memory 记忆体系

Status: implemented

## Problem

仓库的常驻指令散落在根 `AGENTS.md`，会话记忆放在 `.memory/` 的打卡式日期文件里。两套机制都没有机械校验：散文可以硬折行、链接可以指向已删除文件、根指令可以无限膨胀，错误只在评审时靠人眼发现。`.memory/` 的日期文件还会随会话数量线性增长，浓缩规则依赖人工执行，无法验证。

## Decision

文档系统来自 `E:\GitHubCode\04Tools\InitProject` 模板，按 `WebDAVPass/Android` 的 `文档系统接入` 分支实例化后落入本仓库：

- `Docs/` 承载人读文档，`Docs/AGENTS.md` 定义层级、写作规则与词数预算。
- `.agents/notes/` 承载决策记录，`implemented/` 之外的记录按生命周期与类别分目录。
- `.agents/skills/` 承载按场景加载的技能，前缀为本仓库身份 `notifyrelay`。
- `scripts/` 承载七道门禁，由 `npm run verify-all` 串起；`verifyDocs` Gradle 任务把它挂到 `check` 上，PR 工作流的 `docs` job 作为构建的前置。

模板的槽位机制（`instantiate-template.mjs`、`verify-template-placeholders.ts`、`template.placeholders.json`）在实例化完成后整体删除：本仓库不是模板，保留槽位只会让身份字符串与真实名字并存。

`.memory/` 记忆体系停用，会话记忆职责并入 Agent Note。原 `.memory/` 内容与旧文档在接入期间移出仓库备份，重写完成后的当前事实由 `Docs/` 承载。

## Alternatives considered

**保留 `.memory/` 与 Agent Note 并存。** 两套机制记录同类信息，读者需要先判断该读哪一套；打卡式日期文件也无法通过门禁校验。停用一套更简单，代价是丢失逐会话的流水记录，这类流水本来也只对当次会话有价值。

**保留槽位机制，把新内容重新槽位化。** 槽位的意义是让同一份模板产出多个仓库。本仓库已经完成实例化，再槽位化等于把真实名字换回占位符，所有链接与技能目录名都要二次替换，收益为零。

**只接门禁、不迁移旧文档。** 旧文档留在 `Docs/` 会被 `verify-md-wrap` 与预算门禁拒绝；把它们排除在扫描之外则等于门禁对最需要校验的存量文档失效。

## Consequences

门禁带来确定的失败：硬折行、断链、超预算、Agent Note 路径或格式违规都会让 `verify-all` 变红，`./gradlew check` 与 CI 随之失败。代价是新增文档必须先读 `Docs/AGENTS.md`，且 `AGENTS.md` 的每条规则都要附一个能变红的门禁，否则不允许新增。

根 `AGENTS.md` 压到 1050 词预算内，UI 约定、模块 API 边界、代码检索入口与 Git 工作流迁到 `Docs/开发约定.md`。改这些内容时改 `Docs/开发约定.md`，不要往根文件里加。

`npm install` 成为文档门禁的前置：`verifyDocs` 依赖 `node_modules` 里的 `tsx`，未安装时任务失败而不是静默跳过。
