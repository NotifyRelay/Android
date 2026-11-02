package notifyrelay.desktop.platform

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 关于页的开机自启区块。
 *
 * 读写 [DesktopAutoLaunch]；注册失败时勾选态回落实际结果，
 * 与托盘菜单原入口同源。
 */
@Composable
fun DesktopAboutAutoLaunch() {
    var enabled by remember { mutableStateOf(DesktopAutoLaunch.isEnabled()) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SwitchPreference(
            title = "开机自启",
            summary = "登录 Windows 后自动启动 NotifyRelay",
            checked = enabled,
            onCheckedChange = { checked ->
                enabled = DesktopAutoLaunch.setEnabled(checked)
            },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
    }
}
