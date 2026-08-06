# 修复计划 E：NativeCore 原生层

> 来源：[PR #45](https://github.com/NotifyRelay/Android/pull/45) CodeRabbit 评审
> 生成日期：2026-08-06

## 概述

- 评论数：2（2 Major）
- 涉及文件（1 个，与其他计划无重叠，可并行执行）：
  - `nativecore/src/main/java/com/xzyht/notifyrelay/nativecore/NativeCore.kt`

## 执行前置

- 按 AGENTS.md 要求，修改前先迁出 `agent_{原分支}_{nativecore修复}` 分支。
- 本计划仅修改 JNI 包装层 `NativeCore.kt`，不涉及 notify-relay-core Rust 子模块源码，无需走子模块提交流程。
- 验证：修改完成后运行 Android 模块构建（如 `./gradlew :app:assembleDebug`），确认编译通过。

---

## 修复项

### E1. [Major] NativeCore.kt:377-398 — destroyContext 清空音频回调引用

- **问题**：`destroyContext` 未清空 `audioDataCallbackRef` 和 `audioEventCallbackRef`，上下文重建后 `setupAudioCallbacks` 会因旧引用提前返回，无法为新 Rust 上下文重新注册音频回调。
- **修复建议**：在 `destroyContext` 中清空这两个引用，确保重建后能重新注册。

### E2. [Major] NativeCore.kt:281-285 — pushSuperislandState / pushMediaState 判空

- **问题**：调用对应 native 函数前未检查可空的 `ctx`，上下文为空时可能崩溃。
- **修复建议**：仅当 `ctx` 非空时执行调用，保持现有参数转换和返回行为，与剪贴板接口的判空模式一致。

---

## 执行顺序建议

按 E1 → E2 顺序修改（同一文件，先清引用再补判空，修改后整体构建验证）。
