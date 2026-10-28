package notifyrelay.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import notifyrelay.base.log.Logger
import notifyrelay.data.platform.LegacyStoragePortRegistry

/**
 * 旧通知记录文件的读取入口。
 *
 * 文件本身由平台侧提供（Android 为 `filesDir`，JVM 为调用方指定的目录），
 * 本对象只负责「按设备名定位文件 + 解析 JSON」，与平台无关。
 */
object PersistenceManager {
    /** 通知记录文件名前缀（实际文件名格式为：前缀 + 安全化设备名 + 后缀）。 */
    private const val NOTIFICATION_RECORDS_PREFIX = "notification_records_"

    /** 通知记录文件名的后缀（JSON 文件）。 */
    private const val NOTIFICATION_RECORDS_SUFFIX = ".json"

    /** 设备 UUID 存储键。 */
    private const val DEVICE_UUID_KEY = "device_uuid"

    /** 设备标识安全化为文件名：中文「本机」用 local 替换，其他非法字符替换为下划线。 */
    private fun safeDeviceName(device: String): String = if (device == "本机") "local" else device.replace(Regex("[^a-zA-Z0-9_]"), "_")

    /** 设备对应的旧通知记录文件名。 */
    fun notificationFileName(device: String): String = "$NOTIFICATION_RECORDS_PREFIX" + safeDeviceName(device) + NOTIFICATION_RECORDS_SUFFIX

    /**
     * 读取指定设备的通知记录并解析为 JSON 元素列表。
     *
     * 文件不存在或解析失败时返回空列表；解析失败会删除损坏文件，避免反复出错。
     *
     * @param device 设备标识
     * @return 记录对应的 JSON 数组元素；任何异常均返回空列表
     */
    fun readNotificationRecords(device: String): List<JsonElement> {
        val port = LegacyStoragePortRegistry.current ?: return emptyList()
        val raw = port.readNotificationRecords(device) ?: return emptyList()
        return try {
            val element = Json.parseToJsonElement(raw)
            (element as? JsonArray)?.toList() ?: emptyList()
        } catch (e: Exception) {
            Logger.e("PersistenceManager", "读取设备 " + device + " 的通知记录失败: " + e.message)
            try {
                if (port.deleteFile(notificationFileName(device))) {
                    Logger.w("PersistenceManager", "已删除设备 " + device + " 的损坏通知文件")
                }
            } catch (ex: Exception) {
                Logger.e("PersistenceManager", "删除损坏文件失败: " + ex.message)
            }
            emptyList()
        }
    }

    /** 获取所有旧通知记录文件名。 */
    fun getAllNotificationFiles(): List<String> = LegacyStoragePortRegistry.current?.listNotificationRecordFiles() ?: emptyList()

    /** 获取本地设备的 UUID。 */
    fun getLocalDeviceUuid(): String = Storage.getString(DEVICE_UUID_KEY, prefsType = Storage.PrefsType.GENERAL)
}
