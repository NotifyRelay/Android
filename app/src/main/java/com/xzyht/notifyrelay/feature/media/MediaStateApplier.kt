package com.xzyht.notifyrelay.feature.media

import android.content.Context
import com.xzyht.notifyrelay.feature.notification.superisland.media.MediaCapsulePresenter
import com.xzyht.notifyrelay.feature.notification.superisland.store.SuperIslandSourceStore
import github.xzynine.superislandui.diff.DiffSystem
import github.xzynine.superislandui.model.components.MediaProgress
import github.xzynine.superislandui.model.components.MediaSessionData
import notifyrelay.domain.feature.media.MediaSessionSnapshot

/**
 * 会话的展示模型：端口与映射流共用同一份转换，避免两处各写一遍字段映射。
 */
internal fun MediaSessionData.toSnapshot(): MediaSessionSnapshot =
    MediaSessionSnapshot(
        packageName = packageName,
        title = title,
        artist = text,
        coverUrl = coverUrl,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
        sampledAtMs = sampledAtMs,
    )

/**
 * 取会话的进度载荷；无总时长或位置未知时返回 null。
 *
 * 显示侧按 [MediaProgress.sampledAtMs] 起算本地推进，故这里只透传采样值。
 */
internal fun MediaSessionData.toMediaProgress(): MediaProgress? {
    val position = positionMs ?: return null
    val total = durationMs?.takeIf { it > 0L } ?: return null
    return MediaProgress(
        positionMs = position,
        durationMs = total,
        isPlaying = isPlaying,
        sampledAtMs = sampledAtMs,
    )
}

/**
 * 媒体全量状态的构建与下发。
 *
 * 按 plan.md「步骤 4」从 [RemoteMediaSessionManager] 抽离。
 * 纯构造 + 写 Store + 调 [MediaCapsulePresenter.show]，无自有状态。
 *
 * 注释 452/468 说明「Rust 合并引擎已输出全量，本地无需 diff」—— 不引入 diff 逻辑。
 * [buildMediaState] 中 pics 的 key `miui.focus.pic_cover` 是超级岛契约，不可改名。
 */
internal object MediaStateApplier {
    // 构建媒体全量状态（Rust 合并引擎已输出全量，本地无需 diff）
    fun buildMediaState(
        title: String,
        text: String,
        coverUrl: String?,
    ): DiffSystem.State {
        val currentPics = mutableMapOf<String, String>()
        if (!coverUrl.isNullOrBlank()) currentPics["miui.focus.pic_cover"] = coverUrl
        return DiffSystem.State(
            title,
            text,
            MediaCapsulePresenter.buildParamV2(title, text),
            currentPics,
        )
    }

    // 直接以全量状态更新浮窗（Rust 合并引擎已输出全量，本地无需差异合并）
    fun applyMediaSessionState(
        sourceKey: String,
        currentState: DiffSystem.State,
        appName: String?,
        context: Context,
        mediaProgress: MediaProgress? = null,
    ) {
        // 以全量形式写入远端存储，保持 store 语义（结束包/清理时仍可移除）
        SuperIslandSourceStore.applyIncoming(
            sourceKey,
            currentState.title,
            currentState.text,
            currentState.paramV2Raw,
            currentState.pics,
        )
        MediaCapsulePresenter.show(
            context = context,
            sourceId = sourceKey,
            title = currentState.title ?: "",
            text = currentState.text ?: "",
            appName = appName,
            picMap = currentState.pics,
            mediaProgress = mediaProgress,
        )
    }
}
