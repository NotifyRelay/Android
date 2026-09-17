package com.xzyht.notifyrelay.ui.pages.history

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.notification.filter.RemoteFilterConfig
import com.xzyht.notifyrelay.sync.MessageSender
import com.xzyht.notifyrelay.sync.notification.data.NotificationRecord
import com.xzyht.notifyrelay.ui.pages.ToastDebounce
import com.xzyht.notifyrelay.ui.pages.history.dateTimeFormatter
import notifyrelay.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun NotificationCard(
    record: NotificationRecord,
    appIcon: Bitmap?,
    context: Context,
    getCachedAppInfo: (String?) -> Pair<String, Bitmap?>,
    cardColor: Color,
    contentColor: Color,
    installedPackages: Set<String>,
) {
    val notificationTextStyles = MiuixTheme.textStyles
    val cardColorScheme = MiuixTheme.colorScheme

    // 对包名进行等价映射，使用缓存的包名集合，避免同步加载
    val mappedPkg = RemoteFilterConfig.mapToLocalPackage(record.packageName, installedPackages)

    // 使用映射后的包名获取应用信息
    val appInfo: Pair<String, Bitmap?> = getCachedAppInfo(mappedPkg)
    val (_, mappedAppIcon) = appInfo
    val displayAppIcon = mappedAppIcon ?: appIcon

    // 修正：单条通知卡片标题应为原始通知标题
    val displayTitle = record.title ?: "(无标题)"
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        onClick = {
            // 跳转到对应应用主界面
            val pkg = record.packageName
            if (pkg.isNotEmpty()) {
                // 应用等价映射，使用缓存的包名集合，避免同步加载
                val mappedPkg = RemoteFilterConfig.mapToLocalPackage(pkg, installedPackages)

                var canOpen = false
                var intent: Intent? = null
                try {
                    intent = context.packageManager.getLaunchIntentForPackage(mappedPkg)
                    if (intent != null) {
                        canOpen = true
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - ToastDebounce.lastToastTime > ToastDebounce.DEBOUNCE_MILLIS) {
                            ToastUtils.showShortToast(context, "无法打开应用：$mappedPkg")
                            ToastDebounce.lastToastTime = now
                        }
                    }
                } catch (e: Exception) {
                    val now = System.currentTimeMillis()
                    if (now - ToastDebounce.lastToastTime > ToastDebounce.DEBOUNCE_MILLIS) {
                        ToastUtils.showShortToast(context, "启动失败：${e.message}")
                        ToastDebounce.lastToastTime = now
                    }
                }
                // 仅在即将跳转前显示通知标题和内容
                if (canOpen) {
                    // 发送高优先级悬浮通知
                    val title = record.title ?: "(无标题)"
                    val text = record.text ?: "(无内容)"
                    MessageSender.sendHighPriorityNotification(context, title, text)
                    intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        },
        cornerRadius = 8.dp,
        insideMargin = PaddingValues(12.dp),
        colors =
            CardDefaults.defaultColors(
                color = cardColor,
                contentColor = contentColor,
            ),
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (displayAppIcon != null) {
                Image(
                    bitmap = displayAppIcon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            // 标题显示为原始通知标题
            Text(
                text = displayTitle,
                style = notificationTextStyles.body2.copy(color = cardColorScheme.onSurface),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = record.text ?: "(无内容)",
            style = notificationTextStyles.body1.copy(color = cardColorScheme.onSurface),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text =
                LocalDateTime
                    .ofInstant(
                        Instant.ofEpochMilli(record.time),
                        ZoneId.systemDefault(),
                    ).format(dateTimeFormatter),
            style = notificationTextStyles.body2.copy(color = cardColorScheme.onSurfaceSecondary),
        )
    }
}
