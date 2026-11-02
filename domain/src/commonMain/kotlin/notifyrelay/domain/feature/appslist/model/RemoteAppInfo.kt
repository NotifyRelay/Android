package notifyrelay.domain.feature.appslist.model

data class RemoteAppInfo(
    val packageName: String,
    val appName: String,
    val iconBytes: ByteArray? = null,
    val isPinned: Boolean = false,
    var isLoading: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteAppInfo) return false
        if (packageName != other.packageName) return false
        if (appName != other.appName) return false
        if (isPinned != other.isPinned) return false
        return true
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + appName.hashCode()
        result = 31 * result + isPinned.hashCode()
        return result
    }
}
