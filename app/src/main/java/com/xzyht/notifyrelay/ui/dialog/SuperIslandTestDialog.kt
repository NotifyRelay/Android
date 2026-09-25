package com.xzyht.notifyrelay.ui.dialog

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.data.StorageManager
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 超级岛测试对话框，用于测试不同分支下的效果
 */
@Composable
fun SuperIslandTestDialog(
    show: MutableState<Boolean>,
    context: Context,
) {
    // 进度可变开关状态
    var isVariableProgress by remember { mutableStateOf(false) }
    // 发送开关状态，从持久化存储中读取
    var isSendEnabled by remember {
        mutableStateOf(StorageManager.getBoolean(context, "superisland_send_to_other_devices", false))
    }

    WindowDialog(show = show.value, modifier = Modifier, title = "超级岛测试", titleColor = DialogDefaults.titleColor(), summary = "点击下方按钮测试不同分支下的超级岛效果", summaryColor = DialogDefaults.summaryColor(), backgroundColor = DialogDefaults.backgroundColor(), enableWindowDim = true, onDismissRequest = { show.value = false }, onDismissFinished = null, outsideMargin = DialogDefaults.outsideMargin, insideMargin = DialogDefaults.insideMargin, defaultWindowInsetsPadding = true, content = {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            // 进度可变开关
            SwitchPreference(
                title = "测试设置",
                summary = if (isVariableProgress) "进度可变 (测试动画效果)" else "进度固定 (测试静态效果)",
                checked = isVariableProgress,
                onCheckedChange = { isVariableProgress = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )

            // 发送开关
            SwitchPreference(
                title = "发送设置",
                summary = if (isSendEnabled) "发送到其他设备" else "仅本地测试",
                checked = isSendEnabled,
                onCheckedChange = {
                    isSendEnabled = it
                    StorageManager.putBoolean(context, "superisland_send_to_other_devices", it)
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    // 基础文本组件
                    Button(
                        onClick = {
                            testBaseInfo(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试基础文本组件 (baseInfo)")
                    }
                }

                item {
                    // IM图文组件
                    Button(
                        onClick = {
                            testChatInfo(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试IM图文组件 (chatInfo)")
                    }
                }

                item {
                    // 动画文本组件
                    Button(
                        onClick = {
                            testAnimTextInfo(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试动画文本组件 (animTextInfo)")
                    }
                }

                item {
                    // 强调图文组件
                    Button(
                        onClick = {
                            testHighlightInfo(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试强调图文组件 (highlightInfo)")
                    }
                }

                item {
                    // 识别图形组件
                    Button(
                        onClick = {
                            testPicInfo(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试识别图形组件 (picInfo)")
                    }
                }

                item {
                    // 提示组件
                    Button(
                        onClick = {
                            testHintInfo(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试提示组件 (hintInfo)")
                    }
                }

                item {
                    // 文本按钮组件
                    Button(
                        onClick = {
                            testTextButton(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试文本按钮组件 (textButton)")
                    }
                }

                item {
                    // 线性进度组件
                    Button(
                        onClick = {
                            testProgressInfo(context, isVariableProgress)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试线性进度组件 (progressInfo)")
                    }
                }

                item {
                    // 多节点进度组件
                    Button(
                        onClick = {
                            testMultiProgressInfo(context, isVariableProgress)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试多节点进度组件 (multiProgressInfo)")
                    }
                }

                item {
                    // 带有图标的多节点进度组件
                    Button(
                        onClick = {
                            testMultiProgressWithIcons(context, isVariableProgress)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试带图标多节点进度组件")
                    }
                }

                item {
                    // 12306 车票超级岛样例（包名 com.MobileTicket，对齐 Apple Watch 转发链路）
                    Button(
                        onClick = {
                            testTicket12306(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试 12306 车票超级岛 (com.MobileTicket)")
                    }
                }

                item {
                    // 圆形进度组件
                    Button(
                        onClick = {
                            testCircularProgressInfo(context, isVariableProgress)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("测试圆形进度组件 (circular)")
                    }
                }
            }
        }
    })
}
