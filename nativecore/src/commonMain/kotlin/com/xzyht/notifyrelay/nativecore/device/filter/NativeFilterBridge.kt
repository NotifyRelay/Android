package com.xzyht.notifyrelay.nativecore.device.filter

import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.domain.feature.notification.filter.NativeFilterPort

/**
 * Rust 过滤能力端口：以 [NativeCore] 的过滤四方法为唯一来源。
 *
 * Android 与桌面跑的是同一份 Rust core，两端此前各写了一个逐字相同的转发对象；
 * 收敛在此后，平台接线只需注册本对象。
 */
object NativeFilterBridge : NativeFilterPort {
    override fun isReady(): Boolean = NativeCore.isReady()

    override fun setFilterConfig(json: String): Boolean = NativeCore.setFilterConfig(json) == 0

    override fun mapLocalPackage(packageName: String): String? = NativeCore.mapLocalPackage(packageName)

    override fun checkFilterMode(
        packageName: String,
        notificationPackageName: String,
        title: String,
        text: String,
    ): Boolean = NativeCore.checkFilterMode(packageName, notificationPackageName, title, text)
}
