package com.xzyht.notifyrelay.feature.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.xzyht.notifyrelay.common.core.sync.ProtocolSender
import com.xzyht.notifyrelay.common.core.util.Logger
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 剪贴板同步管理器
 * 负责监听剪贴板变化并同步到其他设备
 */
object ClipboardSyncManager {
    private const val TAG = "ClipboardSyncManager"
    private const val CLIPBOARD_TYPE_TEXT = "text"
    private const val CLIPBOARD_TYPE_IMAGE = "image"
    private const val DATA_HEADER = "DATA_CLIPBOARD"
    
    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardContent: String = ""
    private var lastClipboardType: String = ""
    private var lastSyncTime: Long = 0
    private var isSyncing = false
    private var lastReceivedContent: String = ""
    private var lastReceivedType: String = ""
    private var lastReceivedTime: Long = 0
    private var isInternalUpdate = false // 标记是否为内部更新，用于防止循环发送
    private val ANTI_LOOP_DELAY = 1000L // 防止循环发送的延迟时间（毫秒）
    
    /**
     * 初始化剪贴板同步管理器
     */
    fun init(context: Context) {
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        Logger.d(TAG, "剪贴板同步管理器已初始化")
    }
    
    /**
     * 发送剪贴板内容到所有已认证的在线设备
     */
    fun sendClipboardToDevices(deviceManager: DeviceConnectionManager, context: Context) {
        if (isSyncing) return
        
        // 如果是内部更新，直接返回，防止循环发送
        if (isInternalUpdate) {
            Logger.d(TAG, "内部更新剪贴板，跳过发送")
            return
        }
        
        isSyncing = true
        CoroutineScope(Dispatchers.IO).launch {
            var devices = emptyList<com.xzyht.notifyrelay.feature.device.service.DeviceInfo>()
            try {
                devices = deviceManager.getAuthenticatedOnlineDevices()
                if (devices.isEmpty()) {
                    Logger.d(TAG, "没有可用于发送剪贴板内容的在线设备")
                    isSyncing = false
                    return@launch
                }
                
                val clipboardData = getCurrentClipboardData(context)
                if (clipboardData != null) {
                    val (type, content) = clipboardData
                    
                    // 避免重复发送相同内容
                    if (content == lastClipboardContent && type == lastClipboardType) {
                        //Logger.d(TAG, "剪贴板内容未改变，跳过发送")
                        isSyncing = false
                        return@launch
                    }
                    
                    // 避免发送刚刚从远程接收的内容，防止循环发送
                    if (content == lastReceivedContent && type == lastReceivedType) {
                        val now = System.currentTimeMillis()
                        if (now - lastReceivedTime < ANTI_LOOP_DELAY) {
                            Logger.d(TAG, "剪贴板内容来自远程，跳过发送")
                            isSyncing = false
                            return@launch
                        }
                    }
                    
                    // 避免频繁发送
                    val now = System.currentTimeMillis()
                    if (now - lastSyncTime < TimeUnit.SECONDS.toMillis(1)) {
                        Logger.d(TAG, "剪贴板内容同步过频繁，跳过发送")
                        isSyncing = false
                        return@launch
                    }
                    
                    val json = buildClipboardJsonString(type, content, now)
                    
                    // 发送到所有在线设备
                    for (device in devices) {
                        ProtocolSender.sendEncrypted(deviceManager, device, DATA_HEADER, json)
                    }
                    
                    // 更新最后发送记录
                    lastClipboardContent = content
                    lastClipboardType = type
                    lastSyncTime = now
                    Logger.d(TAG, "剪贴板已发送至 ${devices.size} 台设备： $type")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "发送剪贴板至 ${devices.size} 台设备失败", e)
            } finally {
                isSyncing = false
            }
        }
    }
    
    /**
     * 处理接收到的剪贴板消息
     */
    fun handleClipboardMessage(jsonData: String, context: Context) {
        try {
            val json = org.json.JSONObject(jsonData)
            val type = json.getString("clipboardType")
            val content = json.getString("content")
            val time = json.optLong("time", 0)
            
            // 避免处理旧消息
            if (time <= lastSyncTime) {
                Logger.d(TAG, "旧剪贴板消息，跳过处理")
                return
            }
            
            // 保存最后接收的内容、类型和时间，用于防止循环发送
            lastReceivedContent = content
            lastReceivedType = type
            lastReceivedTime = System.currentTimeMillis()
            
            // 更新本地剪贴板
            updateLocalClipboardContent(type, content, context)
            
            // 更新最后接收记录
            lastClipboardContent = content
            lastClipboardType = type
            lastSyncTime = time
        } catch (e: Exception) {
            Logger.e(TAG, "处理剪贴板消息失败", e)
        }
    }
    
    /**
     * 获取当前剪贴板数据
     */
    private fun getCurrentClipboardData(context: Context): Pair<String, String>? {
        try {
            clipboardManager?.let { cm ->
                // 尝试获取剪贴板内容，捕获可能的权限异常
                val clip = cm.primaryClip
                if (clip == null) {
                    Logger.d(TAG, "剪贴板为空")
                    return null
                }
                
                val clipDescription = clip.description
                val item = clip.getItemAt(0)
                
                if (clipDescription != null && item != null) {
                    // 处理文本类型剪贴板内容
                    if (clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                        clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) {
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
                            Logger.w(TAG, "获取剪贴板文本失败：权限拒绝", e)
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
                            val imageBitmap: Bitmap? = item.uri?.let { uri ->
                                try {
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    inputStream?.use {
                                        BitmapFactory.decodeStream(it)
                                    }
                                } catch (e: Exception) {
                                    Logger.e(TAG, "从URI解码图片Bitmap失败", e)
                                    null
                                }
                            }
                            if (imageBitmap != null) {
                                val dataUrl = com.xzyht.notifyrelay.common.core.util.DataUrlUtils.bitmapToDataUri(imageBitmap)
                                // 从data URI中提取纯base64部分
                                val commaIndex = dataUrl.indexOf(',')
                                if (commaIndex > 0) {
                                    val base64Image = dataUrl.substring(commaIndex + 1)
                                    return Pair(CLIPBOARD_TYPE_IMAGE, base64Image)
                                }
                            }
                        } catch (e: SecurityException) {
                            Logger.w(TAG, "获取剪贴板图片失败：权限拒绝", e)
                            return null
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Logger.w(TAG, "获取剪贴板数据失败：权限拒绝。应用可能不在前台。", e)
        } catch (e: Exception) {
            Logger.e(TAG, "获取剪贴板数据失败", e)
        }
        return null
    }
    
    /**
     * 更新本地剪贴板
     */
    private fun updateLocalClipboardContent(type: String, content: String, context: Context) {
        try {
            isInternalUpdate = true
            clipboardManager?.let { cm ->
                when (type) {
                    CLIPBOARD_TYPE_TEXT -> {
                        // 使用便捷方法创建文本剪贴板内容
                        val clip = ClipData.newPlainText("synced_text", content)
                        cm.setPrimaryClip(clip)
                        Logger.d(TAG, "已更新本地剪贴板为文本内容")
                    }
                    CLIPBOARD_TYPE_IMAGE -> {
                        // 构建完整的data URI
                        val dataUrl = "data:image/png;base64,$content"
                        val bitmap = com.xzyht.notifyrelay.common.core.util.DataUrlUtils.decodeDataUrlToBitmap(dataUrl)
                        if (bitmap != null) {
                            // 将Bitmap转换为文本，因为直接存储Bitmap在剪贴板中需要特殊处理
                            // 这里我们将图片转换为Base64字符串存储，其他应用可以解析
                            val clipItem = ClipData.Item(dataUrl)
                            // 使用文本类型，因为ClipData.Item没有直接接受Bitmap的构造函数
                            val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)
                            val clip = ClipData("synced_image", mimeTypes, clipItem)
                            cm.setPrimaryClip(clip)
                            Logger.d(TAG, "已更新剪贴板，包含图片数据URL")
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "更新剪贴板失败：访问被拒绝。应用可能未处于前台状态。", e)
        } catch (e: Exception) {
            Logger.e(TAG, "更新剪贴板失败", e)
        } finally {
            // 延迟重置内部更新标志，确保剪贴板变化监听器有足够时间触发
            CoroutineScope(Dispatchers.IO).launch {
                delay(500)
                isInternalUpdate = false
            }
        }
    }
    
    /**
     * 构建剪贴板JSON消息
     */
    private fun buildClipboardJsonString(type: String, content: String, time: Long): String {
        return org.json.JSONObject().apply {
            put("clipboardType", type)
            put("content", content)
            put("time", time)
        }.toString()
    }
}