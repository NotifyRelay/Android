package com.xzyht.notifyrelay.ui.guide

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GuideAgreementPage(
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    var agreed by rememberSaveable { mutableStateOf(false) }

    val notices =
        listOf(
            "仅局域网传输" to "通知、剪贴板、文件等内容只会在你已配对并连接的设备之间传输，应用不会将数据上传到第三方服务器。",
            "注意通知隐私" to "通知可能包含聊天消息、验证码等敏感信息。请仅在可信设备上开启通知转发，并妥善保管配对密钥。",
            "按需授予权限" to "通知监听、通知发送和应用列表权限是必要权限；蓝牙、悬浮窗、文件管理、后台无限制等可选权限可以在系统设置中随时更改。",
            "功能存在系统差异" to "不同厂商对自启动、后台运行和敏感通知的管理策略不同，请在系统设置中按页面提示逐项确认。",
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
    ) {
        GuidePageHeader(
            stepLabel = "2 / 6",
            title = "使用须知与授权说明",
            subtitle = "请阅读以下说明，确认后继续",
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    notices.forEachIndexed { index, notice ->
                        Row(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = "${index + 1}.",
                                style = MiuixTheme.textStyles.body1,
                                color = colorScheme.primary,
                                modifier = Modifier.width(24.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = notice.first,
                                    style = MiuixTheme.textStyles.body1,
                                    color = colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notice.second,
                                    style = MiuixTheme.textStyles.body2,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    state = if (agreed) ToggleableState.On else ToggleableState.Off,
                    onClick = { agreed = !agreed },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "我已阅读并同意上述使用须知与授权说明",
                    style = MiuixTheme.textStyles.body1,
                    color = colorScheme.onSurface,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable { agreed = !agreed },
                )
            }
        }

        GuidePageFooter(
            hint = if (agreed) null else "请先阅读并同意使用须知",
            nextText = "同意并继续",
            nextEnabled = agreed,
            onBack = onBack,
            onNext = onNext,
        )
    }
}
