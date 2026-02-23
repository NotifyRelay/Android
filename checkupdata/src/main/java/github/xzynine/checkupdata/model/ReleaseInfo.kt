package github.xzynine.checkupdata.model

import org.json.JSONObject

data class ReleaseInfo(
    val id: Long,
    val version: String,
    val versionTag: String,
    val name: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAt: String?,
    val isPrerelease: Boolean,
    val isDraft: Boolean,
    val assets: List<ReleaseAsset>
) {
    data class ReleaseAsset(
        val id: Long,
        val name: String,
        val contentType: String,
        val size: Long,
        val downloadUrl: String,
        val browserDownloadUrl: String
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("id", id)
                put("name", name)
                put("contentType", contentType)
                put("size", size)
                put("downloadUrl", downloadUrl)
                put("browserDownloadUrl", browserDownloadUrl)
            }
        }
        
        companion object {
            fun fromJson(json: JSONObject): ReleaseAsset {
                return ReleaseAsset(
                    id = json.getLong("id"),
                    name = json.getString("name"),
                    contentType = json.getString("contentType"),
                    size = json.getLong("size"),
                    downloadUrl = json.getString("downloadUrl"),
                    browserDownloadUrl = json.getString("browserDownloadUrl")
                )
            }
        }
    }
    
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("version", version)
            put("versionTag", versionTag)
            put("name", name)
            put("releaseNotes", releaseNotes)
            put("htmlUrl", htmlUrl)
            put("publishedAt", publishedAt)
            put("isPrerelease", isPrerelease)
            put("isDraft", isDraft)
            put("assets", org.json.JSONArray().apply {
                assets.forEach { asset ->
                    put(asset.toJson())
                }
            })
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): ReleaseInfo {
            val assetsArray = json.getJSONArray("assets")
            val assets = (0 until assetsArray.length()).map { i ->
                ReleaseAsset.fromJson(assetsArray.getJSONObject(i))
            }
            
            return ReleaseInfo(
                id = json.getLong("id"),
                version = json.getString("version"),
                versionTag = json.getString("versionTag"),
                name = json.getString("name"),
                releaseNotes = json.getString("releaseNotes"),
                htmlUrl = json.getString("htmlUrl"),
                publishedAt = json.optString("publishedAt"),
                isPrerelease = json.getBoolean("isPrerelease"),
                isDraft = json.getBoolean("isDraft"),
                assets = assets
            )
        }
    }
}
