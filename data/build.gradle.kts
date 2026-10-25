import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}

kotlin {
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
            // Room 注解与运行时：实体与 DAO 位于 commonMain
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)
            implementation(libs.androidx.room.paging)

            // Paging 公共端：DAO 的 PagingSource 返回类型
            implementation(libs.androidx.paging.common)

            // DAO 的 Flow 返回类型
            implementation(libs.kotlinx.coroutines.core)

            // KMP 公共端：日志（StorageManagerCore 使用 shared 的 Logger）与过滤配置默认常量
            implementation(project(":shared"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)

            // 依赖base模块
            implementation(project(":base"))
            // KMP 公共端（过滤配置默认常量）
            implementation(project(":shared"))

            // Room Database
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)
            implementation(libs.androidx.room.paging)

            // Paging 3 (DAO PagingSource)
            implementation(libs.androidx.paging.runtime)

            // Gson
            implementation(libs.gson)

            // Kotlin Coroutines
            implementation(libs.bundles.kotlinx.coroutines)
        }
    }
}

dependencies {
    // Room 注解处理器：注册到 Android 目标源集的 KSP 编译
    add("kspAndroid", libs.androidx.room.compiler)
}
