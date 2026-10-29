package com.xzyht.notifyrelay.nativecore.device.appsync

import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import notifyrelay.base.log.Logger
import notifyrelay.data.database.entity.NotificationRecordEntity
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.appslist.InstalledPackagesPortRegistry
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry

/**
 * 入站通知处理器：解析远端通知报文、写入通知历史并归档到聊天记忆。
 *
 * 与 Android 侧 `NotificationRepository.addRemoteNotification` 同语义：
 * - 报文由 Rust core 解析（`parseNotificationInbound`），本类只做字段读取与落库；
 * - 记录键为「时间 + 包名 + 来源设备」，同键原地替换；
 * - 落库后按「包名 + 设备」裁剪到 80 条。
 *
 * 聊天记忆不在此处归档：Android 侧由过滤/复刻链路追加，桌面端由平台侧显式调用，
 * 避免同一报文被记录两次。
 */
object InboundNotificationProcessor {
    private const val TAG = "InboundNotification"

    /** 每个包名在各设备下保留的通知条数上限。 */
    const val MAX_COUNT_PER_PACKAGE = 80

    private const val LOCAL_DEVICE = "本机"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 处理一条已解密的入站通知报文。
     *
     * @param payload 解密后的通知报文明文。
     * @param remoteUuid 来源设备 UUID；为空时不处理（与 Android 侧一致）。
     * @return 写入的历史记录；报文无法解析时返回 null。
     */
    suspend fun process(
        payload: String,
        remoteUuid: String?,
    ): NotificationRecordEntity? {
        if (remoteUuid == null) return null

        val parsedJson = runCatching { NativeCore.parseNotificationInbound(payload) }.getOrNull() ?: return null
        val root =
            runCatching { json.parseToJsonElement(parsedJson) as? JsonObject }.getOrNull()
                ?: return null

        val packageName = root.optString("packageName")
        val appName = root.optString("appName")
        val title = root.optString("title")
        val text = root.optString("text")
        val timeRaw = root.optLong("time")
        val time = if (timeRaw == 0L) System.currentTimeMillis() else timeRaw

        val installedPackages = InstalledPackagesPortRegistry.current?.installedPackagesSync() ?: emptySet()
        val mappedPackage =
            RemoteFilterPortRegistry.current?.mapToLocalPackage(packageName, installedPackages) ?: packageName

        val record =
            NotificationRecordEntity(
                key = buildKey(time, mappedPackage, remoteUuid),
                deviceUuid = remoteUuid,
                packageName = mappedPackage,
                appName = appName.takeIf { it.isNotEmpty() },
                title = title,
                text = text,
                time = time,
            )

        try {
            DatabaseRepository.getInstance().saveNotificationWithLimit(record, MAX_COUNT_PER_PACKAGE)
            Logger.i(TAG, "写入远端通知历史 device=" + remoteUuid + ", pkg=" + mappedPackage)
        } catch (e: IllegalStateException) {
            Logger.e(TAG, "写入远端通知历史失败: device=" + remoteUuid, e)
            return null
        }
        return record
    }

    /** 记录键：时间 + 包名 + 来源设备，与 Android 侧同构。 */
    fun buildKey(
        time: Long,
        packageName: String,
        deviceUuid: String,
    ): String = time.toString() + packageName + deviceUuid

    /** 本机设备标识。 */
    fun localDevice(): String = LOCAL_DEVICE

    /** 读取 JSON 对象的字符串字段；缺失或非字符串时返回空串。 */
    private fun JsonObject.optString(field: String): String =
        (this[field] as? JsonPrimitive)?.content ?: ""

    /** 读取 JSON 对象的 Long 字段；缺失或非数值时返回 0。 */
    private fun JsonObject.optLong(field: String): Long =
        (this[field] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
}
