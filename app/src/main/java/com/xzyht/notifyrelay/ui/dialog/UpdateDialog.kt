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
import androidx.compose.ui.unit.dp
import github.xzynine.checkupdata.model.ReleaseInfo
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdateDialog(
    showDialog: MutableState<Boolean>,
    releaseInfo: ReleaseInfo,
    currentVersion: String,
    onDownload: (ReleaseInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val scrollState = rememberScrollState()
    
    WindowDialog(
        title = "发现新版本",
        summary = "当前版本: $currentVersion → 新版本: ${releaseInfo.version}",
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
                text = releaseInfo.name,
                style = textStyles.title3,
                color = colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (releaseInfo.releaseNotes.isNotEmpty()) {
                Text(
                    text = "更新内容:",
                    style = textStyles.body1,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = releaseInfo.releaseNotes,
                    style = textStyles.body2,
                    color = colorScheme.onSurfaceSecondary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(
                text = "下载更新",
                onClick = {
                    onDownload(releaseInfo)
                    showDialog.value = false
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(
                text = "取消",
                onClick = {
                    showDialog.value = false
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
