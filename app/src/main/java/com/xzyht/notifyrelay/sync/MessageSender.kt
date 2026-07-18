package com.xzyht.notifyrelay.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.util.Base64
import notifyrelay.base.util.Logger
import notifyrelay.base.util.PermissionHelper
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManager
import com.xzyht.notifyrelay.feature.device.service.DeviceInfo
import com.xzyht.notifyrelay.feature.notification.data.ChatMemory
import com.xzyht.notifyrelay.nativecore.NativeCore
import github.xzynine.superislandui.common.SuperIslandProtocol
import github.xzynine.superislandui.common.SuperIslandProtocol.PayloadOptions
import github.xzynine.superislandui.diff.DiffSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONObject
import com.sun.jna.Pointer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 消息发送类
 * 整合聊天测试和普通通知转发的消息发送功能
 * 支持队列和限流，避免大量并发发送导致的通知丢失
 */
object MessageSender {

    private const val TAG = "MessageSender"
    private const val MAX_CONCURRENT_SENDS = 5 // 最大并发发送数
    private const val MAX_RETRY_ATTEMPTS = 3 // 最大重试次数
    private const val RETRY_DELAY_MS = 1000L // 重试延迟

    // 发送队列
    private val sendChannel = Channel<SendTask>(Channel.Factory.UNLIMITED)
    // 超级岛单独发送队列（不去重、持续发送）
    private val superIslandSendChannel = Channel<SuperIslandTask>(Channel.Factory.UNLIMITED)
    private val sendSemaphore = Semaphore(MAX_CONCURRENT_SENDS)
    private val activeSends = AtomicInteger(0)
    // 超级岛发送并发控制（独立于普通通知）
    private val MAX_CONCURRENT_SUPERISLAND_SENDS = 3
    private val superSendSemaphore = Semaphore(MAX_CONCURRENT_SUPERISLAND_SENDS)
    private val activeSuperSends = AtomicInteger(0)
    // 去重 TTL
    private const val SENT_KEY_TTL_MS = 3_000L
    // Rust dedup 上下文缓存（首次使用时赋值）
    private var dedupCtx: Pointer? = null

    // 跟踪每个设备下每个会话的上次完整状态与全量发送时间
    private val lastStatePerDevice = mutableMapOf<String, MutableMap<String, DiffSystem.State>>()
    private val fullSentTimePerDevice = mutableMapOf<String, MutableMap<String, Long>>() // deviceUuid -> featureId -> lastFullSentMs
    // 超级岛：ACK 跟踪与强制全量发送控制
    private const val SI_ACK_TIMEOUT_MS = 4_000L
    private data class PendingAck(val hash: String, val ts: Long)
    private val siPendingAcks = mutableMapOf<String, MutableMap<String, PendingAck>>() // deviceUuid -> featureId -> pending
    private val siForceFullNext = ConcurrentHashMap.newKeySet<String>() // key: deviceUuid|featureId
    private val diffStateLock = Any()

    private class DiffDecision(
        val type: String, // "FULL" 或 "DELTA"
        val diff: DiffSystem.Diff,
        val needSend: Boolean
    )

    private fun computeDiffDecision(
        deviceUuid: String,
        featureId: String,
        newState: DiffSystem.State,
        forceFull: Boolean = false,
        fullResendIntervalMs: Long = 0
    ): DiffDecision {
        val deviceMap = synchronized(lastStatePerDevice) {
            lastStatePerDevice.getOrPut(deviceUuid) { mutableMapOf() }
        }
        val old = synchronized(lastStatePerDevice) { deviceMap[featureId] }
        val diff = DiffSystem.diff(old, newState)

        val now = System.currentTimeMillis()
        val firstOrForce = old == null || forceFull

        val fullMap = synchronized(fullSentTimePerDevice) {
            fullSentTimePerDevice.getOrPut(deviceUuid) { mutableMapOf() }
        }
        val lastFull = synchronized(fullSentTimePerDevice) { fullMap[featureId] ?: 0L }
        val timeForFull = fullResendIntervalMs > 0 && (now - lastFull > fullResendIntervalMs)

        val type = if (firstOrForce || timeForFull) "FULL" else "DELTA"
        val needSend = firstOrForce || timeForFull || !diff.isEmpty()

        return DiffDecision(type, diff, needSend)
    }

    private fun commitDiffState(deviceUuid: String, featureId: String, newState: DiffSystem.State, now: Long, isFull: Boolean) {
        synchronized(diffStateLock) {
            lastStatePerDevice.getOrPut(deviceUuid) { mutableMapOf() }[featureId] = newState
            if (isFull) {
                fullSentTimePerDevice.getOrPut(deviceUuid) { mutableMapOf() }[featureId] = now
            }
        }
    }

    init {
        // 启动队列处理协程
        CoroutineScope(Dispatchers.IO).launch {
            processSendQueue()
        }
        // 启动超级岛队列处理协程（独立）
        CoroutineScope(Dispatchers.IO).launch {
            processSuperIslandSendQueue()
        }
        // 定期清理 Rust 侧去重记录
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val ctx = dedupCtx
                    if (ctx != null) {
                        NativeCore.dedupCleanup(ctx, System.currentTimeMillis(), SENT_KEY_TTL_MS)
                    }
                } catch (_: Exception) {}
                delay(10_000L)
            }
        }
        // 超级岛：ACK 超时扫描，超时则标记下次强制全量
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    val now = System.currentTimeMillis()
                    val snapshot = synchronized(siPendingAcks) { siPendingAcks.mapValues { it.value.toMap() }.toMap() }
                    snapshot.forEach { (deviceUuid, featureMap) ->
                        featureMap.forEach { (featureId, pending) ->
                            if (now - pending.ts > SI_ACK_TIMEOUT_MS) {
                                val key = "$deviceUuid|$featureId"
                                siForceFullNext.add(key)
                                synchronized(siPendingAcks) { siPendingAcks[deviceUuid]?.remove(featureId) }
                                Logger.w("超级岛", "ACK超时：标记下次全量 device=$deviceUuid, feature=$featureId")
                            }
                        }
                    }
                } catch (_: Exception) {}
                delay(2_000L)
            }
        }
    }

    private data class SendTask(
        val device: DeviceInfo,
        val data: String,
        val deviceManager: DeviceConnectionManager,
        val retryCount: Int = 0,
        val dedupKey: String
    )

    // 超级岛发送任务（不使用去重键）
    private data class SuperIslandTask(
        val device: DeviceInfo,
        val data: String,
        val deviceManager: DeviceConnectionManager,
        val retryCount: Int = 0
    )

    /**
     * 处理发送队列
     */
    private suspend fun processSendQueue() {
        for (task in sendChannel) {
            sendSemaphore.acquire()
            activeSends.incrementAndGet()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    sendNotificationDataWithRetry(task)
                } finally {
                    task.deviceManager.rustContextInternal?.let { ctx ->
                        NativeCore.dedupClearPending(ctx, task.dedupKey)
                    }
                    sendSemaphore.release()
                    activeSends.decrementAndGet()
                }
            }
        }
    }

    /**
     * 处理超级岛发送队列（独立于普通通知队列，不走去重逻辑）
     */
    private suspend fun processSuperIslandSendQueue() {
        for (task in superIslandSendChannel) {
            superSendSemaphore.acquire()
            activeSuperSends.incrementAndGet()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 改为即时发送一次，不进行重试（实时性优先）
                    sendSuperIslandDataOnce(task)
                } finally {
                    superSendSemaphore.release()
                    activeSuperSends.decrementAndGet()
                }
            }
        }
    }

    /**
     * 带重试的通知数据发送
     */
    private suspend fun sendNotificationDataWithRetry(task: SendTask) {
        var success = false

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                val auth = task.deviceManager.authenticatedDevices[task.device.uuid]
                if (auth == null || !auth.isAccepted) {
                    //Logger.d(TAG, "设备未认证，跳过发送: ${task.device.displayName}")
                    return
                }

                // 根据负载类型选择报文头（媒体播放使用 DATA_MEDIAPLAY，其它使用 DATA_NOTIFICATION）
                val header = try {
                    val obj = JSONObject(task.data)
                    if (obj.optString("type", "").equals("MEDIA_PLAY", true)) "DATA_MEDIAPLAY" else "DATA_NOTIFICATION"
                } catch (_: Exception) { "DATA_NOTIFICATION" }
                ProtocolSender.sendEncrypted(task.deviceManager, task.device, header, task.data, 10000L)
                success = true
                task.deviceManager.rustContextInternal?.let { ctx ->
                    NativeCore.dedupMarkSent(ctx, task.dedupKey)
                }
                
                if (header == "DATA_MEDIAPLAY") {
                    try {
                        val obj = JSONObject(task.data)
                        val mediaType = obj.optString("mediaType", "")
                        if (mediaType.equals("END", true)) {
                            synchronized(lastStatePerDevice) {
                                lastStatePerDevice.getOrPut(task.device.uuid) { mutableMapOf() }.remove("media_global")
                            }
                            synchronized(fullSentTimePerDevice) {
                                fullSentTimePerDevice.getOrPut(task.device.uuid) { mutableMapOf() }.remove("media_global")
                            }
                        }
                    } catch (_: Exception) {}
                }
                
                //Logger.d(TAG, "通知发送成功到设备: ${task.device.displayName}, data: ${task.data}")

                if (success) return

            } catch (e: Exception) {
                Logger.w(TAG, "发送失败 (尝试 ${attempt + 1}/${MAX_RETRY_ATTEMPTS}): ${task.device.displayName}, 错误: ${e.message}")

                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1)) // 递增延迟
                }
            }
        }

        if (!success) {
            Logger.e(TAG, "发送最终失败，放弃重试: ${task.device.displayName}")
        }
    }

    /**
     * 超级岛数据发送（带重试），不会更新去重表或使用去重键，保证尽可能持续发送
     */
    private suspend fun sendSuperIslandDataWithRetry(task: SuperIslandTask) {
        var success = false

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                val auth = task.deviceManager.authenticatedDevices[task.device.uuid]
                if (auth == null || !auth.isAccepted) {
                    //Logger.d("超级岛", "超级岛: 设备未认证，跳过发送: ${task.device.displayName}")
                    return
                }

                ProtocolSender.sendEncrypted(task.deviceManager, task.device, "DATA_SUPERISLAND", task.data, 10000L)
                success = true
                //Logger.d("超级岛", "超级岛: 发送成功到设备: ${task.device.displayName}")

                if (success) return

            } catch (e: Exception) {
                Logger.w("超级岛", "超级岛: 发送失败 (尝试 ${attempt + 1}/${MAX_RETRY_ATTEMPTS}): ${task.device.displayName}, 错误: ${e.message}")

                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1)) // 递增延迟
                }
            }
        }

        if (!success) {
            Logger.e("超级岛", "超级岛: 发送最终失败，放弃重试: ${task.device.displayName}")
        }
    }

    /**
     * 超级岛即时发送（不重试）。实时性优先：尝试一次发送，遇到错误记录日志后返回。
     */
    private suspend fun sendSuperIslandDataOnce(task: SuperIslandTask) {
        try {
            val auth = task.deviceManager.authenticatedDevices[task.device.uuid]
            if (auth == null || !auth.isAccepted) {
                //Logger.d("超级岛", "超级岛: 设备未认证，跳过发送: ${task.device.displayName}")
                return
            }

            ProtocolSender.sendEncrypted(task.deviceManager, task.device, "DATA_SUPERISLAND", task.data, 10000L)
            //Logger.d("超级岛", "超级岛: 发送成功到设备: ${task.device.displayName}")
        } catch (e: Exception) {
            Logger.w("超级岛", "超级岛: 实时发送失败: ${task.device.displayName}, 错误: ${e.message}")
        }
    }

    /**
     * 发送聊天测试消息
     * @param context 上下文
     * @param message 消息内容
     * @param deviceManager 设备管理器
     */
    fun sendChatMessage(context: Context, message: String, deviceManager: DeviceConnectionManager) {
        try {
            // 获取所有已认证设备
            val allDevices = deviceManager.devices.value.values.map { it.first }
            val sentAny = allDevices.isNotEmpty() && message.isNotBlank()

            if (!sentAny) {
                Logger.w(TAG, "没有可用的设备或消息为空")
                return
            }

            // 构建标准 JSON 格式的消息
            val pkgName: String = context.packageName
            val raw = JSONObject().apply {
                put("packageName", pkgName)
                put("appName", "NotifyRelay")
                put("title", "聊天测试")
                put("text", message)
                put("time", System.currentTimeMillis())
            }.toString()
            allDevices.forEach { device ->
                enqueueNotification(device, raw, deviceManager, "聊天")
            }

            // 记录到聊天历史
            ChatMemory.append(context, "发送: $message")

            Logger.i(TAG, "聊天消息已加入队列，共发送到 ${allDevices.size} 个设备，当前活跃发送: ${activeSends.get()}")
        } catch (e: Exception) {
            Logger.e(TAG, "发送聊天消息失败", e)
        }
    }

    /**
     * 发送媒体播放通知
     * 使用专门的协议前缀标记媒体通知，支持状态变化跟踪
     * 保持差异发送，仅在封面发生变化时发送包含封面的包，否则仅发送文本部分
     */
    private const val MEDIA_FULL_RESEND_INTERVAL_MS = 6_000L
    private const val MEDIA_MIN_SEND_INTERVAL_MS = 3_000L
    private val lastMediaSendTime = mutableMapOf<String, Long>() // deviceUuid -> last any media send

    fun sendMediaPlayNotification(
        context: Context,
        packageName: String,
        appName: String?,
        title: String?,
        text: String?,
        coverUrl: String?,
        time: Long,
        deviceManager: DeviceConnectionManager
    ) {
        try {
            val authenticatedDevices = getAuthenticatedDevices(deviceManager)
            if (authenticatedDevices.isEmpty()) {
                Logger.w(TAG, "没有已认证的设备")
                return
            }

            val isLocked = PermissionHelper.isDeviceLocked(context)

            val state = DiffSystem.State(
                title = title,
                text = text,
                paramV2Raw = null,
                pics = if (coverUrl != null) mapOf("miui.focus.pic_cover" to coverUrl) else emptyMap()
            )

            val featureId = "media_global"

            authenticatedDevices.forEach { deviceInfo ->
                val dd = computeDiffDecision(
                    deviceUuid = deviceInfo.uuid,
                    featureId = featureId,
                    newState = state,
                    fullResendIntervalMs = MEDIA_FULL_RESEND_INTERVAL_MS
                )
                if (!dd.needSend) return@forEach

                val now = System.currentTimeMillis()
                val lastSend = synchronized(lastMediaSendTime) { lastMediaSendTime[deviceInfo.uuid] ?: 0L }
                if (now - lastSend < MEDIA_MIN_SEND_INTERVAL_MS) return@forEach
                synchronized(lastMediaSendTime) { lastMediaSendTime[deviceInfo.uuid] = now }

                val coverChanged = dd.diff.picsChanged.containsKey("miui.focus.pic_cover")
                val effectiveType = if (coverChanged && dd.type != "FULL") "FULL" else dd.type

                val payloadObj = if (effectiveType == "FULL") {
                    SuperIslandProtocol.buildPayload(
                        packageName, appName, time, isLocked, state.toJson(),
                        PayloadOptions.MEDIA_FULL.copy(
                            extraFields = if (coverUrl != null) mapOf("coverUrl" to coverUrl) else emptyMap()
                        )
                    )
                } else {
                    val partialState = DiffSystem.State(
                        title = dd.diff.title, text = dd.diff.text,
                        paramV2Raw = null, pics = emptyMap()
                    )
                    SuperIslandProtocol.buildPayload(
                        packageName, appName, time, isLocked, partialState.toJson(),
                        PayloadOptions.MEDIA_DELTA
                    )
                }
                val raw = payloadObj.toString()
                enqueueNotification(deviceInfo, raw, deviceManager, "媒体播放")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "发送媒体播放通知失败", e)
        }
    }
    
    /**
     * 发送媒体播放结束包
     * 用于通知接收端关闭媒体会话超级岛
     */
    private fun enqueueNotification(
        deviceInfo: DeviceInfo, json: String, deviceManager: DeviceConnectionManager, tag: String = ""
    ): Boolean {
        val ctx = deviceManager.rustContextInternal
        if (ctx == null) {
            Logger.w(TAG, "Rust 未初始化，跳过入队: ${deviceInfo.displayName}")
            return false
        }
        dedupCtx = ctx
        val dedupKey = NativeCore.computeDedupKey(deviceInfo.uuid, json) ?: return false
        if (!NativeCore.dedupCheckAndPend(ctx, dedupKey, SENT_KEY_TTL_MS)) return false
        CoroutineScope(Dispatchers.IO).launch {
            try { sendChannel.send(SendTask(deviceInfo, json, deviceManager, dedupKey = dedupKey)) }
            catch (e: Exception) { NativeCore.dedupClearPending(ctx, dedupKey); Logger.e(TAG, "加入${tag}发送队列失败: ${deviceInfo.displayName}", e) }
        }
        return true
    }

    fun sendMediaPlayEndNotification(
        context: Context,
        packageName: String,
        appName: String?,
        time: Long,
        deviceManager: DeviceConnectionManager
    ) {
        try {
            val authenticatedDevices = getAuthenticatedDevices(deviceManager)
            if (authenticatedDevices.isEmpty()) {
                Logger.w(TAG, "没有已认证的设备")
                return
            }

            val isLocked = PermissionHelper.isDeviceLocked(context)
            val payload = SuperIslandProtocol.buildPayload(
                packageName, appName, time, isLocked, JSONObject(),
                PayloadOptions.MEDIA_END
            ).toString()

            authenticatedDevices.forEach { deviceInfo ->
                synchronized(lastStatePerDevice) {
                    lastStatePerDevice[deviceInfo.uuid]?.remove("media_global")
                }
                synchronized(fullSentTimePerDevice) {
                    fullSentTimePerDevice[deviceInfo.uuid]?.remove("media_global")
                }
                enqueueNotification(deviceInfo, payload, deviceManager, "媒体结束")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "发送媒体播放结束通知失败", e)
        }
    }
    /**
     * 发送普通通知转发消息
     * @param context 上下文
     * @param packageName 应用包名
     * @param appName 应用名称
     * @param title 通知标题
     * @param text 通知内容
     * @param time 通知时间
     * @param deviceManager 设备管理器
     */
    fun sendNotificationMessage(
        context: Context,
        packageName: String,
        appName: String?,
        title: String?,
        text: String?,
        time: Long,
        deviceManager: DeviceConnectionManager
    ) {
        try {
            val authenticatedDevices = getAuthenticatedDevices(deviceManager)

            if (authenticatedDevices.isEmpty()) {
                Logger.w(TAG, "没有已认证的设备")
                return
            }

            // 获取锁屏状态
            val isLocked = PermissionHelper.isDeviceLocked(context)

            // 构建标准 JSON 格式的通知数据
            val raw = JSONObject().apply {
                put("packageName", packageName)
                put("appName", appName ?: packageName)
                put("title", title ?: "")
                put("text", text ?: "")
                put("time", time)
                put("isLocked", isLocked)
            }.toString()
            authenticatedDevices.forEach { deviceInfo ->
                enqueueNotification(deviceInfo, raw, deviceManager, "通知")
            }

            Logger.i(TAG, "通知已加入队列，共 ${authenticatedDevices.size} 个设备，当前活跃发送: ${activeSends.get()}")
        } catch (e: Exception) {
            Logger.e(TAG, "发送通知消息失败", e)
        }
    }

    /**
     * 发送超级岛专用数据（包含 param_v2 原始 JSON 与图片 map）
     */
    fun sendSuperIslandData(
        context: Context,
        superPkg: String,
        appName: String?,
        title: String?,
        text: String?,
        time: Long,
        paramV2Raw: String?,
        picMap: Map<String, String>?,
        deviceManager: DeviceConnectionManager,
        featureIdOverride: String? = null
    ) {
        try {
            val authenticatedDevices = getAuthenticatedDevices(deviceManager)
            if (authenticatedDevices.isEmpty()) {
                Logger.w(TAG, "没有已认证的设备")
                return
            }

            val isLocked = PermissionHelper.isDeviceLocked(context)

            // 处理图片：若 picMap 中是本地 URI/file 路径则读取并编码为 base64 data URI，http(s) 地址或其他字符串保持不变
            val processedPics = mutableMapOf<String, String>()
            if (picMap != null) {
                // 在 IO 线程同步读取后再继续（sendSuperIslandData 本身是同步接口）
                runBlocking(Dispatchers.IO) {
                    picMap.forEach { (k, v) ->
                        try {
                            val lower = v.lowercase()
                            if (lower.startsWith("content://") || lower.startsWith("file://") || v.startsWith(
                                    "/"
                                )
                            ) {
                                try {
                                    val uri = Uri.parse(v)
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        val bytes = input.readBytes()
                                        val mime =
                                            context.contentResolver.getType(uri) ?: "image/png"
                                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                        processedPics[k] = "data:$mime;base64,$b64"
                                    } ?: run {
                                        // 无法打开则回退到原始字符串
                                        processedPics[k] = v
                                    }
                                } catch (e: Exception) {
                                    // 读取失败则保留原值
                                    processedPics[k] = v
                                }
                            } else {
                                // 非本地资源（如 http:// 或 已经是 base64 字符串），保持原样
                                processedPics[k] = v
                            }
                        } catch (e: Exception) {
                            processedPics[k] = v
                        }
                    }
                }
            }

            // 计算特征键（支持外部传入首包固定ID，避免后续波动）
            val featureId = featureIdOverride ?: SuperIslandProtocol.computeFeatureId(
                superPkg, paramV2Raw, title, text
            )

            val finalPics: Map<String, String> = if (processedPics.isNotEmpty()) processedPics.toMap() else (picMap?.toMap() ?: emptyMap())
            val newState = DiffSystem.State(
                title = title,
                text = text,
                paramV2Raw = paramV2Raw,
                pics = finalPics
            )

            // 将超级岛发送任务加入独立队列（不去重，实时性优先）
            authenticatedDevices.forEach { deviceInfo ->
                val forceFull = siForceFullNext.contains("${deviceInfo.uuid}|$featureId")

                val dd = computeDiffDecision(
                    deviceUuid = deviceInfo.uuid,
                    featureId = featureId,
                    newState = newState,
                    forceFull = forceFull,
                    fullResendIntervalMs = 30_000L
                )

                // 超级岛始终发送（即使无差异，也作为心跳包）
                val payloadObj = if (dd.type == "FULL") {
                    SuperIslandProtocol.buildFullPayload(
                        superPkg, appName, time, isLocked, featureId, newState
                    )
                } else {
                    SuperIslandProtocol.buildDeltaPayload(
                        superPkg, appName, time, isLocked, featureId, dd.diff
                    )
                }

                // 记录待ACK哈希
                try {
                    val h = payloadObj.optString("hash", "")
                    if (h.isNotEmpty()) {
                        val map = synchronized(siPendingAcks) { siPendingAcks.getOrPut(deviceInfo.uuid) { mutableMapOf() } }
                        synchronized(siPendingAcks) { map[featureId] = PendingAck(h, System.currentTimeMillis()) }
                    }
                } catch (_: Exception) {}
                val raw = payloadObj.toString()
                val task = SuperIslandTask(deviceInfo, raw, deviceManager)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        superIslandSendChannel.send(task)
                        commitDiffState(deviceInfo.uuid, featureId, newState, System.currentTimeMillis(), dd.type == "FULL")
                    } catch (e: Exception) {
                        Logger.e("超级岛", "超级岛: 加入超级岛发送队列失败：${deviceInfo.displayName}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("超级岛", "超级岛: 发送超级岛数据失败", e)
        }
    }

    /**
     * 发送超级岛终止事件：当本地确认没有该超级岛通知时调用。
     */
    fun sendSuperIslandEnd(
        context: Context,
        superPkg: String,
        appName: String?,
        time: Long,
        paramV2Raw: String?,
        title: String?,
        text: String?,
        deviceManager: DeviceConnectionManager,
        featureIdOverride: String? = null
    ) {
        try {
            val authenticatedDevices = getAuthenticatedDevices(deviceManager)
            if (authenticatedDevices.isEmpty()) return
            val isLocked = PermissionHelper.isDeviceLocked(context)
            val featureId = featureIdOverride ?: SuperIslandProtocol.computeFeatureId(
                superPkg, paramV2Raw, title, text
            )
            val payload = SuperIslandProtocol.buildEndPayload(
                superPkg, appName, time, isLocked, featureId
            ).toString()
            authenticatedDevices.forEach { deviceInfo ->
                // 清理该设备的lastState
                synchronized(lastStatePerDevice) {
                    lastStatePerDevice[deviceInfo.uuid]?.remove(featureId)
                }
                val task = SuperIslandTask(deviceInfo, payload, deviceManager)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        superIslandSendChannel.send(task)
                        //Logger.d("超级岛", "超级岛: 终止数据已加入发送队列：${deviceInfo.displayName}")
                    } catch (e: Exception) {
                        Logger.e("超级岛", "超级岛: 终止数据入队失败：${deviceInfo.displayName}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("超级岛", "超级岛: 发送终止事件失败", e)
        }
    }

    /**
     * 发送高优先级悬浮通知（用于应用跳转指示）
     * @param context 上下文
     * @param title 通知标题
     * @param text 通知内容
     */
    fun sendHighPriorityNotification(context: Context, title: String?, text: String?) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "notifyrelay_temp"

            // 创建通知渠道（如果不存在）
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, "跳转通知", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "应用内跳转指示通知"
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                    importance = NotificationManager.IMPORTANCE_HIGH
                    setBypassDnd(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // 构建通知
            val builder = Notification.Builder(context, channelId).apply {
                setContentTitle(title ?: "(无标题)")
                setContentText(text ?: "(无内容)")
                setSmallIcon(android.R.drawable.ic_dialog_info)
                setCategory(Notification.CATEGORY_MESSAGE)
                setAutoCancel(true)
                setVisibility(Notification.VISIBILITY_PUBLIC)
                setOngoing(false)
            }

            // 发送通知，使用当前时间戳作为ID
            val notifyId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            notificationManager.notify(notifyId, builder.build())

            // 5秒后自动销毁通知
            Handler(context.mainLooper).postDelayed({
                notificationManager.cancel(notifyId)
            }, 5000)

            //Logger.d(TAG, "高优先级悬浮通知已发送: $title")
        } catch (e: Exception) {
            Logger.e(TAG, "发送高优先级通知失败", e)
        }
    }

    /**
     * 获取已认证的设备列表
     * @param deviceManager 设备管理器
     * @return 已认证设备的列表
     */
    private fun getAuthenticatedDevices(deviceManager: DeviceConnectionManager): List<DeviceInfo> {
        return try {
            val field = deviceManager::class.java.getDeclaredField("authenticatedDevices")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val authedMap = field.get(deviceManager) as? Map<String, *>

            val myUuidField = deviceManager::class.java.getDeclaredField("uuid")
            myUuidField.isAccessible = true
            val myUuid = myUuidField.get(deviceManager) as? String

            val authenticatedDevices = mutableListOf<DeviceInfo>()

            authedMap?.forEach { (uuid, _) ->
                val uuidStr = uuid as String
                if (uuidStr == myUuid) return@forEach

                val infoMethod = deviceManager::class.java.getDeclaredMethod("getDeviceInfo", String::class.java)
                infoMethod.isAccessible = true
                val deviceInfo = infoMethod.invoke(deviceManager, uuidStr) as? DeviceInfo

                if (deviceInfo != null) {
                    authenticatedDevices.add(deviceInfo)
                }
            }

            authenticatedDevices
        } catch (e: Exception) {
            Logger.e(TAG, "获取已认证设备列表失败", e)
            emptyList()
        }
    }

    /**
     * 检查是否有可用的设备
     * @param deviceManager 设备管理器
     * @return 是否有可用的设备
     */
    fun hasAvailableDevices(deviceManager: DeviceConnectionManager): Boolean {
        return deviceManager.devices.value.isNotEmpty()
    }

    // 接收端ACK回调：当收到对方SI_ACK时调用，确认hash送达，清理待ACK并解除强制全量
    fun onSuperIslandAck(deviceUuid: String, featureId: String?, hash: String?) {
        try {
            if (featureId.isNullOrEmpty() || hash.isNullOrEmpty()) return
            val pending = synchronized(siPendingAcks) { siPendingAcks[deviceUuid]?.get(featureId) }
            if (pending != null && pending.hash == hash) {
                synchronized(siPendingAcks) { siPendingAcks[deviceUuid]?.remove(featureId) }
                val key = "$deviceUuid|$featureId"
                siForceFullNext.remove(key)
                //Logger.d("超级岛", "ACK匹配成功：device=$deviceUuid, feature=$featureId")
            } else {
                val key = "$deviceUuid|$featureId"
                siForceFullNext.add(key)
                Logger.w("超级岛", "ACK哈希不匹配或无待确认：标记下次全量 device=$deviceUuid, feature=$featureId, ackHash=$hash")
            }
        } catch (_: Exception) {}
    }

    /**
     * 检查消息是否有效
     * @param message 消息内容
     * @return 消息是否有效
     */
    fun isValidMessage(message: String?): Boolean {
        return !message.isNullOrBlank()
    }

}
