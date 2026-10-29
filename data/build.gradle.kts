import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}

kotlin {
    // JVM 目标：供桌面端复用数据层
    jvm()

    // AGP 9 的 KMP 库插件：Android 目标配置位于 kotlin 块内的 android 块。
    android {
        namespace = "notifyrelay.data"
        compileSdk =
            libs.versions.compileSdk
                .get()
                .toInt()

        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        // 发布给库使用方的 keep 规则文件
        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }

        lint {
            targetSdk = 36
        }

        // 启用测试编译，使 KGP 默认创建的 commonTest 源集被挂到 Android 目标的 compilation 上
        withHostTest {
        }

        withDeviceTest {
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Room 运行时与注解：实体、DAO、Migration 均位于 commonMain（Room 2.7+ 为 KMP 库）
            implementation(libs.androidx.room.runtime)

            // KSP 为 DAO 的 PagingSource 生成 LimitOffsetPagingSource，需要 room-paging
            implementation(libs.androidx.room.paging)

            // SQLite 驱动：各平台统一使用 bundled 实现，避免依赖系统 SQLite
            implementation(libs.androidx.sqlite.bundled)

            // Paging 公共端：DAO 的 PagingSource 返回类型
            implementation(libs.androidx.paging.common)

            // DAO 的 Flow 返回类型
            implementation(libs.kotlinx.coroutines.core)

            // 迁移脚本解析旧配置 JSON（替代 Android 专有的 org.json / Gson）
            implementation(libs.kotlinx.serialization.json)

            // 基础设施：Logger
            implementation(project(":base"))

            // 领域模型：SelectedDeviceInfo 以 typealias 复用，避免同类双份定义
            api(project(":domain"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)

            // Android 目标的 Paging 运行时（DAO PagingSource 的 Android 实现）
            implementation(libs.androidx.paging.runtime)

            // Gson
            implementation(libs.gson)

            // Kotlin Coroutines
            implementation(libs.bundles.kotlinx.coroutines)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

dependencies {
    // Room 注解处理器：Android 与 JVM 两个目标各自注册
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}
