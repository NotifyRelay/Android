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

    /** 设备信息文件路径：由 App 侧写入 AndroidId，用于把 adb 设备与已配对设备对应起来。 */
    private const val DEVICE_INFO_PATH =
        "cat /storage/emulated/0/Android/data/com.xzyht.notifyrelay/files/device_info.txt"

    /** 目标应用包名。 */
    private const val PACKAGE_NAME = "com.xzyht.notifyrelay"

    /** adb 设备条目。 */
    data class Device(
        val serial: String,
        val state: String,
        val model: String,
    ) {
        /** 是否在线（device 状态）。 */
        val isOnline: Boolean get() = state == "device"

        /**
         * 是否无线连接。
         *
         * 除 Windows `AdbDeviceFactory.ResolveType` 的 `:` / `tcp` 判定外，
         * 还需识别 adb 无线调试的 mDNS 序列号形态
         * （`adb-<serial>-<suffix>._adb-tls-connect._tcp`），否则会被误判为 USB。
         */
        val isWireless: Boolean
            get() =
                serial.contains(':') ||
                    serial.contains("tcp") ||
                    serial.contains("_adb-tls-connect") ||
                    serial.contains("_adb-tls-pairing")

        /** 该设备的 adb 端口；无线序列号取冒号后的端口，无则按 5555。 */
        val port: Int
            get() = serial.substringAfterLast(':', "5555").toIntOrNull() ?: 5555
    }

    /**
     * 枚举 adb 设备（`adb devices -l`）。
     *
     * @return 设备列表；命令失败返回空列表
     */
    fun devices(adbPath: String): List<Device> {
        val result = run(adbPath, listOf("devices", "-l")) ?: return emptyList()
        return result.stdout
            .lineSequence()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 2) return@mapNotNull null
                val serial = parts[0]
                val state = parts[1]
                val model =
                    parts
                        .firstOrNull { it.startsWith("model:") }
                        ?.removePrefix("model:")
                        .orEmpty()
                Device(serial, state, model)
            }.toList()
    }

    /** 指定序列号是否在线。 */
    fun isOnline(
        adbPath: String,
        serial: String,
    ): Boolean = devices(adbPath).any { it.serial == serial && it.isOnline }

    /**
     * 读取设备的 AndroidId（`device_info.txt` 内容）。
     *
     * @return 设备 UUID；文件不存在或内容为空时返回 null
     */
    fun androidId(
        adbPath: String,
        serial: String,
    ): String? = shell(adbPath, serial, DEVICE_INFO_PATH)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * 连接无线 adb。
     *
     * @param port adb 端口
     * @return 是否连接成功（输出含 connected 且不含 failed/refused）
     */
    fun connect(
        adbPath: String,
        host: String,
        port: Int = 5555,
    ): Boolean {
        if (host.isBlank()) return false
        val result = run(adbPath, listOf("connect", host + ":" + port)) ?: return false
        val output = (result.stdout + result.stderr).lowercase()
        if (output.contains("failed") || output.contains("refused")) return false
        return output.contains("connected")
    }

    /**
     * 对指定设备启用 tcpip 模式（会重启对端 adbd）。
     *
     * @return 是否成功
     */
    fun enableTcpip(
        adbPath: String,
        serial: String,
        port: Int = 5555,
    ): Boolean {
        val result = run(adbPath, listOf("-s", serial, "tcpip", port.toString())) ?: return false
        if (result.exitCode != 0) {
            Logger.w(TAG, "启用 tcpip 失败: " + result.stderr.trim())
            return false
        }
        return true
    }

    /** 设备上是否已安装 NotifyRelay。 */
    fun isAppInstalled(
        adbPath: String,
        serial: String,
    ): Boolean {
        val output = shell(adbPath, serial, "pm list packages " + PACKAGE_NAME).orEmpty()
        return output.contains(PACKAGE_NAME)
    }

    /**
     * 授予 NotifyRelay 运行所需权限（对齐 Windows `AdbDeviceInfoResolver.GrantPermissionsAsync`）。
     *
     * 未安装应用时命令必然失败，属正常情况直接跳过。
     */
    fun grantPermissions(
        adbPath: String,
        serial: String,
    ) {
        if (!isAppInstalled(adbPath, serial)) return
        // pm grant 幂等，直接执行
        run(adbPath, listOf("-s", serial, "shell", "pm grant", PACKAGE_NAME, "android.permission.READ_LOGS"))
        // 后台读剪贴板所需；部分设备不支持该 appops，失败不影响主流程
        run(adbPath, listOf("-s", serial, "shell", "cmd", "appops", "set", PACKAGE_NAME, "READ_CLIPBOARD", "allow"))
    }

    /** 通过 `ip route` 取设备的 WiFi IP。 */
    fun wifiIp(
        adbPath: String,
        serial: String,
    ): String? {
        val output = shell(adbPath, serial, "ip route get 0.0.0.0").orEmpty()
        val idx = output.indexOf("src ")
        if (idx < 0) return null
        val ip = output.substring(idx + 4).trim().substringBefore(' ')
        return ip.takeIf { it.isNotEmpty() }
    }

    /** 解析配置里的解锁命令：按行拆分、去空白、去空行。 */
    fun parseCommands(raw: String): List<String> =
        raw
            .split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
