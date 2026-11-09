package notifyrelay.shared.ui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 本模块的统一弹窗外壳。
 *
 * 参数取 [DialogDefaults] 的默认值，并开启遮罩与窗口内边距。
 *
 * @param show 是否显示。
 * @param title 标题。
 * @param summary 副标题；null 表示不显示。
 * @param onDismissRequest 请求关闭时的回调。
 * @param content 弹窗内容。
 */
@Composable
fun NotifyRelayDialog(
    show: Boolean,
    title: String,
    summary: String?,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    WindowDialog(
        show = show,
        modifier = Modifier,
        title = title,
        titleColor = DialogDefaults.titleColor(),
        summary = summary,
        summaryColor = DialogDefaults.summaryColor(),
        backgroundColor = DialogDefaults.backgroundColor(),
        enableWindowDim = true,
        onDismissRequest = onDismissRequest,
        onDismissFinished = null,
        outsideMargin = DialogDefaults.outsideMargin,
        insideMargin = DialogDefaults.insideMargin,
        defaultWindowInsetsPadding = true,
        content = { content() },
    )
}
