package github.xzynine.checkupdata

import android.content.Context
import github.xzynine.checkupdata.api.GitHubApiService
import github.xzynine.checkupdata.download.SystemDownloader
import github.xzynine.checkupdata.model.CheckUpdateConfig
import github.xzynine.checkupdata.model.ReleaseInfo
import github.xzynine.checkupdata.model.UpdateResult
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
                val filteredReleases = when (rule) {
                    VersionRule.STABLE -> releases.filter { !it.isPrerelease && !it.isDraft }
                    VersionRule.LATEST -> releases.filter { !it.isDraft }
                    VersionRule.PRERELEASE -> releases.filter { it.isPrerelease && !it.isDraft }
                }
                
                val latestRelease = VersionComparator.findLatestRelease(
                    releases = releases,
                    currentVersion = currentVersion,
                    rule = rule
                )
                
                if (latestRelease != null) {
                    UpdateResult.HasUpdate(latestRelease, currentVersion, filteredReleases)
                } else {
                    val remoteReleaseInfo = VersionComparator.getLatestReleaseInfo(releases, rule)
                    val remoteVersion = remoteReleaseInfo?.version ?: "unknown"
                    UpdateResult.NoUpdate(currentVersion, remoteVersion, remoteReleaseInfo, filteredReleases)
                }
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Unknown error", e)
        }
    }
    
    fun downloadRelease(
        releaseInfo: ReleaseInfo,
        proxyUrl: String = "",
        assetFilter: ((ReleaseInfo.ReleaseAsset) -> Boolean)? = null
    ): SystemDownloader.DownloadResult {
        return downloader.downloadRelease(releaseInfo, proxyUrl, assetFilter)
    }
    
    companion object {
        private const val TAG = "CheckUpdateManager"
    }
}
