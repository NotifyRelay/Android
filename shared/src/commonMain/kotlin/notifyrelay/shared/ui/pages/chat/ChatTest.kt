package notifyrelay.shared.ui.pages.chat

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import notifyrelay.domain.feature.device.service.contract.DeviceChatPort
import notifyrelay.domain.feature.notification.chat.ChatMemory
import notifyrelay.shared.ui.port.LocalClipboardPort
import notifyrelay.shared.ui.port.LocalToastPort
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 聊天测试页。
 *
 * 页面只负责展示与输入：发送动作经 [onSendMessage] 注入（平台侧决定发送实现），
 * 历史来自公共 [ChatMemory]。
 */
@Composable
fun UIChatTest(
    deviceManager: DeviceChatPort,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val clipboardPort = LocalClipboardPort.current
    val toastPort = LocalToastPort.current

    var chatInput by rememberSaveable { mutableStateOf("") }
    val chatHistoryState = remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        chatHistoryState.value = ChatMemory.getHistory()
    }
    val notificationCallback: (String) -> Unit =
        remember {
            { _: String ->
                chatHistoryState.value = ChatMemory.getHistory()
            }
        }
    DisposableEffect(deviceManager) {
        deviceManager.registerOnNotificationDataReceived(notificationCallback)
        onDispose {
            deviceManager.unregisterOnNotificationDataReceived(notificationCallback)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(top = 8.dp)) {
        val listState = remember { LazyListState() }
        val chatList = chatHistoryState.value
        var firstLoad by remember { mutableStateOf(true) }
        LaunchedEffect(chatList.size) {
            if (chatList.isNotEmpty()) {
                if (firstLoad) {
                    listState.scrollToItem(chatList.lastIndex)
                    firstLoad = false
                } else {
                    listState.animateScrollToItem(chatList.lastIndex)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
        ) {
            items(chatList) { msg ->
                val isSend = msg.startsWith("发送:")
                val display = msg.removePrefix("发送:").removePrefix("收到:").trim()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isSend) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        color = if (isSend) colorScheme.primaryContainer else colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier =
                            Modifier
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        if (clipboardPort.copyText("message", msg)) {
                                            toastPort.short("已复制原始消息到剪贴板")
                                        } else {
                                            toastPort.short("复制失败")
                                        }
                                    },
                                ),
                    ) {
                        val textToShow = if (display.isNotEmpty()) display else "(无内容)"
                        Text(
                            text = textToShow,
                            style = textStyles.body2,
                            color = if (isSend) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer,
                            maxLines = Int.MAX_VALUE,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            TextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                modifier = Modifier.weight(1f),
                label = "输入消息...",
            )
            Button(
                onClick = {
                    onSendMessage(chatInput)
                    chatHistoryState.value = ChatMemory.getHistory()
                    chatInput = ""
                },
                enabled = deviceManager.devices.value.isNotEmpty() && chatInput.isNotBlank(),
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                Text("发送")
            }
            Button(
                onClick = {
                    ChatMemory.clear()
                    chatHistoryState.value = ChatMemory.getHistory()
                },
                modifier =
                    Modifier
                        .align(Alignment.CenterVertically)
                        .padding(start = 4.dp),
            ) {
                Text("清空对话")
            }
        }
    }
}
