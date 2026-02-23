package github.xzynine.checkupdata

import android.content.Context
import github.xzynine.checkupdata.api.GitHubApiService
import github.xzynine.checkupdata.cache.UpdateCache
import github.xzynine.checkupdata.download.SystemDownloader
import github.xzynine.checkupdata.model.CheckUpdateConfig
import github.xzynine.checkupdata.model.ReleaseInfo
import github.xzynine.checkupdata.model.UpdateResult
import github.xzynine.checkupdata.version.VersionComparator
import github.xzynine.checkupdata.version.VersionRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CheckUpdateManager(private val context: Context) {
    
    private val downloader = SystemDownloader(context)
    private val logBuilder = StringBuilder()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    
    private fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        logBuilder.append("[$timestamp] $message\n")
    }
    
    private fun getAndClearLog(): String {
        val log = logBuilder.toString()
        logBuilder.clear()
        return log
    }
    
    suspend fun checkUpdate(
        owner: String,
        repo: String,
        currentVersion: String,
        rule: VersionRule = VersionRule.STABLE,
        githubToken: String? = null,
        useCache: Boolean = true
    ): UpdateResult = withContext(Dispatchers.IO) {
        log("开始检查更新: owner=$owner, repo=$repo, rule=$rule")
        log("当前版本: $currentVersion")
        
        if (useCache) {
            val cached = UpdateCache.loadCache(context, owner, repo)
            if (cached != null) {
                log("使用缓存数据 (缓存时间: ${cached.cachedAtFormatted})")
                return@withContext processReleases(
                    cached.releases, 
                    currentVersion, 
                    rule,
                    cached.errorLog
                )
            }
        }
        
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
                    log("获取最新稳定版本...")
                    val latest = apiService.getLatestRelease()
                    if (latest != null) {
                        log("获取到最新版本: ${latest.version}")
                        listOf(latest)
                    } else {
                        log("未获取到最新版本")
                        emptyList()
                    }
                }
                else -> {
                    log("获取所有版本列表...")
                    val all = apiService.getReleases()
                    log("获取到 ${all.size} 个版本")
                    all
                }
            }
            
            if (releases.isEmpty()) {
                log("错误: 未找到任何版本")
                UpdateResult.Error("No releases found", errorLog = getAndClearLog())
            } else {
                val filteredReleases = when (rule) {
                    VersionRule.STABLE -> releases.filter { !it.isPrerelease && !it.isDraft }
                    VersionRule.LATEST -> releases.filter { !it.isDraft }
                    VersionRule.PRERELEASE -> releases.filter { it.isPrerelease && !it.isDraft }
                }
                log("过滤后剩余 ${filteredReleases.size} 个版本")
                
                UpdateCache.saveCache(context, owner, repo, filteredReleases)
                
                processReleases(filteredReleases, currentVersion, rule, null)
            }
        } catch (e: Exception) {
            log("错误: ${e.message}")
            e.printStackTrace()
            UpdateResult.Error(
                message = e.message ?: "Unknown error",
                exception = e,
                errorLog = getAndClearLog()
            )
        }
    }
    
    private fun processReleases(
        releases: List<ReleaseInfo>,
        currentVersion: String,
        rule: VersionRule,
        cachedErrorLog: String?
    ): UpdateResult {
        val latestRelease = VersionComparator.findLatestRelease(
            releases = releases,
            currentVersion = currentVersion,
            rule = rule
        )
        
        val errorLog = cachedErrorLog ?: getAndClearLog()
        
        return if (latestRelease != null) {
            log("发现新版本: ${latestRelease.version}")
            UpdateResult.HasUpdate(
                releaseInfo = latestRelease,
                currentVersion = currentVersion,
                allReleases = releases,
                errorLog = errorLog
            )
        } else {
            val remoteReleaseInfo = VersionComparator.getLatestReleaseInfo(releases, rule)
            val remoteVersion = remoteReleaseInfo?.version ?: "unknown"
            log("当前已是最新版本，远端版本: $remoteVersion")
            UpdateResult.NoUpdate(
                currentVersion = currentVersion,
                remoteVersion = remoteVersion,
                releaseInfo = remoteReleaseInfo,
                allReleases = releases,
                errorLog = errorLog
            )
        }
    }
    
    fun downloadRelease(
        releaseInfo: ReleaseInfo,
        proxyUrl: String = "",
        assetFilter: ((ReleaseInfo.ReleaseAsset) -> Boolean)? = null
    ): SystemDownloader.DownloadResult {
        return downloader.downloadRelease(releaseInfo, proxyUrl, assetFilter)
    }
    
    fun clearCache() {
        UpdateCache.clearCache(context)
    }
    
    companion object {
        private const val TAG = "CheckUpdateManager"
    }
}
