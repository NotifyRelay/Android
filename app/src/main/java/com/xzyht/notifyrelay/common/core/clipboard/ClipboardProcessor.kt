package com.xzyht.notifyrelay.common.core.clipboard

import android.content.Context
import com.xzyht.notifyrelay.common.core.util.Logger
import com.xzyht.notifyrelay.feature.clipboard.ClipboardSyncManager

/**
 * 剪贴板消息处理器
 * 负责处理接收到的剪贴板消息
 */
object ClipboardProcessor {
    private const val TAG = "ClipboardProcessor"
    
    /**
     * 处理剪贴板消息的输入数据
     */
    data class ClipboardInput(
        val header: String,
        val rawData: String,
        val sharedSecret: String,
        val remoteUuid: String
    )
    
    /**
     * 处理接收到的剪贴板消息
     *
     * @param context 上下文
     * @param input 剪贴板消息输入
     * @return 是否成功处理
     */
    fun process(
        context: Context,
        input: ClipboardInput
    ): Boolean {
        try {
            Logger.d(TAG, "Processing clipboard message: ${input.header}")
            
            // 调用 ClipboardSyncManager 处理剪贴板消息
            ClipboardSyncManager.handleClipboardMessage(input.rawData, context)
            
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to process clipboard message", e)
            return false
        }
    }
}