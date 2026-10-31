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
            // 叠加层扩展点契约（数据模型与能力接口，无实现）
            implementation(project(":overlay-api"))

            // 叠加层扩展点的 JSON 持久化（映射规则、余额历史）
            implementation(libs.kotlinx.serialization.json)
            // 超级岛公共 UI（含媒体浮窗 MediaIslandCompose）
            implementation(project(":superislandui"))

            // 原生托盘与菜单（ComposeNativeTray，独立版本线）
            implementation(libs.compose.native.tray)
            // 单实例、系统深色模式、开机自启与系统媒体中心
            implementation(libs.nucleus.core.runtime)
            implementation(libs.nucleus.darkmode.detector)
            implementation(libs.nucleus.autolaunch)
            implementation(libs.nucleus.media.control)

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

            // Win32 COM 封装：现代文件对话框（IFileOpenDialog）经 JNA 调用
            implementation(libs.jna.platform)
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

// 罗技电池 Rust 库（子模块 desktop/logi-battery）：C ABI 动态库，供桌面端枚举罗技设备电量。
// 与 smtc-rust 同方式，只构建 host 目标（HID++ 实现仅 Windows 有实际后端）。
val logiBatteryDir = project.projectDir.resolve("logi-battery")
val logiBatteryBuild =
    tasks.register<Exec>("logiBatteryBuild") {
        description = "构建罗技电池 Rust 库（本机 host 目标，供桌面端枚举设备电量）"
        group = "rust"
        doFirst {
            require(logiBatteryDir.exists()) { "罗技电池 Rust 子模块未同步，请执行 git submodule update --init" }
        }
        inputs.dir(logiBatteryDir.resolve("src"))
        inputs.dir(logiBatteryDir.resolve("crates"))
        inputs.file(logiBatteryDir.resolve("Cargo.toml"))
        inputs.file(logiBatteryDir.resolve("Cargo.lock"))
        val outDir = logiBatteryDir.resolve("target/release")
        outputs.dir(outDir)
        workingDir = logiBatteryDir
        // crates.io 直连不可达，走 rsproxy 镜像（与本项目其他 Rust 构建一致）。
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
tasks.named("compileKotlinJvm") { dependsOn(logiBatteryBuild) }
val logiBatteryLibDir = logiBatteryDir.resolve("target/release")

// ===== 可分发包（createDistributable / runDistributable）的原生库 =====
// 三个 Rust 动态库不随 jar 分发：复制进应用镜像的 app/resources，
// 启动参数再把 JNA 与 System.loadLibrary 的搜索路径指向该目录。
val distributableAppResources = layout.buildDirectory.dir("distributable/appResources")

val prepareDistributableNatives =
    tasks.register<Sync>("prepareDistributableNatives") {
        description = "把 Rust 动态库收集到可分发包的资源目录"
        group = "distribution"
        into(distributableAppResources.map { it.dir("common") })
        dependsOn(":core:hostRustBuild")
        dependsOn(smtcRustBuild)
        dependsOn(logiBatteryBuild)
        from(rustHostLibDir) { include("*.dll") }
        from(smtcLibDir) { include("*.dll") }
        from(logiBatteryLibDir) { include("*.dll") }
    }

tasks
    .matching {
        it.name.startsWith("createDistributable") ||
            it.name.startsWith("package") ||
            it.name == "runDistributable" ||
            it.name == "suggestRuntimeModules" ||
            it.name == "prepareAppResources"
    }.configureEach { dependsOn(prepareDistributableNatives) }

tasks
    .matching {
        it.name.startsWith("createDistributable") || it.name.startsWith("package")
    }.configureEach { dependsOn(generateIconIco) }

// ===== 应用图标 =====
// 图标源是矢量文件 src/jvmMain/resources/app_icon.svg：运行期按需要尺寸即时栅格化，
// 打包期由 IconTool 生成 Windows .ico，不维护多档位像素图。
val iconSvgFile = rootProject.projectDir.resolve("shared/src/commonMain/composeResources/drawable/app_icon.svg")
val iconIcoFile = layout.buildDirectory.file("icon.ico")
val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")

val generateIconIco =
    tasks.register<JavaExec>("generateIconIco") {
        description = "由矢量图标生成 Windows 图标文件"
        group = "build"
        dependsOn("jvmJar")
        classpath = jvmMainCompilation.output.allOutputs + jvmMainCompilation.runtimeDependencyFiles
        mainClass.set("notifyrelay.desktop.tool.IconTool")
        inputs.file(iconSvgFile)
        outputs.file(iconIcoFile)
        doFirst {
            args(iconSvgFile.absolutePath, iconIcoFile.get().asFile.absolutePath)
        }
    }

// 原生库经 System.loadLibrary 载入，JDK 24+ 默认对未具名模块的原生访问告警；
// 显式放行以消除 "A restricted method in java.lang.System has been called" 警告。
val nativeAccessArgs = listOf("--enable-native-access=ALL-UNNAMED")

// 全部原生库产物目录：JNA 与 System.loadLibrary 各自按名字查找，故都要在搜索路径上。
val nativeLibPath =
    listOf(rustHostLibDir, smtcLibDir, logiBatteryLibDir)
        .joinToString(File.pathSeparator) { it.absolutePath }

tasks.withType<JavaExec>().configureEach {
    // JNA 优先读 jna.library.path；java.library.path 对 Native.register 不生效
    systemProperty("jna.library.path", nativeLibPath)
    systemProperty("java.library.path", nativeLibPath)
    jvmArgs(nativeAccessArgs)
    // 控制台输出编码：中文 Windows 默认按 GBK 编码 stdout/stderr，捕获端（Gradle/IDE）按 UTF-8 解码会乱码。
    // JDK 18+ 起 file.encoding 不再决定控制台编码，需显式指定 stdout.encoding / stderr.encoding。
    systemProperty("stdout.encoding", "UTF-8")
    systemProperty("stderr.encoding", "UTF-8")
}

tasks.withType<Test>().configureEach {
    systemProperty("jna.library.path", nativeLibPath)
    systemProperty("java.library.path", nativeLibPath)
    systemProperty("stdout.encoding", "UTF-8")
    systemProperty("stderr.encoding", "UTF-8")
    jvmArgs(nativeAccessArgs)
}

compose.desktop {
    application {
        mainClass = "notifyrelay.desktop.MainKt"
        // 打包产物同样需要放行原生访问；库搜索路径指向随包分发的资源目录（$APPDIR 由启动器替换）
        jvmArgs += nativeAccessArgs
        jvmArgs +=
            listOf(
                "-Dstdout.encoding=UTF-8",
                "-Dstderr.encoding=UTF-8",
            )
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "NotifyRelay"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(distributableAppResources)
            // 余额轮询使用 java.net.http.HttpClient：该模块不在 jpackage 默认模块集内，
            // 未声明时运行期报 NoClassDefFoundError（打包运行才会暴露，编译期无感）
            modules("java.net.http")
            windows {
                // 不显示控制台窗口：直接运行 exe 时无额外黑窗
                console = false
                iconFile.set(iconIcoFile)
            }
        }
    }
}
