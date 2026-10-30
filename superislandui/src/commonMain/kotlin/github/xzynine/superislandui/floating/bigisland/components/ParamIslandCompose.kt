package github.xzynine.superislandui.floating.bigisland.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.superislandui.floating.common.CommonImageCompose
import github.xzynine.superislandui.model.components.ActionInfo
import github.xzynine.superislandui.model.core.ParamIsland
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/**
 * ParamIsland（摘要区）的 Compose 实现。
 *
 * 小岛区展示文本与进度，大岛区展示左/右图标与文本；
 * 验证码的复制动作由平台注入 [onCopyVerificationCode]，为空时不渲染复制按钮。
 *
 * @param paramIsland 摘要区数据
 * @param modifier 修饰符
 * @param actions 动作列表（取其中的「复制」文案）
 * @param picMap 图片键值表
 * @param onCopyVerificationCode 复制验证码回调，参数为验证码文本
 */
@Composable
fun ParamIslandCompose(
    paramIsland: ParamIsland,
    modifier: Modifier = Modifier,
    actions: List<ActionInfo>? = null,
    picMap: Map<String, String>? = null,
    onCopyVerificationCode: ((String) -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(8.dp),
    ) {
        // 小岛区
        paramIsland.smallIslandArea?.let { smallArea ->
            Column(modifier = Modifier.fillMaxWidth()) {
                smallArea.primaryText?.let {
                    Text(
                        text = it,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                smallArea.secondaryText?.let {
                    Text(
                        text = it,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                smallArea.progressInfo?.let { progressInfo ->
                    ProgressInfoCompose(progressInfo = progressInfo)
                }
            }
        }

        // 大岛区
        paramIsland.bigIslandArea?.let { bigArea ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (!bigArea.leftImage.isNullOrBlank()) {
                        CommonImageCompose(
                            picKey = bigArea.leftImage,
                            picMap = picMap,
                            size = 40.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        bigArea.primaryText?.let {
                            Text(
                                text = it,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        bigArea.secondaryText?.let {
                            Text(
                                text = it,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    if (bigArea.isVerificationCode && onCopyVerificationCode != null) {
                        val copyAction =
                            actions?.find { it.actionTitle?.contains("复制") == true }
                                ?: ActionInfo(actionTitle = "复制")
                        val code = bigArea.verificationCode ?: bigArea.primaryText
                        if (!code.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                text = copyAction.actionTitle ?: "复制",
                                onClick = { onCopyVerificationCode(code) },
                            )
                        }
                    }

                    if (!bigArea.rightImage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        CommonImageCompose(
                            picKey = bigArea.rightImage,
                            picMap = picMap,
                            size = 40.dp,
                        )
                    }
                }
            }
        }
    }
}
