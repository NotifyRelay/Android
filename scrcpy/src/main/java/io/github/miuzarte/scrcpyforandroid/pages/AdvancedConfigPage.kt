package io.github.miuzarte.scrcpyforandroid.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.scaffolds.AppPageLazyColumn
import io.github.miuzarte.scrcpyforandroid.scaffolds.SuperSlide
import notifyrelay.data.config.ScrcpyPresets
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.SnackbarHostState
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
fun AdvancedConfigPage() {
    val viewModel = LocalScrcpyUiViewModel.current
    val contentPadding = LocalScrcpyPagePadding.current
    val scrollBehavior = LocalScrcpyScrollBehavior.current
    val snackbarHostState = LocalScrcpySnackbarHostState.current ?: remember { SnackbarHostState() }
    val sessionStarted = viewModel.sessionStarted
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val maxSizePresetIndex =
        presetIndexFromInputForAdvancedPage(viewModel.maxSizeInput, ScrcpyPresets.MaxSize)
    val maxFpsPresetIndex =
        presetIndexFromInputForAdvancedPage(viewModel.maxFpsInput, ScrcpyPresets.MaxFPS)
    val audioSourceItems = AUDIO_SOURCE_OPTIONS.map { it.second }
    val audioSourceIndex = AUDIO_SOURCE_OPTIONS.indexOfFirst { it.first == viewModel.audioSourcePreset }
        .let { if (it >= 0) it else 0 }
    val videoSourceItems = VIDEO_SOURCE_OPTIONS.map { it.second }
    val videoSourceIndex = VIDEO_SOURCE_OPTIONS.indexOfFirst { it.first == viewModel.videoSourcePreset }
        .let { if (it >= 0) it else 0 }
    val cameraFacingItems = CAMERA_FACING_OPTIONS.map { it.second }
    val cameraFacingIndex = CAMERA_FACING_OPTIONS.indexOfFirst { it.first == viewModel.cameraFacingPreset }
        .let { if (it >= 0) it else 0 }
    val cameraFpsPresetIndex =
        presetIndexFromInputForAdvancedPage(viewModel.cameraFpsInput, CAMERA_FPS_PRESETS)
    val videoEncoderDropdownItems = listOf("默认") + viewModel.videoEncoderOptions
    val audioEncoderDropdownItems = listOf("默认") + viewModel.audioEncoderOptions
    val videoEncoderIndex = (viewModel.videoEncoderOptions.indexOf(viewModel.videoEncoder) + 1)
        .coerceAtLeast(0)
    val audioEncoderIndex = (viewModel.audioEncoderOptions.indexOf(viewModel.audioEncoder) + 1)
        .coerceAtLeast(0)
    val cameraSizeDropdownItems = listOf("默认") + viewModel.cameraSizeOptions + listOf("自定义")
    val cameraSizeIndex = when (viewModel.cameraSizePreset) {
        "custom" -> viewModel.cameraSizeOptions.size + 1
        in viewModel.cameraSizeOptions -> viewModel.cameraSizeOptions.indexOf(viewModel.cameraSizePreset) + 1
        else -> 0
    }
    val videoEncoderEntries = videoEncoderDropdownItems.map { encoderName ->
        if (encoderName == "默认") {
            SpinnerEntry(title = encoderName)
        } else {
            val type = resolveEncoderTypeLabel(viewModel.videoEncoderTypeMap[encoderName])
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
            val type = resolveEncoderTypeLabel(viewModel.audioEncoderTypeMap[encoderName])
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
                    checked = viewModel.turnScreenOff,
                    onCheckedChange = { value ->
                        viewModel.turnScreenOff = value
                        if (value) scope.launch {
                            // github.com/Genymobile/scrcpy/issues/3376
                            // github.com/Genymobile/scrcpy/issues/4587
                            // github.com/Genymobile/scrcpy/issues/5676
                            snackbarHostState.showSnackbar("注意：大部分设备在关闭屏幕后刷新率会降低/减半")
                        }
                    },
                    enabled = !sessionStarted && !viewModel.noControl,
                )
                SuperSwitch(
                    title = "禁用控制",
                    summary = "--no-control",
                    checked = viewModel.noControl,
                    onCheckedChange = { viewModel.noControl = it },
                    enabled = !sessionStarted,
                )
                SuperSwitch(
                    title = "禁用视频",
                    summary = "--no-video",
                    checked = viewModel.noVideo,
                    onCheckedChange = { viewModel.noVideo = it },
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
                        viewModel.videoSourcePreset = VIDEO_SOURCE_OPTIONS[index].first
                    },
                    enabled = !sessionStarted,
                )
                if (viewModel.videoSourcePreset == "display") {
                    TextField(
                        value = viewModel.displayIdInput,
                        onValueChange = { viewModel.displayIdInput = it },
                        label = "--display-id",
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiSpacing.CardContent)
                            .padding(bottom = UiSpacing.CardContent),
                    )
                }
                if (viewModel.videoSourcePreset == "camera") {
                    TextField(
                        value = viewModel.cameraIdInput,
                        onValueChange = { viewModel.cameraIdInput = it },
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
                        onClick = { viewModel.refreshCameraSizesAction?.invoke() },
                        enabled = !sessionStarted,
                    )
                    SuperDropdown(
                        title = "摄像头朝向",
                        summary = "--camera-facing",
                        items = cameraFacingItems,
                        selectedIndex = cameraFacingIndex,
                        onSelectedIndexChange = { index ->
                            viewModel.cameraFacingPreset = CAMERA_FACING_OPTIONS[index].first
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
                            viewModel.cameraSizePreset =
                                when (index) {
                                    0 -> ""
                                    cameraSizeDropdownItems.lastIndex -> "custom"
                                    else -> cameraSizeDropdownItems[index]
                                }
                        },
                        enabled = !sessionStarted,
                    )
                    if (viewModel.cameraSizePreset == "custom") {
                        TextField(
                            value = viewModel.cameraSizeCustom,
                            onValueChange = { viewModel.cameraSizeCustom = it },
                            label = "--camera-size",
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = UiSpacing.CardContent)
                                .padding(bottom = UiSpacing.CardContent),
                        )
                    }
                    TextField(
                        value = viewModel.cameraArInput,
                        onValueChange = { viewModel.cameraArInput = it },
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
                            viewModel.cameraFpsInput = if (preset == 0) "" else preset.toString()
                        },
                        valueRange = 0f..CAMERA_FPS_PRESETS.lastIndex.toFloat(),
                        steps = (CAMERA_FPS_PRESETS.size - 2).coerceAtLeast(0),
                        enabled = !sessionStarted,
                        unit = "fps",
                        zeroStateText = "默认",
                        showUnitWhenZeroState = false,
                        showKeyPoints = true,
                        keyPoints = CAMERA_FPS_PRESETS.indices.map { it.toFloat() },
                        displayText = viewModel.cameraFpsInput,
                        inputHint = "0 或留空表示默认",
                        inputInitialValue = viewModel.cameraFpsInput,
                        inputFilter = { it.filter(Char::isDigit) },
                        inputValueRange = 0f..Float.MAX_VALUE,
                        onInputConfirm = {
                            val normalized = it.ifBlank { "" }
                            viewModel.cameraFpsInput = if (normalized == "0") "" else normalized
                        },
                    )
                    SuperSwitch(
                        title = "高帧率模式",
                        summary = "--camera-high-speed",
                        checked = viewModel.cameraHighSpeed,
                        onCheckedChange = { viewModel.cameraHighSpeed = it },
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
                        viewModel.audioSourcePreset = AUDIO_SOURCE_OPTIONS[index].first
                    },
                    enabled = !sessionStarted && viewModel.audioEnabled,
                )
                if (viewModel.audioSourcePreset == "custom") {
                    TextField(
                        value = viewModel.audioSourceCustom,
                        onValueChange = { viewModel.audioSourceCustom = it },
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
                    checked = viewModel.audioDup,
                    onCheckedChange = { viewModel.audioDup = it },
                    enabled = !sessionStarted && viewModel.audioEnabled,
                )
                SuperSwitch(
                    title = "仅转发不播放",
                    summary = "--no-audio-playback",
                    checked = viewModel.noAudioPlayback,
                    onCheckedChange = { viewModel.noAudioPlayback = it },
                    enabled = !sessionStarted && viewModel.audioEnabled,
                )
                SuperSwitch(
                    title = "音频失败时终止 [TODO]",
                    summary = "--require-audio",
                    checked = viewModel.requireAudio,
                    onCheckedChange = { viewModel.requireAudio = it },
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
                        viewModel.maxSizeInput = if (preset == 0) "" else preset.toString()
                    },
                    valueRange = 0f..ScrcpyPresets.MaxSize.lastIndex.toFloat(),
                    steps = (ScrcpyPresets.MaxSize.size - 2).coerceAtLeast(0),
                    enabled = !sessionStarted,
                    unit = "px",
                    zeroStateText = "关闭",
                    showUnitWhenZeroState = false,
                    showKeyPoints = true,
                    keyPoints = ScrcpyPresets.MaxSize.indices.map { it.toFloat() },
                    displayText = viewModel.maxSizeInput,
                    inputHint = "0 或留空表示关闭",
                    inputInitialValue = viewModel.maxSizeInput,
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 0f..Float.MAX_VALUE,
                    onInputConfirm = {
                        val normalized = it.ifBlank { "" }
                        viewModel.maxSizeInput = normalized
                    },
                )
                SuperSlide(
                    title = "最大帧率",
                    summary = "--max-fps",
                    value = maxFpsPresetIndex.toFloat(),
                    onValueChange = { value ->
                        val idx = value.roundToInt().coerceIn(0, ScrcpyPresets.MaxFPS.lastIndex)
                        val preset = ScrcpyPresets.MaxFPS[idx]
                        viewModel.maxFpsInput = if (preset == 0) "" else preset.toString()
                    },
                    valueRange = 0f..ScrcpyPresets.MaxFPS.lastIndex.toFloat(),
                    steps = (ScrcpyPresets.MaxFPS.size - 2).coerceAtLeast(0),
                    enabled = !sessionStarted,
                    unit = "fps",
                    zeroStateText = "关闭",
                    showUnitWhenZeroState = false,
                    showKeyPoints = true,
                    keyPoints = ScrcpyPresets.MaxFPS.indices.map { it.toFloat() },
                    displayText = viewModel.maxFpsInput,
                    inputHint = "0 或留空表示关闭",
                    inputInitialValue = viewModel.maxFpsInput,
                    inputFilter = { it.filter(Char::isDigit) },
                    inputValueRange = 0f..Float.MAX_VALUE,
                    onInputConfirm = {
                        val normalized = it.ifBlank { "" }
                        viewModel.maxFpsInput = normalized
                    },
                )
            }
        }

        item {
            Card {
                SuperArrow(
                    title = "重新获取编码器列表",
                    summary = "--list-encoders",
                    onClick = { viewModel.refreshEncodersAction?.invoke() },
                    enabled = !sessionStarted,
                )
                SuperSpinner(
                    title = "视频编码器",
                    summary = "--video-encoder",
                    items = videoEncoderEntries,
                    selectedIndex = videoEncoderIndex,
                    onSelectedIndexChange = { index ->
                        viewModel.videoEncoder = if (index == 0) "" else videoEncoderDropdownItems[index]
                    },
                    enabled = !sessionStarted,
                )
                TextField(
                    value = viewModel.videoCodecOptions,
                    onValueChange = { viewModel.videoCodecOptions = it },
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
                        viewModel.audioEncoder = if (index == 0) "" else audioEncoderDropdownItems[index]
                    },
                    enabled = !sessionStarted && viewModel.audioEnabled,
                )
                TextField(
                    value = viewModel.audioCodecOptions,
                    onValueChange = { viewModel.audioCodecOptions = it },
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
                        value = viewModel.newDisplayWidth,
                        onValueChange = { viewModel.newDisplayWidth = it },
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
                        value = viewModel.newDisplayHeight,
                        onValueChange = { viewModel.newDisplayHeight = it },
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
                        value = viewModel.newDisplayDpi,
                        onValueChange = { viewModel.newDisplayDpi = it },
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
                            value = viewModel.cropWidth,
                            onValueChange = { viewModel.cropWidth = it },
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
                            value = viewModel.cropHeight,
                            onValueChange = { viewModel.cropHeight = it },
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
                            value = viewModel.cropX,
                            onValueChange = { viewModel.cropX = it },
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
                            value = viewModel.cropY,
                            onValueChange = { viewModel.cropY = it },
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
