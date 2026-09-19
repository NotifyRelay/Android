# 记忆文件 
记忆文件位于./memory
00main.md为主记忆，其他的带日期的为次记忆|
信任记忆文件，不要主动验证
## 每次会话开始时（上班打卡）
1. 读 00mian.md 和近期日期的md了解状态

## 会话中
1. 向日期.md记录普通问题

## 每次会话结束前（下班打卡）
1. 更新 00mian.md （如有重要记忆时，没有时仅更新普通记忆文件）


# ai的agent要求

- 尽量最小化改动以避免无法预料的错误
- 回复时使用中文

## UI与交互约定

- 所有 Compose 组件优先使用 Miuix 主题库（如 `MiuixTheme`、`MiuixIcons`、`Button`、`Card` 等）。查阅 Miuix 用法一律通过 `miuix-mcp`，不要去抓取官方文档网页。
- 导航使用 Miuix Navigation3 + NavigationEvent。
- 页面根容器默认背景统一使用 `MiuixTheme.colorScheme.background`：内容区背景一律用 `background`，TopAppBar 可保留默认 `surface` 形成色差。
- 独立页面（含子页、开发者模式等）优先复用公共组件 `ScrollableTopAppBarPage`。

### 应用 API 版本

minSdk：`:app`（主应用）minSdk = 31（Android 12），其余库模块（`:base`、`:data`、`:superislandui`、`:core`、`:nativecore` 目录等）minSdk = 29（Android 10）；`:scrcpy` minSdk = 26（子模块自带配置）。请勿为任一模块声明的 minSdk 以下版本编写兼容性代码。

> 模块命名：`:core` 现指 Android 原生核心（Rust FFI，源码目录 `nativecore/`，内含 `notify-relay-core` 子模块）；
> 原先占用该名的 `notifyrelay.core` 工具库已并入 `:base`，包名为 `notifyrelay.base.util[.image]`。

- 代码风格遵循 Kotlin 官方规范（`kotlin.code.style=official`）。
- 如需扩展功能或集成新依赖，优先查阅 `miuix-mcp` 与本项目现有实现。
  本应用不会上架 Google Play 等应用商店，仅限私有分发和自用，且没有对公网提供服务的计划。

### 模块结构与文件用途

模块划分、目录树、各模块类与方法的用途，统一以 **[`Docs/文件用途基础说明.md`](Docs/文件用途基础说明.md)** 为准，本文件不再单独列举。

在使用工具方法前，请先查阅该文档对应模块的说明，确认是否已有可用实现。

如果已有类似功能的方法，请优先使用现有方法，避免重复实现。如果没有合适的方法，可以根据项目的代码风格和规范自行实现新的工具方法。注意，新方法如果仅是对旧方法的拓展，请在旧方法的基础上进行修改，而不是新建一个类似的方法。

当模块结构、目录或文件用途发生变化时，同步更新 `Docs/文件用途基础说明.md`。

### Git 分支与合并策略

- 功能开发在独立分支进行，合并到 `main` 时推荐使用非快进合并 (`--no-ff`) 以保留分支提交记录。
- 当前长期 `dev` 分支 为开发主线，`main` 分支为发布来源。

### Git 钩子（必须启用）


**启用（每个克隆只需执行一次；Git 不允许钩子路径随仓库自动生效）：**

```bash
git config core.hooksPath .githooks
```

未启用时钩子不会运行——换机器、重新克隆或新增协作者后务必重新执行。可用以下命令确认：

```bash
git config core.hooksPath   # 应输出 .githooks
```


