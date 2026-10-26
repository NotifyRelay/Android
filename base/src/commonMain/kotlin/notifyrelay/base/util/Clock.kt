package notifyrelay.base.util

/**
 * 当前墙钟时间（毫秒）。
 *
 * 平台无关代码无法直接调用 `System.currentTimeMillis()`，故以 expect/actual 暴露。
 * 命名避开 `kotlin.time.TimeSource`，以免与标准库符号混淆。
 */
expect fun nowMillis(): Long
