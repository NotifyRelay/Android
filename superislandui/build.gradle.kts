plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "github.xzynine.superislandui"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        targetSdk = 36
    }
    testOptions {
        targetSdk = 36
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose 运行时与 UI 采用 Compose Multiplatform 坐标（版本由 CMP 统一决定）
    implementation(libs.compose.multiplatform.runtime)
    implementation(libs.compose.multiplatform.foundation)
    implementation(libs.compose.multiplatform.ui)
    implementation(libs.compose.multiplatform.ui.tooling.preview)
    debugImplementation(libs.compose.multiplatform.ui.tooling)

    // material3 保留 androidx 坐标（提供 KMP 元数据变体）
    implementation(libs.androidx.material3)

    // Android-only Compose 依赖
    implementation(libs.androidx.activity.compose)

    // AndroidX Lifecycle
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // 其他依赖
    implementation(libs.gson)
    implementation(libs.bundles.coil)

    // Miuix 风格 UI 库
    implementation(libs.miuix.ui)

    // 依赖其他模块（原 :core 工具类已并入 :base）
    implementation(project(":base"))
    implementation(project(":data"))
    // KMP 公共端（平台标识、文本与计时工具、存储 key 常量）
    implementation(project(":shared"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
