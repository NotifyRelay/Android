package github.xzynine.superislandui.floating.bigisland.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.common.AutoFitText
import github.xzynine.superislandui.common.AutoScrollText
import github.xzynine.superislandui.common.PreviewData
import github.xzynine.superislandui.floating.common.rememberSuperIslandImagePainter
import github.xzynine.superislandui.model.components.MediaSessionData
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val COVER_SIZE = 80

@Composable
fun MediaIslandCompose(
    mediaSession: MediaSessionData,
    isExpanded: Boolean = true,
    showControls: Boolean = true,
    onCollapse: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    ExpandedMediaIsland(
        mediaSession = mediaSession,
        showControls = showControls,
        onPlayPause = onPlayPause,
        onPrevious = onPrevious,
        onNext = onNext,
        onClose = onClose,
    )
}

@Composable
private fun ExpandedMediaIsland(
    mediaSession: MediaSessionData,
    showControls: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            val painter =
                rememberSuperIslandImagePainter(
                    url = mediaSession.coverUrl,
                )

            // 封面图片：有图显示图，无图显示默认音符占位
            Box(
                modifier =
                    Modifier
                        .size(COVER_SIZE.dp)
                        .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (painter != null) {
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = "歌曲封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "\u266A",
                            fontSize = 32.sp,
                            color = Color(0xFF888888.toInt()),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                AutoScrollText(
                    text = mediaSession.title.ifBlank { "未知" },
                    style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFFFFFFFF.toInt()),
                    baseSpeedPxPerSec = 150f,
                    pauseMillis = 0,
                )

                Spacer(modifier = Modifier.height(4.dp))

                AutoFitText(
                    text = mediaSession.text.ifBlank { "未知" },
                    style = MiuixTheme.textStyles.body2,
                    color = Color(0xFFDDDDDD.toInt()),
                    minTextSize = 10f,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "来自: ${mediaSession.deviceName}",
                    style = MiuixTheme.textStyles.body2,
                    color = Color(0xFFDDDDDD.toInt()),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 进度条与时间：仅有进度信息时显示，位于正文与操作按钮之间
        val progressFraction = mediaSession.progressFraction
        if (progressFraction != null) {
            Spacer(modifier = Modifier.height(10.dp))
            MediaProgressRow(
                fraction = progressFraction,
                positionMs = mediaSession.positionMs ?: 0L,
                durationMs = mediaSession.durationMs ?: 0L,
            )
        }

        if (!showControls) return@Column

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .clickable { onPrevious() }
                        .padding(8.dp),
            ) {
                Text(
                    text = "上一首",
                    style = MiuixTheme.textStyles.body2,
                    color = Color(0xFFFFFFFF.toInt()),
                )
            }

            Box(
                modifier =
                    Modifier
                        .clickable { onPlayPause() }
                        .padding(8.dp),
            ) {
                Text(
                    text = if (mediaSession.isPlaying) "暂停" else "播放",
                    style = MiuixTheme.textStyles.body2,
                    color = Color(0xFFFFFFFF.toInt()),
                )
            }

            Box(
                modifier =
                    Modifier
                        .clickable { onNext() }
                        .padding(8.dp),
            ) {
                Text(
                    text = "下一首",
                    style = MiuixTheme.textStyles.body2,
                    color = Color(0xFFFFFFFF.toInt()),
                )
            }
        }
    }
}

/**
 * 播放进度：一条细进度条加两端时间。
 *
 * 进度比例由调用方按已播时长与总时长算出，本组件只负责绘制，
 * 因此暂停时进度停在原处、不自行推进。
 */
@Composable
private fun MediaProgressRow(
    fraction: Float,
    positionMs: Long,
    durationMs: Long,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PROGRESS_TRACK_HEIGHT)
                    .clip(RoundedCornerShape(PROGRESS_TRACK_HEIGHT / 2))
                    .background(Color(0x33FFFFFF)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxSize()
                        .clip(RoundedCornerShape(PROGRESS_TRACK_HEIGHT / 2))
                        .background(PROGRESS_COLOR),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(positionMs),
                style = MiuixTheme.textStyles.body2,
                color = Color(0xB3FFFFFF),
                fontSize = 11.sp,
            )
            Text(
                text = formatDuration(durationMs),
                style = MiuixTheme.textStyles.body2,
                color = Color(0xB3FFFFFF),
                fontSize = 11.sp,
            )
        }
    }
}

/** 毫秒格式化为 `m:ss` 或 `h:mm:ss`；非正数返回 `0:00`。 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        hours.toString() + ":" + minutes.toString().padStart(2, '0') + ":" + seconds.toString().padStart(2, '0')
    } else {
        minutes.toString() + ":" + seconds.toString().padStart(2, '0')
    }
}

/** 进度条高度。 */
private val PROGRESS_TRACK_HEIGHT = 3.dp

/** 已播进度颜色。 */
private val PROGRESS_COLOR = Color(0xFF40C4FF)

@Preview(name = "媒体播放器", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun MediaIslandComposePreview() {
    MediaIslandCompose(
        mediaSession = PreviewData.sampleMediaSessionData,
    )
}
