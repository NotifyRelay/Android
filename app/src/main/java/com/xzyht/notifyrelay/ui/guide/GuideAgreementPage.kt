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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GuideAgreementPage(
    onBack: () -> Unit,
    onNext: () -> Unit,
    reauth: Boolean = false,
    needConsent: Boolean = false,
) {
    val colorScheme = MiuixTheme.colorScheme
    var agreed by rememberSaveable { mutableStateOf(reauth) }
    val context = LocalContext.current

    val notices =
        listOf(
            "仅局域网传输" to "通知、剪贴板、文件等内容只会在你已配对并连接的设备之间传输，应用不会将数据上传到第三方服务器。",
            "注意通知隐私" to "通知可能包含聊天消息、验证码等敏感信息。请仅在可信设备上开启通知转发，并妥善保管配对密钥。",
            "按需授予权限" to "通知监听、通知发送和应用列表权限是必要权限；蓝牙、悬浮窗、文件管理、后台无限制等可选权限可以在系统设置中随时更改。",
            "功能存在系统差异" to "不同厂商对自启动、后台运行和敏感通知的管理策略不同，请在系统设置中按页面提示逐项确认。",
        )

    // 已申请的权限及用途：声明式权限的用途说明来自 AndroidManifest.xml 的 <uses-permission>
    // 上方注释，由构建任务 generatePermissionNotes 提取并写入 res/raw/permission_notes.txt
    // （格式：权限名||用途，逐行）。维护点唯一为 Manifest 注释，协议页不做任何硬编码，
    // 避免与 Manifest 注释双重维护。通知访问（通知监听服务）非 <uses-permission> 声明、Manifest
    // 无逐条注释，作为唯一固定补充项列出。
    val declaredPermissionNotes =
        runCatching {
            context.resources
                .openRawResource(R.raw.permission_notes)
                .bufferedReader()
                .use { it.readText() }
        }.getOrDefault("")
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val idx = line.indexOf("||")
                if (idx < 0) null else line.substring(0, idx) to line.substring(idx + 2)
            }.toList()

    val registeredPermissions =
        declaredPermissionNotes +
            listOf(
                "通知访问（通知监听服务）" to "读取并转发已安装应用的通知内容，是本应用实现消息转发与通知管理的核心能力，需在系统设置中手动开启。",
            )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
    ) {
        GuidePageHeader(
            stepLabel = if (reauth || needConsent) "2 / 3" else "2 / 6",
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

            // 已注册权限清单：逐项列出本应用申请的权限及其用途，供用户阅读确认。
            GuideSectionLabel(title = "已申请的权限及用途", description = "")
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    registeredPermissions.forEachIndexed { index, perm ->
                        Row(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = "${index + 1}.",
                                style = MiuixTheme.textStyles.body1,
                                color = colorScheme.primary,
                                modifier = Modifier.width(24.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = perm.first,
                                    style = MiuixTheme.textStyles.body1,
                                    color = colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = perm.second,
                                    style = MiuixTheme.textStyles.body2,
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }

            if (!reauth) {
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
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        GuidePageFooter(
            hint = if (reauth) null else if (agreed) null else "请先阅读并同意使用须知",
            nextText = if (reauth) "重新授权并继续" else "同意并继续",
            nextEnabled = agreed,
            onBack = onBack,
            onNext = onNext,
        )
    }
}
