package com.xzyht.notifyrelay.ui.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * 路由定义，类型安全的导航键
 * Route definitions, type-safe navigation keys
 */
sealed interface Route : NavKey, Parcelable {
    /**
     * 主页面（设备互联与增强）
     * Main page (Device connection and enhancement)
     */
    @Parcelize
    @Serializable
    data object Main : Route

    /**
     * 历史页面
     * History page
     */
    @Parcelize
    @Serializable
    data object History : Route

    /**
     * 设置页面
     * Settings page
     */
    @Parcelize
    @Serializable
    data object Settings : Route
}
