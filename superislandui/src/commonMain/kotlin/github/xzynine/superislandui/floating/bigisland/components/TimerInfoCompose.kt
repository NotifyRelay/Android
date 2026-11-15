package github.xzynine.superislandui.floating.bigisland.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.floating.common.rememberTimerText
import github.xzynine.superislandui.model.components.TimerInfo
import top.yukonga.miuix.kmp.basic.Text

/**
 * 计时器信息Compose组件（简化）
 */
@Composable
fun TimerInfoCompose(
    timerInfo: TimerInfo,
    picMap: Map<String, String>? = null,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = rememberTimerText(timerInfo),
            fontSize = 18.sp,
            color = Color.White,
        )
    }
}
