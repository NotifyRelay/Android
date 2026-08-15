package com.xzyht.notifyrelay.ui.navigation

import android.annotation.SuppressLint
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
    @SuppressLint("ParcelCreator")
    data object Main : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        @JvmField
        val CREATOR: Parcelable.Creator<Main> = object : Parcelable.Creator<Main> {
            override fun createFromParcel(parcel: Parcel): Main = Main
            override fun newArray(size: Int): Array<Main?> = arrayOfNulls(size)
        }
    }

    /**
     * 历史页面
     * History page
     */
    @Serializable
    @SuppressLint("ParcelCreator")
    data object History : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        @JvmField
        val CREATOR: Parcelable.Creator<History> = object : Parcelable.Creator<History> {
            override fun createFromParcel(parcel: Parcel): History = History
            override fun newArray(size: Int): Array<History?> = arrayOfNulls(size)
        }
    }

    /**
     * 设置页面
     * Settings page
     */
    @Serializable
    @SuppressLint("ParcelCreator")
    data object Settings : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        @JvmField
        val CREATOR: Parcelable.Creator<Settings> = object : Parcelable.Creator<Settings> {
            override fun createFromParcel(parcel: Parcel): Settings = Settings
            override fun newArray(size: Int): Array<Settings?> = arrayOfNulls(size)
        }
    }

    /**
     * Scrcpy高级设置页面
     * Scrcpy advanced settings page
     */
    @Serializable
    @SuppressLint("ParcelCreator")
    data object ScrcpyAdvanced : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        @JvmField
        val CREATOR: Parcelable.Creator<ScrcpyAdvanced> = object : Parcelable.Creator<ScrcpyAdvanced> {
            override fun createFromParcel(parcel: Parcel): ScrcpyAdvanced = ScrcpyAdvanced
            override fun newArray(size: Int): Array<ScrcpyAdvanced?> = arrayOfNulls(size)
        }
    }

    /**
     * Scrcpy虚拟按钮排序页面
     * Scrcpy virtual button order page
     */
    @Serializable
    @SuppressLint("ParcelCreator")
    data object ScrcpyVirtualButtonOrder : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        @JvmField
        val CREATOR: Parcelable.Creator<ScrcpyVirtualButtonOrder> = object : Parcelable.Creator<ScrcpyVirtualButtonOrder> {
            override fun createFromParcel(parcel: Parcel): ScrcpyVirtualButtonOrder = ScrcpyVirtualButtonOrder
            override fun newArray(size: Int): Array<ScrcpyVirtualButtonOrder?> = arrayOfNulls(size)
        }
    }

    /**
     * 设置-远程过滤子页面
     * Settings remote filter sub page
     */
    @Serializable
    @SuppressLint("ParcelCreator")
    data object SettingsRemoteFilter : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        @JvmField
        val CREATOR: Parcelable.Creator<SettingsRemoteFilter> = object : Parcelable.Creator<SettingsRemoteFilter> {
            override fun createFromParcel(parcel: Parcel): SettingsRemoteFilter = SettingsRemoteFilter
            override fun newArray(size: Int): Array<SettingsRemoteFilter?> = arrayOfNulls(size)
        }
    }

    /**
     * 设置-本地过滤子页面
     * Settings local filter sub page
     */
    @Serializable
    @SuppressLint("ParcelCreator")
    data object SettingsLocalFilter : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        @JvmField
        val CREATOR: Parcelable.Creator<SettingsLocalFilter> = object : Parcelable.Creator<SettingsLocalFilter> {
            override fun createFromParcel(parcel: Parcel): SettingsLocalFilter = SettingsLocalFilter
            override fun newArray(size: Int): Array<SettingsLocalFilter?> = arrayOfNulls(size)
        }
    }

    /**
     * 设置-超级岛子页面
     * Settings super island sub page
     */
    @Serializable
    @SuppressLint("ParcelCreator")
    data object SettingsSuperIsland : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        @JvmField
        val CREATOR: Parcelable.Creator<SettingsSuperIsland> = object : Parcelable.Creator<SettingsSuperIsland> {
            override fun createFromParcel(parcel: Parcel): SettingsSuperIsland = SettingsSuperIsland
            override fun newArray(size: Int): Array<SettingsSuperIsland?> = arrayOfNulls(size)
        }
    }

    /**
     * 设置-屏幕镜像子页面
     * Settings scrcpy sub page
     */
    @Serializable
    @SuppressLint("ParcelCreator")
    data object SettingsScrcpy : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        @JvmField
        val CREATOR: Parcelable.Creator<SettingsScrcpy> = object : Parcelable.Creator<SettingsScrcpy> {
            override fun createFromParcel(parcel: Parcel): SettingsScrcpy = SettingsScrcpy
            override fun newArray(size: Int): Array<SettingsScrcpy?> = arrayOfNulls(size)
        }
    }

    /**
     * 设置-关于子页面
     * Settings about sub page
     */
    @Serializable
    @SuppressLint("ParcelCreator")
    data object SettingsAbout : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        @JvmField
        val CREATOR: Parcelable.Creator<SettingsAbout> = object : Parcelable.Creator<SettingsAbout> {
            override fun createFromParcel(parcel: Parcel): SettingsAbout = SettingsAbout
            override fun newArray(size: Int): Array<SettingsAbout?> = arrayOfNulls(size)
        }
    }

    /**
     * 设置-外观子页面
     * Settings appearance sub page
     */
    @Serializable
    @SuppressLint("ParcelCreator")
    data object SettingsAppearance : Route {
        override fun describeContents(): Int = 0
        override fun writeToParcel(parcel: Parcel, flags: Int) {}
        @JvmField
        val CREATOR: Parcelable.Creator<SettingsAppearance> = object : Parcelable.Creator<SettingsAppearance> {
            override fun createFromParcel(parcel: Parcel): SettingsAppearance = SettingsAppearance
            override fun newArray(size: Int): Array<SettingsAppearance?> = arrayOfNulls(size)
        }
    }
}
