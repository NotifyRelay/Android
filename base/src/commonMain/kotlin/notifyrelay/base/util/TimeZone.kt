package notifyrelay.base.util

/**
 * 当前时区相对 UTC 的偏移（分钟）。
 *
 * 时间戳格式化需要按本地时区呈现，而 commonMain 无法读取系统时区，故以 expect/actual 暴露。
 */
expect fun currentZoneOffsetMinutes(): Int
