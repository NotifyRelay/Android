# KMP 与 Rust 绑定

本文是 Kotlin Multiplatform 写侧组织与 UniFFI 绑定链路的当前契约。模块结构见[模块结构](模块结构.md)，构建命令与环境见[构建与验证](构建与验证.md)。

<a id="kmp-targets"></a>

## 模块目标

`:shared`、`:data`、`:superislandui`、`:checkupdata`、`:core` 均为 KMP 模块。`:checkupdata` 为 android 与 jvm 双目标，其余为 android 单目标。

<a id="kmp-source-layout"></a>

## 源集组织

commonMain 承载全部业务逻辑，平台差异经 `expect`/`actual` 或端口接口下移。已上移的组件：`:core` 的 `NativeCore`；`:superislandui` 的 `BitmapUtils`、`CapsuleScrollManager`、`TextSplitter`、`SuperIslandData`。

<a id="kmp-constraints"></a>

## 三条硬约束

带成员的 `expect class` 不能用 `actual typealias` 实现，编译器报 `has no corresponding members`；无成员的才可以。需要成员时改用端口接口加平台同名门面。

KMP 库插件没有 `preBuild` 与 `jniLibs` DSL。前者挂到 `compileAndroidMain` 或 `androidPreBuild`；后者用 `androidComponents.onVariants { sources.jniLibs?.addStaticSourceDirectory(...) }`。

公开签名引用某依赖的类型时，该依赖必须用 `api` 声明。`:checkupdata` 的 kotlinx-serialization、`:core` 的 atomicfu 与 JNA 都属此列。

<a id="uniffi-versions"></a>

## UniFFI 版本

`core/Cargo.toml` 的 `uniffi` 与生成器内置的 `uniffi_bindgen` 必须精确同版本。当前 Gobley 0.3.7 对应 `uniffi = "=0.29.4"`。

<a id="uniffi-config"></a>

## `uniffi.toml`

键写在顶层根表：`package_name`、`cdylib_name`、`kotlin_multiplatform`、`kotlin_targets`。Gobley 的 `new_config` 对根表做扁平反序列化，不读 `[bindings.kotlin]` 子表。

`kotlin_multiplatform = false` 输出 `main/kotlin`，无 `expect` 声明；为 `true` 输出 `commonMain`（`expect`）加目标源集（`actual`）。

<a id="uniffi-source-registration"></a>

## 源集注册

生成物用 `kotlin.directories +=` 注册到 Android 源集的 Kotlin 目录。`java.srcDir` 不参与 Kotlin 编译，Kotlin 扩展的 `sourceSets` 也没有 `main`。

生成物目录整体豁免 ktlint：`.editorconfig` 的 `[**/generated/uniffi/**/*.{kt,kts}]` 配 `ktlint = disabled`。

生成代码另需 `androidx.annotation`（`AndroidSystemCleaner` 用 `@RequiresApi`）与 `kotlinx.atomicfu`（`UniffiHandleMap` 计数器）。

<a id="uniffi-gradle"></a>

## Gradle 任务

`:core:uniffiBindgen` 挂在 `preBuild`，生成物写入 `nativecore/build/generated/uniffi`，不入版本库。任务必须把 `.so` 声明为 `inputs.file`，否则 Rust 导出面变化不会让任务 out-of-date。

<a id="uniffi-rust-side"></a>

## Rust 侧

`src/api/` 是既有 `ffi` 实现的薄委托，只做类型转换，业务逻辑与返回语义留在 `ffi` 内。

11 个裸函数指针回调合并为 `CoreCallbacks` trait，用 `extern "C"` 蹦床装入既有 `router` 与 `audio_state` 字段。

`uniffi::export(callback_interface)` 的注册参数用 `Box<dyn Trait>`，不用 `Arc<dyn Trait>`；0.29 未为 `Arc<dyn T>` 实现 `FfiConverterArc`。

同一类型可分多个 `#[uniffi::export] impl` 块。

`Core::raw_context_ptr()` 暴露上下文地址，供平台侧过渡期复用同一实例。

日志复用既有桥：`log::set_logger` 只能成功一次且已被 `ffi::common::init_log_bridge` 占用，本层用 `set_log_callback_internal` 把既有桥的回调指针指向转发函数。

生成代码的 2 条 `Expression is unused` 警告来自模板的 `IntegrityCheckingUniffiLib` 裸表达式，配置无法消除。
