package notifyrelay.shared.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.shared.ui.port.LocalClipboardSyncPort
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 剪贴板同步页。
 *
 * 只提供开关与状态展示：收发判定（去重、防循环、频率限制、大小阈值）由 Rust core 承担，
 * 平台实现经 [LocalClipboardSyncPort] 注入。
 */
@Composable
fun ClipboardSyncPage(modifier: Modifier = Modifier) {
    val port = LocalClipboardSyncPort.current ?: error("LocalClipboardSyncPort 未提供")
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    val enabled by port.enabled.collectAsState()
    val watching by port.watching.collectAsState()
    val lastResult by port.lastResult.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SwitchPreference(
            title = "剪贴板同步",
            summary = "开启后本机与已连接的设备之间自动同步剪贴板文本与图片",
            checked = enabled,
            onCheckedChange = { port.setEnabled(it) },
        )

        Text(
            text =
                when {
                    !enabled -> "已关闭"
                    !watching -> "已开启，但未能监听系统剪贴板"
                    else -> "监听中"
                },
            style = textStyles.body2,
            color = if (enabled && watching) colorScheme.primary else colorScheme.onSurfaceSecondary,
        )

        Button(
            onClick = { port.syncNow() },
            enabled = enabled && watching,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("立即同步当前剪贴板")
        }

        lastResult?.let { result ->
            Text(
                text = "最近一次：" + result,
                style = textStyles.body2,
                color = colorScheme.onSurfaceSecondary,
            )
        }

        Text(
            text = "说明：内容未变、来自对端的回环内容与过于频繁的改动会被自动忽略；" +
                "超过 2MB 的图片改为文件传输。",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
        )
    }
}
