package notifyrelay.base.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 剪贴板工具类
 *
 * 统一封装「取系统剪贴板服务 → 构造 [ClipData] → 写入」三步样板，
 * 避免各处分别内联 `getSystemService(Context.CLIPBOARD_SERVICE)`。
 */
object ClipboardUtils {
    /**
     * 把 [text] 写入系统剪贴板（[label] 为 ClipData 标签）。
     *
     * 异常语义：内部捕获异常并返回 `false`（与 [ToastUtils] 等 `:base` 工具的宽容风格一致），
     * 调用方无需自行 try/catch；成功写入返回 `true`。
     *
     * @param context 用于获取系统剪贴板服务的上下文。
     * @param label [ClipData] 的可读标签，仅用于系统侧标识。
     * @param text 要写入剪贴板的纯文本内容。
     * @return 成功写入返回 true，否则返回 false。
     */
    fun copyText(
        context: Context,
        label: String,
        text: String,
    ): Boolean =
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            Logger.e("ClipboardUtils", "写入剪贴板失败", e)
            false
        }
}
