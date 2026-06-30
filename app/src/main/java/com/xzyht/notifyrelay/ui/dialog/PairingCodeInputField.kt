package com.xzyht.notifyrelay.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 6 位配对码输入组件。
 * 显示 6 个带边框的数字格，隐藏输入框弹出数字键盘。
 *
 * @param code 当前输入的配对码
 * @param onCodeChange 输入变化回调
 * @param errorMsg 错误提示信息
 * @param enabled 是否允许输入
 */
@Composable
fun PairingCodeInputField(
    code: String,
    onCodeChange: (String) -> Unit,
    errorMsg: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colorScheme = MiuixTheme.colorScheme
    val borderColor = colorScheme.outline
    val inputShape = RoundedCornerShape(8.dp)

    val codeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { codeFocusRequester.requestFocus() }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        for (i in 0 until 6) {
            val digit = if (i < code.length) code[i].toString() else ""
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(48.dp)
                    .border(
                        width = 1.dp,
                        color = if (i == code.length && enabled) colorScheme.primary else borderColor,
                        shape = inputShape
                    )
                    .background(
                        color = colorScheme.surfaceVariant,
                        shape = inputShape
                    )
                    .clickable(enabled) { codeFocusRequester.requestFocus() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (enabled) digit else "*",
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurface
                )
            }
            if (i < 5) Spacer(modifier = Modifier.width(8.dp))
        }
    }

    // 隐藏的输入框（弹出数字键盘）
    BasicTextField(
        value = if (enabled) code else "",
        onValueChange = { newVal ->
            if (enabled) {
                onCodeChange(newVal.filter { it.isDigit() }.take(6))
            }
        },
        modifier = Modifier
            .focusRequester(codeFocusRequester)
            .width(0.dp)
            .height(0.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        enabled = enabled
    )

    if (errorMsg != null) {
        Text(
            text = errorMsg,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
