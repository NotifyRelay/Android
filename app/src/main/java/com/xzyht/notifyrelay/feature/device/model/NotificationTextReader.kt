package com.xzyht.notifyrelay.feature.device.model

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import notifyrelay.base.util.Logger

/**
 * 通知文本 / 验证码读取工具。
 * 原位于 NotificationRepository（NotificationData.kt），为可读性与职责拆分抽离为独立 object。
 * （读 verify_code 隐藏字段、优先于 android.text 的契约不可改。）
 */
object NotificationTextReader {
    /**
     * 兼容 Bundle 字段类型，支持 CharSequence/SpannableString 自动转 String
     */
    fun getStringCompat(
        bundle: Bundle,
        key: String,
    ): String? {
        try {
            val charSeq = bundle.getCharSequence(key)
            return charSeq?.toString()
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * 读取通知的 verify_code 字段（系统短信App在锁屏状态下也会暴露实际验证码）
     * @return 验证码字符串，如果没有则返回 null
     */
    fun getVerifyCode(sbn: StatusBarNotification): String? {
        return try {
            val extras = sbn.notification.extras ?: return null
            extras.getString("verify_code")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取通知文本，优先使用 verify_code 字段（系统短信App在锁屏状态下会暴露实际验证码）
     * @return 实际显示的文本
     */
    fun getNotificationTextWithVerifyCode(sbn: StatusBarNotification): String? {
        try {
            val extras = sbn.notification.extras ?: return null

            // 优先尝试读取 verify_code 字段（系统短信App的隐藏字段）
            val verifyCode = extras.getString("verify_code")
            if (!verifyCode.isNullOrEmpty()) {
                Logger.d("NotifyRelay", "读取到 verify_code 字段(len=${verifyCode.length})")
                return verifyCode
            }

            // 如果没有 verify_code，则使用标准的 android.text 字段
            return getStringCompat(extras, Notification.EXTRA_TEXT)
        } catch (e: Exception) {
            return null
        }
    }
}
