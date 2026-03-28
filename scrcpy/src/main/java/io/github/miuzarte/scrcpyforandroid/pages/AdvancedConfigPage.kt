package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.miuzarte.scrcpyforandroid.constants.ScrcpyPresets
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlide
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSpinner
import top.yukonga.miuix.kmp.extra.SuperSwitch
import kotlin.math.roundToInt

private val AUDIO_SOURCE_OPTIONS = listOf(
    "output" to "output",
    "playback" to "playback",
    "mic" to "mic",
    "mic-unprocessed" to "mic-unprocessed",
    "mic-camcorder" to "mic-camcorder",
    "mic-voice-recognition" to "mic-voice-recognition",
    "mic-voice-communication" to "mic-voice-communication",
    "voice-call" to "voice-call",
    "voice-call-uplink" to "voice-call-uplink",
    "voice-call-downlink" to "voice-call-downlink",
    "voice-performance" to "voice-performance",
    "custom" to "自定义",
)

private val VIDEO_SOURCE_OPTIONS = listOf(
    "display" to "display",
    "camera" to "camera",
)

private val CAMERA_FACING_OPTIONS = listOf(
    "" to "默认",
    "front" to "front",
    "back" to "back",
    "external" to "external",
)

private val CAMERA_FPS_PRESETS = listOf(0, 10, 15, 24, 30, 60, 120, 240, 480, 960)

@Composable
internal fun AdvancedConfigPage(
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    snackbarHostState: SnackbarHostState,
    sessionStarted: Boolean,
    audioEnabled: Boolean,
    noControl: Boolean,
    onNoControlChange: (Boolean) -> Unit,
    audioDup: Boolean,
    onAudioDupChange: (Boolean) -> Unit,
    audioSourcePreset: String,
    onAudioSourcePresetChange: (String) -> Unit,
    audioSourceCustom: String,
    onAudioSourceCustomChange: (String) -> Unit,
    videoSourcePreset: String,
    onVideoSourcePresetChange: (String) -> Unit,
    cameraIdInput: String,
    onCameraIdInputChange: (String) -> Unit,
    cameraFacingPreset: String,
    onCameraFacingPresetChange: (String) -> Unit,
    cameraSizePreset: String,
    onCameraSizePresetChange: (String) -> Unit,
    cameraSizeCustom: String,
    onCameraSizeCustomChange: (String) -> Unit,
    cameraSizeDropdownItems: List<String>,
    cameraSizeIndex: Int,
    cameraArInput: String,
    onCameraArInputChange: (String) -> Unit,
    cameraFpsInput: String,
    onCameraFpsInputChange: (String) -> Unit,
    cameraHighSpeed: Boolean,
    onCameraHighSpeedChange: (Boolean) -> Unit,
    noAudioPlayback: Boolean,
    onNoAudioPlaybackChange: (Boolean) -> Unit,
    noVideo: Boolean,
    onNoVideoChange: (Boolean) -> Unit,
    requireAudio: Boolean,
    onRequireAudioChange: (Boolean) -> Unit,
    turnScreenOff: Boolean,
    onTurnScreenOffChange: (Boolean) -> Unit,
    maxSizeInput: String,
    onMaxSizeInputChange: (String) -> Unit,
    maxFpsInput: String,
    onMaxFpsInputChange: (String) -> Unit,
    videoEncoderDropdownItems: List<String>,
    videoEncoderTypeMap: Map<String, String>,
    videoEncoderIndex: Int,
    onVideoEncoderChange: (String) -> Unit,
    videoCodecOptions: String,
    onVideoCodecOptionsChange: (String) -> Unit,
    audioEncoderDropdownItems: List<String>,
    audioEncoderTypeMap: Map<String, String>,
    audioEncoderIndex: Int,
    onAudioEncoderChange: (String) -> Unit,
    audioCodecOptions: String,
    onAudioCodecOptionsChange: (String) -> Unit,
    onRefreshEncoders: () -> Unit,
    onRefreshCameraSizes: () -> Unit,
    newDisplayWidth: String,
    onNewDisplayWidthChange: (String) -> Unit,
    newDisplayHeight: String,
    onNewDisplayHeightChange: (String) -> Unit,
    newDisplayDpi: String,
    onNewDisplayDpiChange: (String) -> Unit,
    displayIdInput: String,
    onDisplayIdInputChange: (String) -> Unit,
    cropWidth: String,
    onCropWidthChange: (String) -> Unit,
    cropHeight: String,
    onCropHeightChange: (String) -> Unit,
    cropX: String,
    onCropXChange: (String) -> Unit,
    cropY: String,
    onCropYChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val maxSizePresetIndex =
        presetIndexFromInputForAdvancedPage(maxSizeInput, ScrcpyPresets.MaxSize)
    val maxFpsPresetIndex = presetIndexFromInputForAdvancedPage(maxFpsInput, ScrcpyPresets.MaxFPS)
    val audioSourceItems = AUDIO_SOURCE_OPTIONS.map { it.second }
    val audioSourceIndex = AUDIO_SOURCE_OPTIONS.indexOfFirst { it.first == audioSourcePreset }
        .let { if (it >= 0) it else 0 }
    val videoSourceItems = VIDEO_SOURCE_OPTIONS.map { it.second }
    val videoSourceIndex = VIDEO_SOURCE_OPTIONS.indexOfFirst { it.first == videoSourcePreset }
        .let { if (it >= 0) it else 0 }
    val cameraFacingItems = CAMERA_FACING_OPTIONS.map { it.second }
    val cameraFacingIndex = CAMERA_FACING_OPTIONS.indexOfFirst { it.first == cameraFacingPreset }
        .let { if (it >= 0) it else 0 }
    val cameraFpsPresetIndex =
        presetIndexFromInputForAdvancedPage(cameraFpsInput, CAMERA_FPS_PRESETS)
    val videoEncoderEntries = videoEncoderDropdownItems.map { encoderName ->
        if (encoderName == "默认") {
            SpinnerEntry(title = encoderName)
        } else {
            val type = resolveEncoderTypeLabel(videoEncoderTypeMap[encoderName])
            SpinnerEntry(
                title = encoderName,
                summary = type.ifBlank { null },
            )
        }
    }
    val audioEncoderEntries = audioEncoderDropdownItems.map { encoderName ->
        if (encoderName == "默认") {
            SpinnerEntry(title = encoderName)
        } else {
            val type = resolveEncoderTypeLabel(audioEncoderTypeMap[encoderName])
            SpinnerEntry(
                title = encoderName,
                summary = type.ifBlank { null },
            )
        }
    }

    // 高级参数
    AppPageLazyColumn(
        contentPadding = contentPadding,
        scrollBehavior = scrollBehavior,
    ) {
        item {
            Card {
                SuperSwitch(
                    title = "启动后关闭屏幕",
                    summary = "--turn-screen-off",
                    checked = turnScreenOff,
                    onCheckedChange = { value ->
                        onTurnScreenOffChange(value)
                        if (value) scope.launch {
                            // github.com/Genymobile/scrcpy/issues/3376
                            // github.com/Genymobile/scrcpy/issues/4587
                            // github.com/Genymobile/scrcpy/issues/5676
                            snackbarHostState.showSnackbar("注意：大部分设备在关闭屏幕后刷新率会降低/减半")
                        }
                    },
                    enabled = !sessionStarted && !noControl,
                )
                SuperSwitch(
                    title = "禁用控制",
                    summary = "--no-control",
                    checked = noControl,
                    onCheckedChange = onNoControlChange,
                    enabled = !sessionStarted,
                )
                SuperSwitch(
                    title = "禁用视频",
                    summary = "--no-video",
                    checked = noVideo,
                    onCheckedChange = onNoVideoChange,
                    enabled = !sessionStarted,
                )
            }
        }

        item {
            Card {
                SuperDropdown(
                    title = "视频来源",
                    summary = "--video-source",
                    items = videoSourceItems,
                    selectedIndex = videoSourceIndex,
                    onSelectedIndexChange = { index ->
                        onVideoSourcePresetChange(VIDEO_SOURCE_OPTIONS[index].first)
                    },
                    enabled = !sessionStarted,
                )
                if (videoSourcePreset == "display") {
                    TextField(
                        value = displayIdInput,
                        onValueChange = onDisplayIdInputChange,
                        label = "--display-id",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.CardContent)
                            .padding(bottom = UiSpacing.CardContent),
                    )
                }
                if (videoSourcePreset == "camera") {
                    TextField(
                        value = cameraIdInput,
                        onValueChange = onCameraIdInputChange,
                        label = "--camera-id",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.CardContent)
                            .padding(bottom = UiSpacing.CardContent),
                    )
                    SuperArrow(
                        title = "重新获取 Camera Sizes",
                        summary = "--list-camera-sizes",
                        onClick = onRefreshCameraSizes,
                        enabled = !sessionStarted,
                    )
                    SuperDropdown(
                        title = "摄像头朝向",
                        summary = "--camera-facing",
                        items = cameraFacingItems,
                        selectedIndex = cameraFacingIndex,
                        onSelectedIndexChange = { index ->
                            onCameraFacingPresetChange(CAMERA_FACING_OPTIONS[index].first)
                        },
                        enabled = !sessionStarted,
                    )
                    SuperDropdown(
                        title = "摄像头分辨率",
                        summary = "--camera-size",
                        items = cameraSizeDropdownItems,
                        selectedIndex = cameraSizeIndex.coerceIn(
                            0,
                            (cameraSizeDropdownItems.size - 1).coerceAtLeast(0)
                        ),
                        onSelectedIndexChange = { index ->
                            onCameraSizePresetChange(
                                when (index) {
                                    0 -> ""
                                    cameraSizeDropdownItems.lastIndex -> "custom"
                                    else -> cameraSizeDropdownItems[index]
                                },
                            )
                        },
                        enabled = !sessionStarted,
                    )
                    if (cameraSizePreset == "custom") {
                        TextField(
                            value = cameraSizeCustom,
                            onValueChange = onCameraSizeCustomChange,
                            label = "--camera-size",
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = UiSpacing.CardContent)
                                .padding(bottom = UiSpacing.CardContent),
                        )
                    }
                    TextField(
                        value = cameraArInput,
                        onValueChange = onCameraArInputChange,
                        label = "--camera-ar",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.CardContent)
                            .padding(bottom = UiSpacing.CardContent),
                    )
                    SuperSlide(
                        title = "摄像头帧率",
                        summary = "--camera-fps",
                        value = cameraFpsPresetIndex.toFloat(),
                        onValueChange = { value ->
                            val idx = value.roundToInt().coerceIn(0, CAMERA_FPS_PRESETS.lastIndex)
                            val preset = CAMERA_FPS_PRESETS[idx]
                            onCameraFpsInputChange(if (preset == 0) "" else preset.toString())
                        },
                        valueRange = 0f..CAMERA_FPS_PRESETS.lastIndex.toFloat(),
                        steps = (CAMERA_FPS_PRESETS.size - 2).coerceAtLeast(0),
                        enabled = !sessionStarted,
                        unit = "fps",
                        zeroStateText = "默认",
                        showUnitWhenZeroState = false,
                        showKeyPoints = true,
                        keyPoints = CAMERA_FPS_PRESETS.indices.map { it.toFloat() },
                        displayText = cameraFpsInput,
                        inputHint = "0 或留空表示默认",
                        inputInitialValue = cameraFpsInput,
                        inputFilter = { it.filter(Char::isDigit) },
                        inputValueRange = 0f..Float.MAX_VALUE,
                        onInputConfirm = {
                            val normalized = it.ifBlank { "" }
                            onCameraFpsInputChange(if (normalized == "0") "" else normalized)
                        },
                    )
                    SuperSwitch(
                        title = "高帧率模式",
                        summary = "--camera-high-speed",
                        checked = cameraHighSpeed,
                        onCheckedChange = onCameraHighSpeedChange,
                        enabled = !sessionStarted,
                    )
                }
            }
        }

        item {
            Card {
                SuperDropdown(
                    title = "音频来源",
                    summary = "--audio-source",
                    items = audioSourceItems,
                    selectedIndex = audioSourceIndex,
                    onSelectedIndexChange = { index ->
                        onAudioSourcePresetChange(AUDIO_SOURCE_OPTIONS[index].first)
                    },
                    enabled = !sessionStarted && audioEnabled,
                )
                if (audioSourcePreset == "custom") {
                    TextField(
                        value = audioSourceCustom,
                        onValueChange = onAudioSourceCustomChange,
                        label = "--audio-source",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.CardContent)
                            .padding(bottom = UiSpacing.CardContent),
                    )
                }
                SuperSwitch(
                    title = "音频双路输出",
                    summary = "--audio-dup",
                    checked = audioDup,
                    onCheckedChange = onAudioDupChange,
                    enabled = !sessionStarted && audioEnabled,
                )
                SuperSwitch(
                    title = "仅转发不播放",
                    summary = "--no-audio-playback",
                    checked = noAudioPlayback,
                    onCheckedChange = onNoAudioPlaybackChange,
                    enabled = !sessionStarted && audioEnabled,
                )
                SuperSwitch(
                    title = "音频失败时终止 [TODO]",
                    summary = "--require-audio",
                    checked = requireAudio,
                    onCheckedChange = onRequireAudioChange,
                    enabled = false,
                )
            }
        }

        item {
            Card {
                SuperSlide(
                    title = "最大分辨率",
                    summary = "--max-size",
                    value = maxSizePresetIndex.toFloat(),
                    onValueChange = { value ->
                        val idx = value.roundToInt().coerceIn(0, ScrcpyPresets.MaxSize.lastIndex)
                        val preset = ScrcpyPresets.MaxSize[idx]
                        onMaxSizeInputChange(if (preset == 0) "" else preset.toString())
                    },
                    valueRange = 0f..ScrcpyPresets.MaxSize.lastIndex.toFloat(),
                    steps = (ScrcpyPresets.MaxSize.size - 2).coerceAtLeast(0),
                    enabled = !sessionStarted,
                    unit = "px",
                    zeroStateText = "关闭",
                    showUnitWhenZeroState = false,
                    showKeyPoints = true,
                    keyPoints = ScrcpyPresets.MaxSize.indices.map { it.toFloat() },
                    displayText = maxSizeInput,
                    inputHint = "0 或留空表示关闭",
                    inputInitialValue = maxSizeInput,
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 0f..Float.MAX_VALUE,
                    onInputConfirm = {
                        val normalized = it.ifBlank { "" }
                        onMaxSizeInputChange(normalized)
                    },
                )
                SuperSlide(
                    title = "最大帧率",
                    summary = "--max-fps",
                    value = maxFpsPresetIndex.toFloat(),
                    onValueChange = { value ->
                        val idx = value.roundToInt().coerceIn(0, ScrcpyPresets.MaxFPS.lastIndex)
                        val preset = ScrcpyPresets.MaxFPS[idx]
                        onMaxFpsInputChange(if (preset == 0) "" else preset.toString())
                    },
                    valueRange = 0f..ScrcpyPresets.MaxFPS.lastIndex.toFloat(),
                    steps = (ScrcpyPresets.MaxFPS.size - 2).coerceAtLeast(0),
                    enabled = !sessionStarted,
                    unit = "fps",
                    zeroStateText = "关闭",
                    showUnitWhenZeroState = false,
                    showKeyPoints = true,
                    keyPoints = ScrcpyPresets.MaxFPS.indices.map { it.toFloat() },
                    displayText = maxFpsInput,
                    inputHint = "0 或留空表示关闭",
                    inputInitialValue = maxFpsInput,
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 0f..Float.MAX_VALUE,
                    onInputConfirm = {
                        val normalized = it.ifBlank { "" }
                        onMaxFpsInputChange(normalized)
                    },
                )
            }
        }

        item {
            Card {
                SuperArrow(
                    title = "重新获取编码器列表",
                    summary = "--list-encoders",
                    onClick = onRefreshEncoders,
                    enabled = !sessionStarted,
                )
                SuperSpinner(
                    title = "视频编码器",
                    summary = "--video-encoder",
                    items = videoEncoderEntries,
                    selectedIndex = videoEncoderIndex,
                    onSelectedIndexChange = { index ->
                        onVideoEncoderChange(if (index == 0) "" else videoEncoderDropdownItems[index])
                    },
                    enabled = !sessionStarted,
                )
                TextField(
                    value = videoCodecOptions,
                    onValueChange = onVideoCodecOptionsChange,
                    label = "--video-codec-options",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                )
                SuperSpinner(
                    title = "音频编码器",
                    summary = "--audio-encoder",
                    items = audioEncoderEntries,
                    selectedIndex = audioEncoderIndex,
                    onSelectedIndexChange = { index ->
                        onAudioEncoderChange(if (index == 0) "" else audioEncoderDropdownItems[index])
                    },
                    enabled = !sessionStarted && audioEnabled,
                )
                TextField(
                    value = audioCodecOptions,
                    onValueChange = onAudioCodecOptionsChange,
                    label = "--audio-codec-options",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                )
            }
        }

        item {
            Card {
                Text(
                    text = "--new-display",
                    modifier = Modifier
                        .padding(horizontal = UiSpacing.CardTitle)
                        .padding(
                            top = UiSpacing.CardContent,
                            bottom = UiSpacing.FieldLabelBottom,
                        ),
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                ) {
                    TextField(
                        value = newDisplayWidth,
                        onValueChange = onNewDisplayWidthChange,
                        label = "width",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        value = newDisplayHeight,
                        onValueChange = onNewDisplayHeightChange,
                        label = "height",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        value = newDisplayDpi,
                        onValueChange = onNewDisplayDpiChange,
                        label = "dpi",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Card {
                Text(
                    text = "--crop",
                    modifier = Modifier
                        .padding(horizontal = UiSpacing.CardTitle)
                        .padding(
                            top = UiSpacing.CardContent,
                            bottom = UiSpacing.FieldLabelBottom,
                        ),
                    fontWeight = FontWeight.Medium,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardContent)
                        .padding(bottom = UiSpacing.CardContent),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        TextField(
                            value = cropWidth,
                            onValueChange = onCropWidthChange,
                            label = "width",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        TextField(
                            value = cropHeight,
                            onValueChange = onCropHeightChange,
                            label = "height",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
                    ) {
                        TextField(
                            value = cropX,
                            onValueChange = onCropXChange,
                            label = "x",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        TextField(
                            value = cropY,
                            onValueChange = onCropYChange,
                            label = "y",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // TODO: 放进 [AppPageLazyColumn] 里
        item { Spacer(Modifier.height(UiSpacing.BottomContent)) }
    }
}

private fun presetIndexFromInputForAdvancedPage(raw: String, presets: List<Int>): Int {
    if (raw.isBlank()) return 0
    val value = raw.toIntOrNull() ?: return 0
    val exact = presets.indexOf(value)
    if (exact >= 0) return exact
    val nearest = presets.withIndex().minByOrNull { (_, preset) -> kotlin.math.abs(preset - value) }
    return nearest?.index ?: 0
}

private fun resolveEncoderTypeLabel(raw: String?): String {
    return when (raw?.trim()?.lowercase()) {
        "hw" -> "hw"
        "sw" -> "sw"
        else -> ""
    }
}
