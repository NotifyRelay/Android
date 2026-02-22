package github.xzynine.checkupdata.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import github.xzynine.checkupdata.model.CheckUpdateConfig
import github.xzynine.checkupdata.model.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GitHubApiClient(private val config: CheckUpdateConfig) {
    
    private val gson = Gson()
    
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(config.connectTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeout, TimeUnit.MILLISECONDS)
            .build()
    }
    
    suspend fun getLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val url = "$API_BASE_URL/repos/${config.owner}/${config.repo}/releases/latest"
        fetchRelease(url)
    }
    
    suspend fun getReleases(): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        val url = "$API_BASE_URL/repos/${config.owner}/${config.repo}/releases"
        fetchReleases(url)
    }
    
    private fun fetchRelease(url: String): ReleaseInfo? {
        val request = buildRequest(url)
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.let { body ->
                        parseRelease(body)
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun fetchReleases(url: String): List<ReleaseInfo> {
        val request = buildRequest(url)
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.let { body ->
                        parseReleases(body)
                    } ?: emptyList()
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun buildRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
        
        config.githubToken?.let { token ->
            if (token.isNotEmpty()) {
                builder.header("Authorization", "token $token")
            }
        }
        
        return builder.build()
    }
    
    private fun parseRelease(json: String): ReleaseInfo? {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            parseReleaseFromJson(obj)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseReleases(json: String): List<ReleaseInfo> {
        return try {
            val listType = object : TypeToken<List<JsonObject>>() {}.type
            val releases = gson.fromJson<List<JsonObject>>(json, listType)
            releases.mapNotNull { parseReleaseFromJson(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseReleaseFromJson(obj: JsonObject): ReleaseInfo? {
        return try {
            val id = obj.get("id")?.asLong ?: return null
            val tagName = obj.get("tag_name")?.asString ?: return null
            val name = obj.get("name")?.asString ?: tagName
            val body = obj.get("body")?.asString ?: ""
            val htmlUrl = obj.get("html_url")?.asString ?: ""
            val publishedAt = obj.get("published_at")?.asString
            val isPrerelease = obj.get("prerelease")?.asBoolean ?: false
            val isDraft = obj.get("draft")?.asBoolean ?: false
            
            val assetsArray = obj.getAsJsonArray("assets") ?: return ReleaseInfo(
                id = id,
                version = cleanVersion(tagName),
                versionTag = tagName,
                name = name,
                releaseNotes = body,
                htmlUrl = htmlUrl,
                publishedAt = publishedAt,
                isPrerelease = isPrerelease,
                isDraft = isDraft,
                assets = emptyList()
            )
            
            val assets = assetsArray.mapNotNull { assetElement ->
                val assetObj = assetElement.asJsonObject
                try {
                    ReleaseInfo.ReleaseAsset(
                        id = assetObj.get("id")?.asLong ?: 0,
                        name = assetObj.get("name")?.asString ?: return@mapNotNull null,
                        contentType = assetObj.get("content_type")?.asString ?: "",
                        size = assetObj.get("size")?.asLong ?: 0,
                        downloadUrl = assetObj.get("url")?.asString ?: "",
                        browserDownloadUrl = assetObj.get("browser_download_url")?.asString ?: ""
                    )
                } catch (e: Exception) {
                    null
                }
            }
            
            ReleaseInfo(
                id = id,
                version = cleanVersion(tagName),
                versionTag = tagName,
                name = name,
                releaseNotes = body,
                htmlUrl = htmlUrl,
                publishedAt = publishedAt,
                isPrerelease = isPrerelease,
                isDraft = isDraft,
                assets = assets
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun cleanVersion(tagName: String): String {
        var version = tagName
        if (version.startsWith("v", ignoreCase = true)) {
            version = version.substring(1)
        }
        return version
    }
    
    companion object {
        private const val API_BASE_URL = "https://api.github.com"
    }
}
