package notifyrelay.desktop.device

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import notifyrelay.base.log.Logger
import notifyrelay.desktop.DataLayerDesktop
import notifyrelay.domain.feature.device.model.DeviceInfo
import notifyrelay.domain.feature.media.MediaControlAction
import notifyrelay.domain.feature.media.MediaSessionSnapshot

/**
 * 桌面端远端媒体报文处理。
 *
 * - `onMediaPlay`：远端播放状态回推，解析后更新媒体端口供页面展示；
 * - `onMediaControl`：远端下发的控制指令，以系统媒体键在本机执行。
 *
 * 解析复用 core 的 `parseMediaInbound`，与 Android 侧同一套归一化逻辑。
 */
internal object DesktopMediaRemoteHandler {
    private const val TAG = "DesktopMedia"

    private val json = Json { ignoreUnknownKeys = true }

    /** 处理远端播放状态回推。 */
    fun onMediaPlay(
        device: DeviceInfo,
        payload: String,
    ) {
        val parsed =
            runCatching {
                com.xzyht.notifyrelay.nativecore.NativeCore
                    .parseMediaInbound(payload)
            }.getOrNull()
                ?: return
        val root =
            runCatching { json.parseToJsonElement(parsed) as? JsonObject }
                .getOrNull()
                ?: return

        // 字段口径以 core 的 parse_media_inbound 为准：
        // 歌手在 text（无 artist 字段）；封面被重命名为 coverImage；isEnd 表示会话结束。
        val isEnd =
            (root["isEnd"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.content
                ?.toBooleanStrictOrNull() ?: false
        if (isEnd) {
            DataLayerDesktop.mediaControl?.updateRemoteSession(device, null)
            Logger.d(TAG, "远端媒体会话已结束: device=" + device.displayName)
            return
        }

        val snapshot =
            MediaSessionSnapshot(
                packageName = root.stringField("packageName"),
                title = root.stringField("title"),
                artist = root.stringField("text"),
                coverUrl = root.stringField("coverImage"),
            )
        DataLayerDesktop.mediaControl?.updateRemoteSession(device, snapshot)
        Logger.d(TAG, "收到远端媒体状态: device=" + device.displayName)
    }

    /**
     * 处理远端媒体控制指令。
     *
     * 指令字段与 Windows 端一致：`playbackActionType` 取 `Play`/`Pause`/`playPause`/`Next`/`Previous`。
     */
    fun onMediaControl(payload: String) {
        val root =
            runCatching { json.parseToJsonElement(payload) as? JsonObject }
                .getOrNull()
                ?: return
        val actionType = root.stringField("playbackActionType") ?: root.stringField("action") ?: return

        val action =
            when (actionType.lowercase()) {
                "play", "pause", "playpause" -> MediaControlAction.PLAY_PAUSE
                "next" -> MediaControlAction.NEXT
                "previous" -> MediaControlAction.PREVIOUS
                else -> {
                    Logger.d(TAG, "忽略未支持的媒体指令: " + actionType)
                    return
                }
            }
        DataLayerDesktop.mediaControl?.localControl(action)
        Logger.i(TAG, "已执行远端媒体指令: " + actionType)
    }

    /** 读取 JSON 对象的字符串字段。 */
    private fun JsonObject.stringField(field: String): String? =
        (this[field] as? kotlinx.serialization.json.JsonPrimitive)
            ?.jsonPrimitive
            ?.content
            ?.takeIf { it.isNotBlank() }
}
