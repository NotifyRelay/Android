package github.xzynine.checkupdata

import android.content.Context
import github.xzynine.checkupdata.api.GitHubApiService
import github.xzynine.checkupdata.download.SystemDownloader
import github.xzynine.checkupdata.model.CheckUpdateConfig
import github.xzynine.checkupdata.model.ReleaseInfo
import github.xzynine.checkupdata.model.UpdateResult
import github.xzynine.checkupdata.proxy.GitHubProxyDetector
import github.xzynine.checkupdata.version.VersionComparator
import github.xzynine.checkupdata.version.VersionRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CheckUpdateManager(private val context: Context) {
    
    private val downloader = SystemDownloader(context)
    
    suspend fun checkUpdate(
        owner: String,
        repo: String,
        currentVersion: String,
        rule: VersionRule = VersionRule.STABLE,
        githubToken: String? = null
    ): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val config = CheckUpdateConfig(
                owner = owner,
                repo = repo,
                currentVersion = currentVersion,
                githubToken = githubToken
            )
            
            val apiService = GitHubApiService.create(config)
            
            val releases = when (rule) {
                VersionRule.STABLE -> {
                    val latest = apiService.getLatestRelease()
                    if (latest != null) listOf(latest) else emptyList()
                }
                else -> apiService.getReleases()
            }
            
            if (releases.isEmpty()) {
                UpdateResult.Error("No releases found")
            } else {
                val latestRelease = VersionComparator.findLatestRelease(
                    releases = releases,
                    currentVersion = currentVersion,
                    rule = rule
                )
                
                if (latestRelease != null) {
                    UpdateResult.HasUpdate(latestRelease, currentVersion)
                } else {
                    val remoteVersion = VersionComparator.getRemoteVersion(releases, rule) ?: "unknown"
                    UpdateResult.NoUpdate(currentVersion, remoteVersion)
                }
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Unknown error", e)
        }
    }
    
    suspend fun downloadRelease(
        releaseInfo: ReleaseInfo,
        useProxy: Boolean = true,
        assetFilter: ((ReleaseInfo.ReleaseAsset) -> Boolean)? = null
    ): SystemDownloader.DownloadResult {
        return downloader.downloadRelease(releaseInfo, useProxy, assetFilter)
    }
    
    fun downloadReleaseSync(
        releaseInfo: ReleaseInfo,
        useProxy: Boolean = true,
        assetFilter: ((ReleaseInfo.ReleaseAsset) -> Boolean)? = null
    ): SystemDownloader.DownloadResult {
        return downloader.downloadReleaseSync(releaseInfo, useProxy, assetFilter)
    }
    
    fun openReleasePage(releaseInfo: ReleaseInfo): SystemDownloader.DownloadResult {
        return downloader.openReleasePage(releaseInfo)
    }
    
    suspend fun detectBestProxy(forceRefresh: Boolean = false) {
        GitHubProxyDetector.detectBestProxy(forceRefresh)
    }
    
    companion object {
        private const val TAG = "CheckUpdateManager"
    }
}
