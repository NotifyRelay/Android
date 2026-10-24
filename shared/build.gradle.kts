plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // AGP 9 的 KMP 库插件：Android 目标配置位于 kotlin 块内的 android 块。
    android {
        namespace = "notifyrelay.shared"
        compileSdk =
            libs.versions.compileSdk
                .get()
                .toInt()

        minSdk = 29

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }

        // 启用测试编译，使 KGP 默认创建的 commonTest 源集被挂到 Android 目标的 compilation 上
        withHostTest {
        }

        withDeviceTest {
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.multiplatform.runtime)
        }

        androidMain.dependencies {
        }
    }
}
