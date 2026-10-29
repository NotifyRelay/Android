package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 外部 adb 执行与设备解锁。
 *
 * 对齐 Windows 端 `AdbProcessLauncher` 与 `AdbDeviceOperator`：
 * - 启动 adb 进程并并发回收 stdout/stderr；
 * - 以 `dumpsys window policy` 的 showing 值判定锁屏；
 * - 锁屏时按配置的命令序列逐条执行，命令间隔 250ms，`%pwd%` 替换为输入的密码。
 */
internal object DesktopAdbRunner {
    private const val TAG = "DesktopAdb"

    /** 单条 adb 命令的超时（毫秒）。 */
    private const val COMMAND_TIMEOUT_MS = 15_000L

    /** 解锁命令之间的间隔（毫秒），与 Windows 端一致。 */
    private const val UNLOCK_COMMAND_DELAY_MS = 250L

    /** 执行结果。 */
    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    /**
     * 执行一条 adb 命令。
     *
     * @param adbPath adb 可执行文件路径
     * @param args 参数列表（不含 adb 本身）
     * @return 执行结果；进程无法启动时返回 null
     */
    fun run(
        adbPath: String,
        args: List<String>,
    ): Result? {
        if (adbPath.isBlank() || !File(adbPath).exists()) {
            Logger.w(TAG, "adb 路径无效: " + adbPath)
            return null
        }
        return try {
            val process =
                ProcessBuilder(listOf(adbPath) + args)
                    .redirectErrorStream(false)
                    .start()
            // 先并发读取，避免输出缓冲写满导致管道死锁
            var stdout = ""
            var stderr = ""
            val outThread =
                Thread({
                    stdout = process.inputStream.bufferedReader().readText()
                }, "adb-out").apply {
                    isDaemon = true
                    start()
                }
            val errThread =
                Thread({
                    stderr = process.errorStream.bufferedReader().readText()
                }, "adb-err").apply {
                    isDaemon = true
                    start()
                }
            val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                Logger.w(TAG, "adb 命令超时: " + args.joinToString(" "))
                return null
            }
            outThread.join(1000)
            errThread.join(1000)
            Result(process.exitValue(), stdout, stderr)
        } catch (e: java.io.IOException) {
            Logger.w(TAG, "adb 执行失败", e)
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    /** 以 shell 形式执行 adb 命令（等价于 Windows 端的 ExecuteShellCommandAsync）。 */
    fun shell(
        adbPath: String,
        serial: String,
        command: String,
    ): String? {
        val result = run(adbPath, listOf("-s", serial, "shell", command)) ?: return null
        return result.stdout.trim()
    }

    /** 设备是否处于锁屏状态（对齐 Windows：dumpsys window policy 的 showing 值是否为 true）。 */
    fun isLocked(
        adbPath: String,
        serial: String,
    ): Boolean {
        val output =
            shell(adbPath, serial, "dumpsys window policy | grep 'showing=' | cut -d '=' -f2")
                ?.trim()
                .orEmpty()
        return output == "true"
    }

    /**
     * 按命令序列解锁设备。
     *
     * @param commands 命令列表（已替换 `%pwd%`）
     * @return 是否全部执行完成
     */
    fun unlock(
        adbPath: String,
        serial: String,
        commands: List<String>,
    ): Boolean {
        if (commands.isEmpty()) return false
        for (command in commands) {
            Logger.d(TAG, "执行解锁命令: " + command)
            shell(adbPath, serial, command) ?: return false
            try {
                Thread.sleep(UNLOCK_COMMAND_DELAY_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return true
    }

    /** 解析配置里的解锁命令：按行拆分、去空白、去空行。 */
    fun parseCommands(raw: String): List<String> =
        raw
            .split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
