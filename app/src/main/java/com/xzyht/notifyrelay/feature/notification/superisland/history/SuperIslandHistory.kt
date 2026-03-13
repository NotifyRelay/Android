package com.xzyht.notifyrelay.feature.notification.superisland.history

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import notifyrelay.data.database.entity.SuperIslandHistoryEntity
import notifyrelay.data.database.repository.DatabaseRepository
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class SuperIslandHistoryEntry(
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
    val featureId: String? = null
)

object SuperIslandHistory {
    private const val MAX_ENTRIES = 600
    private val gson = Gson()
    private val stringStringMapType = object : TypeToken<Map<String, String>>() {}.type

    suspend fun loadEntryDetail(context: Context, id: Long): SuperIslandHistoryEntry? {
        val repo = DatabaseRepository.getInstance(context)
        val entity = try {
            repo.getSuperIslandHistoryById(id)
        } catch (_: Exception) {
            null
        }
        return entity?.let { e ->
            SuperIslandHistoryEntry(
                id = e.id,
                sourceDeviceUuid = e.sourceDeviceUuid,
                originalPackage = e.originalPackage,
                mappedPackage = e.mappedPackage,
                appName = e.appName,
                title = e.title,
                text = e.text,
                paramV2Raw = e.paramV2Raw,
                picMap = gson.fromJson(e.picMap, stringStringMapType),
                rawPayload = e.rawPayload,
                featureId = e.featureId
            )
        }
    }

    fun append(context: Context, entry: SuperIslandHistoryEntry) {
        val interned = SuperIslandImageStore.internAll(context, entry.picMap)
        val sanitizedEntry = entry.copy(picMap = interned.toMap())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = DatabaseRepository.getInstance(context)
                val entity = SuperIslandHistoryEntity(
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
                    featureId = entry.featureId
                )

                repository.saveSuperIslandHistory(entity)
                repository.deleteOldSuperIslandHistory(MAX_ENTRIES)
            } catch (_: Exception) {}
        }
    }

    fun clearAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = DatabaseRepository.getInstance(context)
                repository.clearSuperIslandHistory()
            } catch (_: Exception) {}
        }

        try {
            SuperIslandImageStore.prune(context, maxEntries = 0, maxAgeDays = 0)
        } catch (_: Exception) {}
    }
}
