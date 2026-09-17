package com.xzyht.notifyrelay.feature.media

import android.content.Context
import com.xzyht.notifyrelay.feature.notification.superisland.media.MediaCapsulePresenter
import com.xzyht.notifyrelay.feature.notification.superisland.store.SuperIslandRemoteStore
import github.xzynine.superislandui.diff.DiffSystem
import org.json.JSONObject

/**
 * 媒体全量状态的构建与下发。
 *
 * 按 plan.md「步骤 4」从 [RemoteMediaSessionManager] 抽离。
 * 纯构造 + 写 Store + 调 [MediaCapsulePresenter.show]，无自有状态。
 *
 * 注释 452/468 说明「Rust 合并引擎已输出全量，本地无需 diff」—— 不引入 diff 逻辑。
 * [buildMediaState] 中 pics 的 key `miui.focus.pic_cover` 是超级岛契约，不可改名。
 */
object MediaStateApplier {
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
    ) {
        // 以全量形式写入远端存储，保持 store 语义（结束包/清理时仍可移除）
        val payload =
            JSONObject().apply {
                put("title", currentState.title ?: "")
                put("text", currentState.text ?: "")
                if (!currentState.paramV2Raw.isNullOrBlank()) {
                    put("param_v2_raw", currentState.paramV2Raw)
                }
                if (currentState.pics.isNotEmpty()) {
                    put("pics", JSONObject(currentState.pics))
                }
            }
        SuperIslandRemoteStore.applyIncoming(sourceKey, payload)
        MediaCapsulePresenter.show(
            context = context,
            sourceId = sourceKey,
            title = currentState.title ?: "",
            text = currentState.text ?: "",
            appName = appName,
            picMap = currentState.pics,
        )
    }
}
