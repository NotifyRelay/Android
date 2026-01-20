package com.xzyht.notifyrelay.common.core.util

/**
 * 电池图标转换器，根据电池电量和充电状态返回对应的Segoe MDL2图标
 */
object BatteryIconConverter {

    /**
     * 根据电池电量和充电状态获取对应的Segoe MDL2图标
     * @param batteryLevel 电池电量，范围 0-100
     * @param isCharging 是否正在充电
     * @return 对应的Segoe MDL2图标Unicode字符
     */
    fun getBatteryIcon(batteryLevel: Int, chargingState: Char): String {
        val clampedLevel = batteryLevel.coerceIn(0, 100)
        // chargingState: '1' = charging, '0' = not charging, '*' = unknown -> treat as not charging for icon choice
        val isCharging = chargingState == '1'
        
        return if (isCharging) {
            // 充电状态下的电池图标
            when {
                clampedLevel >= 100 -> "\uEA93" // 充电完成
                clampedLevel >= 90 -> "\uE83E" // 90-100%
                clampedLevel >= 80 -> "\uE862" // 80-90%
                clampedLevel >= 70 -> "\uE861" // 70-80%
                clampedLevel >= 60 -> "\uE860" // 60-70%
                clampedLevel >= 50 -> "\uE85F" // 50-60%
                clampedLevel >= 40 -> "\uE85E" // 40-50%
                clampedLevel >= 30 -> "\uE85D" // 30-40%
                clampedLevel >= 20 -> "\uE85C" // 20-30%
                clampedLevel >= 10 -> "\uE85B" // 10-20%
                else -> "\uE85A" // 0-10%
            }
        } else {
            // 未充电状态下的电池图标
            when {
                clampedLevel >= 100 -> "\uE83F" // 100%
                clampedLevel >= 90 -> "\uE859" // 90-100%
                clampedLevel >= 80 -> "\uE858" // 80-90%
                clampedLevel >= 70 -> "\uE857" // 70-80%
                clampedLevel >= 60 -> "\uE856" // 60-70%
                clampedLevel >= 50 -> "\uE855" // 50-60%
                clampedLevel >= 40 -> "\uE854" // 40-50%
                clampedLevel >= 30 -> "\uE853" // 30-40%
                clampedLevel >= 20 -> "\uE852" // 20-30%
                clampedLevel >= 10 -> "\uE851" // 10-20%
                else -> "\uE850" // 0-10%
            }
        }
    }
}