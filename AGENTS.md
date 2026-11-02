# AGENTS.md

NotifyRelay 是跨设备通知与状态同步方案：局域网内直连配对，双向同步通知、剪贴板、媒体与音频，数据不经公网服务器。本仓库 `Android/` 同时产出 Android 应用与 JVM 桌面端（`:desktop`）；Windows 端已归档，源码保留在工作区 `Windows/` 作参考。Jetpack Compose + Miuix UI；注释与提交信息均为中文。

本文件是本仓库对 agent 的常驻指令：只放**每个会话都需要在上下文里的命令**，每条一到三行，并链接它的正家。故事、完整示例、场景化流程不放这里。

## 分层

指令分三层，越具体越靠近代码。

| 层 | 位置 | 放什么 |
|---|---|---|
| 常驻指令 | 本文件与各子树 `AGENTS.md` | 每条会话都必须遵守的命令，一句话一条，链接理由 |
| 技能 | [.agents/skills/](.agents/skills/) | 按场景加载的工作流与判断标准，不常驻上下文 |
| 文档 | [Docs/](Docs/README.md) | 当前状态的可检索事实，按读者与层级拆分 |

一个事实只有一个家。别处只准链接，不准复述。

## 命令

```sh
pnpm install           # 安装门禁依赖
pnpm run verify-all    # 全部门禁
pnpm run test:docs     # 快速文档门禁
./gradlew ktlintFormat # Kotlin 代码格式化
```

## 仓库关键事实

**双端产物**：`:app` 是 Android 应用（applicationId `com.xzyht.notifyrelay`）；`:desktop` 是 JVM 桌面端，复用 `:shared` 界面与各端口，含自绘窗口、托盘、系统媒体中心与叠加层。两端共用 `:shared`、`:domain`、`:data`、`:base` 与 Rust 核心。

**模块**：`:core` 的 Gradle 名与源码目录不同名（`nativecore/`，由 `settings.gradle.kts` 重定向）；`:checkupdata` 与 `:scrcpy` 是 git 子模块，在各自仓库修改。清单、依赖与各模块文件用途见[文件用途基础说明](Docs/文件用途基础说明.md)。

**工具链**：AGP 9.4.0、Kotlin 2.4.20、compileSdk 与 targetSdk 37。`:app` minSdk 31，其余库模块 29（`libs.versions.minSdk`），`:scrcpy` 26。不要为低于模块声明 minSdk 的版本写兼容代码。代码风格遵循 `kotlin.code.style=official`。

**构建前提**：Gradle 需写 `C:\Users\WINDOWS\.gradle`，`workspace-write` 沙箱下会被拒；原生产物约 17GB，多工作树用目录联接复用且构建全局串行。命令与警告基线见[构建与验证](Docs/构建与验证.md)。

**Git**：`dev` 是开发主线，`main` 是发布来源，合并推荐 `--no-ff`。钩子需每个克隆手动启用：`git config core.hooksPath .githooks`。

## 不可协商的规则

- **文档写当前状态。** 历史属于 Git 提交、Agent Note 与事故复盘；散文只描述现有机制。新增或修改任何文档前先读[文档标准](Docs/AGENTS.md)与[散文标准](.agents/skills/notifyrelay-prose-standard/SKILL.md)。
- **每段一个物理行。** 用编辑器软换行；`verify-md-wrap` 拒绝硬折行的段落。
- **散文写完整契约，不写思考记录。** 保留行为、失败、时序、归属、情态、例外与后果；删除过程叙述、测试走查与代码复述。
- **决策留痕。** 影响长期维护的判断写入 [Agent Note](.agents/notes/README.md)；纯机械改动与局部界面调整豁免。
- **规则必须可执行。** 新增一条被评审强制的规则时，同时给出它能变红的门禁；只靠人记的规则会被忘掉。
- **改代码就改它拥有的文档。** 配置、默认值、错误、对外行为在同一变更里更新对应 README 与 JSDoc。
- **注释说明本地契约。** 不解释远处行为，不复述代码已经表达的结论。
- **共享状态必须有归属者。** 模块级缓存、单例、全局注册表都要挂到服务、实例或显式作用域上。
- **失败要结构化。** 定义失败类型、处理归属、遏制方式与失败后仍然成立的保证；只打日志不算恢复。
- **先查已有实现。** 使用工具方法前先读[文件用途基础说明](Docs/文件用途基础说明.md)对应模块；已有能力优先复用或就地扩展，不新建近似方法。
- **Compose 组件优先用 Miuix。** 用法查 `miuix-mcp`，不抓官方文档网页；导航用 Miuix Navigation3 + NavigationEvent。
- **本应用不上架应用商店。** 仅私有分发与自用，没有对公网提供服务的计划。

## 正家

| 主题 | 正家 |
|---|---|
| 模块清单、依赖关系、各模块包根与类职责 | [文件用途基础说明](Docs/文件用途基础说明.md) |
| UI 与交互约定、API 版本边界、Git 工作流 | [开发约定](Docs/开发约定.md) |
| 单例归属、仓储入口、状态机边界、异常处理、端口注入 | [模块结构](Docs/模块结构.md) |
| 构建环境、警告基线、干净警告命令、工作树与子模块 | [构建与验证](Docs/构建与验证.md) |
| KMP 源集组织与硬约束、UniFFI 配置与 Rust 导出 | [KMP 与 Rust 绑定](Docs/KMP与绑定.md) |
| 桌面端窗口与托盘、主题、配对、电量、打包、叠加层 | [桌面端](Docs/桌面端.md) |
| 岛通知接入、模板与实时更新 | [超级岛](Docs/超级岛.md) |
| 评审强制的缺陷类别 | [缺陷类别规则](Docs/缺陷类别规则.md) |
| 测试层级与证据选择 | [测试策略](Docs/测试策略.md) |
| 决策记录：为什么、放弃了什么、替代方案 | [Agent Note](.agents/notes/README.md) |

## 目录

```
.agents/
  notes/      决策记录：为什么、放弃了什么、怎么验证
  skills/     按场景加载的工作流
Docs/
  AGENTS.md              文档标准（层级、预算、写作规则）
  模块/                  各模块的文件用途与类职责
scripts/                 文档门禁实现
```

## 校验

提交前按改动面选择最小充分证据，不要反射性地跑全量。CI 拥有穷尽矩阵。

| 改动 | 最小证据 |
|---|---|
| 文档、Agent Note、目录页 | `pnpm run test:docs` |
| Kotlin 源码 | 对应模块 `compileDebugKotlin` 加相关单元测试 |
| 门禁脚本本身 | 该脚本的单元测试，加上一次故意违规的负向验证 |
| 指令文件（`AGENTS.md`、技能） | `pnpm run verify-all` |

**新增门禁必须自证**：写一个非法用例，看它变红，再回退。只证明它能通过，等于没证明它有用。
