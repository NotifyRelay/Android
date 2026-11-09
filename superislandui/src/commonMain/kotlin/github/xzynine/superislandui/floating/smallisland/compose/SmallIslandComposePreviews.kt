package github.xzynine.superislandui.floating.smallisland.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import github.xzynine.superislandui.common.PreviewData
import github.xzynine.superislandui.floating.smallisland.left.AImageText1
import github.xzynine.superislandui.floating.smallisland.left.AImageText5
import github.xzynine.superislandui.floating.smallisland.right.BImageText2
import github.xzynine.superislandui.floating.smallisland.right.BProgressTextInfo
import github.xzynine.superislandui.floating.smallisland.right.BTextInfo

/** 本包组件的 Compose 预览。 */

@Preview(name = "A区组件类型1", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun AComposeType1Preview() {
    ACompose(
        aComp =
            AImageText1(
                title = "A区标题",
                content = "A区内容",
                showHighlightColor = true,
            ),
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "A区组件类型5", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun AComposeType5Preview() {
    ACompose(
        aComp =
            AImageText5(
                title = "A区标题",
                content = "A区内容",
                picKey = "icon_key",
            ),
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "B区文本组件", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun BComposeTextInfoPreview() {
    BCompose(
        bComp =
            BTextInfo(
                title = "B区标题",
                content = "B区内容",
            ),
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "B区图文组件2", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun BComposeImageText2Preview() {
    BCompose(
        bComp =
            BImageText2(
                title = "图文标题",
                content = "图文内容",
                picKey = "icon_key",
            ),
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "B区进度文本组件", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun BComposeProgressTextInfoPreview() {
    BCompose(
        bComp =
            BProgressTextInfo(
                title = "下载中",
                content = "60%",
                progress = 60,
            ),
        picMap = PreviewData.samplePicMap,
    )
}
