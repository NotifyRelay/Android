package io.github.miuzarte.scrcpyforandroid.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.scrcpy.ClientOptions
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.AudioSource
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.VideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import notifyrelay.data.config.ScrcpyDefaults
import notifyrelay.data.model.ConnectionTarget
import java.util.concurrent.Executors
import kotlin.concurrent.Volatile

class AudioForwardingService : Service() {
    companion object {
        private const val TAG = "AudioForwardingService"
        private const val NOTIFICATION_CHANNEL_ID = "audio_forwarding_channel"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "io.github.miuzarte.scrcpyforandroid.ACTION_STOP_AUDIO_FORWARDING"

        @Volatile
        private var isRunning: Boolean = false

        @Volatile
        private var currentSessionTarget: ConnectionTarget? = null

        fun startAudioForwarding(context: Context, host: String, port: Int = ScrcpyDefaults.ADB_PORT, deviceName: String = host): Boolean {
            if (isRunning) {
                Log.w(TAG, "音频转发已在运行中: ${currentSessionTarget?.host}:${currentSessionTarget?.port}")
                return false
            }

            if (host.isBlank()) {
                Log.e(TAG, "目标设备 IP 为空")
                return false
            }

            isRunning = true
            currentSessionTarget = ConnectionTarget(host, port)

            val intent = Intent(context, AudioForwardingService::class.java).apply {
                putExtra("host", host)
                putExtra("port", port)
                putExtra("deviceName", deviceName)
            }
            context.startForegroundService(intent)
            return true
        }

        fun stopAudioForwarding(context: Context) {
            val intent = Intent(context, AudioForwardingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var scrcpy: Scrcpy? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        NativeAdbService.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopForwarding()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val host = intent?.getStringExtra("host")
        val port = intent?.getIntExtra("port", ScrcpyDefaults.ADB_PORT) ?: ScrcpyDefaults.ADB_PORT

        if (host.isNullOrBlank()) {
            Log.e(TAG, "host 为空，停止服务")
            stopSelf()
            return START_NOT_STICKY
        }

        val deviceName = intent.getStringExtra("deviceName") ?: host

        startForeground(NOTIFICATION_ID, createNotification(deviceName))

        scope.launch {
            try {
                val success = startScrcpySession(host, port)

                if (!success) {
                    Log.e(TAG, "启动 scrcpy 会话失败")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动音频转发失败", e)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun startScrcpySession(host: String, port: Int): Boolean {
        return try {
            if (NativeAdbService.isConnectedSync()) {
                Log.i(TAG, "ADB 已连接，先断开现有连接")
                NativeAdbService.disconnect()
            }

            Log.i(TAG, "开始 ADB 连接: $host:$port")
            runCatching {
                NativeAdbService.connect(host, port)
            }.onFailure { e ->
                Log.e(TAG, "ADB 连接失败: $host:$port", e)
                return false
            }
            Log.i(TAG, "ADB 连接成功: $host:$port")

            val mainSettings = loadMainSettings(applicationContext)
            val scrcpyInstance = Scrcpy(
                appContext = applicationContext,
                serverRemotePath = ScrcpyDefaults.SERVER_REMOTE_PATH,
                lowLatency = mainSettings.lowLatency,
            )
            scrcpy = scrcpyInstance

            val options = ClientOptions().apply {
                video = false
                audio = true
                audioCodec = Codec.fromString(ScrcpyDefaults.AUDIO_CODEC, Codec.Type.AUDIO)
                audioBitRate = ScrcpyDefaults.AUDIO_BIT_RATE_KBPS * 1000
                control = false
                audioDup = ScrcpyDefaults.AUDIO_DUP
                audioSource = AudioSource.fromString(ScrcpyDefaults.AUDIO_SOURCE_PRESET)
            }

            Log.i(TAG, "启动 scrcpy 仅音频模式")
            val sessionInfo = scrcpyInstance.start(options)
            Log.i(TAG, "scrcpy 已启动: device=${sessionInfo.deviceName}, audioCodecId=${sessionInfo.audioCodecId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "启动 scrcpy 会话异常", e)
            false
        }
    }

    private fun stopForwarding() {
        scope.launch {
            try {
                val instance = scrcpy
                if (instance != null) {
                    Log.i(TAG, "停止 scrcpy 会话")
                    runCatching { instance.stop() }

                    Log.i(TAG, "断开 ADB 连接")
                    runCatching { NativeAdbService.disconnect() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "停止音频转发失败", e)
            } finally {
                isRunning = false
                currentSessionTarget = null
                scrcpy = null
                Log.i(TAG, "音频转发已停止")
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "AudioForwardingService onDestroy")
        stopForwarding()
        executor.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "音频转发",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "音频转发服务状态"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(deviceName: String): Notification {
        val stopIntent = Intent(this, AudioForwardingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("正在接收音频")
            .setContentText("从 $deviceName 接收音频中")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setRequestPromotedOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "停止",
                stopPendingIntent
            )
            .build()
    }
}
