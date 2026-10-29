package notifyrelay.desktop.device

import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import notifyrelay.base.log.Logger
import notifyrelay.desktop.DataLayerDesktop
import notifyrelay.desktop.platform.DesktopOverlayHost
import notifyrelay.domain.feature.appslist.InstalledPackagesPortRegistry
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry

/**
 * 桌面端超级岛入站处理。
 *
 * 与 Android 侧同源：报文由 Rust core 的 `parseSuperIslandInbound` 归一，
 * 本类只做字段读取并转投叠加层；超级岛的布局解析与绘制由叠加层自行完成
 * （其 `SuperIslandParamV2Parser` 消费 `paramV2Raw`），不在 JVM 侧重复实现。
 */
internal object DesktopSuperIslandRemoteHandler {
    private const val TAG = "DesktopSuperIsland"

    private val json = Json { ignoreUnknownKeys = true }

    /** 处理一条超级岛报文。 */
    fun onSuperIsland(
        deviceUuid: String,
        payload: String,
    ) {
        // 与 Android SuperIslandProcessor 一致：先取原包名并映射为本地包名，
        // 再交给 core 归一（sourceKey 由 uuid|pkg|featureId 拼成，pkg 会影响去重键）
        val rawPkg =
            runCatching {
                (json.parseToJsonElement(payload) as? JsonObject)
                    ?.stringField("packageName")
            }.getOrNull().orEmpty()
        val installed = InstalledPackagesPortRegistry.current?.installedPackagesSync() ?: emptySet()
        val mappedPkg =
            runCatching {
                RemoteFilterPortRegistry.current?.mapToLocalPackage(rawPkg, installed)
            }.getOrNull() ?: rawPkg

        val parsed =
            runCatching {
                NativeCore.parseSuperIslandInbound(deviceUuid, mappedPkg, payload)
            }.getOrNull() ?: return
        val root =
            runCatching { json.parseToJsonElement(parsed) as? JsonObject }.getOrNull()
                ?: return

        val sourceKey = root.stringField("sourceKey")
        if (sourceKey.isNullOrBlank()) {
            Logger.w(TAG, "超级岛报文缺少 sourceKey，忽略")
            return
        }

        // 会话结束：从叠加层移除
        val isEnd = (root["isEnd"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
        if (isEnd) {
            DesktopOverlayHost.removeSuperIsland(sourceKey)
            Logger.d(TAG, "超级岛已结束: " + sourceKey)
            return
        }

        val deviceName = DataLayerDesktop.findOnlineDevice(deviceUuid)?.displayName ?: deviceUuid
        DesktopOverlayHost.showSuperIsland(
            sourceId = sourceKey,
            deviceName = deviceName,
            title = root.stringField("title"),
            text = root.stringField("text"),
            paramV2Raw = root.stringField("paramV2Raw"),
            pics = parsePics(root),
        )
    }

    /** 读取 pics 映射；缺失或空时返回 null。 */
    private fun parsePics(root: JsonObject): Map<String, String>? {
        val pics = root["pics"] as? JsonObject ?: return null
        val map = mutableMapOf<String, String>()
        pics.forEach { (k, v) ->
            val s = (v as? JsonPrimitive)?.jsonPrimitive?.content
            if (!s.isNullOrEmpty()) map[k] = s
        }
        return map.ifEmpty { null }
    }

    /** 读取 JSON 字符串字段。 */
    private fun JsonObject.stringField(field: String): String? = (this[field] as? JsonPrimitive)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
}
