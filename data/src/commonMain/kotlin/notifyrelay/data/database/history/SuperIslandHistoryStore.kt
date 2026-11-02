package notifyrelay.data.database.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import notifyrelay.base.log.Logger
import notifyrelay.base.util.sha256
import notifyrelay.data.database.entity.SuperIslandHistoryEntity
import notifyrelay.data.database.repository.DatabaseRepository
import notifyrelay.data.platform.LegacyStoragePort
import notifyrelay.data.platform.LegacyStoragePortRegistry

/** 超级岛历史的写入与详情读取条目。 */
data class SuperIslandHistoryStoreEntry(
    val id: Long,
    val sourceDeviceUuid: String? = null,
    val originalPackage: String? = null,
    val mappedPackage: String? = null,
    val appName: String? = null,
    val title: String? = null,
    val text: String? = null,
    val paramV2Raw: String? = null,
    val picMap: Map<String, String> = emptyMap(),
    val rawPayload: String? = null,
    val featureId: String? = null,
)

/**
 * 超级岛历史存储：写入、清空与按 id 读取详情。
 *
 * 写入与清空为异步投递（调用方不等待落库）；图片值以数字 id 绑定后入库。
 */
object SuperIslandHistoryStore {
    private const val TAG = "SuperIslandHistoryStore"
    private const val MAX_ENTRIES = 600
    private const val LEGACY_IMAGE_STORE_FILE = "super_island_images.json"
    private const val DEFAULT_PACKAGE_NAME = "unknown"

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 按 id 读取详情（含图片解析）。 */
    suspend fun loadEntryDetail(id: Long): SuperIslandHistoryStoreEntry? {
        val repo = DatabaseRepository.getInstance()
        val entity =
            try {
                repo.getSuperIslandHistoryById(id)
            } catch (e: IllegalStateException) {
                Logger.w(TAG, "读取超级岛历史详情失败: id=" + id, e)
                null
            }
        return entity?.let { e ->
            val resolvedPicMap = resolvePicMap(parsePicMap(e.picMap))
            SuperIslandHistoryStoreEntry(
                id = e.id,
                sourceDeviceUuid = e.sourceDeviceUuid,
                originalPackage = e.originalPackage,
                mappedPackage = e.mappedPackage,
                appName = e.appName,
                title = e.title,
                text = e.text,
                paramV2Raw = e.paramV2Raw,
                picMap = resolvedPicMap,
                rawPayload = e.rawPayload,
                featureId = e.featureId,
            )
        }
    }

    /** 追加一条历史；图片值先绑定为数字 id，写入后裁剪历史条数。 */
    fun append(entry: SuperIslandHistoryStoreEntry) {
        scope.launch {
            try {
                val repository = DatabaseRepository.getInstance()
                val boundPicMap = bindAll(resolvePackageName(entry), entry.picMap)
                val entity =
                    SuperIslandHistoryEntity(
                        id = entry.id,
                        sourceDeviceUuid = entry.sourceDeviceUuid,
                        originalPackage = entry.originalPackage,
                        mappedPackage = entry.mappedPackage,
                        appName = entry.appName,
                        title = entry.title,
                        text = entry.text,
                        paramV2Raw = entry.paramV2Raw,
                        picMap = json.encodeToString(JsonObject.serializer(), toJsonObject(boundPicMap)),
                        rawPayload = entry.rawPayload,
                        featureId = entry.featureId,
                    )

                repository.saveSuperIslandHistory(entity)
                repository.deleteOldSuperIslandHistory(MAX_ENTRIES)
            } catch (e: IllegalStateException) {
                Logger.w(TAG, "写入超级岛历史失败: id=" + entry.id, e)
            }
        }
    }

    /** 把图片原文绑定到（包名, 图片键）并返回「图片键 → 图片 id」映射。 */
    private suspend fun bindAll(
        packageName: String?,
        input: Map<String, String>?,
    ): Map<String, String> {
        if (input.isNullOrEmpty()) return emptyMap()
        val repo = DatabaseRepository.getInstance()
        val normalizedPackage = normalizePackageName(packageName)
        val now = System.currentTimeMillis()
        val out = mutableMapOf<String, String>()
        for ((key, value) in input) {
            if (value.isBlank()) continue
            val imageId = repo.upsertSuperIslandImageBinding(normalizedPackage, key, sha256(value), value, now)
            if (imageId > 0) {
                out[key] = imageId.toString()
            }
        }
        return out
    }

    /** 解析条目所属包名：映射包名优先，其次原始包名，最后应用名。 */
    private fun resolvePackageName(entry: SuperIslandHistoryStoreEntry): String? {
        entry.mappedPackage?.takeIf { it.isNotBlank() }?.let { return it }
        entry.originalPackage?.takeIf { it.isNotBlank() }?.let { return it }
        return entry.appName?.takeIf { it.isNotBlank() }
    }

    /** 清空全部历史。 */
    fun clearAll() {
        scope.launch {
            try {
                DatabaseRepository.getInstance().clearSuperIslandHistory()
            } catch (e: IllegalStateException) {
                Logger.w(TAG, "清空超级岛历史失败", e)
            }
        }
    }

    /**
     * 迁移旧版本写在应用私有目录的图片文件。
     *
     * 平台未注入 [LegacyStoragePort] 或不存在旧文件时跳过；
     * 迁移完成标记由调用方写入偏好。
     */
    suspend fun migrateLegacyImages(): Boolean {
        val port = LegacyStoragePortRegistry.current ?: return false
        val raw = port.readFile(LEGACY_IMAGE_STORE_FILE) ?: return false
        val images = parseLegacyImages(raw)
        if (images.isEmpty()) {
            port.deleteFile(LEGACY_IMAGE_STORE_FILE)
            return true
        }
        val repo = DatabaseRepository.getInstance()
        val now = System.currentTimeMillis()
        for ((hash, data) in images) {
            if (hash.isBlank() || data.isBlank()) continue
            repo.upsertSuperIslandImage(hash, data, now)
        }
        port.deleteFile(LEGACY_IMAGE_STORE_FILE)
        return true
    }

    private fun parseLegacyImages(raw: String): Map<String, String> =
        try {
            val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyMap()
            val source = (root["images"] as? JsonObject) ?: root
            source.entries.associate { (key, value) -> key to value.jsonPrimitive.content }
        } catch (e: IllegalArgumentException) {
            Logger.w(TAG, "旧图片文件解析失败", e)
            emptyMap()
        }

    private fun parsePicMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = json.parseToJsonElement(raw) as? JsonObject ?: return emptyMap()
            obj.entries.associate { (key, value) -> key to value.jsonPrimitive.content }
        } catch (e: IllegalArgumentException) {
            Logger.w(TAG, "picMap 解析失败", e)
            emptyMap()
        }
    }

    private suspend fun resolvePicMap(picMap: Map<String, String>): Map<String, String> {
        if (picMap.isEmpty()) return emptyMap()
        val repo = DatabaseRepository.getInstance()
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

    private fun toJsonObject(source: Map<String, String>): JsonObject = JsonObject(source.mapValues { (_, value) -> JsonPrimitive(value) })

    /** 归一化包名，空值落到 [DEFAULT_PACKAGE_NAME]。 */
    fun normalizePackageName(packageName: String?): String {
        val trimmed = packageName?.trim().orEmpty()
        return if (trimmed.isBlank()) DEFAULT_PACKAGE_NAME else trimmed
    }
}
