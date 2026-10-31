package notifyrelay.overlay.api

/** 键盘状态快照：按下的虚拟键码集合 + 三个切换键的锁定状态。 */
data class KeyboardStateSnapshot(
    /** 当前按下的虚拟键码。 */
    val pressedKeys: List<Int> = emptyList(),
    /** Caps Lock（0x14）是否锁定。 */
    val capsLockToggled: Boolean = false,
    /** Num Lock（0x90）是否锁定。 */
    val numLockToggled: Boolean = false,
    /** Scroll Lock（0x91）是否锁定。 */
    val scrollLockToggled: Boolean = false,
) {
    /** 读取指定切换键的锁定状态；非切换键返回 false。 */
    fun isToggled(vkCode: Int): Boolean =
        when (vkCode) {
            0x14 -> capsLockToggled
            0x90 -> numLockToggled
            0x91 -> scrollLockToggled
            else -> false
        }

    companion object {
        /** 空快照（无按键、切换键均未锁定）。 */
        val EMPTY = KeyboardStateSnapshot()
    }
}

/**
 * 快捷键映射规则。
 *
 * 与 Windows `KeyboardMappingConfig` 同语义：命中 [sourceKeys] 时显示
 * [displayText]（缺省时用 [targetKey] 的键名）。
 */
data class KeyMapping(
    /** 规则标识，用于日志与去重。 */
    val id: String,
    /** 规则名，设置页展示。 */
    val name: String,
    /** 触发的源键集合（全部按下即命中）。 */
    val sourceKeys: List<Int>,
    /** 映射到的目标键码。 */
    val targetKey: Int,
    /** 自定义显示文案；为空时用目标键名。 */
    val displayText: String = "",
    /** 是否启用。 */
    val enabled: Boolean = true,
)

/**
 * 键盘监控数据源。
 *
 * 实现侧经 `SetWindowsHookEx(WH_KEYBOARD_LL)` 安装全局低级键盘钩子；
 * 钩子回调运行在安装钩子的线程，实现须避免在回调内做重活。
 */
interface KeyboardSource {
    /** 是否可安装钩子。 */
    val available: Boolean

    /** 当前按键状态快照。 */
    fun state(): KeyboardStateSnapshot

    /** 当前映射规则。 */
    fun mappings(): List<KeyMapping>

    /** 覆写映射规则；[reload] 由实现决定是否即时生效。 */
    fun setMappings(mappings: List<KeyMapping>)

    /** 安装钩子；已安装时幂等。 */
    fun install()

    /** 卸载钩子；未安装时幂等。 */
    fun uninstall()

    /** 按当前开关状态安装或卸载。 */
    fun syncState()

    /** 按键状态变化订阅；返回取消订阅函数。 */
    fun observeState(listener: (KeyboardStateSnapshot) -> Unit): AutoCloseable

    /** 映射提示订阅（命中映射时的展示文案）；返回取消订阅函数。 */
    fun observeMappingHint(listener: (String) -> Unit): AutoCloseable

    companion object {
        /** 虚拟键码到展示名的映射；未知键返回十六进制形式。 */
        fun keyName(vkCode: Int): String =
            when (vkCode) {
                0x08 -> "Backspace"
                0x09 -> "Tab"
                0x0D -> "Enter"
                0x10 -> "Shift"
                0x11 -> "Ctrl"
                0x12 -> "Alt"
                0x1B -> "Esc"
                0x20 -> "Space"
                0x25 -> "Left"
                0x26 -> "Up"
                0x27 -> "Right"
                0x28 -> "Down"
                0x2C -> "PrintScreen"
                in 0x30..0x39 -> vkCode.toChar().toString()
                in 0x41..0x5A -> vkCode.toChar().toString()
                in 0x60..0x69 -> "Num" + (vkCode - 0x60)
                in 0x70..0x7B -> "F" + (vkCode - 0x6F)
                else -> "0x" + vkCode.toString(16).uppercase()
            }
    }
}
