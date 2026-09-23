package com.xzyht.notifyrelay.ui.activity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.xzyht.notifyrelay.feature.device.service.DeviceConnectionManagerSingleton
import com.xzyht.notifyrelay.feature.media.service.MediaProjectionForegroundService
import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.base.util.Logger
import notifyrelay.base.util.ToastUtils

/**
 * 屏幕捕获授权流程协调器
 *
 * 负责媒体投影授权请求、录音权限请求、投影回调注册与前台服务就绪回调的登记/注销。
 *
 * 注意：三个 `registerForActivityResult` 必须在 Activity **创建前**注册，
 * 因此本类必须在 `MainActivity` 的字段初始化期构造（不能挪到 `onCreate`）。
 */
internal class ScreenCaptureCoordinator(
    private val activity: MainActivity,
) {
    // 屏幕捕获授权结果，等待本应用前台且媒体投影前台服务就绪后处理
    private var pendingScreenCapture: Pair<Int, Intent>? = null
    private var registeredOnForegroundReady: (() -> Unit)? = null
    private var registeredOnRequestMediaProjection: (() -> Unit)? = null

    private val screenCaptureLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                pendingScreenCapture = result.resultCode to result.data!!
                processPendingScreenCapture()
            } else {
                activity.stopService(Intent(activity, MediaProjectionForegroundService::class.java))
            }
        }

    // 屏幕声音捕获（AudioPlaybackCapture）按官方要求需持有 RECORD_AUDIO 权限，
    // 在发起 MediaProjection 授权前一并请求，拒绝时明确提示而非静默失败。
    private val recordAudioPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchScreenCapture()
            } else {
                ToastUtils.showShortToast(activity, "缺少录音权限，无法捕获屏幕声音")
            }
        }

    fun processPendingScreenCapture() {
        if (pendingScreenCapture == null) return
        try {
            val onForegroundReady: () -> Unit = { handleScreenCaptureReady() }
            registeredOnForegroundReady = onForegroundReady
            MediaProjectionForegroundService.onForegroundReady = onForegroundReady
            activity.startForegroundService(Intent(activity, MediaProjectionForegroundService::class.java))
        } catch (e: Exception) {
            Logger.e("NotifyRelay", "屏幕捕获前台服务启动失败，带回前台重试", e)
            bringActivityToFront()
        }
    }

    /**
     * 将宿主 Activity 带回前台（原 MainActivity.bringMainActivityToFront，随本协调器迁入）。
     */
    private fun bringActivityToFront() {
        val intent =
            Intent(activity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun handleScreenCaptureReady() {
        val pending = pendingScreenCapture ?: return
        try {
            val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mpm.getMediaProjection(pending.first, pending.second)
            if (projection != null) {
                pendingScreenCapture = null
                val audioRelay = DeviceConnectionManagerSingleton.getAudioRelay(activity)
                audioRelay.player.stopSendCapture()
                NativeCore.mediaProjection?.stop()
                NativeCore.mediaProjection = projection
                projection.registerCallback(
                    object : MediaProjection.Callback() {
                        override fun onStop() {
                            // 仅当被停止的投影仍是当前投影时才清理，
                            // 避免主动 stop 旧投影时其 onStop 回调误杀新会话。
                            if (NativeCore.mediaProjection === projection) {
                                NativeCore.mediaProjection = null
                                // 投影被系统回收：完整清理（停播放/捕获、注销停止广播、停前台服务并通知远端结束）
                                audioRelay.stop()
                            }
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
                audioRelay.startPendingSend()
            } else {
                pendingScreenCapture = null
                activity.stopService(Intent(activity, MediaProjectionForegroundService::class.java))
            }
        } catch (e: Exception) {
            Logger.e("NotifyRelay", "屏幕捕获授权后启动失败", e)
            pendingScreenCapture = null
            activity.stopService(Intent(activity, MediaProjectionForegroundService::class.java))
        }
    }

    /**
     * 注册「远端请求媒体投影」回调；回调仍在主线程且仅在 STARTED 之后生效。
     */
    fun registerProjectionRequestCallback() {
        val projectionRequestCallback: () -> Unit = {
            Handler(Looper.getMainLooper()).post {
                if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    return@post
                }
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    launchScreenCapture()
                }
            }
        }
        registeredOnRequestMediaProjection = projectionRequestCallback
        DeviceConnectionManagerSingleton.getAudioRelay(activity).onRequestMediaProjection = projectionRequestCallback
    }

    /**
     * 注销本协调器注册的静态回调。用 `===` 判断，避免注销掉别人注册的回调。
     */
    fun release() {
        if (MediaProjectionForegroundService.onForegroundReady === registeredOnForegroundReady) {
            MediaProjectionForegroundService.onForegroundReady = null
        }
        registeredOnForegroundReady = null
        if (DeviceConnectionManagerSingleton.getAudioRelay(activity).onRequestMediaProjection === registeredOnRequestMediaProjection) {
            DeviceConnectionManagerSingleton.getAudioRelay(activity).onRequestMediaProjection = null
        }
        registeredOnRequestMediaProjection = null
        pendingScreenCapture = null
    }

    private fun launchScreenCapture() {
        val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
    }
}
