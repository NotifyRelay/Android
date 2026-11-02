package notifyrelay.data.repository.applist

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import notifyrelay.data.Storage

/**
 * 置顶应用仓库。
 *
 * 按设备维度（deviceUuid）维护置顶应用集合，采用内存状态流 + 偏好持久化双写：
 * - 内存：[pinnedApps]（供 UI 观察）
 * - 持久化：键 `pinned_apps_<deviceUuid>` 的字符串集合
 *
 * 该 object 是置顶状态的唯一持有者。
 */
object PinnedAppsRepository {
    private const val KEY_PINNED_APPS_PREFIX = "pinned_apps_"

    private val _pinnedApps = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** 设备 UUID → 已置顶包名集合。 */
    val pinnedApps: StateFlow<Map<String, Set<String>>> = _pinnedApps.asStateFlow()

    /** 从偏好载入指定设备的置顶集合并写入内存状态。 */
    fun loadPinnedApps(deviceUuid: String) {
        val pinnedSet = Storage.getStringSet(KEY_PINNED_APPS_PREFIX + deviceUuid, emptySet())
        val currentMap = _pinnedApps.value.toMutableMap()
        currentMap[deviceUuid] = pinnedSet
        _pinnedApps.value = currentMap
    }

    /** 置顶指定设备的某个应用：先更新内存状态，再持久化。 */
    fun pinApp(
        deviceUuid: String,
        packageName: String,
    ) {
        val currentMap = _pinnedApps.value.toMutableMap()
        val currentSet = currentMap[deviceUuid]?.toMutableSet() ?: mutableSetOf()
        currentSet.add(packageName)
        currentMap[deviceUuid] = currentSet.toSet()
        _pinnedApps.value = currentMap
        savePinnedApps(deviceUuid)
    }

    /** 取消置顶指定设备的某个应用：先更新内存状态，再持久化。 */
    fun unpinApp(
        deviceUuid: String,
        packageName: String,
    ) {
        val currentMap = _pinnedApps.value.toMutableMap()
        val currentSet = currentMap[deviceUuid]?.toMutableSet() ?: mutableSetOf()
        currentSet.remove(packageName)
        currentMap[deviceUuid] = currentSet.toSet()
        _pinnedApps.value = currentMap
        savePinnedApps(deviceUuid)
    }

    /** 判断指定设备的某个应用是否已置顶。 */
    fun isAppPinned(
        deviceUuid: String,
        packageName: String,
    ): Boolean = _pinnedApps.value[deviceUuid]?.contains(packageName) ?: false

    private fun savePinnedApps(deviceUuid: String) {
        Storage.putStringSet(
            KEY_PINNED_APPS_PREFIX + deviceUuid,
            _pinnedApps.value[deviceUuid] ?: emptySet(),
        )
    }
}
