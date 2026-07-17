package com.xzyht.notifyrelay.feature.notification.superisland.image

import android.content.Context
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.xzynine.superislandui.diff.DiffSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import notifyrelay.base.util.Logger
import notifyrelay.data.StorageManager
import notifyrelay.data.database.entity.SuperIslandHistoryEntity
import notifyrelay.data.database.repository.DatabaseRepository
import java.io.File

/**
 * 超级岛图片去重存储（数据库绑定）。
 *
 * 存储策略：
 * - 按内容 hash 做全局去重，图片原文存入数据库；
 * - 使用 packageName + imageKey 绑定当前图片；
 * - 历史记录存储图片ID，加载时再读取图片数据。
 */
object SuperIslandImageStore {
    private const val LEGACY_IMAGE_STORE_FILE = "super_island_images.json"
    private const val MIGRATION_FLAG_KEY = "super_island_image_db_migrated"
    private const val DEFAULT_PACKAGE_NAME = "unknown"

    private val gson = Gson()
    private val stringStringMapType = object : TypeToken<Map<String, String>>() {}.type
    private val migrationMutex = Mutex()
    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var migrationDeferred: Deferred<Unit>? = null

    suspend fun internAll(
        context: Context,
        packageName: String?,
        input: Map<String, String>?
    ): Map<String, String> {
        if (input.isNullOrEmpty()) return emptyMap()
        ensureMigrated(context)
        val repo = DatabaseRepository.getInstance(context)
        val normalizedPackage = normalizePackageName(packageName)
        val now = System.currentTimeMillis()
        for ((key, value) in input) {
            if (value.isBlank()) continue
            val hash = DiffSystem.sha256(value)
            repo.upsertSuperIslandImageBinding(normalizedPackage, key, hash, value, now)
        }
        return input.toMap()
    }

    suspend fun bindAll(
        context: Context,
        packageName: String?,
        input: Map<String, String>?
    ): Map<String, String> {
        if (input.isNullOrEmpty()) return emptyMap()
        ensureMigrated(context)
        val repo = DatabaseRepository.getInstance(context)
        val normalizedPackage = normalizePackageName(packageName)
        val now = System.currentTimeMillis()
        val out = mutableMapOf<String, String>()
        for ((key, value) in input) {
            if (value.isBlank()) continue
            val hash = DiffSystem.sha256(value)
            val imageId = repo.upsertSuperIslandImageBinding(normalizedPackage, key, hash, value, now)
            if (imageId > 0) {
                out[key] = imageId.toString()
            }
        }
        return out
    }

    suspend fun resolvePicMap(
        context: Context,
        picMap: Map<String, String>?
    ): Map<String, String> {
        if (picMap.isNullOrEmpty()) return emptyMap()
        ensureMigrated(context)
        val repo = DatabaseRepository.getInstance(context)
        val out = mutableMapOf<String, String>()
        for ((key, value) in picMap) {
            if (value.isBlank()) continue
            val imageId = value.toLongOrNull()
            if (imageId == null || imageId <= 0) {
                out[key] = value
            } else {
                val data = repo.resolveSuperIslandImageById(imageId)
                out[key] = if (!data.isNullOrBlank()) data else value
            }
        }
        return out
    }

    suspend fun resolveSuspend(context: Context?, value: String?): String? {
        if (value.isNullOrBlank()) return value
        val trimmed = value.trim()
        val imageId = trimmed.toLongOrNull() ?: return value
        if (imageId <= 0 || context == null) return value
        ensureMigrated(context)
        return DatabaseRepository.getInstance(context).resolveSuperIslandImageById(imageId) ?: value
    }

    fun resolve(context: Context?, value: String?): String? {
        if (value.isNullOrBlank()) return value
        val trimmed = value.trim()
        val imageId = trimmed.toLongOrNull() ?: return value
        if (imageId <= 0 || context == null || isMainThread()) {
            if (isMainThread()) {
                Logger.w("SuperIslandImageStore", "resolve在主线程调用，无法解析图片ID: $imageId，请使用resolveSuspend或resolvePicMap")
            }
            return value
        }
        return runBlocking(Dispatchers.IO) {
            DatabaseRepository.getInstance(context).resolveSuperIslandImageById(imageId) ?: value
        }
    }

    suspend fun prune(context: Context, maxEntries: Int = 3000, maxAgeDays: Int = 30) {
        ensureMigrated(context)
        DatabaseRepository.getInstance(context).pruneSuperIslandImages(maxEntries, maxAgeDays)
    }

    suspend fun clearAll(context: Context) {
        ensureMigrated(context)
        DatabaseRepository.getInstance(context).clearSuperIslandImages()
    }

    private fun normalizePackageName(packageName: String?): String {
        val trimmed = packageName?.trim().orEmpty()
        return if (trimmed.isBlank()) DEFAULT_PACKAGE_NAME else trimmed
    }

    private fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    private suspend fun ensureMigrated(context: Context) {
        if (StorageManager.getBoolean(context, MIGRATION_FLAG_KEY, false)) return

        val deferredToAwait: Deferred<Unit>? = migrationMutex.withLock {
            if (StorageManager.getBoolean(context, MIGRATION_FLAG_KEY, false)) return@withLock null

            migrationDeferred?.let { return@withLock it }

            migrationScope.async {
                try {
                    migrateLegacyData(context)
                    StorageManager.putBoolean(context, MIGRATION_FLAG_KEY, true)
                } catch (_: Exception) {
                    migrationMutex.withLock {
                        migrationDeferred = null
                    }
                }
            }.also { migrationDeferred = it }
        }

        deferredToAwait?.await()
    }

    private suspend fun migrateLegacyData(context: Context) {
        val legacyImages = readLegacyImageStore(context)
        val repo = DatabaseRepository.getInstance(context)
        val now = System.currentTimeMillis()

        for ((hash, data) in legacyImages) {
            if (hash.isBlank() || data.isBlank()) continue
            repo.upsertSuperIslandImage(hash, data, now)
        }

        val historyEntries = repo.getSuperIslandHistoryFull()
        for (entry in historyEntries) {
            val oldMap = parsePicMap(entry.picMap)
            if (oldMap.isEmpty()) continue

            val packageName = resolvePackageName(entry)
            val newMap = mutableMapOf<String, String>()
            for ((key, rawValue) in oldMap) {
                if (rawValue.isBlank()) continue
                val numericId = rawValue.toLongOrNull()
                if (numericId != null && numericId > 0) {
                    newMap[key] = rawValue
                    continue
                }
                val resolved = resolveLegacyValue(rawValue, legacyImages) ?: continue
                val hash = DiffSystem.sha256(resolved)
                val imageId = repo.upsertSuperIslandImageBinding(packageName, key, hash, resolved, now)
                if (imageId > 0) {
                    newMap[key] = imageId.toString()
                }
            }

            val updatedEntry = entry.copy(picMap = gson.toJson(newMap))
            repo.saveSuperIslandHistory(updatedEntry)
        }

        deleteLegacyStoreFile(context)
    }

    private fun parsePicMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            gson.fromJson(raw, stringStringMapType) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun resolvePackageName(entry: SuperIslandHistoryEntity): String {
        val mapped = entry.mappedPackage?.trim().orEmpty()
        if (mapped.isNotBlank()) return mapped
        val original = entry.originalPackage?.trim().orEmpty()
        if (original.isNotBlank()) return original
        val appName = entry.appName?.trim().orEmpty()
        return if (appName.isNotBlank()) appName else DEFAULT_PACKAGE_NAME
    }

    private fun resolveLegacyValue(value: String, legacyImages: Map<String, String>): String? {
        val trimmed = value.trim()
        return if (trimmed.startsWith("ref:", ignoreCase = true)) {
            val hash = trimmed.substringAfter(":").trim()
            legacyImages[hash]
        } else {
            trimmed
        }
    }

    private fun readLegacyImageStore(context: Context): Map<String, String> {
        val file = File(context.filesDir, LEGACY_IMAGE_STORE_FILE)
        if (!file.exists()) return emptyMap()
        return try {
            val raw = file.readText(Charsets.UTF_8)
            val wrapperType = object : TypeToken<Map<String, Any>>() {}.type
            val wrapper: Map<String, Any>? = gson.fromJson(raw, wrapperType)
            if (wrapper != null && wrapper.containsKey("images")) {
                val imagesJson = gson.toJson(wrapper["images"])
                gson.fromJson(imagesJson, stringStringMapType) ?: emptyMap()
            } else {
                gson.fromJson(raw, stringStringMapType) ?: emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun deleteLegacyStoreFile(context: Context) {
        try {
            File(context.filesDir, LEGACY_IMAGE_STORE_FILE).delete()
        } catch (_: Exception) {}
    }
}