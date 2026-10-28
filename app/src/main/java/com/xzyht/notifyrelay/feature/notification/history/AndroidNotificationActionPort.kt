package com.xzyht.notifyrelay.feature.notification.history

import android.content.Context
import android.content.Intent
import com.xzyht.notifyrelay.sync.MessageSender
import notifyrelay.base.util.ToastUtils
import notifyrelay.shared.ui.pages.history.NotificationActionPort

/** 以 PackageManager / ToastUtils / MessageSender 实现 [NotificationActionPort]。 */
class AndroidNotificationActionPort(
    private val context: Context,
) : NotificationActionPort {
    override fun openApp(packageName: String): String? {
        val intent =
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return "无法打开应用：" + packageName
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            "启动失败：" + (e.message ?: "")
        }
    }

    override fun showToast(message: String) {
        ToastUtils.showShortToast(context, message)
    }

    override fun resendAsHighPriority(
        title: String?,
        text: String?,
    ) {
        MessageSender.sendHighPriorityNotification(context, title, text)
    }
}
