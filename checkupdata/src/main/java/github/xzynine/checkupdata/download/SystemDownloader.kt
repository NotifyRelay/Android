package github.xzynine.checkupdata.download

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import github.xzynine.checkupdata.model.ReleaseInfo
import github.xzynine.checkupdata.proxy.GitHubProxy
import github.xzynine.checkupdata.proxy.GitHubProxyDetector

class SystemDownloader(private val context: Context) {
    
    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    
    suspend fun downloadRelease(
        releaseInfo: ReleaseInfo,
        useProxy: Boolean = true,
        assetFilter: ((ReleaseInfo.ReleaseAsset) -> Boolean)? = null
    ): DownloadResult {
        val asset = assetFilter?.let { filter ->
            releaseInfo.assets.find(filter)
        } ?: releaseInfo.assets.firstOrNull { 
            it.name.endsWith(".apk", ignoreCase = true) 
        } ?: releaseInfo.assets.firstOrNull()
        
        if (asset == null) {
            return DownloadResult.NoAsset
        }
        
        val downloadUrl = if (useProxy) {
            val proxy = GitHubProxyDetector.detectBestProxy()
            proxy.wrapUrl(asset.browserDownloadUrl)
        } else {
            asset.browserDownloadUrl
        }
        
        return startDownload(asset.name, downloadUrl)
    }
    
    fun downloadReleaseSync(
        releaseInfo: ReleaseInfo,
        useProxy: Boolean = true,
        assetFilter: ((ReleaseInfo.ReleaseAsset) -> Boolean)? = null
    ): DownloadResult {
        val asset = assetFilter?.let { filter ->
            releaseInfo.assets.find(filter)
        } ?: releaseInfo.assets.firstOrNull { 
            it.name.endsWith(".apk", ignoreCase = true) 
        } ?: releaseInfo.assets.firstOrNull()
        
        if (asset == null) {
            return DownloadResult.NoAsset
        }
        
        val downloadUrl = if (useProxy) {
            val proxy = GitHubProxyDetector.getCachedProxy() ?: GitHubProxy.DIRECT
            proxy.wrapUrl(asset.browserDownloadUrl)
        } else {
            asset.browserDownloadUrl
        }
        
        return startDownload(asset.name, downloadUrl)
    }
    
    fun openReleasePage(releaseInfo: ReleaseInfo): DownloadResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.htmlUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DownloadResult.Success
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "Failed to open browser", e)
        }
    }
    
    private fun startDownload(fileName: String, url: String): DownloadResult {
        return try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription(url)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            
            downloadManager.enqueue(request)
            DownloadResult.Success
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "Failed to start download", e)
        }
    }
    
    sealed class DownloadResult {
        object Success : DownloadResult()
        object NoAsset : DownloadResult()
        data class Error(val message: String, val exception: Throwable? = null) : DownloadResult()
    }
}
