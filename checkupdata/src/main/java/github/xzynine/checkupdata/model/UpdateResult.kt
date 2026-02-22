package github.xzynine.checkupdata.model

sealed class UpdateResult {
    data class HasUpdate(
        val releaseInfo: ReleaseInfo,
        val currentVersion: String
    ) : UpdateResult()
    
    data class NoUpdate(
        val currentVersion: String,
        val remoteVersion: String
    ) : UpdateResult()
    
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : UpdateResult()
}
