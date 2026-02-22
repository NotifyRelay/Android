package github.xzynine.checkupdata.api

import github.xzynine.checkupdata.model.CheckUpdateConfig
import github.xzynine.checkupdata.model.ReleaseInfo

interface GitHubApiService {
    
    suspend fun getLatestRelease(): ReleaseInfo?
    
    suspend fun getReleases(): List<ReleaseInfo>
    
    companion object {
        fun create(config: CheckUpdateConfig): GitHubApiService {
            return GitHubApiServiceImpl(config)
        }
    }
}

private class GitHubApiServiceImpl(config: CheckUpdateConfig) : GitHubApiService {
    
    private val client = GitHubApiClient(config)
    
    override suspend fun getLatestRelease(): ReleaseInfo? {
        return client.getLatestRelease()
    }
    
    override suspend fun getReleases(): List<ReleaseInfo> {
        return client.getReleases()
    }
}
