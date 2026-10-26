plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.foundation.layout)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.ui.text)
            implementation(libs.compose.multiplatform.ui.tooling.preview)
            implementation(libs.compose.multiplatform.components.resources)
            // material3 与 material-icons-extended 使用 androidx 条目：其 Gradle 模块含 metadata 变体，
            // org.jetbrains.compose 侧对应版本线不覆盖 1.12.2。
            implementation(libs.androidx.material3)
            implementation(libs.androidx.compose.material.icons.extended)
            implementation(libs.miuix.nav)
            implementation(libs.miuix.ui.kmp)
            // preference 组件的 KMP 坐标（MiuixSpinnerPreference 使用）
            implementation(libs.miuix.preference.kmp)
            // 扩展图标（ScrollableTopAppBarPage / DeleteButton 等使用 MiuixIcons.Extended）
            implementation(libs.miuix.icons)
            implementation(libs.kotlinx.serialization.json)
            // UpdateDialog 依赖的 ReleaseInfo 数据模型（KMP 库）
            implementation(project(":checkupdata"))
        }

        androidMain.dependencies {
            implementation(libs.compose.multiplatform.ui.tooling)
            // Android 侧日志通道：DebugTree 输出与级别过滤
            implementation(libs.timber)
        }
    }
}
