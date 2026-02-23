package github.xzynine.checkupdata.model

sealed class UpdateResult {
    data class HasUpdate(
        val releaseInfo: ReleaseInfo,
        val currentVersion: String,
        val allReleases: List<ReleaseInfo> = emptyList()
    ) : UpdateResult()
    
    data class NoUpdate(
        val currentVersion: String,
        val remoteVersion: String,
        val releaseInfo: ReleaseInfo?,
        val allReleases: List<ReleaseInfo> = emptyList()
    ) : UpdateResult()
    
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : UpdateResult()
}
