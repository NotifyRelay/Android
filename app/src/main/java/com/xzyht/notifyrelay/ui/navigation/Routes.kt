package com.xzyht.notifyrelay.ui.navigation

import android.os.Parcel
import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
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
    @Serializable
    data object Main : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        object CREATOR : Parcelable.Creator<Main> {
            override fun createFromParcel(parcel: Parcel): Main = Main
            override fun newArray(size: Int): Array<Main?> = arrayOfNulls(size)
        }
    }

    /**
     * 历史页面
     * History page
     */
    @Serializable
    data object History : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        object CREATOR : Parcelable.Creator<History> {
            override fun createFromParcel(parcel: Parcel): History = History
            override fun newArray(size: Int): Array<History?> = arrayOfNulls(size)
        }
    }

    /**
     * 设置页面
     * Settings page
     */
    @Serializable
    data object Settings : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        object CREATOR : Parcelable.Creator<Settings> {
            override fun createFromParcel(parcel: Parcel): Settings = Settings
            override fun newArray(size: Int): Array<Settings?> = arrayOfNulls(size)
        }
    }

    /**
     * Scrcpy高级设置页面
     * Scrcpy advanced settings page
     */
    @Serializable
    data object ScrcpyAdvanced : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        object CREATOR : Parcelable.Creator<ScrcpyAdvanced> {
            override fun createFromParcel(parcel: Parcel): ScrcpyAdvanced = ScrcpyAdvanced
            override fun newArray(size: Int): Array<ScrcpyAdvanced?> = arrayOfNulls(size)
        }
    }

    /**
     * Scrcpy快速设备排序页面
     * Scrcpy reorder devices page
     */
    @Serializable
    data object ScrcpyReorderDevices : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        object CREATOR : Parcelable.Creator<ScrcpyReorderDevices> {
            override fun createFromParcel(parcel: Parcel): ScrcpyReorderDevices = ScrcpyReorderDevices
            override fun newArray(size: Int): Array<ScrcpyReorderDevices?> = arrayOfNulls(size)
        }
    }

    /**
     * Scrcpy虚拟按钮排序页面
     * Scrcpy virtual button order page
     */
    @Serializable
    data object ScrcpyVirtualButtonOrder : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        object CREATOR : Parcelable.Creator<ScrcpyVirtualButtonOrder> {
            override fun createFromParcel(parcel: Parcel): ScrcpyVirtualButtonOrder = ScrcpyVirtualButtonOrder
            override fun newArray(size: Int): Array<ScrcpyVirtualButtonOrder?> = arrayOfNulls(size)
        }
    }
}
