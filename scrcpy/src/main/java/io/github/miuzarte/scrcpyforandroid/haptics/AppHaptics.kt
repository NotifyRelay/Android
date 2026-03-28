package io.github.miuzarte.scrcpyforandroid.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun performHaptics(type: HapticFeedbackType): Unit {
    LocalHapticFeedback.current.performHapticFeedback(type)
}

@Immutable
data class AppHaptics(
    val confirm: () -> Unit,
    val contextClick: () -> Unit,
    val gestureEnd: () -> Unit,
    val gestureThresholdActivate: () -> Unit,
    val keyboardTap: () -> Unit,
    val longPress: () -> Unit,
    val reject: () -> Unit,
    val segmentFrequentTick: () -> Unit,
    val segmentTick: () -> Unit,
    val textHandleMove: () -> Unit,
    val toggleOff: () -> Unit,
    val toggleOn: () -> Unit,
    val virtualKey: () -> Unit,
)

@Composable
fun rememberAppHaptics(): AppHaptics {
    val hapticFeedback = LocalHapticFeedback.current

    val performConfirm = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    val performContextClick = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
    }
    val performGestureEnd = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
    }
    val performGestureThresholdActivate = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    val performKeyboardTan = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
    }
    val performLongPress = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val performReject = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Reject)
    }
    val performSegmentFrequentTick = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    val performSegmentTick = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }
    val performTextHandleMove = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val performToggleOff = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOff)
    }
    val performToggleOn = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
    }
    val performVirtualKey = rememberUpdatedState {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
    }

    return remember {
        AppHaptics(
            confirm = { performConfirm.value.invoke() },
            contextClick = { performContextClick.value.invoke() },
            gestureEnd = { performGestureEnd.value.invoke() },
            gestureThresholdActivate = { performGestureThresholdActivate.value.invoke() },
            keyboardTap = { performKeyboardTan.value.invoke() },
            longPress = { performLongPress.value.invoke() },
            reject = { performReject.value.invoke() },
            segmentFrequentTick = { performSegmentFrequentTick.value.invoke() },
            segmentTick = { performSegmentTick.value.invoke() },
            textHandleMove = { performTextHandleMove.value.invoke() },
            toggleOff = { performToggleOff.value.invoke() },
            toggleOn = { performToggleOn.value.invoke() },
            virtualKey = { performVirtualKey.value.invoke() },
        )
    }
}
