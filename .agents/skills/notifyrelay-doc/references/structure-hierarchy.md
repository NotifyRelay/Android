<a id="page-structure-and-hierarchy"></a>

# 页面结构与层级

<a id="summary"></a>

## 摘要

每页在暴露操作或实现深度之前，先给新人一道简短的门。跨包的学习与工程材料放在有意设计的 `docs/` 层级下，而包契约留在代码旁。小规则文件各自拥有一项可独立检索的要求，但任意碎片化不是目标。末尾的 Dev Note 把活跃的工作上下文与其上方稳定的解释隔离开。

<a id="table-of-contents"></a>

## 目录

- [页面顺序](#page-order)
- [章节推进](#section-progression)
- [文档层级](#documentation-hierarchy)
- [小规则文件](#small-rule-files)
- [Further Exploration](#further-exploration)
- [Dev Note 归属](#dev-note-ownership)
- [Dev Note](#dev-note)

<a id="page-order"></a>

## 页面顺序

格式所有者允许时，手写的面向人页面按此顺序排列。生成的产物可以生成同样的入口章节，而 Agent Note 与事故复盘保留其仓库定义的骨架。

1. YAML 元数据。
2. H1 标题。
3. 页面之间的导航条。
4. `## Summary`：三到五句解释性文字，说明读者能做什么或观察到什么、为何在意、主要运行模型，以及最重要的边界。英文包 README 的 Summary 保持在门禁规定的 100 词以内。
5. `## Table of Contents`：指向本页各 H2 的链接；保持导航性而非描述性。
6. 稳定内容，按从面向用户的使用到面向开发者的设计与操作细节排序。
7. 可选的 `## Further Exploration`，给出面向新人的相邻主题链接。
8. 末尾的 `## Dev Note`，用于非权威的活跃工作上下文。

不要为机器自有小文件、生成片段，或由已执行解析器定义另一种头部的格式强加 Summary 与 Table of Contents 包装。把例外写进格式所有者，而不是产出非法输出。包 README 门禁要求 `Model Experience` 与 `Known Limitations and Deferred Work` 作为最后两个 H2：把 `Further Exploration` 放在它们之前，并以 limitations 那个 H2 内部的 `### Dev Note` 结尾。

<a id="section-progression"></a>

## 章节推进

每个有实质内容的 H2 都在表格、代码或 H3 子节之前，先用一段简短的定向段落开场。说明该节的主题与决策相关要点；不要重复其完整内容。

页内按读者深度排列内容：

1. 基础使用：何时选用该特性、必需输入、最短安全示例、可观察的成功与可能的恢复。
2. 进阶使用：配置选择、限制、操作与集成行为。
3. 开发者细节：归属、生命周期、数据模型、失败、性能与值得维护的扩展点。

把厚重的开发者细节与末尾的 Dev Note 折进 `<details>` 块，并保持章节标题可见（机制见 [style.md](style.md)）。

折叠的开发者细节按需求是概念层面的：总体设计概念、主要组件的架构与粗线条数据流——足以理解该包如何工作——加上源码映射表与指向代码的链接以取精确细节。它绝不能变成穷尽清单：不放完整 API 盘点、列清单、事件载荷枚举或 JSDoc 复述。Dev Note 是唯一可以存放半成品想法、草稿与未定方向的地方；其余一切（包括折叠块）都是打磨过的、描述当前状态的散文。

把穷尽的生成类型、schema 或清单留在紧凑的入口段落与稳定索引之后。一次查找需要扫过无关分组时，按既有领域所有者拆分它们。

<a id="documentation-hierarchy"></a>

## 文档层级

包契约用包内 README，并放在源码旁边。跨包 Markdown 按受众与学习意图组织，而不是把无关页面平摊在 `docs/` 下。

```text
docs/
  learn/
    overview/
    cordis/
    practices/
  user/
  developer/
    discussion/
  scratch/
  subsystems/
```

把这张表当作目标地图，而非随手大规模搬迁的许可。一次搬一个连贯主题，原子地修好所有入站链接与网站映射，保留公开路由或别名，并让 `subsystems/` 保持扁平，因为其页面在逻辑上平行。

`docs/scratch/` 存放受版本跟踪、会过期但仍须跨越交接存活的讨论。每个 scratch 页面写明其所有者、创建日期、过期时间与晋升目标。一次性的本地笔记依旧被忽略且不提交。

<a id="small-rule-files"></a>

## 小规则文件

一项可独立检索的规则、实践、示例族或决策，在拥有自己的所有者、变更节奏、入站链接或校验时，给它一个独立小文件。相关的文件归入描述性层级，例如 `docs/developer/code-quality/`。拆分会导致读者为理解一项义务而必须打开多个文件时，就让强耦合的规则留在一起。

索引页用三到五句解释该文件夹，并按用途链接其直接子项。它不复述每个子项的规则。

<a id="further-exploration"></a>

## Further Exploration

这一可选节面向读完本页、想获得相邻理解的新人。链接三到七个直接相关的页面，从最近的前置到更深的探索排序，并用一个短句说明每个页面补充了什么。不要把它变成完整的站点索引。

<a id="dev-note-ownership"></a>

## Dev Note 归属

手写页面以 Dev Note 结尾，但要明确它是非权威的。活跃假设、兼容性顾虑、粗略备选方案、进度指针与未解问题可以放在那里；稳定的行为、必需的限制与被接受的决策理由属于其普通所有者。

Dev Note 可以镜像或链接任务进度，但不得变成第二个可写的队列。工作收尾时，把持久结论晋升、把可复用的理由移到 Agent Note、把事故时间线留在事故复盘里，并删除已解决的闲聊。Git 历史保留旧迭代。

<a id="dev-note"></a>

## Dev Note

这个必需的末节刻意是手写页面中最不精炼的部分，但它仍有生命周期纪律。空的 Dev Note 应写 `None.`，而不是堆砌占位散文；生成格式与解析器自有的格式可以通过点名的例外省略它。
