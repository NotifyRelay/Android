package com.xzyht.notifyrelay.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.ui.dialog.SuperIslandTestDialog
import notifyrelay.data.StorageManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SUPER_ISLAND_KEY = "superisland_enabled"

@Composable
fun UISuperIslandSettings() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(StorageManager.getBoolean(context, SUPER_ISLAND_KEY, true)) }

    // 测试对话框状态
    val showTestDialog = remember { mutableStateOf(false) }

    MiuixTheme {
        val colorScheme = MiuixTheme.colorScheme
        val textStyles = MiuixTheme.textStyles

        Scaffold(
            popupHost = { }, // 置空，避免与顶层Scaffold冲突
            // 悬浮工具栏
            floatingToolbar = {
                FloatingToolbar {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        // 测试按钮
                        Button(
                            onClick = {
                                showTestDialog.value = true
                            }
                        ) {
                            top.yukonga.miuix.kmp.basic.Text("测试超级岛分支")
                        }
                    }
                }
            },
            floatingToolbarPosition = ToolbarPosition.BottomEnd
        ) {
            Surface(color = colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SuperSwitch(
                        title = "超级岛读取",
                        summary = "控制是否尝试从本机通知中读取小米超级岛数据并转发",
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            StorageManager.putBoolean(context, SUPER_ISLAND_KEY, it)
                        }
                    )


                }
            }
        }

        // 显示测试对话框
        SuperIslandTestDialog(showTestDialog, context)
    }
}
