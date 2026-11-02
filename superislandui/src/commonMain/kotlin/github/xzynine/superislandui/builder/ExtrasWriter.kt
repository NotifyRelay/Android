package github.xzynine.superislandui.builder

/**
 * 通知 extras 写入端口。
 *
 * 超级岛 extra 的写入约束集中在 [SuperIslandExtrasCore]，具体容器（Android 为 Bundle）由平台实现。
 */
interface ExtrasWriter {
    fun putString(
        key: String,
        value: String,
    )

    /** 以 [key] 写入一份字符串键值集合（实现方自行构造对应容器）。 */
    fun putStringEntries(
        key: String,
        entries: Map<String, String>,
    )
}
