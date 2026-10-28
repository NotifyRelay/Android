package notifyrelay.base.util

import java.util.TimeZone

actual fun currentZoneOffsetMinutes(): Int = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
