pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NotifyRelay"
include(":app")
include(":data")
include(":base")
include(":checkupdata")

include(":superislandui")
include(":scrcpy")
// Rust 核心（JNA 绑定 + `notify-relay-core` 子模块）占用 `:core` 模块名；
// 源码目录仍为 nativecore/，子模块路径不变，仅做 Gradle 模块名映射。
// 原 `:core` 工具库（notifyrelay.core）已整体并入 `:base`（notifyrelay.base.util）。
include(":core")
project(":core").projectDir = file("nativecore")
// LSPosed 模块（启用超级岛鉴权绕过）已拆分为独立仓库 NotifyRelay-LSP 与独立 APK，
// 不再作为主应用模块参与构建；主应用重装不再触发其热重载。
// 仓库：https://github.com/NotifyRelay/NotifyRelay-LSP
