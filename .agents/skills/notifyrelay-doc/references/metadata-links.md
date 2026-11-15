<a id="metadata-links"></a>

# 元数据与链接

<a id="summary"></a>

## 摘要

README 元数据是检索与模板选择的接口，不是缩微报告或广告。frontmatter 不携带文件名约定或已执行门禁已经拥有的字段。链接语法必须在 GitHub 上正确渲染，因此仓库链接保持渲染器可解析的相对 URL。

<a id="table-of-contents"></a>

## 目录

- [README 元数据](#readme-metadata)
- [description 质量](#description-quality)
- [仓库链接与路径提及](#repository-links-and-path-mentions)
- [Dev Note](#dev-note)

<a id="readme-metadata"></a>

## README 元数据

每份手写 README 都以 YAML frontmatter 开头。允许自定义字段，但公共字段要稳定到足以支持搜索与索引。

```yaml
---
description: "该页覆盖什么、读者何时应打开它。"
---
```

README 必须提供 `description`。页面标题与包 manifest 已经拥有名称，文档职责及其读者路径已经表达受众；在 frontmatter 中重复任一者都不增加检索价值。在仓库自有的分类法与检索消费方能证明其超出 description 与全文搜索的价值之前，不要添加 `tags`。键保持小写并带连字符，除非既有所有者定义了另一种拼写；不要把易变的代码清单抄进 frontmatter。

<a id="description-quality"></a>

## description 质量

agent 会搜索 frontmatter 的 `description` 值，在加载完整文档前先筛选页面。像写技能 description 那样写每个值：说明页面覆盖什么、读者何时应打开它。用一到两个具体句子，包含可搜索的领域术语，并把本页与邻近所有者区分开。不要概括每一节、声称更优、重复标题、含糊自夸、保留变更历史或写技术状态报告。

好：`The shipped JSONL session-persistence backend for deployments and maintainers choosing, configuring, or debugging per-session durable logs with optional Zstandard compression.`

弱：`The best and most advanced session storage implementation with lots of optimizations.`

<a id="repository-links-and-path-mentions"></a>

## 仓库链接与路径提及

让链接目标可被机器检查，让路径提及相对于上下文。当前页的菜单使用仅片段的链接。外部资源使用完整 URL。

理想的内部链接模型从仓库根命名目标，但以 `/docs/...` 开头的 Markdown URL 在 GitHub 上会解析到仓库之外，并被 `verify-md-links` 跳过。在仓库自有的解析器能在所有渲染器中支持根路径之前，Markdown 链接使用当前渲染器可解析的相对 URL，并把 `docs/` 这类逻辑路径提及写成相对于讨论上下文的形式。绝不要仅为看起来像绝对路径就采用未经验证的前导斜杠链接。

<a id="dev-note"></a>

## Dev Note

None.
