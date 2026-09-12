package github.xzynine.superislandui.builder

import android.os.Bundle

/**
 * 超级岛通知 extras 约束写入器。
 *
 * 统一 `miui.focus.*` 相关 extra key 与标准焦点通知标记，避免各调用方硬编码字符串、
 * 写出与规范不一致的 extras。配合 [SuperIslandParamBuilder] 使用，即可完整、合规地
 * 发出超级岛通知。
 */
object SuperIslandExtras {
    /** 岛通知参数载荷（由 [SuperIslandParamBuilder.build] 生成） */
    const val KEY_PARAM = "miui.focus.param"

    /** 图片资源 Bundle */
    const val KEY_PICS = "miui.focus.pics"

    /** Action 资源 Bundle */
    const val KEY_ACTIONS = "miui.focus.actions"

    const val KEY_IS_FOCUS_NOTIFICATION = "miui.isFocusNotification"
    const val KEY_SHOW_BADGE = "miui.showBadge"
    const val KEY_SOURCE_PACKAGE = "superIslandSourcePackage"
    const val KEY_APP_PACKAGE = "app_package"
    const val KEY_REDUCED_IMAGES = "android.reduced.images"
    const val KEY_ISLAND_UPDATE_NO_FLOAT = "miui.island.updateNoFloat"
    const val KEY_ISLAND_FIRST_FLOAT = "miui.island.firstFloat"
    const val KEY_ENABLE_FLOAT = "miui.enableFloat"

    /** 图片资源 key 前缀（规范要求） */
    const val PIC_KEY_PREFIX = "miui.focus.pic_"

    /** Action key 前缀（规范要求） */
    const val ACTION_KEY_PREFIX = "miui.focus.action_"

    /** 写入 param_v2 载荷 */
    fun writeParam(
        extras: Bundle,
        payload: String,
    ) {
        extras.putString(KEY_PARAM, payload)
    }

    /**
     * 写入标准焦点通知标记（包名、缩略图、焦点/角标开关）。
     * @param sourcePackage 超级岛源包名（一般为本应用包名）
     */
    fun writeStandardFlags(
        extras: Bundle,
        sourcePackage: String,
    ) {
        extras.putBoolean(KEY_REDUCED_IMAGES, true)
        extras.putString(KEY_SOURCE_PACKAGE, sourcePackage)
        extras.putString(KEY_APP_PACKAGE, sourcePackage)
        extras.putBoolean(KEY_IS_FOCUS_NOTIFICATION, true)
        extras.putBoolean(KEY_SHOW_BADGE, false)
    }

    /**
     * 写入图片资源：仅接受 `miui.focus.pic_` 前缀的 key，同时写入独立 extra 与 [KEY_PICS] Bundle。
     * @return 实际写入的图片数量
     */
    fun writePicMap(
        extras: Bundle,
        picMap: Map<String, String>?,
    ): Int {
        if (picMap.isNullOrEmpty()) return 0
        val picsBundle = Bundle()
        var count = 0
        picMap.forEach { (key, url) ->
            if (key.startsWith(PIC_KEY_PREFIX)) {
                extras.putString(key, url)
                picsBundle.putString(key, url)
                count++
            }
        }
        if (count > 0) extras.putBundle(KEY_PICS, picsBundle)
        return count
    }
}
