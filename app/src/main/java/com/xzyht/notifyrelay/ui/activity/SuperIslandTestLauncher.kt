package com.xzyht.notifyrelay.ui.activity

import android.app.Activity
import com.xzyht.notifyrelay.BuildConfig
import com.xzyht.notifyrelay.ui.dialog.triggerSuperIslandTestSample
import notifyrelay.base.util.Logger

/**
 * 超级岛测试样本的调试入口。
 *
 * 通过 intent 直接触发超级岛测试样本，免去手工点击测试对话框：
 * ```
 * adb shell am start -n com.xzyht.notifyrelay/.ui.activity.MainActivity \
 *     --es superIslandTest multi_progress_with_icons
 * ```
 * 仅 debug 构建生效：release 包不读取该参数，也不触发任何测试样本，保证正常启动流程不变。
 */
internal object SuperIslandTestLauncher {
    /** 调试用：intent 携带该 extra 时，按值触发对应的超级岛测试样本（见 [triggerSuperIslandTestSample]） */
    const val EXTRA_SUPER_ISLAND_TEST = "superIslandTest"

    /** 调试用：是否使用可变进度（测试动画效果），默认固定进度 */
    const val EXTRA_SUPER_ISLAND_TEST_VARIABLE = "superIslandTestVariable"

    /**
     * 在 debug 构建下按当前 intent 触发测试样本。
     *
     * @return `true` 表示已按 intent 触发（或识别失败并记录日志）；release 构建或无该 extra 时返回 `false`。
     */
    fun tryLaunchFromIntent(activity: Activity): Boolean {
        if (!BuildConfig.DEBUG) return false
        val intent = activity.intent ?: return false
        val sampleId = intent.getStringExtra(EXTRA_SUPER_ISLAND_TEST) ?: return false
        val variable = intent.getBooleanExtra(EXTRA_SUPER_ISLAND_TEST_VARIABLE, false)
        return if (triggerSuperIslandTestSample(activity, sampleId, variable)) {
            Logger.i("MainActivity", "已按 intent 触发超级岛测试样本: $sampleId")
            true
        } else {
            Logger.w("MainActivity", "未知的超级岛测试样本 ID: $sampleId")
            false
        }
    }
}
