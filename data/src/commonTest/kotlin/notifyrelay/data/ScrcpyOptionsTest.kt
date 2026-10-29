package notifyrelay.data

import notifyrelay.data.config.ScrcpyAudioOutputMode
import notifyrelay.data.config.ScrcpyOptions
import notifyrelay.data.config.ScrcpySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * scrcpy 参数串契约测试。
 *
 * 断言追加顺序与 Windows 端 `ScrcpyConfigBuilder.Build` 一致，防止参数拼装漂移。
 */
class ScrcpyOptionsTest {
    @Test
    fun defaultSettingsProduceExpectedArguments() {
        val args = ScrcpyOptions.buildArguments(ScrcpySettings(), "192.168.1.5:5555")
        // 默认值对齐 Android 端 ScrcpyDefaults：关闭屏幕默认关、码率 8.0、帧率与显示 id 留空
        assertEquals(listOf("-s", "192.168.1.5:5555", "--video-bit-rate=8.0", "--audio-bit-rate=128"), args)
    }

    @Test
    fun audioOnlyModeShortCircuitsVideoOptions() {
        val args =
            ScrcpyOptions.buildArguments(
                ScrcpySettings(disableVideoForwarding = true),
                "serial",
            )
        assertEquals(listOf("-s", "serial", "--no-video", "--audio-source=playback", "--audio-dup", "--audio-bit-rate=128"), args)
        assertTrue(args.none { it.startsWith("--max-size") })
    }

    @Test
    fun audioOutputModeMapsToFlags() {
        val remote = ScrcpyOptions.buildArguments(ScrcpySettings(audioOutputMode = ScrcpyAudioOutputMode.REMOTE), "s")
        assertTrue(remote.contains("--no-audio"))
        val both = ScrcpyOptions.buildArguments(ScrcpySettings(audioOutputMode = ScrcpyAudioOutputMode.BOTH), "s")
        assertTrue(both.contains("--audio-dup"))
        val desktop = ScrcpyOptions.buildArguments(ScrcpySettings(audioOutputMode = ScrcpyAudioOutputMode.DESKTOP), "s")
        assertTrue(desktop.none { it == "--no-audio" || it == "--audio-dup" })
    }

    @Test
    fun startAppIsPlacedBeforeOtherArguments() {
        val args = ScrcpyOptions.buildArguments(ScrcpySettings(), "serial", startApp = "com.example")
        assertEquals("--start-app=com.example", args[2])
    }

    @Test
    fun codecOptionExpandsIntoSeparateArguments() {
        // 下标 1 对应 h264 + OMX.qcom 编码器，应展开为两个独立参数
        val args = ScrcpyOptions.buildArguments(ScrcpySettings(videoCodec = 1), "serial")
        assertTrue(args.contains("--video-codec=h264"))
        assertTrue(args.contains("--video-encoder=OMX.qcom.video.encoder.avc"))
    }

    @Test
    fun virtualDisplayAddsNewDisplayArgument() {
        val args = ScrcpyOptions.buildArguments(ScrcpySettings(virtualDisplaySize = "1920x1080"), "serial")
        assertTrue(args.contains("--new-display=1920x1080"))
    }
}
