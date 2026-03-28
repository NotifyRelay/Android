package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SuperSlide(
    title: String,
    summary: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    unit: String = "",
    zeroStateText: String? = null,
    showUnitWhenZeroState: Boolean = false,
    showKeyPoints: Boolean = false,
    keyPoints: List<Float> = emptyList(),
    displayFormatter: (Float) -> String = { it.toInt().toString() },
    displayText: String? = null,
    inputTitle: String = title,
    inputHint: String = unit,
    inputInitialValue: String = displayFormatter(value),
    inputFilter: (String) -> String = { text -> text.filter { it.isDigit() || it == '.' } },
    inputValueRange: ClosedFloatingPointRange<Float>? = null,
    onInputConfirm: (String) -> Unit,
) {
    var showInputDialog by remember { mutableStateOf(false) }
    var holdArrow by remember { mutableStateOf(false) }

    SuperArrow(
        title = title,
        summary = summary,
        onClick = {
            showInputDialog = true
            holdArrow = true
        },
        holdDownState = holdArrow,
        endActions = {
            val isZeroState = value == 0f && zeroStateText != null
            val valueText =
                if (isZeroState) zeroStateText else (displayText ?: displayFormatter(value))
            val shouldShowUnit = unit.isNotBlank() && (!isZeroState || showUnitWhenZeroState)
            val text = if (shouldShowUnit) "$valueText $unit" else valueText
            Text(
                text = text,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        enabled = enabled,
        bottomAction = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                showKeyPoints = showKeyPoints,
                keyPoints = keyPoints,
                enabled = enabled,
            )
        },
    )

    if (showInputDialog) {
        var valueText by remember(inputInitialValue) { mutableStateOf(inputInitialValue) }
        val activeInputRange = inputValueRange ?: valueRange
        SuperDialog(
            show = true,
            onDismissRequest = {
                showInputDialog = false
                holdArrow = false
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = inputTitle)
            }
            TextField(
                value = valueText,
                onValueChange = { valueText = inputFilter(it) },
                label = inputHint,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = UiSpacing.Large),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = UiSpacing.Large),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
            ) {
                TextButton(
                    text = "取消",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showInputDialog = false
                        holdArrow = false
                    },
                )
                TextButton(
                    text = "确定",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val inputValue = valueText.trim().toFloatOrNull()
                        if (inputValue != null && inputValue >= activeInputRange.start && inputValue <= activeInputRange.endInclusive) {
                            onInputConfirm(valueText.trim())
                            showInputDialog = false
                            holdArrow = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
