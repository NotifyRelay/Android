package notifyrelay.shared.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import notifyrelay.base.log.LogFileStore
import notifyrelay.base.log.LogStorageInfo
import notifyrelay.shared.ui.common.bytesToReadable
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页的日志存储区块。
 *
 * 只负责展示与触发动作；目录打开、导出等平台行为通过回调注入，
 * 保证两端共用同一份界面实现。
 *
 * @param onOpenDirectory 打开日志目录
 * @param onExport 导出全部分片
 * @param refreshKey 变化时重新读取存储信息
 */
@Composable
fun LogStorageSection(
    onOpenDirectory: () -> Unit,
    onExport: () -> Unit,
    refreshKey: Int = 0,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    var info by remember { mutableStateOf<LogStorageInfo?>(null) }

    LaunchedEffect(refreshKey) {
        info = LogFileStore.storageInfo()
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "运行日志",
        style = textStyles.main,
        color = colorScheme.onSurfaceSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    val storage = info
    val summary =
        if (storage == null) {
            "日志通道未初始化"
        } else {
            buildString {
                append("保留 ").append(storage.retentionDays).append(" 天 · ")
                append(storage.fileCount).append(" 个分片 · ")
                append(bytesToReadable(storage.totalBytes))
                append("\n单文件上限 ").append(bytesToReadable(storage.maxFileBytes))
                append("\n").append(storage.directory)
            }
        }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ArrowPreference(
            title = "日志存储",
            summary = summary,
            onClick = onOpenDirectory,
        )
        ArrowPreference(
            title = "导出日志",
            summary = "打包全部分片，便于反馈问题",
            onClick = onExport,
        )
    }
}

/**
 * 桌面端关于页内容：版本信息、平台专有区块与日志存储。
 *
 * Android 的关于页内容由 :app 提供（含更新检查等平台能力），
 * 桌面端在此复用公共组件，避免两端各写一份界面。
 *
 * @param autoLaunchSlot 平台专有区块（桌面端注入开机自启开关）；null 时不渲染
 */
@Composable
fun DesktopAboutContent(
    versionName: String,
    onOpenDirectory: () -> Unit,
    onExport: () -> Unit,
    appIcon: ImageBitmap? = null,
    onCheckUpdate: (() -> Unit)? = null,
    isCheckingUpdate: Boolean = false,
    refreshKey: Int = 0,
    autoLaunchSlot: (@Composable () -> Unit)? = null,
) {
    val colorScheme = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles

    Column(modifier = Modifier.fillMaxWidth()) {
        appIcon?.let {
            Image(
                bitmap = it,
                contentDescription = "应用图标",
                modifier =
                    Modifier
                        .padding(top = 24.dp)
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .align(Alignment.CenterHorizontally),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Notify Relay",
            style = textStyles.title1,
            color = colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            text = "版本 " + versionName,
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
        )

        onCheckUpdate?.let {
            ArrowPreference(
                title = "检测更新",
                summary = if (isCheckingUpdate) "检查中..." else "点击检查是否有新版本",
                onClick = it,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        autoLaunchSlot?.invoke()

        LogStorageSection(
            onOpenDirectory = onOpenDirectory,
            onExport = onExport,
            refreshKey = refreshKey,
        )

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "© 2026 Notify Relay",
            style = textStyles.body2,
            color = colorScheme.onSurfaceSecondary,
            modifier =
                Modifier
                    .padding(24.dp)
                    .align(Alignment.CenterHorizontally),
        )
    }
}
