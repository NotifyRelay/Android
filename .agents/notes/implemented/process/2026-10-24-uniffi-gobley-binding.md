# Agent Note: UniFFI 替换手写 JNA 绑定

Status: implemented

## Problem

Kotlin 侧的手写 JNA 绑定有 75 个 `nrc_` 声明、482 行，与 Rust 导出面靠人工保持同步。任何一侧漂移都不报错，只在运行时表现为类型不匹配或方法找不到。绑定层还外泄了 `Pointer` 类型，使平台代码直接持有裸指针。

## Decision

用 UniFFI 加 Gobley 生成 Kotlin 绑定，整体替换手写 JNA；`api` 层作为 `ffi` 的薄委托，不做重写式迁移。版本对齐、配置结构、源集注册与任务输入要求见 [KMP 与 Rust 绑定](../../../../Docs/KMP与绑定.md)。

## Alternatives considered

**重写式迁移，把业务逻辑一并搬到 `api` 层。** `ffi` 内部签名假设过多（22 文件、约 5090 行），整体重写会把换绑定方式与改业务实现混在一次改动里，回归时无法区分来源。

**逐条规则豁免生成代码的 ktlint 告警。** 生成器模板变化会带出新规则，逐条维护不可持续，且 ktlint 的路径过滤在 Windows 下不可靠。

**保留手写 JNA 绑定与 UniFFI 并存。** 两套绑定会各自维护一份导出面，任何一侧漂移都不报错。整体替换后 `NotifyRelayCore.kt` 与 `com.sun.jna` 在 Kotlin 侧已无引用。

## Consequences

升级 Gobley 或 UniFFI 时必须同时改 `Cargo.toml` 与生成器版本，两者不能独立演进。生成代码的 2 条 `Expression is unused` 警告来自模板，配置无法消除，接受为基线。
