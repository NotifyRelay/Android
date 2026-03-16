package com.xzyht.notifyrelay.feature.notification.superisland.formatter

import android.content.Context
import com.xzyht.notifyrelay.feature.notification.superisland.image.SuperIslandImageStore
import com.xzyht.notifyrelay.feature.notification.superisland.model.core.ParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.model.core.parseParamV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.base.util.Logger

data class FormattedSuperIslandData(
    val paramV2: ParamV2?,
    val paramV2Raw: String?,
    val resolvedPicMap: Map<String, String>
)

object SuperIslandDataFormatter {
    private const val TAG = "超级岛数据格式化"

    suspend fun formatForDisplay(
        context: Context,
        paramV2Raw: String?,
        picMap: Map<String, String>?
    ): FormattedSuperIslandData {
        val paramV2 = parseParamV2Safe(paramV2Raw)
        val resolvedPicMap = resolvePicMapSafe(context, picMap)
        
        Logger.d(TAG, "formatForDisplay: paramV2=${paramV2 != null}, picMapSize=${resolvedPicMap.size}")
        
        return FormattedSuperIslandData(
            paramV2 = paramV2,
            paramV2Raw = paramV2Raw,
            resolvedPicMap = resolvedPicMap
        )
    }

    fun parseParamV2Safe(raw: String?): ParamV2? {
        return try {
            val s = raw ?: return null
            if (s.isBlank()) null else parseParamV2(s)
        } catch (e: Exception) {
            Logger.w(TAG, "解析paramV2失败: ${e.message}")
            null
        }
    }

    suspend fun resolvePicMapSafe(
        context: Context,
        picMap: Map<String, String>?
    ): Map<String, String> {
        return try {
            if (picMap.isNullOrEmpty()) {
                emptyMap()
            } else {
                withContext(Dispatchers.IO) {
                    SuperIslandImageStore.resolvePicMap(context, picMap)
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "解析picMap失败: ${e.message}")
            picMap ?: emptyMap()
        }
    }

    fun isMediaType(paramV2: ParamV2?, paramV2Raw: String?): Boolean {
        return paramV2?.business == "media" || 
               paramV2Raw?.contains("\"business\":\"media\"") == true
    }

    fun isProgressType(paramV2: ParamV2?): Boolean {
        return paramV2?.progressInfo != null || paramV2?.multiProgressInfo != null
    }
}
