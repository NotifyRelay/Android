package notifyrelay.desktop

import java.io.File
import java.util.Properties

/**
 * 桌面端构建信息。
 *
 * 版本号与 Android 同源：读取版本库根目录的 version.properties。
 * 已打包运行时工作目录位于打包目录内，因此需沿父目录逐级向上查找；
 * 打包时注入的系统属性优先级最高。
 */
object BuildInfo {
    val VERSION_NAME: String by lazy {
        System.getProperty("notifyrelay.version.name")
            ?: readFromVersionProperties()
            ?: "dev"
    }

    /** 沿父目录向上查找 version.properties 并读取 versionName。 */
    private fun readFromVersionProperties(): String? =
        runCatching {
            var dir: File? = File(System.getProperty("user.dir") ?: ".")
            repeat(8) {
                val candidate = dir?.resolve("version.properties")
                if (candidate != null && candidate.isFile) {
                    val props = Properties()
                    candidate.inputStream().use { props.load(it) }
                    val name = props.getProperty("versionName")
                    if (!name.isNullOrBlank()) return name
                }
                dir = dir?.parentFile
            }
            null
        }.getOrNull()
}
