package github.xzynine.checkupdata.cache

import android.content.Context
import github.xzynine.checkupdata.model.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UpdateCache {
    private const val CACHE_DIR = "checkupdata_cache"
    private const val CACHE_FILE = "releases_cache.json"
    private const val CACHE_DURATION = 60 * 60 * 1000L
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    private fun getCacheFile(context: Context): File {
        return File(getCacheDir(context), CACHE_FILE)
    }
    
    suspend fun saveCache(
        context: Context,
        owner: String,
        repo: String,
        releases: List<ReleaseInfo>,
        errorLog: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val cacheFile = getCacheFile(context)
            val json = JSONObject().apply {
                put("owner", owner)
                put("repo", repo)
                put("cachedAt", System.currentTimeMillis())
                put("releases", JSONArray().apply {
                    releases.forEach { release ->
                        put(release.toJson())
                    }
                })
                errorLog?.let { put("errorLog", it) }
            }
            
            FileOutputStream(cacheFile).use { output ->
                output.write(json.toString(2).toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun loadCache(
        context: Context,
        owner: String,
        repo: String
    ): CachedData? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = getCacheFile(context)
            if (!cacheFile.exists()) return@withContext null
            
            val content = FileInputStream(cacheFile).use { input ->
                input.readBytes()
            }.let { String(it) }
            
            val json = JSONObject(content)
            
            if (json.getString("owner") != owner || json.getString("repo") != repo) {
                return@withContext null
            }
            
            val cachedAt = json.getLong("cachedAt")
            if (System.currentTimeMillis() - cachedAt > CACHE_DURATION) {
                return@withContext null
            }
            
            val releasesArray = json.getJSONArray("releases")
            val releases = (0 until releasesArray.length()).map { i ->
                ReleaseInfo.fromJson(releasesArray.getJSONObject(i))
            }
            
            val errorLog = json.optString("errorLog")
            
            CachedData(
                owner = owner,
                repo = repo,
                cachedAt = cachedAt,
                releases = releases,
                errorLog = errorLog
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun clearCache(context: Context) {
        try {
            val cacheFile = getCacheFile(context)
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    data class CachedData(
        val owner: String,
        val repo: String,
        val cachedAt: Long,
        val releases: List<ReleaseInfo>,
        val errorLog: String?
    ) {
        val cachedAtFormatted: String
            get() = dateFormat.format(Date(cachedAt))
    }
}
