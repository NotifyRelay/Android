package io.github.miuzarte.scrcpyforandroid.constants

object AppDefaults {
    const val EVENT_LOG_LINES = 512
    const val ADB_PORT = 5555

    // Devices
    const val QUICK_CONNECT_INPUT = ""

    const val PAIR_HOST = ""
    const val PAIR_PORT = ""
    const val PAIR_CODE = ""

    const val AUDIO_ENABLED = true
    const val AUDIO_CODEC = "opus"
    const val AUDIO_BIT_RATE_KBPS = 128
    const val AUDIO_BIT_RATE_INPUT = "128"
    const val VIDEO_CODEC = "h264"
    const val VIDEO_BIT_RATE_MBPS = 8f
    const val VIDEO_BIT_RATE_INPUT = "8.0"

    const val TURN_SCREEN_OFF = false
    const val NO_CONTROL = false
    const val NO_VIDEO = false

    const val VIDEO_SOURCE_PRESET = "display"
    const val DISPLAY_ID = ""

    const val CAMERA_ID = ""
    const val CAMERA_FACING_PRESET = ""
    const val CAMERA_SIZE_PRESET = ""
    const val CAMERA_SIZE_CUSTOM = ""
    const val CAMERA_AR = ""
    const val CAMERA_FPS = ""
    const val CAMERA_HIGH_SPEED = false

    const val AUDIO_SOURCE_PRESET = "output"
    const val AUDIO_SOURCE_CUSTOM = ""
    const val AUDIO_DUP = false
    const val NO_AUDIO_PLAYBACK = false
    const val REQUIRE_AUDIO = false

    const val MAX_SIZE_INPUT = ""
    const val MAX_FPS_INPUT = ""

    const val VIDEO_ENCODER = ""
    const val VIDEO_CODEC_OPTION = ""
    const val AUDIO_ENCODER = ""
    const val AUDIO_CODEC_OPTION = ""

    const val NEW_DISPLAY_WIDTH = ""
    const val NEW_DISPLAY_HEIGHT = ""
    const val NEW_DISPLAY_DPI = ""

    const val CROP_WIDTH = ""
    const val CROP_HEIGHT = ""
    const val CROP_X = ""
    const val CROP_Y = ""

    // Settings
    const val THEME_BASE_INDEX = 0
    const val MONET = false

    const val FULLSCREEN_DEBUG_INFO = false
    const val SHOW_FULLSCREEN_VIRTUAL_BUTTONS = true
    const val KEEP_SCREEN_ON_WHEN_STREAMING = false
    const val DEVICE_PREVIEW_CARD_HEIGHT_DP = 320
    const val PREVIEW_VIRTUAL_BUTTON_SHOW_TEXT = true
    const val VIRTUAL_BUTTONS_LAYOUT =
        "more:1,app_switch:1,home:0,back:1,menu:0,notification:0,volume_up:0,volume_down:0,volume_mute:0,power:0,screenshot:0"

    const val CUSTOM_SERVER_URI = ""

    const val SERVER_REMOTE_PATH = "/data/local/tmp/scrcpy-server.jar"
    const val SERVER_REMOTE_PATH_INPUT = ""

    const val ADB_KEY_NAME = "scrcpy"
    const val ADB_KEY_NAME_INPUT = ""
    const val ADB_PAIRING_AUTO_DISCOVER_ON_DIALOG_OPEN = true
    const val ADB_AUTO_RECONNECT_PAIRED_DEVICE = true
    const val ADB_MDNS_LAN_DISCOVERY = true
}
