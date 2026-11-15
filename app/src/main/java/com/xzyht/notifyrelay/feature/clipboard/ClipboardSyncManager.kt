package com.xzyht.notifyrelay.feature.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.content.FileProvider
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import com.xzyht.notifyrelay.nativecore.NativeCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import notifyrelay.base.log.Logger
import notifyrelay.base.util.PermissionHelper
import notifyrelay.base.util.ToastUtils
import notifyrelay.base.util.image.ImageUtils
import notifyrelay.domain.feature.device.service.contract.DeviceQueryPort
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 剪贴板同步管理器。
 *
 * 去重、防循环、频率限制与发送全部由 Rust core 内部闭环（`NativeCore.clipboardOnChanged()` /
 * `NativeCore.clipboardOnReceived()`），平台端只负责系统剪贴板读写与前台/Fcitx 权限判定。
 *
 * 写回剪贴板的形态决定它会不会被 Fcitx5 的剪贴板广播原样带回：文本按原样写入，回灌由 Rust 的
 * 防循环按内容排除；图片写成内容 URI，Fcitx5 只广播条目文本非空的剪贴板，图片因此不进入这条
 * 回灌路径（按 data URL 文本写入时它会被当作文本转发回对端）。
 */
object ClipboardSyncManager {
    private const val TAG = "ClipboardSyncManager"
    private const val CLIPBOARD_TYPE_TEXT = "text"
    private const val CLIPBOARD_TYPE_IMAGE = "image"
    private const val MIME_TEXT = "text/plain"
    private const val MIME_IMAGE = "image/png"

    /** 剪贴板图片临时目录名，与 `res/xml/clipboard_paths.xml` 的 `path` 必须一致。 */
    private const val IMAGE_DIR_NAME = "clipboard"

    /** FileProvider authority，与 `AndroidManifest.xml` 中的声明必须一致。 */
    private const val AUTHORITY_SUFFIX = ".clipboard"

    /** 剪贴板读写自身的协程归属者，避免每次调用新建作用域。 */
    private val clipboardScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var clipboardManager: ClipboardManager? = null

    fun isFcitx5Paired(context: Context): Boolean {
        FcitxClipboardManager.restorePairedState(context)
        return FcitxClipboardManager.isPaired
    }

    fun init(context: Context) {
        Logger.d(TAG, "剪贴板同步管理器已初始化")
        PermissionHelper.AppForegroundDetector.initialize(context)
    }

    private fun getClipboardManager(context: Context): ClipboardManager? {
        if (clipboardManager == null) {
            // 使用 applicationContext 避免间接持有 Activity/Service 导致无法回收
            clipboardManager =
                context.applicationContext
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }
        return clipboardManager
    }

    /**
     * 处理接收到的剪贴板消息。
     * Rust 解析报文、归一化类型并登记防循环时间窗，返回内容供写入系统剪贴板。
     */
    fun handleClipboardMessage(
        jsonData: String,
        context: Context,
    ) {
        try {
            val deviceManager = DeviceConnectionManagerSingleton.getDeviceQuery(context)
            val resultJson =
                NativeCore.clipboardOnReceived(
                    jsonData,
                    System.currentTimeMillis(),
                )
            val result = JSONObject(resultJson ?: return)
            val type = result.getString("type")
            val content = result.getString("content")
            if (content.isEmpty()) return

            // 防循环由 Rust 内部闭环处理，平台端直接更新本地剪贴板

            // 更新本地剪贴板
            updateLocalClipboardContent(type, content, context)
        } catch (e: Exception) {
            Logger.e(TAG, "处理剪贴板消息失败", e)
        }
    }

    /**
     * 读取系统剪贴板当前内容：文本优先，其次图片；返回（类型, 内容），读不到时为 null。
     *
     * 不做前台/Fcitx 判定：调用方（透明 Activity）已经拿到前台，Android 10+ 只有前台才能读剪贴板。
     */
    fun readPrimaryClip(context: Context): Pair<String, String>? {
        try {
            getClipboardManager(context)?.let { cm ->
                // 尝试获取剪贴板内容，捕获可能的权限异常
                val clip = cm.primaryClip
                if (clip == null) {
                    return null
                }

                val clipDescription = clip.description
                val item = clip.getItemAt(0)

                if (clipDescription != null && item != null) {
                    // 处理文本类型剪贴板内容
                    if (clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                        clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
                    ) {
                        try {
                            val text = item.text?.toString()
                            if (!text.isNullOrEmpty()) {
                                // 检查是否为图片的data URL格式
                                if (text.startsWith("data:image/") && text.contains(",")) {
                                    // 从data URL中提取纯base64部分
                                    val commaIndex = text.indexOf(',')
                                    if (commaIndex > 0) {
                                        val base64Image = text.substring(commaIndex + 1)
                                        return Pair(CLIPBOARD_TYPE_IMAGE, base64Image)
                                    }
                                }
                                return Pair(CLIPBOARD_TYPE_TEXT, text)
                            }
                        } catch (e: SecurityException) {
                            // 忽略权限异常，直接返回null
                            return null
                        }
                    }

                    // 处理图片类型剪贴板内容
                    // 检查是否支持图片类型
                    var hasImageType = false
                    for (i in 0 until clipDescription.mimeTypeCount) {
                        if (clipDescription.getMimeType(i).startsWith("image/")) {
                            hasImageType = true
                            break
                        }
                    }

                    if (hasImageType) {
                        try {
                            // 尝试获取Bitmap
                            val imageBitmap: Bitmap? =
                                item.uri?.let { uri ->
                                    try {
                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        inputStream?.use {
                                            BitmapFactory.decodeStream(it)
                                        }
                                    } catch (e: Exception) {
                                        // 忽略异常，返回null
                                        null
                                    }
                                }
                            if (imageBitmap != null) {
                                return Pair(CLIPBOARD_TYPE_IMAGE, ImageUtils.bitmapToBase64(imageBitmap))
                            }
                        } catch (e: SecurityException) {
                            // 忽略权限异常，直接返回null
                            return null
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            // 忽略权限异常，直接返回null
        } catch (e: Exception) {
            // 忽略其他异常，直接返回null
        }
        return null
    }

    /**
     * 更新本地剪贴板。
     *
     * 图片写成内容 URI：剪贴板里的图片必须是 URI，写成 data URL 文本会被 Fcitx5 记成剪贴板条目
     * 并由它的剪贴板广播当作文本转发回对端。
     */
    private fun updateLocalClipboardContent(
        type: String,
        content: String,
        context: Context,
    ) {
        val appContext = context.applicationContext
        try {
            getClipboardManager(appContext)?.let { cm ->
                when (type) {
                    CLIPBOARD_TYPE_TEXT -> {
                        // 使用便捷方法创建文本剪贴板内容
                        val clip = ClipData.newPlainText("synced_text", content)
                        cm.setPrimaryClip(clip)
                        Logger.d(TAG, "已更新本地剪贴板为文本内容")
                    }
                    CLIPBOARD_TYPE_IMAGE -> {
                        clipboardScope.launch {
                            try {
                                val file = writeClipboardImage(appContext, content)
                                if (file == null) {
                                    Logger.w(TAG, "剪贴板图片写入失败，保持原剪贴板内容")
                                    return@launch
                                }
                                val uri =
                                    FileProvider.getUriForFile(
                                        appContext,
                                        appContext.packageName + AUTHORITY_SUFFIX,
                                        file,
                                    )
                                cm.setPrimaryClip(
                                    ClipData.newUri(appContext.contentResolver, "synced_image", uri),
                                )
                                removeOtherClipboardImages(appContext, file)
                                Logger.d(TAG, "已更新剪贴板为图片：" + uri)
                            } catch (e: Exception) {
                                Logger.e(TAG, "更新剪贴板图片失败", e)
                            }
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "更新剪贴板失败：访问被拒绝。应用可能未处于前台状态。", e)
        } catch (e: Exception) {
            Logger.e(TAG, "更新剪贴板失败", e)
        }
    }

    /**
     * 把 [base64] 解码后写入剪贴板图片目录，返回文件；内容不是可解码的图片时返回 null。
     *
     * 只解码图片头（`inJustDecodeBounds`）校验，不分配整张位图。
     */
    private fun writeClipboardImage(
        context: Context,
        base64: String,
    ): File? {
        val bytes =
            try {
                Base64.decode(base64, Base64.DEFAULT)
            } catch (e: Exception) {
                Logger.e(TAG, "剪贴板图片 Base64 解码失败", e)
                return null
            }
        if (bytes.isEmpty()) {
            Logger.w(TAG, "剪贴板图片内容为空")
            return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Logger.w(TAG, "剪贴板图片无法解码，跳过写入")
            return null
        }
        val dir = File(context.cacheDir, IMAGE_DIR_NAME)
        if (!dir.exists() && !dir.mkdirs()) {
            Logger.w(TAG, "剪贴板图片目录创建失败：" + dir.absolutePath)
            return null
        }
        return try {
            File(dir, "clipboard_" + System.currentTimeMillis() + ".png").also { it.writeBytes(bytes) }
        } catch (e: Exception) {
            Logger.e(TAG, "剪贴板图片落盘失败", e)
            null
        }
    }

    /** 清掉本次写入之外的剪贴板图片，避免缓存目录堆积。 */
    private fun removeOtherClipboardImages(
        context: Context,
        keep: File,
    ) {
        try {
            File(context.cacheDir, IMAGE_DIR_NAME)
                .listFiles()
                ?.filter { it.isFile && it.absolutePath != keep.absolutePath }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Logger.w(TAG, "清理剪贴板图片失败：" + e.message)
        }
    }

    /**
     * 把一份已读到的剪贴板内容发送给在线设备。
     *
     * @param type [CLIPBOARD_TYPE_TEXT] 或 [CLIPBOARD_TYPE_IMAGE]
     * @param force 跳过 Rust 的"内容未改变"判定；防循环仍然生效
     */
    fun sendClipboardData(
        deviceManager: DeviceQueryPort,
        type: String,
        content: String,
        context: Context,
        force: Boolean,
    ) {
        // 发送可能晚于调用方生命周期：只保留应用上下文
        val appContext = context.applicationContext
        clipboardScope.launch {
            try {
                val devices = deviceManager.getAuthenticatedOnlineDevices()
                if (devices.isEmpty()) {
                    Logger.d(TAG, "没有可用于发送剪贴板内容的在线设备")
                    return@launch
                }

                handleSendResult(
                    appContext,
                    NativeCore.clipboardOnChanged(
                        buildTargetsJson(devices.map { it.uuid }),
                        if (type == CLIPBOARD_TYPE_IMAGE) MIME_IMAGE else MIME_TEXT,
                        content,
                        System.currentTimeMillis(),
                        force,
                    ),
                    type,
                )
            } catch (e: Exception) {
                Logger.e(TAG, "发送剪贴板失败", e)
            }
        }
    }

    /**
     * 直接同步文本内容到其他设备
     * 不触发系统剪贴板读取，不检查前台状态，用于特定场景（如验证码复制、Fcitx5 广播）
     */
    fun syncTextDirectly(
        deviceManager: DeviceQueryPort,
        text: String,
        context: Context,
    ) {
        if (text.isBlank()) return
        sendClipboardData(deviceManager, CLIPBOARD_TYPE_TEXT, text, context, force = false)
    }

    private fun buildTargetsJson(deviceUuids: List<String>): String {
        val arr = JSONArray()
        deviceUuids.forEach { arr.put(it) }
        return arr.toString()
    }

    /**
     * 处理 Rust 返回的发送结果：file_transfer 动作在 Android 侧尚未实现文件传输通道，
     * 大内容剪贴板会被丢弃，需向用户提示同步失败。
     */
    private fun handleSendResult(
        context: Context,
        resultJson: String?,
        type: String,
    ) {
        val action =
            try {
                JSONObject(resultJson ?: return).optString("action", "skipped")
            } catch (e: Exception) {
                return
            }
        when (action) {
            "sent" -> Logger.d(TAG, "剪贴板已发送（Rust 处理）：" + type)
            "skipped" -> Logger.d(TAG, "剪贴板已跳过（Rust 处理）")
            "file_transfer" -> {
                Logger.w(TAG, "剪贴板大内容需走文件传输通道，同步失败")
                Handler(Looper.getMainLooper()).post {
                    ToastUtils.showShortToast(context, "剪贴板内容过大（超过 2MB），同步失败")
                }
            }
            else -> Logger.d(TAG, "剪贴板结果未知：" + action)
        }
    }
}
