import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

val rustCoreDir = project.projectDir.resolve("notify-relay-core")

// Rust 构建任务：通过 cargo-ndk 交叉编译生成 .so
// 依赖: cargo install cargo-ndk  &&  rustup target add aarch64-linux-android x86_64-linux-android
val rustBuild =
    tasks.register<Exec>("rustBuild") {
        description = "构建 Rust 核心库（需 cargo-ndk + NDK）"
        group = "rust"
        doFirst {
            require(rustCoreDir.exists()) { "Rust 子模块未同步，请执行 git submodule update --init" }
        }
        inputs.dir(rustCoreDir.resolve("src"))
        inputs.file(rustCoreDir.resolve("Cargo.toml"))
        inputs.file(rustCoreDir.resolve("Cargo.lock"))
        inputs.file(rustCoreDir.resolve("uniffi.toml"))
        val outDir =
            project.layout.buildDirectory
                .dir("generated/rust/jniLibs")
                .get()
                .asFile
        outputs.dir(outDir)
        workingDir = rustCoreDir
        commandLine(
            "cargo",
            "ndk",
            "-t",
            "arm64-v8a",
            "-t",
            "x86_64",
            "-o",
            outDir.absolutePath,
            "build",
            "--release",
        )
    }

// UniFFI 绑定生成（KMP 形态）：以 kotlin_multiplatform = true 的配置产出
// commonMain（expect 声明）与 androidMain（actual 实现）双目录结构。
// 工具为 gobley-uniffi-bindgen，需先执行:
//   cargo install gobley-uniffi-bindgen --version 0.3.7 --locked
// 生成物写入 build/generated/uniffi-kmp，作为独立 Kotlin 源码目录参与编译，不进入版本库。
val uniffiBindgen =
    tasks.register<Exec>("uniffiBindgen") {
        description = "由 Rust 库生成 UniFFI Kotlin 绑定（KMP 形态，需 gobley-uniffi-bindgen）"
        group = "rust"
        dependsOn(rustBuild)
        val soFile = rustCoreDir.resolve("build/target/aarch64-linux-android/release/libnotify_relay_core.so")
        // KMP 配置文件位于本模块目录（仓库侧），不写入 notify-relay-core 子模块
        val configFile = project.projectDir.resolve("uniffi.kmp.toml")
        val outDir =
            project.layout.buildDirectory
                .dir("generated/uniffi-kmp")
                .get()
                .asFile
        inputs.file(configFile)
        inputs.files(rustCoreDir.resolve("Cargo.lock"))
        // .so 是真正的输入：Rust 侧导出面变化时其内容随之改变，
        // 未声明会导致本任务被判定 UP-TO-DATE 而沿用旧绑定。
        inputs.file(soFile)
        outputs.dir(outDir)
        doFirst {
            require(soFile.exists()) { "Rust 产物缺失：$soFile（请先执行 :core:rustBuild）" }
            outDir.deleteRecursively()
            outDir.mkdirs()
        }
        commandLine(
            "gobley-uniffi-bindgen",
            "--library",
            "--config",
            configFile.absolutePath,
            "--out-dir",
            outDir.absolutePath,
            soFile.absolutePath,
        )
    }

// 将 Rust 构建与绑定生成挂钩到 Gradle 构建生命周期，确保在编译前就绪。
// KMP 库插件不创建 preBuild 任务，故挂到 Android 目标的编译任务上。
tasks.matching { it.name == "compileAndroidMain" || it.name == "androidPreBuild" }.configureEach {
    dependsOn(rustBuild, uniffiBindgen)
}

kotlin {
    android {
        namespace = "com.xzyht.notifyrelay.nativecore"
        compileSdk =
            libs.versions.compileSdk
                .get()
                .toInt()

        minSdk = 29

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            // UniFFI 生成物以 expect/actual class 承载 Core 类型（官方 Beta 特性）
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }

        // Rust 产物以 .so 形式随 AAR 分发，需声明不压缩
        androidResources {
            noCompress += listOf("so")
        }

        // 启用测试编译，使 KGP 默认创建的 commonTest 源集被挂到 Android 目标的 compilation 上
        withHostTest {
        }
    }

    sourceSets {
        commonMain.dependencies {
            // 公开签名使用 kotlinx.serialization.json 类型
            api(libs.kotlinx.serialization.json)

            // NativeCore.coreRef 使用 atomicfu 的 AtomicRef；UniFFI 生成的绑定亦依赖其原子类型
            api(libs.atomicfu)

            // 日志通道
            implementation(project(":shared"))
        }

        androidMain.dependencies {
            // UniFFI 生成的 Android 绑定经 JNA 加载 .so（api 传递给消费模块）
            api("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")

            // UniFFI 生成的 Android 绑定使用 @RequiresApi（AndroidSystemCleaner，API 34+）
            implementation(libs.androidx.annotation)
        }
    }
}

// Rust .so 注册：KMP 库插件的 android 目标不暴露 jniLibs DSL，
// 经 AndroidComponents 在变体上追加 jniLibs 目录（cargo-ndk 产出的 ABI 结构）。
androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addStaticSourceDirectory(
            layout.buildDirectory
                .dir("generated/rust/jniLibs")
                .get()
                .asFile
                .absolutePath,
        )
    }
}

// 生成物目录注册：commonMain 与 androidMain 分别对应 uniffi-kmp 的两个输出目录
kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(
    layout.buildDirectory.dir("generated/uniffi-kmp/commonMain/kotlin"),
)

kotlin.sourceSets.getByName("androidMain").kotlin.srcDir(
    layout.buildDirectory.dir("generated/uniffi-kmp/androidMain/kotlin"),
)
