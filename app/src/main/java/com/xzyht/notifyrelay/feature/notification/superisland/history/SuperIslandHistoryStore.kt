package com.xzyht.notifyrelay.feature.notification.superisland.history

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import notifyrelay.data.database.entity.SuperIslandHistoryEntity
import notifyrelay.data.database.repository.DatabaseRepository

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

object SuperIslandHistoryStore {
    private const val MAX_ENTRIES = 600
    private val gson = Gson()
    private val stringStringMapType = object : TypeToken<Map<String, String>>() {}.type

    suspend fun loadEntryDetail(
        context: Context,
        id: Long,
    ): SuperIslandHistoryStoreEntry? {
        val repo = DatabaseRepository.getInstance(context)
        val entity =
            try {
                repo.getSuperIslandHistoryById(id)
            } catch (_: Exception) {
                null
            }
        return entity?.let { e ->
            val rawPicMap: Map<String, String> =
                try {
                    gson.fromJson(e.picMap, stringStringMapType) ?: emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }
            val resolvedPicMap =
                try {
                    SuperIslandImageStore.resolvePicMap(context, rawPicMap)
                } catch (_: Exception) {
                    rawPicMap
                }
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

    fun append(
        context: Context,
        entry: SuperIslandHistoryStoreEntry,
    ) {
        val packageName = resolvePackageName(entry)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val boundPicMap = SuperIslandImageStore.bindAll(context, packageName, entry.picMap)
                val sanitizedEntry = entry.copy(picMap = boundPicMap)
                val repository = DatabaseRepository.getInstance(context)
                val entity =
                    SuperIslandHistoryEntity(
                        id = sanitizedEntry.id,
                        sourceDeviceUuid = sanitizedEntry.sourceDeviceUuid,
                        originalPackage = sanitizedEntry.originalPackage,
                        mappedPackage = sanitizedEntry.mappedPackage,
                        appName = sanitizedEntry.appName,
                        title = sanitizedEntry.title,
                        text = sanitizedEntry.text,
                        paramV2Raw = sanitizedEntry.paramV2Raw,
                        picMap = gson.toJson(sanitizedEntry.picMap),
                        rawPayload = sanitizedEntry.rawPayload,
                        featureId = entry.featureId,
                    )

                repository.saveSuperIslandHistory(entity)
                repository.deleteOldSuperIslandHistory(MAX_ENTRIES)
            } catch (_: Exception) {
            }
        }
    }

    fun clearAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = DatabaseRepository.getInstance(context)
                repository.clearSuperIslandHistory()
            } catch (_: Exception) {
            }
        }

        try {
            CoroutineScope(Dispatchers.IO).launch {
                SuperIslandImageStore.clearAll(context)
            }
        } catch (_: Exception) {
        }
    }

    private fun resolvePackageName(entry: SuperIslandHistoryStoreEntry): String? {
        val mapped = entry.mappedPackage?.takeIf { it.isNotBlank() }
        if (!mapped.isNullOrBlank()) return mapped
        val original = entry.originalPackage?.takeIf { it.isNotBlank() }
        if (!original.isNullOrBlank()) return original
        return entry.appName?.takeIf { it.isNotBlank() }
    }
}
