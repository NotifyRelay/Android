package notifyrelay.data.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import notifyrelay.base.log.Logger
import notifyrelay.base.util.AppListHelper
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 以应用私有目录（`filesDir`）与 PackageManager 实现 [LegacyStoragePort]。
 *
 * 旧版本把数据写在 `context.filesDir` 下的 JSON 文件里，本实现按同样的命名规则定位。
 */
class AndroidLegacyStoragePort(
    private val context: Context,
) : LegacyStoragePort {
    private val prefix = "notification_records_"
    private val suffix = ".json"

    override fun readNotificationRecords(device: String): String? = readFile(fileNameFor(device))

    override fun listNotificationRecordFiles(): List<String> =
        context.filesDir
            .listFiles()
            ?.filter { it.name.startsWith(prefix) && it.name.endsWith(suffix) }
            ?.map { it.name }
            ?: emptyList()

    override fun readFile(name: String): String? {
        val file = File(context.filesDir, name)
        if (!file.exists()) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    override fun deleteFile(name: String): Boolean = runCatching { File(context.filesDir, name).delete() }.getOrDefault(false)

    override fun installedApps(): List<LegacyInstalledApp> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0).mapNotNull { appInfo ->
            runCatching {
                val iconBytes =
                    runCatching {
                        val drawable = pm.getApplicationIcon(appInfo)
                        val bitmap =
                            if (drawable is BitmapDrawable) {
                                drawable.bitmap
                            } else {
                                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { created ->
                                    val canvas = Canvas(created)
                                    drawable.setBounds(0, 0, width, height)
                                    drawable.draw(canvas)
                                }
                            }
                        ByteArrayOutputStream().use { baos ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                            baos.toByteArray()
                        }
                    }.getOrNull()

                LegacyInstalledApp(
                    packageName = appInfo.packageName,
                    appName = AppListHelper.getApplicationLabel(context, appInfo),
                    isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                    iconPngBytes = iconBytes,
                )
            }.onFailure { Logger.w("LegacyStorage", "读取应用信息失败: " + appInfo.packageName, it) }
                .getOrNull()
        }
    }

    override fun writeExternalFile(
        name: String,
        content: String,
    ): Boolean =
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, name).writeText(content)
            true
        }.getOrDefault(false)

    private fun fileNameFor(device: String): String {
        val safe = if (device == "本机") "local" else device.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return prefix + safe + suffix
    }
}
