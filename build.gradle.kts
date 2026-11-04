// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ktlint) apply false
    // 固定 Kotlin Gradle 插件版本为 2.4.20，以读取 miuix (Kotlin 2.4.20) 编译的元数据。
    // 原先写在 buildscript { classpath(...) } 里，但 buildscript 块中拿不到版本目录，
    // 改用 plugins 块（apply false 只把 KGP 放进构建脚本 classpath，不会应用插件），效果等价。
    alias(libs.plugins.kotlin.android) apply false
    // KMP 公共端（:shared）
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    base
}

// 对除 git 子模块（checkupdata/scrcpy）外的所有项目应用 ktlint；
// 子模块代码在各自仓库维护，本仓库不得格式化或提交其内容。
apply(plugin = "org.jlleitschuh.gradle.ktlint")

subprojects {
    if (name != "checkupdata" && name != "scrcpy") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
    }
}

// Guard: ensure we don't create kotlinLSPProjectDeps task multiple times.
// Some external init-scripts (used by Kotlin Language Server) may add the
// same task concurrently which causes a "task already exists" failure.
// Only register the task when it does not already exist.
if (tasks.findByName("kotlinLSPProjectDeps") == null) {
    tasks.register("kotlinLSPProjectDeps")
}

// Compatibility shim for Kotlin Language Server init scripts:
// Some LSP init scripts expect an 'androidCompileClasspath' configuration to exist
// in Android projects. Newer Android Gradle Plugin versions or some module types
// may not expose that configuration, causing the LSP script task to fail.
// Create a no-op configuration with that name on projects that don't have it so
// the dependency-collection task can safely query it.
gradle.projectsEvaluated {
    rootProject.allprojects.forEach { p ->
        try {
            if (p.configurations.findByName("androidCompileClasspath") == null) {
                p.configurations.create("androidCompileClasspath")
            }
        } catch (e: Exception) {
            // best-effort: ignore failures creating the shim
            p.logger.debug("Skip androidCompileClasspath shim for ${p.path}", e)
        }
    }
}

// 文档系统门禁：经 pnpm 运行 scripts/verify-all.ts。
// 首次运行前需要 `pnpm install`（Windows 下 Gradle 调用 pnpm.cmd，其余平台调用 pnpm）。
val verifyDocs =
    tasks.register<Exec>("verifyDocs") {
        group = "verification"
        description = "运行文档系统全部门禁（pnpm run verify-all）"
        executable = if (System.getProperty("os.name").contains("windows", ignoreCase = true)) "pnpm.cmd" else "pnpm"
        args("run", "verify-all")
        inputs.files(
            fileTree("Docs"),
            fileTree(".agents"),
            fileTree("scripts"),
            "AGENTS.md",
            "package.json",
            "pnpm-lock.yaml",
            "pnpm-workspace.yaml",
        )
        // 重复代码门禁的语料是全部 Kotlin、Rust 与 TypeScript 源码，必须一并声明为 inputs，
        // 否则只改源码时本任务被判为最新而跳过，门禁不会运行。
        inputs.files(
            listOf(
                "app",
                "base",
                "data",
                "desktop",
                "domain",
                "nativecore",
                "overlay-api",
                "shared",
                "superislandui",
                "scrcpy",
                "checkupdata",
            ).map { module ->
                fileTree(module) {
                    include("**/*.kt", "**/*.kts", "**/*.rs", "**/*.ts")
                    exclude("**/build/**", "**/target/**", "**/node_modules/**")
                }
            },
            "build.gradle.kts",
            "settings.gradle.kts",
        )
    }

// `./gradlew check` 聚合全部验证：文档门禁与各模块 check 一起执行。
tasks.named("check") {
    dependsOn(verifyDocs)
}
