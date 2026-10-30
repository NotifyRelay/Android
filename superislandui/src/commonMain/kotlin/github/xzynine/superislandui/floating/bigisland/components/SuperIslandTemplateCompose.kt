package github.xzynine.superislandui.floating.bigisland.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.floating.common.CommonImageCompose
import github.xzynine.superislandui.floating.common.SuperIslandComposeRoot
import github.xzynine.superislandui.model.components.MediaSessionData
import github.xzynine.superislandui.model.core.ParamV2
import top.yukonga.miuix.kmp.basic.Text

/** 媒体封面在 picMap 中的键。 */
private const val COVER_PIC_KEY = "miui.focus.pic_cover"

/**
 * 超级岛展开态总装：按 paramV2 选择媒体大岛、摘要区或模板组件，并追加进度组件。
 *
 * 两个平台共用同一套分派；平台差异经回调与参数表达。
 *
 * @param paramV2 已解析的 param_v2；为空时回落到标题/正文
 * @param picMap 图片键值表
 * @param fallbackTitle 无 paramV2 时的标题
 * @param fallbackContent 无 paramV2 时的正文
 * @param mediaAppName 媒体类型时的应用名（同时作为设备名）
 * @param mediaControlsVisible 媒体卡是否显示控制按钮
 * @param isOverlapping 卡片是否处于重叠提示态
 * @param onCopyVerificationCode 复制验证码回调；为空时不渲染复制按钮
 * @param onPlayPause 播放/暂停回调
 * @param onPrevious 上一首回调
 * @param onNext 下一首回调
 * @param onCollapseClick 收起回调
 */
@Composable
fun SuperIslandTemplateCompose(
    paramV2: ParamV2?,
    picMap: Map<String, String>? = null,
    fallbackTitle: String? = null,
    fallbackContent: String? = null,
    mediaAppName: String? = null,
    mediaControlsVisible: Boolean = true,
    isOverlapping: Boolean = false,
    onCopyVerificationCode: ((String) -> Unit)? = null,
    onPlayPause: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onCollapseClick: () -> Unit = {},
) {
    SuperIslandComposeRoot(
        content = {
            when {
                paramV2 == null -> {
                    FallbackCompose(
                        picMap = picMap,
                        title = fallbackTitle,
                        content = fallbackContent,
                    )
                }

                paramV2.business == "media" -> {
                    MediaIslandCompose(
                        mediaSession =
                            MediaSessionData(
                                packageName = mediaAppName.orEmpty(),
                                appName = mediaAppName,
                                title = fallbackTitle.orEmpty(),
                                text = fallbackContent.orEmpty(),
                                coverUrl = picMap?.get(COVER_PIC_KEY) ?: picMap?.values?.firstOrNull(),
                                deviceName = mediaAppName.orEmpty(),
                            ),
                        isExpanded = true,
                        showControls = mediaControlsVisible,
                        onCollapse = onCollapseClick,
                        onPlayPause = onPlayPause,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onClose = onCollapseClick,
                    )
                }

                paramV2.paramIsland != null -> {
                    ParamIslandCompose(
                        paramIsland = paramV2.paramIsland,
                        actions = paramV2.actions,
                        picMap = picMap,
                        onCopyVerificationCode = onCopyVerificationCode,
                    )
                }

                else -> {
                    TemplateCompose(paramV2, picMap)
                }
            }

            // 进度组件：主链之后独立追加
            if (paramV2?.multiProgressInfo != null) {
                MultiProgressCompose(
                    paramV2.multiProgressInfo,
                    picMap = picMap,
                    business = paramV2.business,
                )
            } else if (paramV2?.progressInfo != null) {
                ProgressCompose(paramV2.progressInfo, picMap)
            }
        },
        isOverlapping = isOverlapping,
    )
}

/** 无 paramV2 时的兜底：A 区焦点图标 + 标题/正文。 */
@Composable
private fun FallbackCompose(
    picMap: Map<String, String>?,
    title: String?,
    content: String?,
) {
    Box(modifier = Modifier.padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CommonImageCompose(
                picKey = null,
                picMap = picMap,
                size = 24.dp,
                isFocusIcon = true,
                contentDescription = null,
            )

            Column(modifier = Modifier.padding(start = 8.dp)) {
                if (!title.isNullOrEmpty()) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (!content.isNullOrEmpty()) {
                    Text(
                        text = content,
                        color = Color(0xFFDDDDDD),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

/** 组合模板：主内容组件 + 右侧识别图形 + 底部按钮区。 */
@Composable
private fun TemplateCompose(
    paramV2: ParamV2,
    picMap: Map<String, String>?,
) {
    val hasPrimaryContent =
        paramV2.baseInfo != null ||
            paramV2.chatInfo != null ||
            paramV2.animTextInfo != null ||
            paramV2.iconTextInfo != null ||
            paramV2.coverInfo != null ||
            paramV2.highlightInfo != null
    val hasPicInfo = paramV2.picInfo != null
    val hasButton =
        paramV2.highlightInfoV3 != null ||
            paramV2.hintInfo != null ||
            paramV2.textButton != null ||
            !paramV2.actions.isNullOrEmpty()

    if (!hasPrimaryContent && !hasPicInfo && !hasButton) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(text = "未支持的模板", color = Color.White)
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (hasPrimaryContent || hasPicInfo) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        paramV2.baseInfo != null -> BaseInfoCompose(paramV2.baseInfo, picMap)
                        paramV2.chatInfo != null -> ChatInfoCompose(paramV2, picMap)
                        paramV2.animTextInfo != null -> AnimTextInfoCompose(paramV2.animTextInfo, picMap)
                        paramV2.iconTextInfo != null -> IconTextInfoCompose(paramV2.iconTextInfo, picMap)
                        paramV2.coverInfo != null -> CoverInfoCompose(paramV2.coverInfo, picMap)
                        paramV2.highlightInfo != null -> HighlightInfoCompose(paramV2.highlightInfo, picMap)
                        paramV2.picInfo != null -> PicInfoCompose(paramV2.picInfo, picMap)
                    }
                }
                if (hasPicInfo && hasPrimaryContent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CommonImageCompose(
                        picKey = paramV2.picInfo.pic,
                        picMap = picMap,
                        size = 28.dp,
                    )
                }
            }
        }

        if (hasButton) {
            if (hasPrimaryContent || hasPicInfo) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x33FFFFFF)),
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            when {
                paramV2.highlightInfoV3 != null -> HighlightInfoV3Compose(paramV2.highlightInfoV3, picMap)
                paramV2.hintInfo != null -> HintInfoCompose(paramV2.hintInfo, picMap)
                paramV2.textButton != null -> TextButtonCompose(paramV2.textButton, picMap)
            }
            // 动作区独立于上方分支：actions 非空即渲染，避免被其它按钮组件吞掉
            if (!paramV2.actions.isNullOrEmpty()) {
                ActionCompose(paramV2.actions, picMap)
            }
        }
    }
}
