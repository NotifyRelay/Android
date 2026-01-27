package com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.builder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.components.ActionCompose
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.components.AnimTextInfoCompose
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.components.BaseInfoCompose
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.components.ChatInfoCompose
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.components.HighlightInfoCompose
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.components.HintInfoCompose
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.components.PicInfoCompose
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.components.TextButtonCompose
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.model.ParamV2
import com.xzyht.notifyrelay.feature.notification.superisland.floating.BigIsland.param.ParamIslandCompose
import com.xzyht.notifyrelay.common.core.util.Logger

/**
 * 超级岛Compose组件的基础接口
 */
@Composable
fun SuperIslandCompose(
    paramV2: ParamV2,
    picMap: Map<String, String>? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(8.dp)
    ) {
        Logger.d("超级岛", "SuperIslandCompose入口: paramIsland=${paramV2.paramIsland != null}, highlight=${paramV2.highlightInfo != null}, baseInfo=${paramV2.baseInfo != null}")
        
        // 根据ParamV2的类型渲染不同的Compose组件
        when {
            paramV2.paramIsland != null -> {
                Logger.d("超级岛", "SuperIslandCompose: 渲染 ParamIslandCompose")
                ParamIslandCompose(paramV2.paramIsland, actions = paramV2.actions, picMap = picMap)
            }
            paramV2.baseInfo != null -> {
                Logger.d("超级岛", "SuperIslandCompose: 渲染 BaseInfoCompose")
                BaseInfoCompose(paramV2.baseInfo, picMap = picMap)
            }
            paramV2.chatInfo != null -> {
                Logger.d("超级岛", "SuperIslandCompose: 渲染 ChatInfoCompose")
                ChatInfoCompose(paramV2, picMap = picMap)
            }
            paramV2.animTextInfo != null -> {
                Logger.d("超级岛", "SuperIslandCompose: 渲染 AnimTextInfoCompose")
                AnimTextInfoCompose(paramV2.animTextInfo, picMap = picMap)
            }
            paramV2.highlightInfo != null -> {
                Logger.d("超级岛", "SuperIslandCompose: 渲染 HighlightInfoCompose")
                HighlightInfoCompose(paramV2.highlightInfo, picMap = picMap)
            }
            paramV2.picInfo != null -> {
                Logger.d("超级岛", "SuperIslandCompose: 渲染 PicInfoCompose")
                PicInfoCompose(paramV2.picInfo, picMap = picMap)
            }
            paramV2.hintInfo != null -> {
                Logger.d("超级岛", "SuperIslandCompose: 渲染 HintInfoCompose")
                HintInfoCompose(paramV2.hintInfo, picMap = picMap)
            }
            paramV2.textButton != null -> {
                Logger.d("超级岛", "SuperIslandCompose: 渲染 TextButtonCompose")
                TextButtonCompose(paramV2.textButton, picMap = picMap)
            }
            paramV2.actions?.isNotEmpty() == true -> {
                Logger.d("超级岛", "SuperIslandCompose: 渲染 ActionCompose")
                ActionCompose(paramV2.actions, picMap)
            }
            // 其他类型的组件将在后续添加
            else -> {
                Logger.d("超级岛", "SuperIslandCompose: 渲染 DefaultSuperIslandCompose (未匹配模板)")
                // 默认组件，显示未支持的模板
                DefaultSuperIslandCompose()
            }
        }
    }
}

/**
 * 默认的超级岛组件，用于显示未支持的模板
 */
@Composable
fun DefaultSuperIslandCompose() {
    Text(
        text = "未支持的模板",
        color = Color.White,
        modifier = Modifier.fillMaxSize().padding(16.dp)
    )
}