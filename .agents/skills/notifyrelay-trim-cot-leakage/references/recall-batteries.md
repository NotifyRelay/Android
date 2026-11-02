# 召回题库

针对[分类法](../SKILL.md#taxonomy)的探针，在 2026-08 的清理中调优。每个命中都需要语义判断——题库按设计会过度匹配，而按性质又会漏匹配：清理的每一轮评审都发现了没有任何题库捕捉到的案例，所以要把它们与一次不带模式的通读（读范围内最密集的散文）搭配使用。

<a id="invocation-rules"></a>

## 调用规则

- 加上 `--hidden --glob '!.git/**'`，以便搜到 `.agents/`；ripgrep 默认跳过点目录，而清理中最大的漏检风险正是 Agent Note。
- 排除项放最后，以免后续包含重新纳入它们：`--glob '!vendor/**' --glob '!node_modules/**' --glob '!.agents/notes/archived/**' --glob '!.agents/skills/notifyrelay-trim-cot-leakage/**'`（本技能自身的文件引用泄漏原文作为校准材料），再加上范围内已录制的 fixture 与快照目录。[所属参考页](../SKILL.md)也会因其引用的证据而自我命中；把它当作证据判断，而不是当作使用示范。
- 自然语言行带 `-i`，使句首大写的命中（"This PR adds…"、"Probably fine…"）；第一行匹配代码模式，保持大小写敏感——`-i` 会把 `\bT\d\b` 与 `\bP-I\b` 变成噪声。
- 限定完整词组。`\bthis PR\b` 必须匹配 "this PR adds"，而不匹配 "this project"、"this process" 或 "this provider"。
- 零命中的模式在匹配到已知正例前证明不了任何事，噪声大的模式在拒绝近似负例前也证明不了任何事。信任语料结果之前，两者都要校准。
- 编写语言探针要瞄准语言的表面：在其余为英文的 Markdown 与代码注释/JSDoc 中搜中文残留。用泛化的 ASCII 搜索在中文散文里找英文残留，在代码与标识符附近噪声太大；改为把新增散文与所属页面对照。

<a id="english-battery"></a>

## 英文题库

```sh
rg -n --hidden '\(decision \d|\(audit [A-Z]\d|design §|plan §|design ledger|\(B ruling|\bP-I\b|\bW\d\b|\bT\d\b' ...
rg -n --hidden -i '\bthis PR\b|\bthis branch\b|\bthis stack\b|\blater PRs?\b|\bprevious commits?\b|\bthis commit\b' ...
rg -n --hidden -i '\bused to\b|\bno longer\b|\bpreviously\b|\bthe old\b|\bwas renamed\b|\bwas moved\b' ...
rg -n --hidden -i '\bv1\b|this cut|\bcut \d|\btoday\b|\bfor now\b|roadmap' ...
rg -n --hidden -i 'rejected in review|review round|reviewer|as of v\d' ...
rg -n --hidden -i 'probably |should be enough|should suffice|it simply|is safe —|is safe --' ...
rg -n --hidden '§\d' ...
```

<a id="chinese-batteries"></a>

## 中文题库

```sh
# 中文散文中的变更或评审叙述。
rg -n --hidden '评审|上一?轮|旧版|老的|不再|以前|本版|遗留' --glob '*.md' ...

# 英文 Markdown 中的编写语言残留。
rg -n --hidden '设计稿|评审|上一?轮|旧版|老的|不再|以前|本版|遗留|私有|(^|[^a-zA-Z])端([^a-zA-Z]|$)' --glob '*.md' ...

# 英文代码注释与 JSDoc 中的编写语言残留。
rg -n --hidden '(^[[:space:]]*(//|/\*|\*)|//|/\*)[^\r\n]*(设计稿|评审|上一?轮|旧版|老的|不再|以前|本版|遗留|私有|端)' --glob '*.{ts,tsx,js,jsx,mjs,cjs,css}' ...
rg -n --hidden '#[^\r\n]*(设计稿|评审|上一?轮|旧版|老的|不再|以前|本版|遗留|私有|端)' --glob '*.py' ...
```

<a id="known-false-positive-families"></a>

## 已知误报族

清理期间经判断后保留；预计它们会再次出现：

- **工具性的 "used to"** —— "the key used to sign requests" 是工具用途，不是时间上的过去。时间形式在前面有主体状态（"colors used to come from…"）。
- **运行时的旧/新** —— "the old connection drains before the new one accepts" 命名的是交接期间两个存活对象，不是仓库状态。
- **流程文档中的 "This PR"** —— 讨论 PR 工作流的文档（"the PR body should…"、模板、本仓库的流程记录）可以正当地说 "PR"；禁令针对的是文档采纳某个 PR 关于代码的视角。
- **作为协议或路径段的 `v1`** —— `/v1/chat` 端点与线协议格式名是标识符，不是版本印记。
- **有已提交所有者的 `§N`** —— 外部标准（RFC 9110 §10.1.5）与拥有自身 § 编号的已提交文档仍可按章节引用。
- **对比性 "actually" 与名词 "wait"** —— 普通英文，不是含糊措辞；没有已提交行探测它们，所以只有用更宽泛的含糊措辞模式扩展题库时它们才会浮现。
- **运行时的 "today" 与录制的时间戳** —— 询问当前日期的提示词或测试用的是自然时间，不是仓库版本印记；录制的 CLI 输出保留其口吻。抵达模型或用户的措辞在任何编辑前仍遵循行为证据规则。
- **中文散文中的 本版本** —— 在带版本制品语境下是 "this release" 的正当译法；被禁的索引式写法是作为裸印记、对应 "this cut" 的 本版。
- **Alternatives considered 章节** —— Agent Note 体裁槽位中的 "rejected" 是获准的归属地，不是评审编排。
