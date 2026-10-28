import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm {
        // Compose Desktop 需要 JVM 11+
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":domain"))
            implementation(project(":data"))
            implementation(project(":core"))
            implementation(project(":base"))

            // 与 :shared 一致的 UI 组件库（shared 侧为 implementation，不传递）
            implementation(libs.miuix.ui.kmp)
            implementation(libs.miuix.preference.kmp)
            implementation(libs.miuix.nav)
            implementation(libs.miuix.icons)

            implementation(compose.desktop.currentOs)
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel.compose.kmp)
            implementation(libs.androidx.paging.compose)
            implementation(libs.androidx.paging.common)

            // Compose Desktop 需要 Main 调度器（Swing EDT）
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

// Rust 动态库（:core 的 hostRustBuild 产物）经 JNA 加载：
// 把其所在目录加入 jvm 运行与测试的库搜索路径。
val rustHostLibDir = rootProject.projectDir.resolve("nativecore/notify-relay-core/build/target/release")

tasks.withType<JavaExec>().configureEach {
    // JNA 优先读 jna.library.path；java.library.path 对 Native.register 不生效
    systemProperty("jna.library.path", rustHostLibDir.absolutePath)
    systemProperty("java.library.path", rustHostLibDir.absolutePath)
}

tasks.withType<Test>().configureEach {
    systemProperty("jna.library.path", rustHostLibDir.absolutePath)
    systemProperty("java.library.path", rustHostLibDir.absolutePath)
}

compose.desktop {
    application {
        mainClass = "notifyrelay.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "NotifyRelay"
            packageVersion = "1.0.0"
        }
    }
}
