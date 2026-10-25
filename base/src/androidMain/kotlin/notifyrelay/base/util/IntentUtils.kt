package notifyrelay.base.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Intent工具类，提供通用的Intent处理方法
 */
object IntentUtils {
    /**
     * 启动Activity
     * @param context 上下文
     * @param intent Intent对象
     * @param addNewTaskFlag 是否添加FLAG_ACTIVITY_NEW_TASK标志
     */
    fun startActivity(
        context: Context,
        intent: Intent,
        addNewTaskFlag: Boolean = false,
    ) {
        if (addNewTaskFlag) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 启动Activity（简化版）
     * @param context 上下文
     * @param action Intent action
     * @param data 数据URI
     * @param addNewTaskFlag 是否添加FLAG_ACTIVITY_NEW_TASK标志
     */
    fun startActivity(
        context: Context,
        action: String,
        data: Uri? = null,
        addNewTaskFlag: Boolean = false,
    ) {
        val intent = Intent(action)
        if (data != null) {
            intent.data = data
        }
        if (addNewTaskFlag) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 创建显式Intent
     * @param context 上下文
     * @param cls 目标Activity类
     * @return Intent对象
     */
    fun <T> createIntent(
        context: Context,
        cls: Class<T>,
    ): Intent = Intent(context, cls)

    /**
     * 创建隐式Intent
     * @param action Intent action
     * @return Intent对象
     */
    fun createImplicitIntent(action: String): Intent = Intent(action)
}
