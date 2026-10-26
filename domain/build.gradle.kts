import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "notifyrelay.domain"
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

        withHostTest {
        }

        withDeviceTest {
        }
    }

    sourceSets {
        commonMain.dependencies {
            // 领域模型与契约仅依赖基础设施层
            api(project(":base"))

            // PairingViewModel 需要 ViewModel 与协程
            implementation(libs.androidx.lifecycle.viewmodel)
        }

        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }
    }
}
