package notifyrelay.base.util

/**
 * 超级岛的跨模块存储 key。
 *
 * `superisland_enabled` 同时被 `:app` 与 `:superislandui` 读写（后者依赖 `:base`），
 * 故在 `:base` 集中定义单一来源，避免两侧各留一份字面量、改一处漏一处静默出错。
 *
 * **字符串值不可更改**：已发布版本的持久化契约，改动会重置已有用户设置。
 */
object SuperIslandStorageKeys {
    /** 超级岛总开关。 */
    const val ENABLED = "superisland_enabled"
}
