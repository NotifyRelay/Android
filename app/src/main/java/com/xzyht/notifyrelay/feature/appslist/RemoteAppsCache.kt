package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import com.xzyht.notifyrelay.nativecore.device.appsync.RemoteAppsCacheCore
import notifyrelay.domain.feature.appslist.model.RemoteAppInfo

/**
 * 远程应用缓存（Android 侧入口）。
 *
 * 实现位于公共端 [RemoteAppsCacheCore]；本类保留 `Context` 形参以不动既有调用点。
 */
internal object RemoteAppsCache {
    /** 缓存远程应用列表。 */
    suspend fun cacheRemoteAppList(
        context: Context,
        apps: Map<String, String>,
        deviceUuid: String,
    ) {
        RemoteAppsCacheCore.cache(apps, deviceUuid)
    }

    /** 获取指定设备的远程应用列表（含置顶状态）。 */
    suspend fun getRemoteAppsList(
        context: Context,
        deviceUuid: String,
    ): List<RemoteAppInfo> = RemoteAppsCacheCore.list(deviceUuid)
}
