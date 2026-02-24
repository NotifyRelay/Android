package com.xzyht.notifyrelay.ui.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import github.xzynine.checkupdata.model.ReleaseInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun UpdateDialog(
    showDialog: MutableState<Boolean>,
    releaseInfo: ReleaseInfo?,
    currentVersion: String,
    hasUpdate: Boolean,
    allReleases: List<ReleaseInfo>,
    onDownload: (ReleaseInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val scrollState = rememberScrollState()
    
    val statusColor = if (hasUpdate) Color(0xFFE53935) else Color(0xFF43A047)
    val statusText = if (hasUpdate) "发现新版本，建议更新" else "当前已是最新版本"
    val remoteVersion = releaseInfo?.version ?: "未知"
    
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    
    if (hasUpdate && releaseInfo != null) {
        expandedGroups[releaseInfo.version] = true
    }
    
    WindowDialog(
        title = "版本检查结果",
        summary = "当前版本: $currentVersion | 远端版本: $remoteVersion",
        show = showDialog,
        onDismissRequest = {
            showDialog.value = false
            onDismiss()
        }
    ) {
        BackHandler(onBack = {
            showDialog.value = false
            onDismiss()
        })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = statusText,
                style = textStyles.title3,
                color = statusColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasUpdate && releaseInfo != null) {
                    TextButton(
                        text = "下载更新",
                        onClick = {
                            showDialog.value = false
                            onDismiss()
                            onDownload(releaseInfo)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                TextButton(
                    text = "关闭",
                    onClick = {
                        showDialog.value = false
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            
            if (allReleases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "历史版本:",
                    style = textStyles.body1,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                allReleases.forEach { release ->
                    val isExpanded = expandedGroups[release.version] ?: false
                    val isLatestUpdate = hasUpdate && releaseInfo != null && release.version == releaseInfo.version
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { expandedGroups[release.version] = !isExpanded },
                        cornerRadius = 8.dp,
                        insideMargin = PaddingValues(12.dp),
                        colors = CardDefaults.defaultColors(
                            color = if (isLatestUpdate) colorScheme.primaryContainer else colorScheme.surface,
                            contentColor = if (isLatestUpdate) colorScheme.onPrimaryContainer else colorScheme.onSurface
                        ),
                        showIndication = !isExpanded,
                        pressFeedbackType = if (isExpanded) PressFeedbackType.None else PressFeedbackType.Sink
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = release.name.ifEmpty { "v${release.version}" },
                                style = textStyles.body1,
                                color = if (isLatestUpdate) colorScheme.primary else colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (release.isPrerelease) {
                                Text(
                                    text = "(预发布)",
                                    style = textStyles.main,
                                    color = colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (isExpanded) "收起" else "展开",
                                style = textStyles.body2,
                                color = colorScheme.primary
                            )
                        }
                        
                        if (isExpanded && release.releaseNotes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = release.releaseNotes,
                                style = textStyles.body2,
                                color = colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
