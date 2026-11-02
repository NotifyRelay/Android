package github.xzynine.superislandui.builder

import android.os.Bundle

/**
 * 超级岛通知 extras 约束写入器（Android 门面）。
 *
 * 常量与写入约束位于公共源集的 [SuperIslandExtrasCore]；本对象保留 Bundle 形参的既有调用形式。
 */
object SuperIslandExtras {
    const val KEY_PARAM = SuperIslandExtrasCore.KEY_PARAM
    const val KEY_PICS = SuperIslandExtrasCore.KEY_PICS
    const val KEY_ACTIONS = SuperIslandExtrasCore.KEY_ACTIONS
    const val KEY_IS_FOCUS_NOTIFICATION = SuperIslandExtrasCore.KEY_IS_FOCUS_NOTIFICATION
    const val KEY_SHOW_BADGE = SuperIslandExtrasCore.KEY_SHOW_BADGE
    const val KEY_SOURCE_PACKAGE = SuperIslandExtrasCore.KEY_SOURCE_PACKAGE
    const val KEY_APP_PACKAGE = SuperIslandExtrasCore.KEY_APP_PACKAGE
    const val KEY_REDUCED_IMAGES = SuperIslandExtrasCore.KEY_REDUCED_IMAGES
    const val KEY_ISLAND_UPDATE_NO_FLOAT = SuperIslandExtrasCore.KEY_ISLAND_UPDATE_NO_FLOAT
    const val KEY_ISLAND_FIRST_FLOAT = SuperIslandExtrasCore.KEY_ISLAND_FIRST_FLOAT
    const val KEY_ENABLE_FLOAT = SuperIslandExtrasCore.KEY_ENABLE_FLOAT
    const val PIC_KEY_PREFIX = SuperIslandExtrasCore.PIC_KEY_PREFIX
    const val ACTION_KEY_PREFIX = SuperIslandExtrasCore.ACTION_KEY_PREFIX

    /**
     * 写入图片资源：仅接受 `miui.focus.pic_` 前缀的 key，同时写入独立 extra 与 [KEY_PICS] Bundle。
     * @return 实际写入的图片数量
     */
    fun writePicMap(
        extras: Bundle,
        picMap: Map<String, String>?,
    ): Int = SuperIslandExtrasCore.writePicMap(BundleExtrasWriter(extras), picMap)
}

private class BundleExtrasWriter(
    private val bundle: Bundle,
) : ExtrasWriter {
    override fun putString(
        key: String,
        value: String,
    ) {
        bundle.putString(key, value)
    }

    override fun putStringEntries(
        key: String,
        entries: Map<String, String>,
    ) {
        val sub = Bundle()
        entries.forEach { (entryKey, entryValue) -> sub.putString(entryKey, entryValue) }
        bundle.putBundle(key, sub)
    }
}
