package com.xzyht.notifyrelay

import android.app.Application
import com.xzyht.notifyrelay.feature.appslist.AndroidAppIconCodecPort
import com.xzyht.notifyrelay.feature.appslist.AndroidAppIconPort
import com.xzyht.notifyrelay.feature.appslist.AndroidLocalAppsPort
import com.xzyht.notifyrelay.feature.appslist.AndroidPinnedAppsPort
import com.xzyht.notifyrelay.feature.appslist.AndroidRemoteAppsPort
import com.xzyht.notifyrelay.feature.media.AndroidAudioRelayPort
import com.xzyht.notifyrelay.feature.media.AndroidMediaControlPort
import com.xzyht.notifyrelay.feature.notification.filter.AndroidLocalFilterPort
import com.xzyht.notifyrelay.feature.notification.filter.AndroidRemoteFilterPort
import com.xzyht.notifyrelay.feature.notification.history.AndroidNotificationHistoryPort
import com.xzyht.notifyrelay.feature.notification.history.AndroidSuperIslandHistoryPort
import com.xzyht.notifyrelay.feature.notification.superisland.config.AndroidSuperIslandConfigPort
import com.xzyht.notifyrelay.feature.platform.AndroidFormFactorPort
import com.xzyht.notifyrelay.feature.platform.AndroidInstalledPackagesPort
import com.xzyht.notifyrelay.feature.platform.AndroidNativeFilterPort
import com.xzyht.notifyrelay.nativecore.device.appsync.IconCodecPortRegistry
import notifyrelay.data.DataLayerAndroid
import notifyrelay.data.Storage
import notifyrelay.data.filter.RemoteFilterRepository
import notifyrelay.domain.feature.appslist.InstalledPackagesPortRegistry
import notifyrelay.domain.feature.appslist.RemoteAppsPortRegistry
import notifyrelay.domain.feature.media.AudioRelayPortRegistry
import notifyrelay.domain.feature.media.MediaControlPortRegistry
import notifyrelay.domain.feature.notification.filter.LocalFilterPortRegistry
import notifyrelay.domain.feature.notification.filter.NativeFilterPortRegistry
import notifyrelay.domain.feature.notification.filter.RemoteFilterPortRegistry
import notifyrelay.domain.feature.notification.history.NotificationHistoryPortRegistry
import notifyrelay.domain.feature.notification.history.SuperIslandHistoryPortRegistry
import notifyrelay.domain.feature.notification.superisland.SuperIslandConfigPortRegistry
import notifyrelay.domain.platform.FormFactorPortRegistry
import notifyrelay.shared.feature.appslist.AppIconPortRegistry
import notifyrelay.shared.feature.appslist.LocalAppsPortRegistry
import notifyrelay.shared.feature.appslist.PinnedAppsPortRegistry
import notifyrelay.shared.ui.common.DebugFlags
import notifyrelay.shared.ui.screen.loadLogLevel

/**
 * 应用入口：初始化数据层，并把平台能力实现注册到公共端端口。
 */
class NotifyRelayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DataLayerAndroid.initialize(this)
        installPorts()
        loadPersistedFlags()
    }

    /** 注册公共端所需的能力端口。 */
    private fun installPorts() {
        FormFactorPortRegistry.install(AndroidFormFactorPort(this))
        InstalledPackagesPortRegistry.install(AndroidInstalledPackagesPort(this))
        NativeFilterPortRegistry.install(AndroidNativeFilterPort)
        IconCodecPortRegistry.install(AndroidAppIconCodecPort(this))

        AppIconPortRegistry.install(AndroidAppIconPort(this))
        LocalAppsPortRegistry.install(AndroidLocalAppsPort(this))
        PinnedAppsPortRegistry.install(AndroidPinnedAppsPort())
        RemoteAppsPortRegistry.install(AndroidRemoteAppsPort(this))
        LocalFilterPortRegistry.install(AndroidLocalFilterPort())
        RemoteFilterPortRegistry.install(AndroidRemoteFilterPort(RemoteFilterRepository()))
        MediaControlPortRegistry.install(AndroidMediaControlPort(this))
        AudioRelayPortRegistry.install(AndroidAudioRelayPort(this))
        SuperIslandConfigPortRegistry.install(AndroidSuperIslandConfigPort(this))
        NotificationHistoryPortRegistry.install(AndroidNotificationHistoryPort(this))
        SuperIslandHistoryPortRegistry.install(AndroidSuperIslandHistoryPort(this))
    }

    /** 装载日志级别与调试开关初值。 */
    private fun loadPersistedFlags() {
        loadLogLevel { key, default -> Storage.getInt(key, default) }
        DebugFlags.load { key, default -> Storage.getBoolean(key, default) }
    }
}
