package com.xzyht.notifyrelay.feature.notification.superisland.floating

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import com.xzyht.notifyrelay.feature.clipboard.ClipboardSyncManager
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import github.xzynine.superislandui.floating.bigisland.components.SuperIslandTemplateCompose
import github.xzynine.superislandui.floating.smallisland.compose.BigIslandCollapsedCompose
import github.xzynine.superislandui.model.core.ParamV2
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import notifyrelay.base.log.Logger
import notifyrelay.base.util.ClipboardUtils
import notifyrelay.base.util.ToastUtils
import notifyrelay.shared.ui.viewmodel.MediaControlViewModel
import notifyrelay.shared.ui.viewmodel.mediaControlViewModel
import org.json.JSONObject

/**
 * 浮窗条目数据类，对应原有EntryRecord
 */
@Stable
data class FloatingEntry(
    val key: String,
    val paramV2: ParamV2?,
    val paramV2Raw: String?,
    val picMap: Map<String, String>?,
    val isExpanded: Boolean,
    val summaryOnly: Boolean,
    val business: String?,
    val title: String? = null,
    val text: String? = null,
    val appName: String? = null,
    val isOverlapping: Boolean = false,
    val height: Int = 0, // 实际高度，由Compose组件测量后设置
)

/**
 * 超级岛浮窗父容器组件
 */
@Composable
fun FloatingWindowContainer(
    entries: List<FloatingEntry>,
    onEntryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onUpdateEntryHeight: ((String, Int) -> Unit)? = null,
    isContainerDragging: Boolean = false,
    mediaViewModel: MediaControlViewModel = mediaControlViewModel(),
) {
    LocalContext.current

    // 当entries列表为空时，不渲染任何内容，避免拦截触摸事件
    if (entries.isEmpty()) {
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // 遍历所有条目，按顺序显示（最新的在顶部）
        entries.forEach { entry ->
            // 使用key函数确保Compose能正确识别不同的条目，特别是当条目内容更新时
            key(entry.key) {
                // 创建一个无交互源，用于移除点击效果
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null, // 移除默认点击效果
                                onClick = {
                                    // 只有当容器未拖动时才执行点击操作
                                    if (!isContainerDragging) {
                                        onEntryClick(entry.key)
                                    }
                                },
                            ).onGloballyPositioned {
                                // 测量条目实际高度并更新
                                val measuredHeight = it.size.height
                                onUpdateEntryHeight?.invoke(entry.key, measuredHeight)
                            },
                    contentAlignment = Alignment.Center, // 确保内容居中对齐
                ) {
                    // 简化动画，避免闪烁
                    val expandedEnterTransition =
                        slideInVertically {
                            // 从顶部滑入
                            -it / 2 // 减少滑入距离，使动画更平滑
                        } + fadeIn()

                    val expandedExitTransition =
                        slideOutVertically {
                            // 向顶部滑出
                            -it / 2 // 减少滑出距离，使动画更平滑
                        } + fadeOut()

                    val collapsedEnterTransition =
                        slideInVertically {
                            // 从底部滑入
                            it / 2 // 减少滑入距离，使动画更平滑
                        } + fadeIn()

                    val collapsedExitTransition =
                        slideOutVertically {
                            // 向底部滑出
                            it / 2 // 减少滑出距离，使动画更平滑
                        } + fadeOut()

                    // 展开态内容
                    androidx.compose.animation.AnimatedVisibility(
                        visible = entry.isExpanded,
                        modifier = Modifier,
                        enter = expandedEnterTransition,
                        exit = expandedExitTransition,
                    ) {
                        val context = LocalContext.current
                        SuperIslandTemplateCompose(
                            paramV2 = entry.paramV2,
                            picMap = entry.picMap,
                            fallbackTitle = entry.title,
                            fallbackContent = entry.text,
                            mediaAppName = entry.appName,
                            isOverlapping = entry.isOverlapping,
                            onCopyVerificationCode = { code ->
                                if (code.contains("*")) {
                                    ToastUtils.showShortToast(context, "请解锁对端设备")
                                } else if (!ClipboardUtils.copyText(context, "verification code", code)) {
                                    Logger.e("ParamIslandCompose", "复制验证码失败")
                                    ToastUtils.showShortToast(context, "复制失败")
                                } else {
                                    runCatching {
                                        ClipboardSyncManager.syncTextDirectly(
                                            DeviceConnectionManagerSingleton.getDeviceQuery(context),
                                            code,
                                            context,
                                        )
                                        ToastUtils.showShortToast(context, "验证码已复制")
                                    }.onFailure {
                                        Logger.e("ParamIslandCompose", "复制验证码失败", it)
                                        ToastUtils.showShortToast(context, "复制失败")
                                    }
                                }
                            },
                            onPlayPause = { mediaViewModel.playPauseLocal() },
                            onPrevious = { mediaViewModel.previousLocal() },
                            onNext = { mediaViewModel.nextLocal() },
                            onCollapseClick = { onEntryClick(entry.key) },
                        )
                    }

                    // 摘要态内容
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !entry.isExpanded,
                        modifier = Modifier,
                        enter = collapsedEnterTransition,
                        exit = collapsedExitTransition,
                    ) {
                        // 提取回落文本：摘要态优先显示有意义的title，appName作为补充
                        val fallbackTitle = entry.title?.takeIf { it.isNotBlank() }
                        val fallbackContent = entry.appName?.takeIf { it.isNotBlank() }

                        // 从paramV2Raw中正确解析bigIslandJson，与传统实现保持一致
                        // 使用remember块确保entry.paramV2Raw变化时重新解析
                        // 合并三次解析为一次，避免重复解析同一个字符串
                        val parsedRoot =
                            remember(entry.paramV2Raw) {
                                entry.paramV2Raw?.let {
                                    try {
                                        JSONObject(it)
                                    } catch (e: Exception) {
                                        Logger.w("超级岛", "解析paramV2Raw失败: ${e.message}, 原始数据: $it")
                                        null
                                    }
                                }
                            }

                        // 从parsedRoot中提取bigIslandArea，并转换为 UI 侧入参的 kotlinx JSON 树；
                        // 转换失败按 null 处理，与原实现的兜底一致
                        val bigIslandJson =
                            remember(parsedRoot) {
                                parsedRoot?.let { root ->
                                    val island =
                                        root.optJSONObject("param_island")
                                            ?: root.optJSONObject("paramIsland")
                                            ?: root.optJSONObject("islandParam")
                                    val bigIslandArea =
                                        island?.optJSONObject("bigIslandArea") ?: island?.optJSONObject(
                                            "bigIsland",
                                        )
                                    bigIslandArea?.let {
                                        runCatching { Json.parseToJsonElement(it.toString()).jsonObject }.getOrNull()
                                    }
                                }
                            }

                        // 优先从entry.paramV2中提取aodPic，然后回退到parsedRoot
                        val aodPic =
                            remember(entry.paramV2, parsedRoot) {
                                entry.paramV2?.aodPic ?: parsedRoot?.optString("aodPic", "")?.takeIf { it.isNotBlank() }
                            }

                        // 优先从entry.paramV2中提取picFunction，然后回退到parsedRoot
                        val picFunction =
                            remember(entry.paramV2, parsedRoot) {
                                entry.paramV2?.picFunction ?: entry.paramV2?.highlightInfo?.picFunction
                                    ?: parsedRoot
                                        ?.optJSONObject("highlightInfo")
                                        ?.optString("picFunction", "")
                                        ?.takeIf { it.isNotBlank() }
                            }

                        // 直接显示摘要态内容，不添加额外的Box包装，避免方形背景
                        BigIslandCollapsedCompose(
                            bigIsland = bigIslandJson,
                            picMap = entry.picMap,
                            fallbackTitle = fallbackTitle,
                            fallbackContent = fallbackContent,
                            isOverlapping = entry.isOverlapping,
                            picFunction = picFunction,
                            aodPic = aodPic,
                        )
                    }
                    // 闭合Box组件
                }
            }
        }
    }
}
