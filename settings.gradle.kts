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
include(":core")
include(":base")
include(":checkupdata")

include(":superislandui")
include(":scrcpy")
include(":nativecore")
// LSPosed 模块（启用超级岛鉴权绕过）已拆分为独立仓库 NotifyRelay-LSP 与独立 APK，
// 不再作为主应用模块参与构建；主应用重装不再触发其热重载。
// 仓库：https://github.com/NotifyRelay/NotifyRelay-LSP
