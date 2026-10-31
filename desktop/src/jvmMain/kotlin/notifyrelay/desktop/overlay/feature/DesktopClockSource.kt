package notifyrelay.desktop.overlay.feature

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import notifyrelay.overlay.api.ClockConfig
import notifyrelay.overlay.api.ClockSource
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 时间数据源（纯 JVM，无 JNA）。
 *
 * 经 [LocalTime.now] 读取系统时钟，按 [ClockConfig] 格式化；
 * 显示秒时每秒刷新，不显示秒时对齐到分钟边界刷新。
 */
class DesktopClockSource(
    initialConfig: ClockConfig = ClockConfig(),
) : ClockSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    @Volatile
    private var config: ClockConfig = initialConfig

    @Volatile
    private var formatter: DateTimeFormatter = formatterOf(initialConfig)

    @Volatile
    private var currentText: String = LocalTime.now().format(formatter)

    private var refreshJob: Job? = null

    @Volatile
    private var running = false

    override fun text(): String = currentText

    override fun start() {
        if (running) return
        running = true
        refreshJob =
            scope.launch {
                while (isActive) {
                    currentText = LocalTime.now().format(formatter)
                    listeners.forEach { it(currentText) }
                    val waitMs = if (config.showSeconds) 1_000L else (60L - LocalTime.now().second) * 1_000L
                    delay(waitMs.coerceAtLeast(200L))
                }
            }
    }

    override fun stop() {
        if (!running) return
        running = false
        refreshJob?.cancel()
        refreshJob = null
    }

    override fun updateConfig(config: ClockConfig) {
        this.config = config
        this.formatter = formatterOf(config)
        // 立即按新格式刷新一次
        currentText = LocalTime.now().format(formatter)
        listeners.forEach { it(currentText) }
    }

    override fun observe(listener: (String) -> Unit): AutoCloseable {
        listeners.add(listener)
        listener(currentText)
        return AutoCloseable { listeners.remove(listener) }
    }

    private companion object {
        fun formatterOf(config: ClockConfig): DateTimeFormatter =
            DateTimeFormatter.ofPattern(
                when {
                    config.showSeconds && config.use24Hour -> "HH:mm:ss"
                    config.showSeconds -> "hh:mm:ss a"
                    config.use24Hour -> "HH:mm"
                    else -> "hh:mm a"
                },
            )
    }
}
