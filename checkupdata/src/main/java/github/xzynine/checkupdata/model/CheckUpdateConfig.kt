package github.xzynine.checkupdata.model

data class CheckUpdateConfig(
    val owner: String,
    val repo: String,
    val currentVersion: String,
    val githubToken: String? = null,
    val connectTimeout: Long = 15000,
    val readTimeout: Long = 15000
)
