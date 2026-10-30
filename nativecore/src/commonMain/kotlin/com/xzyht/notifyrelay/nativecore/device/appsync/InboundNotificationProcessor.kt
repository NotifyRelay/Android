package com.xzyht.notifyrelay.nativecore.device.appsync

import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import notifyrelay.base.log.Logger
import notifyrelay.data.database.entity.NotificationRecordEntity
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.domain.feature.appslist.InstalledPackagesPortRegistry
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry

/**
 * 入站通知处理器：解析远端通知报文、写入通知历史并判定是否已入库。
 *
 * - 报文由 Rust core 解析（`parseNotificationInbound`），本类只做字段读取与落库；
 * - 记录键为「时间 + 包名 + 来源设备」，同键原地替换；
 * - 落库后按「包名 + 设备」裁剪到 [MAX_COUNT_PER_PACKAGE] 条；
 * - 「已入库检定」唯一判定处为 [isDuplicate]，本机与远端两条路径共用。
 *
 * 聊天记忆不在此处归档：平台侧按自身链路追加，避免同一报文被记录两次。
 */
object InboundNotificationProcessor {
    private const val TAG = "InboundNotification"

    /** 每个包名在各设备下保留的通知条数上限。 */
    const val MAX_COUNT_PER_PACKAGE = 80

    /** 相似度比对的历史窗口：窗口内按相似度判定，窗口外仅比对完全一致。 */
    const val SIMILARITY_WINDOW_MS = 24 * 60 * 60 * 1000L

    /** 相似度比对的候选记录条数上限（按时间倒序取最近的）。 */
    const val SIMILARITY_CANDIDATE_LIMIT = 200

    private const val LOCAL_DEVICE = "本机"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 处理一条已解密的入站通知报文：解析 → 已入库检定 → 落库。
     *
     * 检定必须在落库之前完成，否则本条记录会被自身命中。
     *
     * @param payload 解密后的通知报文明文。
     * @param remoteUuid 来源设备 UUID；为空时不处理（与 Android 侧一致）。
     * @return 写入的历史记录与检定结果；报文无法解析时返回 null。
     */
    suspend fun process(
        payload: String,
        remoteUuid: String?,
    ): InboundResult? {
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

        val duplicate = isDuplicate(mappedPackage, appName, title, text)

        try {
            DatabaseRepository.getInstance().saveNotificationWithLimit(record, MAX_COUNT_PER_PACKAGE)
            Logger.i(TAG, "写入远端通知历史 device=" + remoteUuid + ", pkg=" + mappedPackage + ", 重复=" + duplicate)
        } catch (e: IllegalStateException) {
            Logger.e(TAG, "写入远端通知历史失败: device=" + remoteUuid, e)
            return null
        }
        return InboundResult(record = record, duplicate = duplicate)
    }

    /** 入站处理结果。 */
    data class InboundResult(
        /** 已写入的历史记录。 */
        val record: NotificationRecordEntity,
        /** 与库中既有记录重复（含 10 秒窗口命中）。 */
        val duplicate: Boolean,
    )

    /**
     * 已入库检定：判断一条通知是否与库中记录重复。
     *
     * 判定顺序：
     * 1. core 的 10 秒内容窗口（键为「应用名 + 标题 + 正文」）；
     * 2. [SIMILARITY_WINDOW_MS] 内的同包名候选记录，按「应用名指向同一应用 + 标题与正文相似度」判定；
     * 3. 窗口之外的记录仅比对完全一致。
     *
     * 本方法只查询、不写窗口状态；确认展示后由调用方调 [markContentSeen]。
     *
     * @param packageName 已映射的本机等价包名。
     * @param appName 应用显示名。
     * @param scopeDeviceUuid 限定比对的设备维度；为空时跨全部设备。本机采集链路传 [localDevice]，
     *   远端入站链路留空。
     * @param excludeKey 需排除的记录键：平台已把本条写入库时传入，避免自我命中。
     * @return 是否判为重复。
     */
    suspend fun isDuplicate(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        scopeDeviceUuid: String? = null,
        excludeKey: String? = null,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (NativeCore.isReady() && NativeCore.contentDedup(CONTENT_DEDUP_QUERY, appName, title, text) == 1) {
            Logger.d(TAG, "10 秒内容窗口命中: app=" + appName)
            return true
        }

        val repository =
            runCatching { DatabaseRepository.getInstance() }.getOrNull()
                ?: return false

        val recent =
            runCatching {
                repository.getRecentNotificationsByPackage(
                    packageName = packageName,
                    sinceTime = now - SIMILARITY_WINDOW_MS,
                    limit = SIMILARITY_CANDIDATE_LIMIT,
                    deviceUuid = scopeDeviceUuid,
                )
            }.getOrElse {
                Logger.w(TAG, "读取相似度候选失败: pkg=" + packageName, it)
                return false
            }

        val windowHit =
            recent
                .filter { record -> excludeKey == null || record.key != excludeKey }
                .any { record ->
                    NativeCore.shouldDeduplicate(
                        appName,
                        title,
                        text,
                        record.appName.orEmpty(),
                        record.title.orEmpty(),
                        record.text.orEmpty(),
                    )
                }
        if (windowHit) {
            Logger.d(TAG, "24 小时内相似记录命中: pkg=" + packageName + ", app=" + appName)
            return true
        }

        val exactOld =
            runCatching {
                repository.hasNotificationWithExactContent(packageName, appName, title, text, scopeDeviceUuid)
            }.getOrDefault(false)
        if (exactOld) {
            Logger.d(TAG, "24 小时外完全一致记录命中: pkg=" + packageName)
        }
        return exactOld
    }

    /**
     * 标记内容已见（core 的 10 秒窗口）。
     *
     * 仅在确认展示后调用；被判定为重复的条目不再刷新窗口。
     */
    fun markContentSeen(
        appName: String,
        title: String,
        text: String,
    ) {
        if (!NativeCore.isReady()) return
        NativeCore.contentDedup(CONTENT_DEDUP_MARK, appName, title, text)
    }

    /** 清除 core 的 10 秒内容窗口。 */
    fun clearContentWindow() {
        if (!NativeCore.isReady()) return
        NativeCore.contentDedup(CONTENT_DEDUP_CLEAR, "", "", "")
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
    private fun JsonObject.optString(field: String): String = (this[field] as? JsonPrimitive)?.content ?: ""

    /** 读取 JSON 对象的 Long 字段；缺失或非数值时返回 0。 */
    private fun JsonObject.optLong(field: String): Long = (this[field] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

    private const val CONTENT_DEDUP_QUERY = 0
    private const val CONTENT_DEDUP_MARK = 1
    private const val CONTENT_DEDUP_CLEAR = 2
}
