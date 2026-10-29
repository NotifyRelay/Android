import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

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

// SMTC Rust 桥（子模块 desktop/smtc-rust）：JNI 动态库，供桌面端读取系统媒体会话。
// 与 notify-relay-core 的 hostRustBuild 同方式，只构建 host 目标（该库仅 Windows 有实际实现）。
val smtcRustDir = project.projectDir.resolve("smtc-rust")
val smtcRustBuild =
    tasks.register<Exec>("smtcRustBuild") {
        description = "构建 SMTC Rust JNI 库（本机 host 目标，供桌面端读取系统媒体会话）"
        group = "rust"
        doFirst {
            require(smtcRustDir.exists()) { "SMTC Rust 子模块未同步，请执行 git submodule update --init" }
        }
        inputs.dir(smtcRustDir.resolve("src"))
        inputs.file(smtcRustDir.resolve("Cargo.toml"))
        inputs.file(smtcRustDir.resolve("Cargo.lock"))
        val outDir = smtcRustDir.resolve("target/release")
        outputs.dir(outDir)
        workingDir = smtcRustDir
        // crates.io 直连不可达，走 rsproxy 镜像（与本项目其他 Rust 构建一致）。
        // 镜像配置经环境变量 CARGO_NET_* 传入：命令行 --config 的引号会被 Gradle 剥掉。
        environment("CARGO_NET_OFFLINE", "false")
        environment("CARGO_REGISTRIES_CRATES_IO_PROTOCOL", "sparse")
        environment("CARGO_SOURCE_CRATES_IO_REPLACE_WITH", "rsproxy-sparse")
        environment("CARGO_SOURCE_RSPROXY_SPARSE_REGISTRY", "sparse+https://rsproxy.cn/index/")
        // 用 cargo 默认 target 目录：与仓库自身 .gitignore 及手工构建一致，避免两处产物
        commandLine(
            "cargo",
            "build",
            "--release",
        )
    }

// 该库参与 :desktop 的编译与运行：先构建，再把产物目录并入库搜索路径。
tasks.named("compileKotlinJvm") { dependsOn(smtcRustBuild) }
val smtcLibDir = smtcRustDir.resolve("target/release")

tasks.withType<JavaExec>().configureEach {
    // JNA 优先读 jna.library.path；java.library.path 对 Native.register 不生效
    // 两个 Rust 产物目录都要在搜索路径上：JNA 与 System.loadLibrary 各自按名字找
    systemProperty("jna.library.path", rustHostLibDir.absolutePath + File.pathSeparator + smtcLibDir.absolutePath)
    systemProperty("java.library.path", rustHostLibDir.absolutePath + File.pathSeparator + smtcLibDir.absolutePath)
    // 控制台输出编码：中文 Windows 默认按 GBK 编码 stdout/stderr，捕获端（Gradle/IDE）按 UTF-8 解码会乱码。
    // JDK 18+ 起 file.encoding 不再决定控制台编码，需显式指定 stdout.encoding / stderr.encoding。
    systemProperty("stdout.encoding", "UTF-8")
    systemProperty("stderr.encoding", "UTF-8")
}

tasks.withType<Test>().configureEach {
    systemProperty("jna.library.path", rustHostLibDir.absolutePath + File.pathSeparator + smtcLibDir.absolutePath)
    systemProperty("java.library.path", rustHostLibDir.absolutePath + File.pathSeparator + smtcLibDir.absolutePath)
    systemProperty("stdout.encoding", "UTF-8")
    systemProperty("stderr.encoding", "UTF-8")
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
