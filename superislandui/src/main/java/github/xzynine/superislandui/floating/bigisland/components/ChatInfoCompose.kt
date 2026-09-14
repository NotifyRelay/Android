package github.xzynine.superislandui.floating.bigisland.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.common.PreviewData
import github.xzynine.superislandui.floating.common.CircularProgressCompose
import github.xzynine.superislandui.floating.common.SuperIslandImageUtil
import github.xzynine.superislandui.model.core.ParamV2
import notifyrelay.core.util.image.ImageUtils

/**
 * IM图文组件（chatInfo）Compose 实现
 * 版式（MD 结构图）：圆角方形头像（右下角叠加应用图标徽标）+ 主要文本 + 次要文本
 */
@Composable
fun ChatInfoCompose(
    paramV2: ParamV2,
    picMap: Map<String, String>?,
) {
    val chatInfo = paramV2.chatInfo ?: return

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像（圆角方形，圆角取尺寸 1/4）+ 右下角应用图标徽标
        val avatarUrl = chatInfo.picProfile?.let { picMap?.get(it) }
        // 应用图标：优先自定义 appIconPkg，其次系统注入的桌面图标
        val badgeUrl =
            chatInfo.appIconPkg?.let { picMap?.get(it) }
                ?: picMap?.get("miui.focus.pic_app_icon")
        if (!avatarUrl.isNullOrEmpty() || !badgeUrl.isNullOrEmpty()) {
            Box(modifier = Modifier.size(48.dp)) {
                if (!avatarUrl.isNullOrEmpty()) {
                    SuperIslandImageUtil.rememberSuperIslandImagePainter(avatarUrl)?.let {
                        Image(
                            painter = it,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
                if (!badgeUrl.isNullOrEmpty()) {
                    SuperIslandImageUtil.rememberSuperIslandImagePainter(badgeUrl)?.let {
                        Image(
                            painter = it,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                        )
                    }
                }
            }
        }

        // 圆形进度条（IM + 进度场景，从 actions/progressInfo 获取）
        val actionWithProgress = paramV2.actions?.firstOrNull { it.progressInfo != null }
        val progressInfo = actionWithProgress?.progressInfo ?: paramV2.progressInfo

        if (progressInfo != null) {
            Spacer(modifier = Modifier.width(8.dp))

            val progressColor = ImageUtils.parseColor(progressInfo.colorProgress) ?: 0xFF3482FF.toInt()
            val trackColor =
                ImageUtils.parseColor(progressInfo.colorProgressEnd)
                    ?: ((progressColor and 0x00FFFFFF) or (0x33 shl 24))

            CircularProgressCompose(
                progress = progressInfo.progress,
                colorReach = Color(progressColor),
                colorUnReach = Color(trackColor),
                strokeWidth = 3.5.dp,
                isClockwise = true,
                size = 48.dp,
            )
        }

        // 文本内容
        Column(modifier = Modifier.weight(1f)) {
            chatInfo.title?.let {
                Text(
                    text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                    color = Color(ImageUtils.parseColor(chatInfo.colorTitle) ?: 0xFFFFFFFF.toInt()),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            chatInfo.content?.let {
                Text(
                    text = SuperIslandImageUtil.parseSimpleHtmlToAnnotatedString(it),
                    color = Color(ImageUtils.parseColor(chatInfo.colorContent) ?: 0xFFDDDDDD.toInt()),
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Preview(name = "聊天信息", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun ChatInfoComposePreview() {
    ChatInfoCompose(
        paramV2 = PreviewData.sampleParamV2WithChat,
        picMap = PreviewData.samplePicMap,
    )
}
