package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import notifyrelay.data.Storage
import notifyrelay.data.config.ScrcpyOptions
import notifyrelay.data.config.ScrcpySettings
import notifyrelay.data.config.ScrcpySettingsStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 桌面端 scrcpy 启动器。
 *
 * 与 Windows 端 `ScreenMirrorService` 同方式：调用外部 scrcpy 可执行文件，
 * 每台设备最多一个进程，按设备序列号索引；进程退出码 0 / 2 / -1 视为正常结束
 * （2 与 -1 是 scrcpy 在设备断开时的既有取值），其余上报提示。
 */
object DesktopScrcpyLauncher {
    private const val TAG = "DesktopScrcpy"

    /** scrcpy 可执行文件路径的存储键。 */
    private const val KEY_SCRCPY_PATH = "scrcpy_path"

    /** adb 可执行文件路径的存储键。 */
    private const val KEY_ADB_PATH = "adb_path"

    /** scrcpy 图标环境变量：由宿主导出后 scrcpy 用作窗口图标。 */
    private const val ENV_ICON_PATH = "SCRCPY_ICON_PATH"

    /** 解锁命令中的密码占位符。 */
    private const val PASSWORD_PLACEHOLDER = "%pwd%"

    /** 正在运行的进程：设备序列号 → 进程。 */
    private val runningProcesses = ConcurrentHashMap<String, Process>()

    /** 读取已保存的 scrcpy 路径。 */
    fun scrcpyPath(): String = Storage.getString(KEY_SCRCPY_PATH, "")

    /** 保存 scrcpy 路径；同目录下存在 adb 时一并记录（对应 Windows `TrySetCompanionTool`）。 */
    fun setScrcpyPath(path: String) {
        Storage.putString(KEY_SCRCPY_PATH, path)
        val directory = File(path).parentFile ?: return
        val companion = File(directory, "adb.exe")
        if (companion.exists()) {
            Storage.putString(KEY_ADB_PATH, companion.absolutePath)
        }
    }

    /** 读取已保存的 adb 路径。 */
    fun adbPath(): String = Storage.getString(KEY_ADB_PATH, "")

    /** 保存 adb 路径。 */
    fun setAdbPath(path: String) {
        Storage.putString(KEY_ADB_PATH, path)
    }

    /** 解析可执行文件路径并校验存在性；缺失时返回 null。 */
    fun resolveScrcpy(): File? {
        val path = scrcpyPath()
        if (path.isBlank()) {
            Logger.w(TAG, "尚未配置 scrcpy 路径")
            return null
        }
        val file = File(path)
        if (!file.exists()) {
            Logger.w(TAG, "scrcpy 路径不存在: " + path)
            return null
        }
        return file
    }

    /**
     * 启动指定设备的投屏。
     *
     * 序列号由 adb 实测得出（AndroidId / 型号匹配 → 偏好选择 → 必要时自动重连），
     * 不再假定 ip:5555。
     *
     * @param deviceUuid 设备 UUID（用于读取该设备的配置）
     * @param deviceName 设备显示名（型号模糊匹配用）
     * @param deviceIp 设备 IP（自动重连与无线连接用）
     * @param startApp 启动后拉起的包名；为空表示仅投屏
     * @param startAppTitle 启动应用时的窗口标题；为空则用包名
     * @return 是否成功启动
     */
    fun launch(
        deviceUuid: String,
        deviceName: String,
        deviceIp: String,
        startApp: String = "",
        startAppTitle: String = "",
    ): Boolean {
        val executable = resolveScrcpy()
        if (executable == null) {
            DesktopToastHost.show("未找到 scrcpy，请先在屏幕镜像设置中选择路径", DesktopToastHost.Duration.LONG)
            return false
        }
        val settings = ScrcpySettingsStore.load(deviceUuid)

        // 解析实际可用的 adb 序列号；失败时不再退化为固定端口
        val serial =
            DesktopAdbDeviceResolver.resolveSerial(
                adbPath = adbPath(),
                deviceUuid = deviceUuid,
                deviceName = deviceName,
                deviceIp = deviceIp,
                preference = settings.devicePreference,
                tcpipEnabled = settings.adbTcpipModeEnabled,
            )
        if (serial.isNullOrBlank()) {
            DesktopToastHost.show("未找到该设备的 adb 连接，请确认已开启 USB 调试或无线调试", DesktopToastHost.Duration.LONG)
            return false
        }
        // 同一设备重复启动：先终止已有进程（对应 Windows 端 killExistingProcess）
        stop(serial)

        // 启动前按设备配置解锁（对齐 Windows ScrcpyDeviceSelector.ResolveDeviceSerialAsync）
        if (settings.unlockBeforeLaunch) {
            unlockIfNeeded(deviceUuid, serial, settings)
        }

        val args = ScrcpyOptions.buildArguments(settings, serial, startApp, startAppTitle)
        return try {
            val command = listOf(executable.absolutePath) + args
            Logger.i(TAG, "启动 scrcpy: " + command.joinToString(" "))
            // 不合并 stderr：错误输出单独回收，异常退出时用于提示与日志
            val builder =
                ProcessBuilder(command)
                    .redirectErrorStream(false)
            // 图标路径：仅在已配置时注入，供 scrcpy 作为窗口图标
            windowIconPath()?.let { builder.environment()[ENV_ICON_PATH] = it }
            val process = builder.start()
            runningProcesses[serial] = process
            rememberSerial(deviceUuid, serial)
            watchProcess(serial, process)
            true
        } catch (e: java.io.IOException) {
            Logger.e(TAG, "启动 scrcpy 失败: " + serial, e)
            DesktopToastHost.show("启动投屏失败：" + (e.message ?: ""), DesktopToastHost.Duration.LONG)
            false
        }
    }

    /**
     * 以仅音频模式启动 scrcpy（`--no-video`），把设备声音转发到本机播放。
     *
     * 与 [launch] 共用序列号解析与解锁流程，仅参数固定为音频转发集合；
     * 进程同样按序列号登记，退出由 [watchProcess] 回收。
     *
     * @param audioOnly 为 true 时强制仅音频（忽略设备投屏配置）
     * @return 是否成功启动
     */
    fun launchAudio(
        deviceUuid: String,
        deviceName: String,
        deviceIp: String,
        audioOnly: Boolean = true,
    ): Boolean {
        val executable = resolveScrcpy()
        if (executable == null) {
            DesktopToastHost.show("未找到 scrcpy，请先在屏幕镜像设置中选择路径", DesktopToastHost.Duration.LONG)
            return false
        }
        val settings = ScrcpySettingsStore.load(deviceUuid)
        val serial =
            DesktopAdbDeviceResolver.resolveSerial(
                adbPath = adbPath(),
                deviceUuid = deviceUuid,
                deviceName = deviceName,
                deviceIp = deviceIp,
                preference = settings.devicePreference,
                tcpipEnabled = settings.adbTcpipModeEnabled,
            )
        if (serial.isNullOrBlank()) {
            DesktopToastHost.show("未找到该设备的 adb 连接，无法转发音频", DesktopToastHost.Duration.LONG)
            return false
        }
        if (settings.unlockBeforeLaunch) {
            unlockIfNeeded(deviceUuid, serial, settings)
        }

        // 仅音频模式：强制 --no-video，其余音频参数沿用设备配置
        val args =
            ScrcpyOptions.buildArguments(
                settings.copy(disableVideoForwarding = audioOnly || settings.disableVideoForwarding),
                serial,
            )
        return try {
            val command = listOf(executable.absolutePath) + args
            Logger.i(TAG, "启动 scrcpy 音频转发: " + command.joinToString(" "))
            val builder = ProcessBuilder(command).redirectErrorStream(false)
            windowIconPath()?.let { builder.environment()[ENV_ICON_PATH] = it }
            val process = builder.start()
            runningProcesses[serial] = process
            rememberSerial(deviceUuid, serial)
            watchProcess(serial, process)
            true
        } catch (e: java.io.IOException) {
            Logger.e(TAG, "启动 scrcpy 音频转发失败: " + serial, e)
            DesktopToastHost.show("启动音频转发失败：" + (e.message ?: ""), DesktopToastHost.Duration.LONG)
            false
        }
    }

    /** 解析指定设备当前可用的 adb 序列号；无连接时返回 null。 */
    fun resolveSerialFor(
        deviceUuid: String,
        deviceName: String,
        deviceIp: String,
    ): String? {
        val settings = ScrcpySettingsStore.load(deviceUuid)
        return DesktopAdbDeviceResolver.resolveSerial(
            adbPath = adbPath(),
            deviceUuid = deviceUuid,
            deviceName = deviceName,
            deviceIp = deviceIp,
            preference = settings.devicePreference,
            tcpipEnabled = settings.adbTcpipModeEnabled,
        )
    }

    /** 按设备 UUID 停止该设备的 scrcpy 进程（投屏与音频共用一张进程表）。 */
    fun stopByDeviceUuid(
        deviceUuid: String,
        deviceName: String,
        deviceIp: String,
    ) {
        // 先用当前解析结果停止；解析不到时回落到按 IP 推导的常见序列号
        val serial = resolveSerialFor(deviceUuid, deviceName, deviceIp)
        if (serial != null) {
            stop(serial)
        }
        serialSerialsOf(deviceUuid).forEach { stop(it) }
    }

    /** 该设备既有进程的序列号登记：UUID → 序列号。 */
    private val deviceSerials = ConcurrentHashMap<String, MutableSet<String>>()

    /** 记录设备与序列号的对应关系，供按设备停止时精确命中。 */
    private fun rememberSerial(
        deviceUuid: String,
        serial: String,
    ) {
        deviceSerials.getOrPut(deviceUuid) { java.util.concurrent.CopyOnWriteArraySet() }.add(serial)
    }

    /** 取该设备登记过的序列号集合。 */
    private fun serialSerialsOf(deviceUuid: String): Set<String> {
        val serials = deviceSerials.remove(deviceUuid) ?: return emptySet()
        return serials.toSet()
    }

    /** 停止指定设备的投屏进程。 */
    fun stop(serial: String) {
        val process = runningProcesses.remove(serial) ?: return
        runCatching { process.destroy() }
            .onFailure { Logger.w(TAG, "终止 scrcpy 失败: " + serial, it) }
    }

    /** 停止全部投屏进程。 */
    fun stopAll() {
        runningProcesses.keys.toList().forEach { stop(it) }
    }

    /** 指定设备是否正在投屏。 */
    fun isRunning(serial: String): Boolean {
        val process = runningProcesses[serial] ?: return false
        if (process.isAlive) return true
        runningProcesses.remove(serial)
        return false
    }

    /** 读取当前配置了投屏的设备序列号。 */
    fun runningSerials(): Set<String> = runningProcesses.filterValues { it.isAlive }.keys

    /** 后台等待进程结束并按退出码给出反馈。 */
    private fun watchProcess(
        serial: String,
        process: Process,
    ) {
        Thread({
            // 并发回收两路输出，避免管道写满阻塞；stderr 用于异常退出时上报
            val errorBuffer = StringBuilder()
            val outThread =
                Thread({
                    runCatching {
                        process.inputStream.bufferedReader().forEachLine { line ->
                            if (line.isNotBlank()) Logger.d(TAG, "scrcpy: " + line)
                        }
                    }
                }, "scrcpy-out-" + serial).apply {
                    isDaemon = true
                    start()
                }
            val errThread =
                Thread({
                    runCatching {
                        process.errorStream.bufferedReader().forEachLine { line ->
                            if (line.isNotBlank()) {
                                synchronized(errorBuffer) { errorBuffer.appendLine(line) }
                            }
                        }
                    }
                }, "scrcpy-err-" + serial).apply {
                    isDaemon = true
                    start()
                }

            val code =
                runCatching { process.waitFor() }
                    .getOrDefault(-1)
            runCatching {
                outThread.join(1000)
                errThread.join(1000)
            }
            runningProcesses.remove(serial)
            // 0 正常结束；2 与 -1 为 scrcpy 在设备断开 / 被终止时的既有取值，不报错
            if (code != 0 && code != 2 && code != -1) {
                val detail = synchronized(errorBuffer) { errorBuffer.toString().trim() }
                Logger.w(TAG, "scrcpy 异常退出: serial=" + serial + " code=" + code + (if (detail.isEmpty()) "" else " 错误输出=" + detail))
                DesktopToastHost.show(
                    if (detail.isEmpty()) "投屏已退出（退出码 " + code + "）" else "投屏失败：" + detail.lineSequence().first(),
                    DesktopToastHost.Duration.LONG,
                )
            } else {
                Logger.i(TAG, "scrcpy 已结束: serial=" + serial + " code=" + code)
            }
        }, "scrcpy-watch-" + serial).apply {
            isDaemon = true
            start()
        }
    }

    /** 密码缓存：设备 UUID → 密码条目。 */
    private val passwordCache = ConcurrentHashMap<String, PasswordEntry>()

    private data class PasswordEntry(
        val password: String,
        val cachedAt: Long,
        val timeoutMinutes: Int,
    )

    /**
     * 设备锁屏时按配置的命令序列解锁。
     *
     * 命令里的 %pwd% 替换为密码：超时大于 0 且缓存有效时复用缓存密码，
     * 否则弹窗询问并在超时大于 0 时缓存（对齐 Windows ScrcpyPasswordCache）。
     */
    private fun unlockIfNeeded(
        deviceUuid: String,
        serial: String,
        settings: ScrcpySettings,
    ) {
        val adb = adbPath()
        if (adb.isBlank()) {
            Logger.w(TAG, "未配置 adb 路径，跳过解锁")
            return
        }
        if (!DesktopAdbRunner.isLocked(adb, serial)) return

        var commands = DesktopAdbRunner.parseCommands(settings.unlockCommands)
        if (commands.isEmpty()) {
            Logger.w(TAG, "已锁屏但未配置解锁命令")
            return
        }
        if (commands.any { it.contains(PASSWORD_PLACEHOLDER) }) {
            val password = resolvePassword(deviceUuid, settings.unlockTimeout) ?: return
            commands = commands.map { it.replace(PASSWORD_PLACEHOLDER, password) }
        }
        DesktopAdbRunner.unlock(adb, serial, commands)
    }

    /** 取解锁密码：优先命中缓存，否则弹窗询问。 */
    private fun resolvePassword(
        deviceUuid: String,
        timeoutMinutes: Int,
    ): String? {
        if (timeoutMinutes > 0) {
            val cached = passwordCache[deviceUuid]
            if (cached != null &&
                cached.timeoutMinutes == timeoutMinutes &&
                System.currentTimeMillis() <= cached.cachedAt + timeoutMinutes * 60_000L
            ) {
                return cached.password
            }
            passwordCache.remove(deviceUuid)
        }
        val password = DesktopPasswordPrompt.ask(deviceUuid) ?: return null
        if (timeoutMinutes > 0) {
            passwordCache[deviceUuid] = PasswordEntry(password, System.currentTimeMillis(), timeoutMinutes)
        }
        return password
    }

    /** 窗口图标路径：优先使用应用数据目录下导出好的图标文件。 */
    private fun windowIconPath(): String? {
        val file = File(System.getProperty("user.home"), ".notifyrelay/scrcpy_icon.png")
        return if (file.exists()) file.absolutePath else null
    }
}
