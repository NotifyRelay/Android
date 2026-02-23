package github.xzynine.checkupdata.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import github.xzynine.checkupdata.model.ReleaseInfo

class SystemDownloader(private val context: Context) {
    
    private val downloadManager: DownloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    
    fun downloadRelease(
        releaseInfo: ReleaseInfo,
        proxyUrl: String = "",
        assetFilter: ((ReleaseInfo.ReleaseAsset) -> Boolean)? = null
    ): DownloadResult {
        val asset = assetFilter?.let { filter ->
            releaseInfo.assets.find(filter)
        } ?: releaseInfo.assets.firstOrNull { 
            it.name.endsWith(".apk", ignoreCase = true) 
        } ?: releaseInfo.assets.firstOrNull()
        
        if (asset == null) {
            val availableAssets = releaseInfo.assets.map { it.name }
            return DownloadResult.NoAsset(
                message = "No matching asset found",
                availableAssets = availableAssets
            )
        }
        
        val downloadUrl = if (proxyUrl.isNotEmpty()) {
            "$proxyUrl${asset.browserDownloadUrl}"
        } else {
            asset.browserDownloadUrl
        }
        
        return startDownload(asset.name, downloadUrl, asset.size)
    }
    
    private fun startDownload(fileName: String, url: String, fileSize: Long): DownloadResult {
        return try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription(url)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            
            val downloadId = downloadManager.enqueue(request)
            DownloadResult.Success(downloadId, fileName, fileSize)
        } catch (e: Exception) {
            DownloadResult.Error(
                message = "Failed to start download: ${e.message}",
                exception = e,
                fileName = fileName,
                url = url
            )
        }
    }
    
    sealed class DownloadResult {
        data class Success(
            val downloadId: Long,
            val fileName: String,
            val fileSize: Long
        ) : DownloadResult()
        
        data class NoAsset(
            val message: String,
            val availableAssets: List<String>
        ) : DownloadResult()
        
        data class Error(
            val message: String,
            val exception: Throwable? = null,
            val fileName: String? = null,
            val url: String? = null
        ) : DownloadResult()
    }
}
