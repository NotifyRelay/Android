# Agent Note: KMP 化选择 commonMain 承载业务逻辑

Status: implemented

## Problem

把模块迁到 Kotlin Multiplatform 时有两种组织方式：业务逻辑留在平台源集、只共享接口，或把业务逻辑整体上移到 commonMain、平台差异下移。前者改动小但共享不到实现，后者改动大但两个平台真正共用同一份代码。仓库要在两者之间做一次不可逆的选择。

## Decision

commonMain 承载全部业务逻辑，平台差异经 `expect`/`actual` 或端口接口下移。模块目标、已上移组件与三条硬约束见 [KMP 与 Rust 绑定](../../../../Docs/KMP与绑定.md)。

## Alternatives considered

**保持 android 单目标，只做接口收窄。** 这是 KMP 化之前的方案：只把 UI 与平台层解耦，不引入多平台构建。它解决了耦合问题，但没有解决同一份业务逻辑要在桌面端复用的需求，因此最终仍要迁到 KMP。

**用 `actual typealias` 统一 `expect class` 的实现方式。** 对无成员的 `expect class` 可行，对带成员的会直接编译失败。两种情形必须分开处理，不能为了写法统一而回避带成员的情况。

**在 KMP 模块里继续用 `jniLibs.srcDirs`。** KMP 库插件不提供该 DSL，写了也不生效，产物里不会包含 `.so`。只能用 `androidComponents.onVariants` 的静态源目录接口。

## Consequences

新增 KMP 模块时构建脚本要按 KMP 插件的实际能力写，不能照搬 Android 库模块的 DSL。跨模块暴露的类型要检查依赖声明是 `api` 还是 `implementation`，后者会让下游编译失败而报错位置在消费方。
