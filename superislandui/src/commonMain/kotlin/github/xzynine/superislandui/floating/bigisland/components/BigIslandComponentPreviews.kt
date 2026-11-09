package github.xzynine.superislandui.floating.bigisland.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import github.xzynine.superislandui.common.PreviewData

/** 本包组件的 Compose 预览。 */

@Preview(name = "操作按钮", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun ActionComposePreview() {
    ActionCompose(
        actions = PreviewData.sampleActions,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "动画文本信息", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun AnimTextInfoComposePreview() {
    AnimTextInfoCompose(
        animTextInfo = PreviewData.sampleAnimTextInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "动画文本带计时器", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun AnimTextInfoComposeWithTimerPreview() {
    AnimTextInfoCompose(
        animTextInfo = PreviewData.sampleAnimTextInfoWithTimer,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "文本组件1", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun BaseInfoComposeType1Preview() {
    BaseInfoCompose(
        baseInfo = PreviewData.sampleBaseInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "文本组件2", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun BaseInfoComposeType2Preview() {
    BaseInfoCompose(
        baseInfo = PreviewData.sampleBaseInfoType2,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "聊天信息", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun ChatInfoComposePreview() {
    ChatInfoCompose(
        paramV2 = PreviewData.sampleParamV2WithChat,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "封面组件", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun CoverInfoComposePreview() {
    CoverInfoCompose(
        coverInfo = PreviewData.sampleCoverInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "默认状态", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun DefaultComposePreview() {
    DefaultCompose()
}

@Preview(name = "有标题内容", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun DefaultComposeWithDataPreview() {
    DefaultCompose(
        title = "通知标题",
        content = "这是通知内容的详细描述信息",
        image = null,
    )
}

@Preview(name = "强调图文", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HighlightInfoComposePreview() {
    HighlightInfoCompose(
        highlightInfo = PreviewData.sampleHighlightInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "强调图文带计时器", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HighlightInfoComposeWithTimerPreview() {
    HighlightInfoCompose(
        highlightInfo = PreviewData.sampleHighlightInfoWithTimer,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "按钮组件5", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HighlightInfoV3ComposePreview() {
    HighlightInfoV3Compose(
        highlightInfoV3 = PreviewData.sampleHighlightInfoV3,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "按钮组件5-无图标", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HighlightInfoV3ComposeNoIconPreview() {
    HighlightInfoV3Compose(
        highlightInfoV3 =
            PreviewData.sampleHighlightInfoV3.copy(
                actionInfo = PreviewData.sampleHighlightInfoV3.actionInfo?.copy(actionIcon = null),
            ),
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "按钮组件3", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HintInfoComposeType1Preview() {
    HintInfoCompose(
        hintInfo = PreviewData.sampleHintInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "按钮组件2", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun HintInfoComposeType2Preview() {
    HintInfoCompose(
        hintInfo = PreviewData.sampleHintInfoType2,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "新图文组件", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun IconTextInfoComposePreview() {
    IconTextInfoCompose(
        iconTextInfo = PreviewData.sampleIconTextInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "媒体播放器", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun MediaIslandComposePreview() {
    MediaIslandCompose(
        mediaSession = PreviewData.sampleMediaSessionData,
    )
}

@Preview(name = "多进度条50%", showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360)
@Composable
fun MultiProgressComposePreview() {
    MultiProgressCompose(
        multiProgressInfo = PreviewData.sampleMultiProgressInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "多进度条完成", showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360)
@Composable
fun MultiProgressComposeCompletePreview() {
    MultiProgressCompose(
        multiProgressInfo = PreviewData.sampleMultiProgressInfoComplete,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "图片信息类型1", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun PicInfoComposeType1Preview() {
    PicInfoCompose(
        picInfo = PreviewData.samplePicInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "图片信息类型2", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun PicInfoComposeType2Preview() {
    PicInfoCompose(
        picInfo = PreviewData.samplePicInfoType2,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "进度条60%", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun ProgressComposePreview() {
    ProgressCompose(
        progressInfo = PreviewData.sampleProgressInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "进度条25%", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun ProgressComposeLowPreview() {
    ProgressCompose(
        progressInfo = PreviewData.sampleProgressInfoLow,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "进度组件1(带节点)", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun ProgressComposeWithNodesPreview() {
    ProgressCompose(
        progressInfo = PreviewData.sampleProgressInfoWithNodes,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "进度信息", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun ProgressInfoComposePreview() {
    ProgressInfoCompose(
        progressInfo = PreviewData.sampleProgressInfo,
    )
}

@Preview(name = "文本按钮", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun TextButtonComposePreview() {
    TextButtonCompose(
        textButton = PreviewData.sampleTextButton,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "计时器正计时", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun TimerInfoComposePreview() {
    TimerInfoCompose(
        timerInfo = PreviewData.sampleTimerInfo,
        picMap = PreviewData.samplePicMap,
    )
}

@Preview(name = "计时器倒计时", showBackground = true, backgroundColor = 0xFF000000, widthDp = 360)
@Composable
fun TimerInfoComposeCountdownPreview() {
    TimerInfoCompose(
        timerInfo = PreviewData.sampleTimerInfoCountdown,
        picMap = PreviewData.samplePicMap,
    )
}
