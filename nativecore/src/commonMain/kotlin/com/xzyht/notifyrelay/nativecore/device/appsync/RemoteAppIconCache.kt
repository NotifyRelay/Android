package com.xzyht.notifyrelay.nativecore.device.appsync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import notifyrelay.data.database.entity.AppDeviceEntity
import notifyrelay.data.database.entity.AppEntity
import notifyrelay.data.database.repository.DatabaseRepository

/**
 * 远端应用图标缓存（纯字节）。
 *
 * 图标统一以 PNG 字节存放于 `AppEntity.iconBytes`；
 * 位图转换由 [IconCodecPort] 承担，本类不出现平台位图类型。
 *
 * 该 object 是图标更新信号的唯一持有者，UI 侧订阅 [iconUpdates] 刷新。
 */
object RemoteAppIconCache {
    private val _iconUpdates = MutableStateFlow<Pair<String, Long>?>(null)

    /** 图标更新信号：包名 与 时间戳。 */
    val iconUpdates: StateFlow<Pair<String, Long>?> = _iconUpdates.asStateFlow()

    /** 读取单个外部应用图标的 PNG 字节。 */
    suspend fun cachedBytes(packageName: String): ByteArray? =
        DatabaseRepository.getInstance().getAppByPackageName(packageName)?.iconBytes

    /** 批量读取外部应用图标的 PNG 字节；缺失项为 null。 */
    suspend fun cachedBytes(packageNames: List<String>): Map<String, ByteArray?> {
        val apps = DatabaseRepository.getInstance().getAppsByPackageNames(packageNames)
        val appMap = apps.associateBy { it.packageName }
        return packageNames.associateWith { packageName -> appMap[packageName]?.iconBytes }
    }

    /** 缓存外部应用图标；[pngBytes] 为 null 时标记为缺失。 */
    suspend fun cache(
        packageName: String,
        pngBytes: ByteArray?,
        deviceUuid: String,
    ) {
        val repo = DatabaseRepository.getInstance()
        val now = System.currentTimeMillis()
        val existing = repo.getAppByPackageName(packageName)
        val entity =
            if (existing != null) {
                existing.copy(
                    iconBytes = pngBytes,
                    isIconMissing = pngBytes == null,
                    lastUpdated = now,
                )
            } else {
                AppEntity(
                    packageName = packageName,
                    appName = packageName,
                    isSystemApp = false,
                    iconBytes = pngBytes,
                    isIconMissing = pngBytes == null,
                    lastUpdated = now,
                )
            }
        repo.saveApp(entity)
        repo.saveAppDeviceAssociations(
            listOf(
                AppDeviceEntity(
                    packageName = packageName,
                    sourceDevice = deviceUuid,
                    lastUpdated = now,
                ),
            ),
        )
        _iconUpdates.value = packageName to now
    }

    /** 标记图标缺失。 */
    suspend fun markMissing(packageName: String) {
        DatabaseRepository.getInstance().markAppIconAsMissing(packageName)
    }

    /** 从给定包名中筛出图标缺失的项。 */
    suspend fun missingOf(packageNames: List<String>): List<String> {
        val repo = DatabaseRepository.getInstance()
        return packageNames.filter { packageName ->
            val app = repo.getAppByPackageName(packageName)
            app?.isIconMissing ?: true
        }
    }
}
