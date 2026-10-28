package notifyrelay.data.config

import notifyrelay.base.log.Logger
import notifyrelay.data.platform.LegacyStoragePortRegistry

/**
 * 设备信息文件生成。
 *
 * 内容为本机 UUID，供 PC 端通过 adb 从应用外部目录读取；
 * 目录位置由 [LegacyStoragePortRegistry] 注入的平台实现决定。
 */
object DeviceInfoManager {
    private const val TAG = "DeviceInfoManager"
    private const val DEVICE_INFO_FILE = "device_info.txt"

    /**
     * 生成供 PC 端 adb 读取的设备信息文件（内容为本机 UUID）。
     *
     * @param deviceUuid 本机 UUID，为空时跳过写入
     */
    fun generateDeviceInfoFile(deviceUuid: String) {
        try {
            if (deviceUuid.isEmpty()) {
                Logger.e(TAG, "设备 UUID 为空，跳过生成设备信息文件")
                return
            }
            val port = LegacyStoragePortRegistry.current
            if (port == null) {
                Logger.e(TAG, "未注册 LegacyStoragePort，跳过生成设备信息文件")
                return
            }
            if (port.writeExternalFile(DEVICE_INFO_FILE, deviceUuid)) {
                Logger.d(TAG, "设备信息文件已生成，UUID: " + deviceUuid)
            } else {
                Logger.e(TAG, "生成设备信息文件失败")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "生成设备信息文件失败", e)
        }
    }
}
