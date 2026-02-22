package github.xzynine.checkupdata.model

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
    )
}
