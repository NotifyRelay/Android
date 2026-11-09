package github.xzynine.superislandui.floating.common

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 本包组件的 Compose 预览。 */

@Preview(name = "通用文本块", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun CommonTextBlockComposePreview() {
    CommonTextBlockCompose(
        frontTitle = "前置标题",
        title = "主标题",
        content = "内容描述",
        narrow = false,
        highlight = true,
        monospace = false,
    )
}

@Preview(name = "圆形进度环", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun CircularProgressComposePreview() {
    CircularProgressCompose(
        progress = 60,
        colorReach = Color(0xFF3482FF),
        colorUnReach = Color(0x33333333),
        strokeWidth = 3.dp,
        isClockwise = true,
        size = 48.dp,
    )
}

@Preview(name = "超级岛根布局", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun SuperIslandComposeRootPreview() {
    SuperIslandComposeRoot(
        content = {
            CommonTextBlockCompose(
                frontTitle = null,
                title = "超级岛标题",
                content = "超级岛内容",
                narrow = false,
                highlight = false,
                monospace = false,
            )
        },
    )
}
