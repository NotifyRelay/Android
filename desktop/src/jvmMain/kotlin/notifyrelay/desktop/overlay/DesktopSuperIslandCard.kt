package notifyrelay.desktop.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.xzynine.superislandui.floating.bigisland.components.SuperIslandTemplateCompose
import github.xzynine.superislandui.floating.smallisland.compose.BigIslandCollapsedCompose
import github.xzynine.superislandui.model.core.ParamV2
import github.xzynine.superislandui.model.core.parseParamV2
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** 超级岛展开后的自动收起时长。 */
private const val SUPER_ISLAND_AUTO_COLLAPSE_MS = 3_000L

/** 展开态卡片宽度（与媒体卡同宽）。 */
private val SUPER_ISLAND_CARD_WIDTH = 420.dp

/** 超级岛无更新时的存活时长（非媒体类型）。 */
internal const val SUPER_ISLAND_TIMEOUT_MS = 12_000L

/** 媒体类型超级岛的存活时长（长于轮询刷新周期）。 */
internal const val SUPER_ISLAND_MEDIA_TIMEOUT_MS = 45_000L

/** 仅摘要显示的业务标识（手电筒）：不展开为大岛。 */
private const val BUSINESS_SUMMARY_ONLY = "miui_flashlight"

/** 媒体封面在 picMap 中的键。 */
private const val COVER_PIC_KEY = "miui.focus.pic_cover"

/**
 * 超级岛卡片。
 *
 * 展开态按 paramV2 选择对应的渲染方式（媒体大岛 / 摘要区 A-B / 模板组件），
 * 超时后收起为摘要胶囊；内容（标题/正文/paramV2）变化时重新展开并重置计时。
 */
@Composable
internal fun SuperIslandCard(entry: SuperIslandEntry) {
    val contentKey =
        entry.title.orEmpty() + "\u0001" + entry.text.orEmpty() + "\u0001" + entry.paramV2Raw.orEmpty()
    val paramV2 = remember(entry.paramV2Raw) { parseParamV2OrNull(entry.paramV2Raw) }
    // 仅摘要类（如手电筒）不展开：与 Android FloatingWindowManager 的 summaryOnly 同口径
    val summaryOnly =
        paramV2?.business == BUSINESS_SUMMARY_ONLY ||
            entry.paramV2Raw?.contains(BUSINESS_SUMMARY_ONLY) == true
    var expanded by remember { mutableStateOf(!summaryOnly) }
    LaunchedEffect(contentKey, summaryOnly) {
        if (summaryOnly) {
            expanded = false
            return@LaunchedEffect
        }
        expanded = true
        delay(SUPER_ISLAND_AUTO_COLLAPSE_MS)
        expanded = false
    }

    if (expanded) {
        ExpandedSuperIslandCard(entry, paramV2)
    } else {
        BigIslandCollapsedCompose(
            bigIsland = extractBigIsland(entry.paramV2Raw),
            picMap = entry.pics,
            fallbackTitle = entry.title,
            fallbackContent = entry.text,
            picFunction = paramV2?.picFunction,
            aodPic = paramV2?.aodPic,
        )
    }
}

/** 展开态：复用共享的超级岛总装（媒体大岛 / 摘要区 / 模板组件）。 */
@Composable
private fun ExpandedSuperIslandCard(
    entry: SuperIslandEntry,
    paramV2: ParamV2?,
) {
    // 共享总装内部用 fillMaxWidth，叠加层里需自行限定卡片宽度
    Box(Modifier.width(SUPER_ISLAND_CARD_WIDTH)) {
        SuperIslandTemplateCompose(
            paramV2 = paramV2,
            picMap = entry.pics,
            fallbackTitle = entry.title,
            fallbackContent = entry.text,
            mediaAppName = entry.deviceName,
            mediaControlsVisible = false,
        )
    }
}

/** 解析 paramV2；失败或为空时返回 null。 */
private fun parseParamV2OrNull(raw: String?): ParamV2? {
    if (raw.isNullOrBlank()) return null
    return runCatching { parseParamV2(raw) }.getOrNull()
}

/** 从 paramV2 原始 JSON 中取 bigIslandArea 子树，供摘要胶囊渲染。 */
private fun extractBigIsland(raw: String?): JsonObject? {
    if (raw.isNullOrBlank()) return null
    val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    val island =
        (root["param_island"] ?: root["paramIsland"] ?: root["islandParam"])?.let {
            runCatching { it.jsonObject }.getOrNull()
        } ?: return null
    return (island["bigIslandArea"] ?: island["bigIsland"])?.let {
        runCatching { it.jsonObject }.getOrNull()
    }
}
