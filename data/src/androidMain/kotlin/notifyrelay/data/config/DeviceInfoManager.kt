package notifyrelay.data.config

import android.content.Context
import notifyrelay.base.util.Logger
import java.io.File

object DeviceInfoManager {
    private const val TAG = "DeviceInfoManager"
    private const val DEVICE_INFO_FILE = "device_info.txt"

    /**
     * 生成供 PC 端 adb 读取的设备信息文件（内容为本机 UUID）。
     * @param deviceUuid 本机 UUID，为空时跳过写入
     */
    fun generateDeviceInfoFile(
        context: Context,
        deviceUuid: String,
    ) {
        try {
            if (deviceUuid.isEmpty()) {
                Logger.e(TAG, "设备 UUID 为空，跳过生成设备信息文件")
                return
            }

            // 创建文件路径
            val externalFilesDir = context.getExternalFilesDir(null)
            val deviceInfoFile = File(externalFilesDir, DEVICE_INFO_FILE)

            // 写入 UUID 到文件
            deviceInfoFile.writeText(deviceUuid)
            Logger.d(TAG, "设备信息文件已生成: ${deviceInfoFile.absolutePath}")
            Logger.d(TAG, "写入的 UUID: $deviceUuid")
        } catch (e: Exception) {
            Logger.e(TAG, "生成设备信息文件失败", e)
        }
    }
}
