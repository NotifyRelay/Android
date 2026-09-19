package com.xzyht.notifyrelay.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import com.xzyht.notifyrelay.feature.appslist.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 应用图标批量预加载器，供 [NotificationHistoryViewModel] 与 [SuperIslandHistoryViewModel] 共用。
 *
 * 由调用方以**组合**方式持有，并把各自的缓存 [MutableStateFlow] 与在途集合 [MutableSet] 注入进来，
 * 因此两个 ViewModel 的缓存与失效语义仍各自独立。
 *
 * **注意**：`AppRepository.iconUpdates` 的订阅**不在此处**，仍留在各 ViewModel 的 `init` 中
 * （失效动作的缓存归属不同，搬走会让两者的失效语义纠缠）。
 *
 * @param application 用于查询 `PackageManager` 与获取图标。
 * @param scope 执行批量加载的协程作用域（通常是 `viewModelScope`）。
 * @param cache 图标缓存（`包名 -> (应用名, 图标)`），加载完成后写回。
 * @param iconLoading 在途包名集合，用于去重并发加载。
 */
internal class AppIconPreloader(
    private val application: Application,
    private val scope: CoroutineScope,
    private val cache: MutableStateFlow<Map<String, Pair<String, Bitmap?>>>,
    private val iconLoading: MutableSet<String>,
) {
    /**
     * 批量预加载图标（已缓存或在途的包名会跳过）。
     *
     * @param packageNames 目标包名列表。
     * @param excludeUnknownApp 是否排除 `"(未知应用)"` 这一占位包名（超级岛历史需要）。
     */
    fun preload(
        packageNames: List<String>,
        excludeUnknownApp: Boolean = false,
    ) {
        val targets = packageNames.filter { it.isNotBlank() && (!excludeUnknownApp || it != "(未知应用)") }
        if (targets.isEmpty()) return

        val toLoad =
            synchronized(iconLoading) {
                val cached = cache.value
                val loadTargets =
                    targets.filter { pkg ->
                        !iconLoading.contains(pkg) && cached[pkg] == null
                    }
                iconLoading.addAll(loadTargets)
                loadTargets
            }

        if (toLoad.isEmpty()) return

        scope.launch(Dispatchers.IO) {
            try {
                val updates = mutableMapOf<String, Pair<String, Bitmap?>>()
                for (packageName in toLoad) {
                    updates[packageName] = getAppNameAndIcon(packageName)
                }
                if (updates.isNotEmpty()) {
                    cache.update { current ->
                        current + updates
                    }
                }
            } finally {
                synchronized(iconLoading) {
                    iconLoading.removeAll(toLoad)
                }
            }
        }
    }

    private suspend fun getAppNameAndIcon(packageName: String): Pair<String, Bitmap?> {
        var name: String
        try {
            val pm = application.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            name = pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            name = packageName
        }
        val icon: Bitmap? =
            try {
                AppRepository.getAppIconWithAutoRequest(application, packageName)
            } catch (_: Exception) {
                null
            }
        return name to icon
    }
}
