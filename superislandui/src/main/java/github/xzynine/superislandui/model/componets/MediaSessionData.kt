package github.xzynine.superislandui.model.componets

data class MediaSessionData(
    val packageName: String,
    val appName: String?,
    val title: String,
    val text: String,
    val coverUrl: String?,
    val appIconUrl: String? = null,
    val deviceName: String,
    val timestamp: Long = System.currentTimeMillis()
)
