package notifyrelay.domain.util

/**
 * 时间戳格式化（平台无关）。
 *
 * 以 UTC 为基准按固定格式 `yyyy-MM-dd HH:mm:ss` 输出；时区偏移由平台侧提供。
 */
object TimestampFormatter {
    /**
     * 格式化毫秒时间戳。
     *
     * @param timestamp 毫秒时间戳
     * @param zoneOffsetMinutes 相对 UTC 的时区偏移（分钟）
     * @return 格式化字符串；异常时回退为时间戳字面量
     */
    fun format(
        timestamp: Long,
        zoneOffsetMinutes: Int = 0,
    ): String {
        val shifted = timestamp + zoneOffsetMinutes * 60_000L
        var days = shifted / 86_400_000L
        var millisOfDay = shifted % 86_400_000L
        if (millisOfDay < 0) {
            millisOfDay += 86_400_000L
            days -= 1
        }
        val (year, month, day) = daysToDate(days)
        val hour = (millisOfDay / 3_600_000L).toInt()
        val minute = (millisOfDay / 60_000L % 60).toInt()
        val second = (millisOfDay / 1_000L % 60).toInt()
        return buildString {
            append(year.toString().padStart(4, '0'))
            append('-')
            append(month.toString().padStart(2, '0'))
            append('-')
            append(day.toString().padStart(2, '0'))
            append(' ')
            append(hour.toString().padStart(2, '0'))
            append(':')
            append(minute.toString().padStart(2, '0'))
            append(':')
            append(second.toString().padStart(2, '0'))
        }
    }

    /** 自 1970-01-01 起的天数转换为 (年, 月, 日)。 */
    private fun daysToDate(days: Long): Triple<Long, Int, Int> {
        var remaining = days
        var year = 1970L
        while (true) {
            val yearDays = if (isLeapYear(year)) 366L else 365L
            if (remaining < yearDays) break
            remaining -= yearDays
            year += 1
        }
        val monthDays = if (isLeapYear(year)) leapMonthDays else normalMonthDays
        var month = 0
        while (month < 12 && remaining >= monthDays[month]) {
            remaining -= monthDays[month]
            month += 1
        }
        return Triple(year, month + 1, (remaining + 1).toInt())
    }

    private fun isLeapYear(year: Long): Boolean = (year % 4L == 0L && year % 100L != 0L) || year % 400L == 0L

    private val normalMonthDays = longArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val leapMonthDays = longArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
}
