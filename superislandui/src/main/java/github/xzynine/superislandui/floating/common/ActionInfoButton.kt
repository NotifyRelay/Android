package github.xzynine.superislandui.floating.common

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.model.components.ActionInfo
import notifyrelay.core.util.image.ImageUtils

/**
 * 圆头图文按钮（actionInfo）：圆角背景 + 可选图标 + 文本
 * 供按钮组件2/3（hintInfo）、按钮组件5（highlightInfoV3）等复用
 */
@Composable
fun ActionInfoButton(
    action: ActionInfo,
    picMap: Map<String, String>? = null,
) {
    val uiMode = LocalConfiguration.current.uiMode
    val preferDark = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val context = LocalContext.current
    val iconKey = if (preferDark) action.actionIconDark ?: action.actionIcon else action.actionIcon
    var iconUrl: String? = resolveIconUrl(picMap, iconKey, context)
    if (iconUrl.isNullOrEmpty()) {
        iconUrl = resolveIconUrl(picMap, action.actionIcon, context)
    }

    val titleColor =
        Color(
            (if (preferDark) ImageUtils.parseColor(action.actionTitleColorDark) else ImageUtils.parseColor(action.actionTitleColor))
                ?: 0xFFFFFFFF.toInt(),
        )
    val bgColor =
        Color(
            (if (preferDark) ImageUtils.parseColor(action.actionBgColorDark) else ImageUtils.parseColor(action.actionBgColor))
                ?: 0xFF3482FF.toInt(),
        )

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (!iconUrl.isNullOrEmpty()) {
            val painter = SuperIslandImageUtil.rememberSuperIslandImagePainter(iconUrl)
            painter?.let {
                Image(
                    painter = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        action.actionTitle?.let { title ->
            androidx.compose.material3.Text(
                text = title,
                color = titleColor,
                fontSize = 13.sp,
                modifier = if (!iconUrl.isNullOrEmpty()) Modifier.padding(start = 4.dp) else Modifier,
            )
        }
    }
}
