package github.xzynine.checkupdata.proxy

data class GitHubProxy(
    val name: String,
    val prefix: String,
    val enabled: Boolean = true
) {
    fun wrapUrl(originalUrl: String): String {
        if (!enabled || prefix.isEmpty()) {
            return originalUrl
        }
        return "$prefix$originalUrl"
    }
    
    companion object {
        val DIRECT = GitHubProxy(
            name = "Direct",
            prefix = "",
            enabled = true
        )
        
        val GH_PROXY_COM = GitHubProxy(
            name = "ghproxy.com",
            prefix = "https://ghproxy.com/",
            enabled = true
        )
        
        val MIRROR_GH_PROXY_COM = GitHubProxy(
            name = "mirror.ghproxy.com",
            prefix = "https://mirror.ghproxy.com/",
            enabled = true
        )
        
        val GH_PROXY_COM_CN = GitHubProxy(
            name = "gh-proxy.com",
            prefix = "https://gh-proxy.com/",
            enabled = true
        )
        
        val GHPS_CC = GitHubProxy(
            name = "ghps.cc",
            prefix = "https://ghps.cc/",
            enabled = true
        )
        
        val ALL_PROXIES = listOf(
            DIRECT,
            MIRROR_GH_PROXY_COM,
            GH_PROXY_COM,
            GH_PROXY_COM_CN,
            GHPS_CC
        )
    }
}
