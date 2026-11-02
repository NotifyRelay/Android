package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import notifyrelay.data.config.ScrcpyDevicePreference
import java.util.concurrent.ConcurrentHashMap

/**
 * scrcpy 目标设备的 adb 序列号解析。
 *
 * 对齐 Windows 端 `ScrcpyDeviceSelector.ResolveDeviceSerialAsync` 与 `AdbTcpReconnector`：
 * 1. 按 AndroidId（设备侧 `device_info.txt`）匹配已配对设备，取不到时按型号模糊匹配；
 * 2. 按设备偏好（自动 / USB / 无线 / 每次询问）从候选中选定序列号；
 * 3. 无可用 adb 连接时尝试自动重连（多来源 IP 逐个连 5555）；
 * 4. 仍无 USB 且开启 tcpip 模式时，尝试对 USB 设备启用 tcpip 后重连。
 */
internal object DesktopAdbDeviceResolver {
    private const val TAG = "DesktopAdbSelect"

    /** 无线 adb 失败冷却（键为设备 UUID）：本次连接期间失败后不再重试。 */
    private val wirelessCooldown = ConcurrentHashMap<String, Boolean>()

    /** 清除失败冷却（USB 重新连接时调用）。 */
    fun clearCooldown(deviceUuid: String) {
        wirelessCooldown.remove(deviceUuid)
    }

    /** 设备是否处于失败冷却。 */
    fun isCoolingDown(deviceUuid: String): Boolean = wirelessCooldown.containsKey(deviceUuid)

    /**
     * 解析用于 scrcpy 的 adb 序列号。
     *
     * @param adbPath adb 可执行文件路径
     * @param deviceUuid 已配对设备 UUID（用于 AndroidId 精确匹配）
     * @param deviceName 设备显示名（型号模糊匹配与提示用）
     * @param deviceIp 设备 IP（自动重连与无线连接用）
     * @param preference 设备偏好
     * @param tcpipEnabled 是否允许对 USB 设备启用 tcpip
     * @return 可用序列号；无法确定时返回 null
     */
    fun resolveSerial(
        adbPath: String,
        deviceUuid: String,
        deviceName: String,
        deviceIp: String,
        preference: ScrcpyDevicePreference,
        tcpipEnabled: Boolean,
    ): String? {
        if (adbPath.isBlank()) {
            Logger.w(TAG, "未配置 adb 路径，无法解析设备")
            return null
        }
        var all = DesktopAdbRunner.devices(adbPath)
        var candidates = matchCandidates(adbPath, all, deviceUuid, deviceName)

        // 无匹配时先尝试建立无线 adb（幂等：已在线直接成功，直连失败才考虑 tcpip）
        if (candidates.isEmpty() && deviceIp.isNotBlank()) {
            Logger.d(TAG, "未匹配到 adb 设备，尝试建立无线 adb: " + deviceIp)
            if (ensureWireless(adbPath, deviceUuid, deviceIp, tcpipEnabled)) {
                all = DesktopAdbRunner.devices(adbPath)
                candidates = matchCandidates(adbPath, all, deviceUuid, deviceName)
            }
        }

        // 仍无候选：对在线 USB 设备启用 tcpip 后重连（会重启对端 adbd，故置于最后）
        if (candidates.isEmpty() && tcpipEnabled && deviceIp.isNotBlank()) {
            val usb =
                all.firstOrNull {
                    it.isOnline && !it.isWireless && DesktopAdbRunner.androidId(adbPath, it.serial) == deviceUuid
                }
            if (usb != null && !isCoolingDown(deviceUuid)) {
                Logger.d(TAG, "对 USB 设备启用 tcpip: " + usb.serial)
                if (DesktopAdbRunner.enableTcpip(adbPath, usb.serial) && DesktopAdbRunner.connect(adbPath, deviceIp)) {
                    all = DesktopAdbRunner.devices(adbPath)
                    candidates = matchCandidates(adbPath, all, deviceUuid, deviceName)
                } else {
                    wirelessCooldown[deviceUuid] = true
                }
            }
        }

        if (candidates.isEmpty()) {
            Logger.w(TAG, "未找到可用 adb 设备: " + deviceName)
            return null
        }

        return selectByPreference(adbPath, candidates, preference)
    }

    /** 按 AndroidId 精确匹配，取不到时按型号模糊匹配；均无命中则返回空。 */
    private fun matchCandidates(
        adbPath: String,
        all: List<DesktopAdbRunner.Device>,
        deviceUuid: String,
        deviceName: String,
    ): List<DesktopAdbRunner.Device> {
        val online = all.filter { it.isOnline }
        val exact =
            online.filter { device ->
                val id = DesktopAdbRunner.androidId(adbPath, device.serial)
                !id.isNullOrEmpty() && id == deviceUuid
            }
        if (exact.isNotEmpty()) return exact
        if (deviceName.isBlank()) return emptyList()
        // 型号模糊匹配：adb 的 model 不含空格，与显示名做双向包含判断
        val compact = deviceName.replace(" ", "")
        return online.filter { device ->
            device.model.isNotBlank() &&
                (compact.contains(device.model, ignoreCase = true) || device.model.contains(compact, ignoreCase = true))
        }
    }

    /**
     * 幂等地为设备建立无线 adb（对齐 Windows `WirelessAdbConnector.TryEnableAsync`）。
     *
     * 1. 已有该 host 的在线无线设备则直接返回；
     * 2. 先尝试直连（无副作用）；
     * 3. 仅当直连失败且允许 tcpip、且能找到该设备的 USB 序列号时，
     *    才对其启用 tcpip 后重连（会重启对端 adbd）。
     *
     * @return 是否已建立可用的无线 adb
     */
    fun ensureWireless(
        adbPath: String,
        deviceUuid: String,
        host: String,
        tcpipEnabled: Boolean,
    ): Boolean {
        if (host.isBlank() || adbPath.isBlank()) return false
        if (isCoolingDown(deviceUuid)) {
            Logger.d(TAG, "无线 adb 处于失败冷却，跳过: " + deviceUuid)
            return false
        }
        val serial = host + ":5555"
        // 幂等：已在线则直接成功
        if (DesktopAdbRunner.isOnline(adbPath, serial)) return true

        // 直连（无副作用）
        if (DesktopAdbRunner.connect(adbPath, host)) {
            if (!DesktopAdbRunner.androidId(adbPath, serial).isNullOrEmpty()) {
                Logger.i(TAG, "直连无线 adb 成功: " + serial)
                clearCooldown(deviceUuid)
                return true
            }
            Logger.w(TAG, "无线 adb 连接成功但 device_info.txt 不可读: " + serial)
        }

        if (!tcpipEnabled) {
            Logger.d(TAG, "未开启无线调试，跳过 tcpip")
            return false
        }

        // 找该设备的 USB 序列号后启用 tcpip
        val usb =
            DesktopAdbRunner
                .devices(adbPath)
                .firstOrNull { it.isOnline && !it.isWireless && DesktopAdbRunner.androidId(adbPath, it.serial) == deviceUuid }
        if (usb == null) {
            Logger.d(TAG, "未找到匹配的 USB 设备，无法启用 tcpip")
            wirelessCooldown[deviceUuid] = true
            return false
        }
        if (!DesktopAdbRunner.enableTcpip(adbPath, usb.serial)) {
            wirelessCooldown[deviceUuid] = true
            return false
        }
        // adbd 重启需要短暂等待
        runCatching { Thread.sleep(200) }
        val ok = DesktopAdbRunner.connect(adbPath, host) && !DesktopAdbRunner.androidId(adbPath, serial).isNullOrEmpty()
        if (ok) {
            clearCooldown(deviceUuid)
        } else {
            wirelessCooldown[deviceUuid] = true
        }
        return ok
    }

    /** 按设备偏好选择序列号；自动模式优先 USB。 */
    private fun selectByPreference(
        adbPath: String,
        candidates: List<DesktopAdbRunner.Device>,
        preference: ScrcpyDevicePreference,
    ): String {
        val usb = candidates.firstOrNull { !it.isWireless }
        val wifi = candidates.firstOrNull { it.isWireless }
        val chosen =
            when (preference) {
                ScrcpyDevicePreference.USB -> usb ?: candidates.first()
                ScrcpyDevicePreference.TCPIP -> wifi ?: candidates.first()
                // 自动与「每次询问」在桌面端统一按自动处理：优先 USB，其次无线
                ScrcpyDevicePreference.AUTO, ScrcpyDevicePreference.ASK_EVERY_TIME -> usb ?: wifi ?: candidates.first()
            }
        // 无线候选建立后校验 device_info.txt 可读，避免连上了却不可用
        if (chosen.isWireless && DesktopAdbRunner.androidId(adbPath, chosen.serial).isNullOrEmpty()) {
            Logger.w(TAG, "无线设备不可读 device_info.txt: " + chosen.serial)
        }
        // 选定后授予所需权限（幂等；未安装应用时内部自动跳过）
        DesktopAdbRunner.grantPermissions(adbPath, chosen.serial)
        Logger.i(TAG, "选定 adb 序列号: " + chosen.serial + " (wireless=" + chosen.isWireless + ")")
        return chosen.serial
    }
}
