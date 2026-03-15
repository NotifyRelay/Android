# Copilot Instructions

## ai的agent要求

- 要求修改时直接修改不二次征求同意
- 尽量最小化改动以避免无法预料的错误
- 回复时使用中文
## UI与交互约定

- 所有 Compose 组件优先使用 Miuix 主题库（如 `MiuixTheme`、`MiuixIcons`、`Button`、`Card` 等），详见[官方组件文档](https://miuix-kotlin-multiplatform.github.io/miuix/zh_CN/components/)。

### 应用 API 版本

不兼容api 29以下版本,请勿使用相应的兼容性代码

- 代码风格遵循 Kotlin 官方规范，使用 Ktlint 进行格式化。
- 如需扩展功能或集成新依赖，优先查阅 Miuix 官方文档与本项目现有实现。
  本应用不会上架 Google Play等应用商店，仅限私有分发和自用,且没有对公网提供服务的计划。

### 应用工具方法规范

在使用工具方法前，请先查看以下工具包中的实现：
- `app\src\main\java\com\xzyht\notifyrelay\util` (通用工具类)
- `app\src\main\java\com\xzyht\notifyrelay\feature\notification\superisland\common` (浮岛通用工具)
- `app\src\main\java\com\xzyht\notifyrelay\feature\notification\superisland\floating\common` (浮岛视图工具)
- `app\src\main\java\com\xzyht\notifyrelay\ui\common` (UI通用工具)
- `app\src\main\java\com\xzyht\notifyrelay\servers` (服务层工具)

如果已有类似功能的方法，请优先使用现有方法，避免重复实现。如果没有合适的方法，可以根据项目的代码风格和规范自行实现新的工具方法。注意,新方法如果仅是对旧方法的拓展,请在旧方法的基础上进行修改,而不是新建一个类似的方法。

## 应用版本号规则:

版本号格式为 `主版本号.次版本号.修订号`，如 `0.190.01181332`。

- 主版本号   （0）表示重大更新或架构变更，
- 次版本号  （190）main的主线提交数，
- 修订号    （01181332）当前时间或时间加release

dev合并会main时使用非快进合并以保留dev分支的提交记录

