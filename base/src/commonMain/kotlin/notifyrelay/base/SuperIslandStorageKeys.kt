package notifyrelay.base

/**
 * 超级岛的跨模块存储 key。
 *
 * `superisland_enabled` 同时被 `:app` 与 `:superislandui` 读写，
 * 故在 `:shared` 集中定义单一来源，避免两侧各留一份字面量、改一处漏一处静默出错。
 *
 * **字符串值不可更改**：已发布版本的持久化契约，改动会重置已有用户设置。
 */
object SuperIslandStorageKeys {
    /** 超级岛总开关。 */
    const val ENABLED = "superisland_enabled"

    /** 浮窗兼容开关。 */
    const val FLOATING_WINDOW = "super_island_floating_window"

    /** 通知列表模式开关。 */
    const val NOTIFICATION_LIST = "super_island_notification_list"

    /** 规范信息注入方式（存显式编码，非枚举序数）。 */
    const val SPEC_INJECTION_MODE = "spec_injection_mode"

    /** 镜像应用过滤开关。 */
    const val MIRROR_FILTER_ENABLED = "super_island_mirror_filter_enabled"

    /** 远端超级岛显示开关。 */
    const val REMOTE_SHOW = "superisland_show"

    /** 历史条目复制时是否附带图片原始数据。 */
    const val COPY_IMAGE_DATA = "superisland_copy_image_data"
}
