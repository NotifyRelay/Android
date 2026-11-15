package notifyrelay.shared.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.domain.feature.device.model.HandshakeRequest
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 握手请求弹窗。
 *
 * 关闭逻辑由父组件的 NavigationBackHandler 统一处理。
 *
 * @param showDialog 显示状态。
 * @param handshakeRequest 待确认的请求；null 时本组件不渲染。
 * @param onAccept 同意。
 * @param onReject 拒绝。
 * @param onDismiss 关闭。
 */
@Composable
fun HandshakeRequestDialog(
    showDialog: MutableState<Boolean>,
    handshakeRequest: HandshakeRequest?,
    onAccept: (HandshakeRequest) -> Unit,
    onReject: (HandshakeRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    if (handshakeRequest == null) return

    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val req = handshakeRequest

    NotifyRelayDialog(
        show = showDialog.value,
        title = "新设备连接请求",
        summary = null,
        onDismissRequest = {
            onReject(req)
            onDismiss()
        },
    ) {
        Column {
            Text(
                text = "设备名: ${req.device.displayName}",
                style = textStyles.body2,
                color = colorScheme.onSurfaceContainerVariant,
            )
            Text(
                text = "UUID: ${req.device.uuid}",
                style = textStyles.body2,
                color = colorScheme.onSurfaceContainerVariant,
            )
            Text(
                text = "IP: ${req.device.ip}  端口: ${req.device.port}",
                style = textStyles.body2,
                color = colorScheme.onSurfaceContainerVariant,
            )
            if (!req.publicKey.isNullOrBlank()) {
                Text(
                    text = "公钥: ${req.publicKey}",
                    style = textStyles.body2,
                    color = colorScheme.onSurfaceContainerVariant,
                )
            }
            Text(
                text = "是否允许该设备连接？",
                style = textStyles.body2,
                color = colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = "同意",
                    onClick = {
                        onAccept(req)
                        onDismiss()
                    },
                )
                TextButton(
                    text = "拒绝",
                    onClick = {
                        onReject(req)
                        onDismiss()
                    },
                )
            }
        }
    }
}
