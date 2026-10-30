package notifyrelay.desktop.overlay

import com.xzyht.notifyrelay.nativecore.NativeCore
import github.xzynine.superislandui.model.core.parseParamV2
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import notifyrelay.base.log.Logger
import notifyrelay.desktop.DataLayerDesktop
import notifyrelay.domain.feature.appslist.InstalledPackagesPortRegistry
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry

/**
 * 桌面端超级岛入站处理。
 *
 * 报文由 Rust core 归一后写入叠加层状态；界面由叠加层窗口用 Compose 渲染。
 */
internal object DesktopSuperIslandInbound {
    private const val TAG = "DesktopSuperIsland"

    private val json = Json { ignoreUnknownKeys = true }

    /** 处理一条超级岛报文。 */
    fun handle(
        deviceUuid: String,
        payload: String,
    ) {
        // 与 Android 一致：先取原包名并映射为本地包名（sourceKey 含包名）
        val rawPkg =
            runCatching {
                (json.parseToJsonElement(payload) as? JsonObject)?.stringField("packageName")
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
            runCatching { json.parseToJsonElement(parsed) as? JsonObject }.getOrNull() ?: return

        val sourceKey = root.stringField("sourceKey")
        if (sourceKey.isNullOrBlank()) {
            Logger.w(TAG, "超级岛报文缺少 sourceKey，忽略")
            return
        }

        val isEnd = (root["isEnd"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
        if (isEnd) {
            DesktopOverlayStore.removeSuperIsland(sourceKey)
            return
        }

        val paramV2Raw = root.stringField("paramV2Raw")
        val business =
            runCatching {
                paramV2Raw?.let {
                    github.xzynine.superislandui.model.core
                        .parseParamV2(it)
                        ?.business
                }
            }.getOrNull()

        val deviceName = DataLayerDesktop.findOnlineDevice(deviceUuid)?.displayName ?: deviceUuid
        DesktopOverlayStore.putSuperIsland(
            SuperIslandEntry(
                sourceKey = sourceKey,
                deviceName = deviceName,
                title = root.stringField("title"),
                text = root.stringField("text"),
                paramV2Raw = paramV2Raw,
                pics = parsePics(root),
                business = business,
            ),
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
