import org.gradle.kotlin.dsl.registering
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

val rustCoreDir = project.projectDir.resolve("notify-relay-core")

// Rust 构建任务：通过 cargo-ndk 交叉编译生成 .so
// 依赖: cargo install cargo-ndk  &&  rustup target add aarch64-linux-android x86_64-linux-android
val rustBuild by tasks.registering(Exec::class) {
    description = "构建 Rust 核心库（需 cargo-ndk + NDK）"
    group = "rust"
    doFirst {
        require(rustCoreDir.exists()) { "Rust 子模块未同步，请执行 git submodule update --init" }
    }
    inputs.dir(rustCoreDir.resolve("src"))
    inputs.file(rustCoreDir.resolve("Cargo.toml"))
    inputs.file(rustCoreDir.resolve("Cargo.lock")).optional()
    val outDir = project.layout.buildDirectory.dir("generated/rust/jniLibs").get().asFile
    outputs.dir(outDir)
    workingDir = rustCoreDir
    commandLine("cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", outDir.absolutePath,
        "build", "--release")
}

// 将 Rust 构建挂钩到 Gradle 构建生命周期，确保在打包前生成 .so
tasks.named("preBuild") {
    dependsOn(rustBuild)
}

android {
    namespace = "com.xzyht.notifyrelay.nativecore"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(project.file("build/generated/rust/jniLibs"))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // JNA 用于 Rust FFI 调用（api 传递给消费模块，如 app 直接使用 Pointer/Callback）
    api(libs.jna) { artifact { type = "aar" } }
}
