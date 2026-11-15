import org.gradle.api.tasks.PathSensitivity
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
        compilerOptions {
            // UniFFI 生成物以 expect/actual class 承载原生对象类型（官方 Beta 特性）
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":domain"))
            implementation(project(":data"))
            implementation(project(":core"))
            implementation(project(":base"))
            implementation(project(":checkupdata"))
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

            // UniFFI 生成的电池库绑定经 JNA 加载动态库，内部使用 atomicfu 原子计数器
            implementation(libs.jna)
            implementation(libs.atomicfu)
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
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
        // 只声明动态库为输出：target/release 下含 deps/build 等中间产物，
        // 声明整目录会让 Gradle 每次判定「输出被外部修改」而无法缓存
        outputs.file(smtcRustDir.resolve("target/release/smtc_jni.dll")).withPropertyName("smtcLib")
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

// 设备电池 Rust 库（子模块 desktop/device-battery-lib）：C ABI 动态库，供桌面端枚举电池设备电量。
// 与 smtc-rust 同方式，只构建 host 目标（HID++ 实现仅 Windows 有实际后端）。
val deviceBatteryDir = project.projectDir.resolve("device-battery-lib")
val deviceBatteryBuild =
    tasks.register<Exec>("deviceBatteryBuild") {
        description = "构建设备电池 Rust 库（本机 host 目标，供桌面端枚举设备电量）"
        group = "rust"
        doFirst {
            require(deviceBatteryDir.exists()) { "设备电池 Rust 子模块未同步，请执行 git submodule update --init" }
        }
        inputs.dir(deviceBatteryDir.resolve("src"))
        inputs.dir(deviceBatteryDir.resolve("crates"))
        inputs.file(deviceBatteryDir.resolve("Cargo.toml"))
        inputs.file(deviceBatteryDir.resolve("Cargo.lock"))
        outputs.file(deviceBatteryDir.resolve("target/release/device_battery_lib.dll")).withPropertyName("batteryLib")
        workingDir = deviceBatteryDir
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
tasks.named("compileKotlinJvm") { dependsOn(deviceBatteryBuild) }
val deviceBatteryLibDir = deviceBatteryDir.resolve("target/release")

// UniFFI 绑定生成：以 kotlin_multiplatform = true 的配置产出 jvmMain 目录结构，
// 生成物作为独立 Kotlin 源码目录参与 :desktop 编译，不进入版本库。
// 工具为 gobley-uniffi-bindgen，需先执行:
//   cargo install gobley-uniffi-bindgen --version 0.3.7 --locked
val batteryUniffiConfig = deviceBatteryDir.resolve("uniffi.toml")
val batteryUniffiOutDir =
    layout.buildDirectory
        .dir("generated/uniffi-battery")
        .get()
        .asFile

val uniffiBatteryBindgen =
    tasks.register<Exec>("uniffiBatteryBindgen") {
        description = "由电池库生成 UniFFI Kotlin 绑定（需 gobley-uniffi-bindgen）"
        group = "rust"
        dependsOn(deviceBatteryBuild)
        val hostLibFile = deviceBatteryLibDir.resolve("device_battery_lib.dll")
        inputs.file(batteryUniffiConfig)
        inputs.file(deviceBatteryDir.resolve("Cargo.lock"))
        // 动态库是真正的输入：Rust 侧导出面变化时其内容随之改变，
        // 未声明会导致本任务被判定 UP-TO-DATE 而沿用旧绑定。
        inputs.file(hostLibFile)
        outputs.dir(batteryUniffiOutDir)
        doFirst {
            require(hostLibFile.exists()) { "电池库产物缺失：$hostLibFile（请先执行 :desktop:deviceBatteryBuild）" }
            batteryUniffiOutDir.deleteRecursively()
            batteryUniffiOutDir.mkdirs()
        }
        commandLine(
            "gobley-uniffi-bindgen",
            "--library",
            hostLibFile.absolutePath,
            "--config",
            batteryUniffiConfig.absolutePath,
            "--out-dir",
            batteryUniffiOutDir.absolutePath,
        )
    }

tasks.named("compileKotlinJvm") { dependsOn(uniffiBatteryBindgen) }

// 生成目录是 ktlint 的扫描输入但不属于本模块源文件：显式声明依赖，
// 否则并行构建报 implicit dependency 校验失败。
tasks.matching { it.name.startsWith("runKtlintCheckOver") }.configureEach {
    dependsOn(uniffiBatteryBindgen)
}

// 生成物目录注册：commonMain 承载 expect 声明，jvmMain 承载 actual 实现
kotlin.sourceSets
    .getByName("commonMain")
    .kotlin
    .srcDir(batteryUniffiOutDir.resolve("commonMain/kotlin"))
kotlin.sourceSets
    .getByName("jvmMain")
    .kotlin
    .srcDir(batteryUniffiOutDir.resolve("jvmMain/kotlin"))

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
        dependsOn(deviceBatteryBuild)
        from(rustHostLibDir) { include("*.dll") }
        from(smtcLibDir) { include("*.dll") }
        from(deviceBatteryLibDir) { include("*.dll") }
    }

tasks
    .matching {
        it.name.startsWith("createDistributable") ||
            it.name.startsWith("package") ||
            it.name == "runDistributable" ||
            it.name == "suggestRuntimeModules" ||
            it.name == "prepareAppResources"
    }.configureEach { dependsOn(prepareDistributableNatives) }

// 打包任务把 appResourcesRootDir 当作输入目录，但该目录由 prepareDistributableNatives
// 在依赖链上游同步而来：Compose 插件的 up-to-date 判定只看目录快照，
// 原生库内容更新后仍会判为 UP-TO-DATE，导致镜像里保留旧 .so/.dll。
// 这里把「原生库文件本身」显式声明为打包任务的输入，内容一变即触发重新打包。
tasks
    .matching {
        it.name.startsWith("createDistributable") ||
            it.name.startsWith("package") ||
            it.name == "prepareAppResources"
    }.configureEach {
        inputs
            .files(
                fileTree(rustHostLibDir) { include("*.dll", "*.so", "*.dylib") },
                fileTree(smtcLibDir) { include("*.dll", "*.so", "*.dylib") },
                fileTree(deviceBatteryLibDir) { include("*.dll", "*.so", "*.dylib") },
            ).withPropertyName("nativeLibraries")
            .withPathSensitivity(PathSensitivity.NAME_ONLY)
    }

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
    listOf(rustHostLibDir, smtcLibDir, deviceBatteryLibDir)
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
