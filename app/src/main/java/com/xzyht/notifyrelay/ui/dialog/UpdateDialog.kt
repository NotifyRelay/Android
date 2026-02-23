package com.xzyht.notifyrelay.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import github.xzynine.checkupdata.model.ReleaseInfo
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdateDialog(
    showDialog: MutableState<Boolean>,
    releaseInfo: ReleaseInfo?,
    currentVersion: String,
    hasUpdate: Boolean,
    onDownload: (ReleaseInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val scrollState = rememberScrollState()
    
    val statusColor = if (hasUpdate) Color(0xFFE53935) else Color(0xFF43A047)
    val statusText = if (hasUpdate) "发现新版本，建议更新" else "当前已是最新版本"
    val remoteVersion = releaseInfo?.version ?: "未知"
    
    WindowDialog(
        title = "版本检查结果",
        summary = "当前版本: $currentVersion | 远端版本: $remoteVersion",
        show = showDialog,
        onDismissRequest = {
            showDialog.value = false
            onDismiss()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = statusText,
                style = textStyles.title3,
                color = statusColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (releaseInfo != null) {
                Text(
                    text = releaseInfo.name,
                    style = textStyles.body1,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (releaseInfo.releaseNotes.isNotEmpty()) {
                    Text(
                        text = "更新内容:",
                        style = textStyles.body2,
                        color = colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = releaseInfo.releaseNotes,
                        style = textStyles.body2,
                        color = colorScheme.onSurfaceSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (hasUpdate && releaseInfo != null) {
                TextButton(
                    text = "下载更新",
                    onClick = {
                        showDialog.value = false
                        onDismiss()
                        onDownload(releaseInfo)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            TextButton(
                text = "关闭",
                onClick = {
                    showDialog.value = false
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
