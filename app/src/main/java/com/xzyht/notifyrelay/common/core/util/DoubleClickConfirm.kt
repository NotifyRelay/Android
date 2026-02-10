package com.xzyht.notifyrelay.common.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import notifyrelay.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text

/**
 * 双击确认状态接口
 * 用于管理双击确认按钮的状态
 */
interface DoubleClickConfirmState {
    /**
     * 是否处于确认状态
     */
    var isConfirming: Boolean
}

/**
 * 创建并管理双击确认状态
 * @return DoubleClickConfirmState 实例
 */
@Composable
fun rememberDoubleClickConfirm(): DoubleClickConfirmState {
    // 创建并保存状态实例
    val state = remember {
        object : DoubleClickConfirmState {
            override var isConfirming by mutableStateOf(false)
        }
    }
    
    // 监听确认状态变化，2秒后自动清除确认状态
    LaunchedEffect(state.isConfirming) {
        if (state.isConfirming) {
            delay(2000)
            if (state.isConfirming) {
                state.isConfirming = false
            }
        }
    }
    
    return state
}

/**
 * 双击确认按钮组件
 * 第一次点击进入确认状态，第二次点击执行确认操作
 * 
 * @param text 按钮默认文本
 * @param confirmText 确认状态下的按钮文本，默认为"确认?"
 * @param onClick 第一次点击时的回调
 * @param onConfirm 第二次点击确认时的回调
 * @param modifier 修饰符
 * @param colors 默认状态下的按钮颜色
 * @param confirmColors 确认状态下的按钮颜色，默认为红色
 */
@Composable
fun DoubleClickConfirmButton(
    text: String,
    confirmText: String = "确认?",
    onClick: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    confirmColors: ButtonColors = ButtonDefaults.buttonColors(color = Color.Red),
    textColor: androidx.compose.ui.graphics.Color? = null,
    confirmTextColor: androidx.compose.ui.graphics.Color? = null
) {
    val state = rememberDoubleClickConfirm()
    val context = LocalContext.current
    val colorScheme = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
    
    Button(
        onClick = {
            if (state.isConfirming) {
                // 第二次点击，执行确认操作
                onConfirm()
                state.isConfirming = false
            } else {
                // 第一次点击，显示提示并进入确认状态
                ToastUtils.showShortToast(context, "再次点击确认$text")
                onClick()
                state.isConfirming = true
            }
        },
        modifier = modifier,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = if (state.isConfirming) confirmColors else colors
    ) {
        Text(
            text = if (state.isConfirming) confirmText else text,
            color = if (state.isConfirming) {
                confirmTextColor ?: Color.White
            } else {
                textColor ?: Color.White
            },
            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2
        )
    }
}
