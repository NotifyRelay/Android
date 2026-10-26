package com.xzyht.notifyrelay.ui.port

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.xzyht.notifyrelay.R
import notifyrelay.shared.ui.guide.GuideResources
import notifyrelay.shared.ui.guide.LocalGuideResources

/** 为 [content] 提供引导页资源。 */
@Composable
fun ProvideGuideResources(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val resources =
        GuideResources(
            readRawText = { name ->
                val id =
                    if (name == "permission_notes") R.raw.permission_notes else 0
                if (id == 0) {
                    null
                } else {
                    runCatching {
                        context.resources
                            .openRawResource(id)
                            .bufferedReader()
                            .use { it.readText() }
                    }.getOrNull()
                }
            },
            drawablePainter = { name ->
                val id = if (name == "guide_icon_arrow") R.drawable.guide_icon_arrow else 0
                if (id == 0) null else painterResource(id)
            },
        )
    CompositionLocalProvider(LocalGuideResources provides resources, content = content)
}
