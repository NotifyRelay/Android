import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "notifyrelay.base"
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

        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }

        // 启用测试编译，使 KGP 默认创建的 commonTest 源集被挂到 Android 目标的 compilation 上
        withHostTest {
        }

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            // 日志实现
            implementation(project(":shared"))

            // BatteryIconConverter 返回 Compose Color（CMP 公共端坐标）
            implementation(libs.compose.multiplatform.ui)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.lifecycle.process)

            // BatteryIconConverter.getBatteryColor 返回 Compose Color
            implementation(libs.androidx.compose.ui)
            // ImageUtils.loadBitmap 基于 Coil（同时传递 kotlinx-coroutines）
            implementation(libs.bundles.coil)

            implementation(project(":shared"))
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }

        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.junit)
            implementation(libs.androidx.espresso.core)
        }
    }
}
