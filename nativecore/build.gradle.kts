import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
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

// UniFFI 绑定生成：从 cargo-ndk 产出的 .so 中读取元数据，生成 Kotlin 绑定。
// 工具为 gobley-uniffi-bindgen（Kotlin Multiplatform 绑定生成器），
// 需先执行: cargo install gobley-uniffi-bindgen --version 0.3.7 --locked
// 生成物写入 build/generated/uniffi，作为独立 Kotlin 源码目录参与编译，不进入版本库。
val uniffiBindgen =
    tasks.register<Exec>("uniffiBindgen") {
        description = "由 Rust 库生成 UniFFI Kotlin 绑定（需 gobley-uniffi-bindgen）"
        group = "rust"
        dependsOn(rustBuild)
        val soFile = rustCoreDir.resolve("build/target/aarch64-linux-android/release/libnotify_relay_core.so")
        val configFile = rustCoreDir.resolve("uniffi.toml")
        val outDir =
            project.layout.buildDirectory
                .dir("generated/uniffi")
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

// 将 Rust 构建与绑定生成挂钩到 Gradle 构建生命周期，确保在编译前就绪
tasks.named("preBuild") {
    dependsOn(rustBuild, uniffiBindgen)
}

android {
    namespace = "com.xzyht.notifyrelay.nativecore"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories += "build/generated/rust/jniLibs"
            // UniFFI 生成物是 Kotlin 源码，需注册到 Android 源集的 kotlin 目录
            // （java.srcDir 仅作用于 Java 源，不会参与 Kotlin 编译）
            kotlin.directories += "build/generated/uniffi/main/kotlin"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// UniFFI 生成物（build/generated/uniffi）为第三方产出，每次构建都会重新生成覆盖。
// 其文件名规则豁免由根 .editorconfig 的路径段配置，见 `[**/generated/uniffi/**/*.{kt,kts}]`。

dependencies {
    // JNA 由 UniFFI 生成的绑定在运行时使用（api 传递给消费模块）
    api(libs.jna) { artifact { type = "aar" } }

    // UniFFI 生成的绑定（UniffiHandleMap）依赖 kotlinx.atomicfu 的原子类型
    implementation(libs.atomicfu)

    // UniFFI 生成的 Android 绑定使用 @RequiresApi（AndroidSystemCleaner，API 34+）
    implementation(libs.androidx.annotation)

    implementation(project(":base"))
}
