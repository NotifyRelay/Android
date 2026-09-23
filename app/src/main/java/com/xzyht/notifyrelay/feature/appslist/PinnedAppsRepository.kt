package com.xzyht.notifyrelay.feature.appslist

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 置顶应用仓库。
 *
 * 按设备维度（deviceUuid）维护置顶应用集合，采用内存状态流 + SharedPreferences 双写：
 * - 内存：[pinnedApps]（供 UI 观察）
 * - 持久化：`remote_apps_prefs` 中的 `pinned_apps_<deviceUuid>` 字符串集合
 *
 * 该 object 是置顶状态的唯一持有者，[AppRepository] 仅作为门面转发，避免出现多份状态。
 */
internal object PinnedAppsRepository {
    private const val PREFS_NAME = "remote_apps_prefs"
    private const val KEY_PINNED_APPS_PREFIX = "pinned_apps_"

    private val _pinnedApps = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val pinnedApps: StateFlow<Map<String, Set<String>>> = _pinnedApps.asStateFlow()

    /**
     * 从 SharedPreferences 载入指定设备的置顶应用集合到内存状态。
     *
     * @param context Android 上下文，用于访问 SharedPreferences。
     * @param deviceUuid 设备 UUID。
     */
    fun loadPinnedApps(
        context: Context,
        deviceUuid: String,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_PINNED_APPS_PREFIX + deviceUuid
        val pinnedSet = prefs.getStringSet(key, emptySet()) ?: emptySet()
        val currentMap = _pinnedApps.value.toMutableMap()
        currentMap[deviceUuid] = pinnedSet.toSet()
        _pinnedApps.value = currentMap
    }

    /**
     * 置顶指定设备的某个应用：先更新内存状态，再持久化到 SharedPreferences。
     *
     * @param context Android 上下文，用于访问 SharedPreferences。
     * @param deviceUuid 设备 UUID。
     * @param packageName 目标应用包名。
     */
    fun pinApp(
        context: Context,
        deviceUuid: String,
        packageName: String,
    ) {
        val currentMap = _pinnedApps.value.toMutableMap()
        val currentSet = currentMap[deviceUuid]?.toMutableSet() ?: mutableSetOf()
        currentSet.add(packageName)
        currentMap[deviceUuid] = currentSet.toSet()
        _pinnedApps.value = currentMap
        savePinnedApps(context, deviceUuid)
    }

    /**
     * 取消置顶指定设备的某个应用：先更新内存状态，再持久化到 SharedPreferences。
     *
     * @param context Android 上下文，用于访问 SharedPreferences。
     * @param deviceUuid 设备 UUID。
     * @param packageName 目标应用包名。
     */
    fun unpinApp(
        context: Context,
        deviceUuid: String,
        packageName: String,
    ) {
        val currentMap = _pinnedApps.value.toMutableMap()
        val currentSet = currentMap[deviceUuid]?.toMutableSet() ?: mutableSetOf()
        currentSet.remove(packageName)
        currentMap[deviceUuid] = currentSet.toSet()
        _pinnedApps.value = currentMap
        savePinnedApps(context, deviceUuid)
    }

    /**
     * 判断指定设备的某个应用是否已置顶。
     *
     * @param deviceUuid 设备 UUID。
     * @param packageName 目标应用包名。
     * @return 已置顶返回 true，否则返回 false。
     */
    fun isAppPinned(
        deviceUuid: String,
        packageName: String,
    ): Boolean = _pinnedApps.value[deviceUuid]?.contains(packageName) ?: false

    private fun savePinnedApps(
        context: Context,
        deviceUuid: String,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = KEY_PINNED_APPS_PREFIX + deviceUuid
        prefs.edit().putStringSet(key, _pinnedApps.value[deviceUuid] ?: emptySet()).apply()
    }
}
