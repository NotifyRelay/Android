package github.xzynine.checkupdata.proxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object GitHubProxyDetector {
    
    private const val TEST_URL = "https://github.com/favicon.ico"
    private const val TIMEOUT_MS = 5000L
    
    private val cachedProxy = AtomicReference<GitHubProxy?>(null)
    private var lastCheckTime = 0L
    private const val CACHE_DURATION = 60 * 60 * 1000L
    
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .build()
    }
    
    suspend fun detectBestProxy(forceRefresh: Boolean = false): GitHubProxy = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedProxy.get()
        
        if (!forceRefresh && cached != null && (now - lastCheckTime) < CACHE_DURATION) {
            return@withContext cached
        }
        
        val bestProxy = measureProxies()
        cachedProxy.set(bestProxy)
        lastCheckTime = now
        bestProxy
    }
    
    private suspend fun measureProxies(): GitHubProxy = coroutineScope {
        val results = GitHubProxy.ALL_PROXIES.map { proxy ->
            async {
                val responseTime = measureResponseTime(proxy)
                ProxyResult(proxy, responseTime)
            }
        }.awaitAll()
        
        val proxyResults = results
            .filter { it.responseTime != null && it.proxy != GitHubProxy.DIRECT }
            .sortedBy { it.responseTime ?: Long.MAX_VALUE }
        
        if (proxyResults.isNotEmpty()) {
            proxyResults.first().proxy
        } else {
            val directResult = results.find { it.proxy == GitHubProxy.DIRECT }
            if (directResult?.responseTime != null) {
                GitHubProxy.DIRECT
            } else {
                GitHubProxy.MIRROR_GH_PROXY_COM
            }
        }
    }
    
    private fun measureResponseTime(proxy: GitHubProxy): Long? {
        return try {
            val testUrl = proxy.wrapUrl(TEST_URL)
            val request = Request.Builder()
                .url(testUrl)
                .head()
                .build()
            
            val startTime = System.currentTimeMillis()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    System.currentTimeMillis() - startTime
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun getCachedProxy(): GitHubProxy? {
        val cached = cachedProxy.get() ?: return null
        val now = System.currentTimeMillis()
        if ((now - lastCheckTime) > CACHE_DURATION) {
            return null
        }
        return cached
    }
    
    fun clearCache() {
        cachedProxy.set(null)
        lastCheckTime = 0
    }
    
    private data class ProxyResult(
        val proxy: GitHubProxy,
        val responseTime: Long?
    )
}
