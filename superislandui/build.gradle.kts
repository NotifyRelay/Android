plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    // 为 commonMain 的 JSON 处理提供 kotlinx.serialization 编译器支持
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // AGP 9 的 KMP 库插件：Android 目标配置位于 kotlin 块内的 android 块。
    android {
        namespace = "github.xzynine.superislandui"
        compileSdk =
            libs.versions.compileSdk
                .get()
                .toInt()

        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            // PlatformBitmap 以 expect/actual class 承载（Kotlin 官方 Beta 特性）
            freeCompilerArgs.add("-Xexpect-actual-classes")
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
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            targetSdk {
                version = release(36)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Compose 运行时与 UI 采用 Compose Multiplatform 坐标（版本由 CMP 统一决定）；
            // 平台无关的 UI 主体位于 commonMain，故这些依赖声明在公共源集。
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.foundation.layout)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.ui.text)
            // @Preview 注解由该构件提供，公共端与 Android 端共用同一注解
            implementation(libs.compose.multiplatform.ui.tooling.preview)
            // 动画 API（AnimatedVisibility、animate*AsState 等）
            implementation(libs.compose.multiplatform.animation)

            // material3 保留 androidx 坐标（提供 KMP 元数据变体）
            implementation(libs.androidx.material3)

            // Miuix 风格 UI 库的 KMP 坐标（含 metadata 变体，公共源集可用）
            implementation(libs.miuix.ui.kmp)

            // commonMain 的 JSON 解析/构建统一使用 kotlinx.serialization.json（替代 org.json）
            implementation(libs.kotlinx.serialization.json)

            // BitmapUtils 缓存池使用 atomicfu 的同步原语
            implementation(libs.atomicfu)

            // KMP 公共端（平台标识、文本与计时工具、颜色工具、存储 key 常量）
            implementation(project(":shared"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)

            // Compose 工具仅 Android 目标使用：预览渲染与布局检查器适配
            implementation(libs.compose.multiplatform.ui.tooling)

            // Android-only Compose 依赖
            implementation(libs.androidx.activity.compose)

            // AndroidX Lifecycle
            implementation(libs.bundles.lifecycle)
            implementation(libs.androidx.lifecycle.viewmodel.compose)

            // 其他依赖
            implementation(libs.gson)
            implementation(libs.bundles.coil)

            // 依赖其他模块（原 :core 工具类已并入 :base）
            implementation(project(":base"))
            implementation(project(":data"))
            // KMP 公共端：Android 侧的提取/构建代码同样使用其工具
            implementation(project(":shared"))
        }
    }
}
