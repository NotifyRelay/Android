package com.xzyht.notifyrelay

import android.app.Application
import com.xzyht.notifyrelay.feature.appslist.AndroidAppIconPort
import com.xzyht.notifyrelay.feature.appslist.AndroidLocalAppsPort
import com.xzyht.notifyrelay.feature.appslist.AndroidPinnedAppsPort
import com.xzyht.notifyrelay.feature.notification.filter.AndroidLocalFilterPort
import notifyrelay.data.DataLayerAndroid
import notifyrelay.domain.feature.notification.filter.LocalFilterPortRegistry
import notifyrelay.shared.feature.appslist.AppIconPortRegistry
import notifyrelay.shared.feature.appslist.LocalAppsPortRegistry
import notifyrelay.shared.feature.appslist.PinnedAppsPortRegistry

/**
 * 应用入口：初始化数据层，并把平台能力实现注册到公共端端口。
 */
class NotifyRelayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DataLayerAndroid.initialize(this)
        installPorts()
    }

    /** 注册公共端所需的能力端口。 */
    private fun installPorts() {
        AppIconPortRegistry.install(AndroidAppIconPort(this))
        LocalAppsPortRegistry.install(AndroidLocalAppsPort(this))
        PinnedAppsPortRegistry.install(AndroidPinnedAppsPort(this))
        LocalFilterPortRegistry.install(AndroidLocalFilterPort(this))
    }
}
