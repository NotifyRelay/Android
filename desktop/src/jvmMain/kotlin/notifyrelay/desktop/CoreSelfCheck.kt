package notifyrelay.desktop

import com.xzyht.notifyrelay.nativecore.NativeCore
import java.io.File

/**
 * Rust 核心自检：验证 JNA 能加载本机动态库并调用导出函数。
 *
 * 在数据层初始化前执行，结果同时写入用户目录便于排查。
 */
object CoreSelfCheck {
    /**
     * 执行自检。
     *
     * @return 诊断信息；动态库加载失败时返回失败原因
     */
    fun run(): String {
        val result =
            try {
                val abi = NativeCore.getAbiProbe()
                val created = NativeCore.createContext()
                val ready = NativeCore.isReady()
                "abi=" + abi + ", contextCreated=" + created + ", ready=" + ready
            } catch (e: Throwable) {
                "失败: " + (e.message ?: e::class.simpleName)
            }
        writeReport(result)
        return result
    }

    /** 把结果写入用户目录下的 .notifyrelay/core-selfcheck.txt。 */
    private fun writeReport(result: String) {
        runCatching {
            val dir = File(System.getProperty("user.home"), ".notifyrelay")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "core-selfcheck.txt").writeText(result)
        }
    }
}
