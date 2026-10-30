package com.xzyht.notifyrelay.nativecore.device.notification

import com.xzyht.notifyrelay.nativecore.device.SendGateway
import com.xzyht.notifyrelay.nativecore.device.appsync.InboundNotificationProcessor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import notifyrelay.base.log.Logger
import notifyrelay.data.database.entity.NotificationRecordEntity
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.data.filter.LocalFilterRepository
import notifyrelay.domain.feature.device.service.contract.DeviceAuthTablePort

/**
 * 本机通知转发网关（平台无关，两平台共用）。
 *
 * 平台侧负责采集本机通知并给出「应用名 / 原始标识 / 标题 / 内容」，本网关统一负责：
 * 1. **匹配应用包名**：按 [LocalNotification.resolvePackage] 决定是否用应用显示名反查已同步的远端应用列表；
 * 2. **过滤**：经 [LocalFilterRepository.shouldForward] 判定是否转发；
 * 3. **构造**：组装 `DATA_NOTIFICATION` 明文；
 * 4. **广播**：发往全部已认证设备；可选按本机标识落库。
 *
 * 两平台差异全部经参数显式表达，不在此处分支：
 * - Android 侧通知已带包名，故不反查；且入站前已由通知库落库，故不重复落库。
 * - Windows 侧应用标识为 AUMID，需要按应用名反查安卓包名；采集层无独立落库，需本网关落库。
 *
 * 字段消费：core `parse_notification_inbound` 只归一 `packageName`/`appName`/`title`/`text`/`time`；
 * 对端（Windows `NotificationService`）在归一之后仍从原始 payload 读取
 * `notificationKey`/`notificationType`/`appIcon`/`isLocked` 等字段，故新增报文按完整字段集写入。
 *
 * 范围约束：本网关**只发送新增通知**。本应用不是镜像通知，不承担撤回语义，
 * 不发起任何非用户触发的移除操作。
 */
object LocalNotificationForwardGateway {
    private const val TAG = "LocalNotification"

    /** 通知类型：新增。 */
    const val TYPE_NEW = "New"

    private val json = Json { encodeDefaults = false }

    /** 本机通知的平台无关视图。 */
    data class LocalNotification(
        /** 平台侧通知标识（Android 为通知键，Windows 为系统通知 id）。 */
        val id: String,
        /** 应用显示名。 */
        val appName: String,
        /** 平台侧原始标识：Android 为包名，Windows 为 AUMID 或包族名。 */
        val rawPackage: String,
        val title: String,
        val text: String,
        /** 是否按 [appName] 反查远端包名；Android 已带包名时为 false。 */
        val resolvePackage: Boolean = false,
        /** 应用图标 base64；为空时不写入报文。 */
        val appIcon: String = "",
        /** 采集时设备是否锁屏。 */
        val isLocked: Boolean = false,
        val time: Long = System.currentTimeMillis(),
        /** 报文中的通知键；默认与 Windows 的 `local_<id>` 同构。 */
        val notificationKey: String = "local_" + id,
    )

    /**
     * 用应用显示名反查已同步应用的包名。
     *
     * 对应 Windows `RemoteAppRepository.FindPackageByAppName`：应用名精确匹配，
     * 取不到时返回 null（调用方回落到原始标识）。
     */
    suspend fun resolvePackageName(appName: String): String? {
        if (appName.isBlank()) return null
        return runCatching { DatabaseRepository.getInstance().getAppByAppName(appName)?.packageName }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * 转发一条本机通知：过滤 → （可选）匹配包名 → 已入库检定 → 构造 → （可选）落库 → 广播。
     *
     * 已入库检定为重复时仍落库与广播，只是不上屏：返回值中的 `showOnScreen` 为 false，
     * 平台层据此跳过弹幕/系统通知复刻。
     *
     * @param notification 采集结果
     * @param deviceManager 设备端口（取已认证设备）
     * @param persistLocally 是否以本机标识写入通知历史；平台已自行落库时传 false
     * @param existingKey 平台已落库时本条记录的键，用于检定排除自身
     * @return 转发结果；被过滤时为 null
     */
    suspend fun forward(
        notification: LocalNotification,
        deviceManager: DeviceAuthTablePort,
        persistLocally: Boolean = false,
        existingKey: String? = null,
    ): ForwardResult? {
        // 过滤用原始标识：与 Windows 一致（ShouldForward 收到的是 appPackage 而非解析结果）
        if (!LocalFilterRepository.shouldForward(notification.rawPackage, notification.appName, notification.title, notification.text)) {
            Logger.d(TAG, "本机通知被过滤: " + notification.appName)
            return null
        }

        val packageName =
            if (notification.resolvePackage) {
                resolvePackageName(notification.appName) ?: notification.rawPackage
            } else {
                notification.rawPackage
            }

        // 已入库检定：与远端入站同一判定入口，命中时只影响上屏
        val duplicate =
            isAlreadyStored(
                packageName = packageName,
                appName = notification.appName,
                title = notification.title,
                text = notification.text,
                excludeKey = existingKey,
            )
        if (duplicate) {
            Logger.d(TAG, "本机通知已入库，仅转发不上屏: " + notification.appName)
        } else {
            InboundNotificationProcessor.markContentSeen(notification.appName, notification.title, notification.text)
        }

        val raw = buildNewPayload(notification, packageName)
        if (persistLocally) persist(raw)
        broadcast(raw, deviceManager)
        Logger.i(TAG, "已转发本机通知: " + notification.appName + " → " + packageName)
        return ForwardResult(showOnScreen = !duplicate)
    }

    /** 转发结果：是否已入库（决定是否上屏）。 */
    data class ForwardResult(
        /** 未命中已入库检定时为 true，平台层据此上屏。 */
        val showOnScreen: Boolean,
    )

    /**
     * 是否与库中记录重复。
     *
     * 判定复用公共入站处理器的 [InboundNotificationProcessor.isDuplicate]：
     * 10 秒内容窗口 → 24 小时内相似度 → 更早记录完全一致。
     */
    private suspend fun isAlreadyStored(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        excludeKey: String?,
    ): Boolean =
        runCatching {
            InboundNotificationProcessor.isDuplicate(packageName, appName, title, text, excludeKey)
        }.getOrDefault(false)

    /** 组装新增通知报文。 */
    private fun buildNewPayload(
        notification: LocalNotification,
        packageName: String,
    ): String {
        val now = notification.time
        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("type", "DATA_NOTIFICATION")
                put("notificationKey", notification.notificationKey)
                put("time", now)
                put("timeStamp", now.toString())
                put("notificationType", TYPE_NEW)
                put("packageName", packageName)
                put("appName", notification.appName)
                put("title", notification.title)
                put("text", notification.text)
                if (notification.appIcon.isNotEmpty()) put("appIcon", notification.appIcon)
                put("isLocked", notification.isLocked)
            },
        )
    }

    /**
     * 以本机标识写入通知历史。
     *
     * 记录键与裁剪上限复用入站处理器的公共实现，保证本机与远端通知同构。
     */
    private suspend fun persist(raw: String) {
        runCatching {
            val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching
            val time = root.stringOrEmpty("time").toLongOrNull() ?: System.currentTimeMillis()
            val packageName = root.stringOrEmpty("packageName")
            val localDevice = InboundNotificationProcessor.localDevice()
            val record =
                NotificationRecordEntity(
                    key = InboundNotificationProcessor.buildKey(time, packageName, localDevice),
                    deviceUuid = localDevice,
                    packageName = packageName,
                    appName = root.stringOrEmpty("appName").takeIf { it.isNotEmpty() },
                    title = root.stringOrEmpty("title"),
                    text = root.stringOrEmpty("text"),
                    time = time,
                )
            DatabaseRepository.getInstance().saveNotificationWithLimit(record, InboundNotificationProcessor.MAX_COUNT_PER_PACKAGE)
        }.onFailure { Logger.w(TAG, "本机通知落库失败", it) }
    }

    /** 广播到全部已认证设备。 */
    private fun broadcast(
        raw: String,
        deviceManager: DeviceAuthTablePort,
    ) {
        val devices = SendGateway.getAuthenticatedDevices(deviceManager)
        if (devices.isEmpty()) {
            Logger.w(TAG, "没有已认证的设备")
            return
        }
        devices.forEach { device ->
            if (!SendGateway.enqueueNotification(device, raw, deviceManager, "本机通知")) {
                Logger.w(TAG, "本机通知入队失败: " + device.displayName)
            }
        }
    }

    /** 读取 JSON 字符串字段。 */
    private fun JsonObject.stringOrEmpty(field: String): String = (this[field] as? JsonPrimitive)?.content.orEmpty()
}
