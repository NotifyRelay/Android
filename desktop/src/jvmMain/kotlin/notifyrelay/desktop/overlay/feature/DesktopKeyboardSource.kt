package notifyrelay.desktop.overlay.feature

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import notifyrelay.base.log.Logger
import notifyrelay.overlay.api.KeyMapping
import notifyrelay.overlay.api.KeyboardSource
import notifyrelay.overlay.api.KeyboardStateSnapshot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 键盘监控数据源。
 *
 * 全局低级键盘钩子（`WH_KEYBOARD_LL`）只能在**安装它的线程**上收到回调，
 * 故本类自建一条专用线程：线程内安装钩子并持续泵消息循环，
 * 停止时经 `PostThreadMessage(WM_QUIT)` 唤醒并等待线程退出。
 *
 * 钩子回调内只更新状态并投递推送任务（不阻塞输入路径）；
 * 订阅方在协程调度线程上收到通知。
 */
class DesktopKeyboardSource(
    private val mappingsProvider: () -> List<KeyMapping> = { emptyList() },
    private val enabledProvider: () -> Boolean = { true },
) : KeyboardSource {
    private companion object {
        const val TAG = "DesktopKeyboard"
    }

    /** 按键状态：虚拟键码 → 是否按下。 */
    private val keyStates = ConcurrentHashMap<Int, Boolean>()

    /** 切换键状态：虚拟键码 → 是否锁定。 */
    private val toggleStates = ConcurrentHashMap<Int, Boolean>()

    /** 映射命中状态：映射 id → 已按下的源键集合。 */
    private val mappingStates = ConcurrentHashMap<String, MutableSet<Int>>()

    /** 已触发的映射 id，避免长按期间重复提示。 */
    private val triggeredMappings = ConcurrentHashMap.newKeySet<String>()

    private val stateListeners = CopyOnWriteArrayList<(KeyboardStateSnapshot) -> Unit>()

    private val hintListeners = CopyOnWriteArrayList<(String) -> Unit>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val running = AtomicBoolean(false)

    /** 钩子线程 id：供停止时投递退出消息。 */
    private val hookThreadId = AtomicInteger(0)

    @Volatile
    private var hookThread: Thread? = null

    @Volatile
    private var bridge: KeyboardHookBridge? = null

    /** 按键推送是否已在途：合并高频按键，避免每次按下都触发界面重组。 */
    private val pushPending = AtomicBoolean(false)

    override val available: Boolean
        get() = runCatching { User32Ex.INSTANCE }.isSuccess

    override fun state(): KeyboardStateSnapshot {
        val pressed =
            buildList {
                // 修饰键按实时状态补偿：钩子可能漏掉在安装前就已按下的边沿
                Win32Keyboard.MODIFIER_KEYS.forEach { vk ->
                    val down = keyStates[vk] == true || (bridge?.isDown(vk) ?: false)
                    if (down) add(vk)
                }
                keyStates.forEach { (vk, down) ->
                    if (down && vk !in Win32Keyboard.MODIFIER_KEYS) add(vk)
                }
            }.distinct()
        return KeyboardStateSnapshot(
            pressedKeys = pressed,
            capsLockToggled = toggleStates[0x14] ?: false,
            numLockToggled = toggleStates[0x90] ?: false,
            scrollLockToggled = toggleStates[0x91] ?: false,
        )
    }

    override fun mappings(): List<KeyMapping> = mappingsProvider()

    override fun setMappings(mappings: List<KeyMapping>) {
        mappingStates.clear()
        triggeredMappings.clear()
        Logger.d(TAG, "映射规则已更新，共 " + mappings.count { it.enabled } + " 条启用")
    }

    /** 安装钩子并启动消息循环线程；已安装时幂等。 */
    override fun install() {
        if (!available) {
            Logger.w(TAG, "user32 不可用，跳过键盘钩子安装")
            return
        }
        if (!running.compareAndSet(false, true)) return

        hookThread =
            Thread(
                {
                    val localBridge =
                        KeyboardHookBridge { vkCode, isDown ->
                            onKeyEvent(vkCode, isDown)
                        }
                    bridge = localBridge
                    hookThreadId.set(localBridge.currentThreadId())

                    if (!localBridge.install()) {
                        Logger.w(TAG, "键盘钩子安装失败")
                        running.set(false)
                        return@Thread
                    }

                    // 安装前先读取切换键初始锁定状态，避免首次提示前状态为空
                    Win32Keyboard.TOGGLE_KEYS.forEach { vk ->
                        toggleStates[vk] = localBridge.isToggled(vk)
                    }
                    Logger.i(TAG, "键盘钩子已安装")

                    // 消息循环：GetMessage 阻塞直到有消息或收到 WM_QUIT
                    while (running.get() && localBridge.pumpOnce()) {
                        // 循环条件即退出条件，无需额外处理
                    }

                    localBridge.uninstall()
                    Logger.i(TAG, "键盘钩子已卸载")
                },
                "keyboard-hook",
            ).apply {
                isDaemon = true
                start()
            }
    }

    /** 卸载钩子并结束消息循环；未安装时幂等。 */
    override fun uninstall() {
        if (!running.compareAndSet(true, false)) return
        // 投递退出消息唤醒阻塞中的 GetMessage，随后等待线程自然结束
        runCatching { bridge?.postQuit(hookThreadId.get()) }
        runCatching { hookThread?.join(1_000) }
        hookThread = null
        bridge = null
        keyStates.clear()
        mappingStates.clear()
        triggeredMappings.clear()
    }

    /** 按当前开关安装或卸载钩子；由调用方在开关或叠加层总开关变化时触发。 */
    override fun syncState() {
        if (enabledProvider()) install() else uninstall()
    }

    override fun observeState(listener: (KeyboardStateSnapshot) -> Unit): AutoCloseable {
        stateListeners.add(listener)
        listener(state())
        return AutoCloseable { stateListeners.remove(listener) }
    }

    override fun observeMappingHint(listener: (String) -> Unit): AutoCloseable {
        hintListeners.add(listener)
        return AutoCloseable { hintListeners.remove(listener) }
    }

    /**
     * 钩子回调入口：只更新状态并合并推送，不阻塞输入路径。
     */
    private fun onKeyEvent(
        vkCode: Int,
        isDown: Boolean,
    ) {
        keyStates[vkCode] = isDown

        // 映射判定：源键全部按下时提示一次，任一起抬起后复位
        val mappings = mappingsProvider()
        mappings.forEach { mapping ->
            if (!mapping.enabled || mapping.sourceKeys.isEmpty()) return@forEach
            val pressed = mappingStates.getOrPut(mapping.id) { ConcurrentHashMap.newKeySet<Int>() }
            if (isDown) pressed.add(vkCode) else pressed.remove(vkCode)

            val hit = pressed.containsAll(mapping.sourceKeys)
            if (hit && triggeredMappings.add(mapping.id)) {
                val text = mapping.displayText.ifBlank { KeyboardSource.keyName(mapping.targetKey) }
                notifyHint(text)
            } else if (!hit) {
                triggeredMappings.remove(mapping.id)
            }
        }

        if (pushPending.compareAndSet(false, true)) {
            scope.launch {
                pushPending.set(false)
                notifyState()
            }
        }
    }

    private fun notifyState() {
        val snapshot = state()
        stateListeners.forEach { listener ->
            runCatching { listener(snapshot) }
                .onFailure { Logger.w(TAG, "键盘状态回调失败", it) }
        }
    }

    private fun notifyHint(text: String) {
        scope.launch {
            hintListeners.forEach { listener ->
                runCatching { listener(text) }
                    .onFailure { Logger.w(TAG, "映射提示回调失败", it) }
            }
        }
    }
}
