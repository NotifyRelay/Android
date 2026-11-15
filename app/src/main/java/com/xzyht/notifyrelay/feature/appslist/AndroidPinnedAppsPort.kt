package com.xzyht.notifyrelay.feature.appslist

import notifyrelay.shared.feature.appslist.PinnedAppsPort
import notifyrelay.shared.feature.appslist.RepositoryPinnedAppsPort

/**
 * Android 的置顶端口。
 *
 * 置顶状态存在公共数据层，两端读同一份；本类只作为平台侧的注册名，行为全部来自
 * [RepositoryPinnedAppsPort]，不再重复实现四个转发方法。
 */
object AndroidPinnedAppsPort : PinnedAppsPort by RepositoryPinnedAppsPort
