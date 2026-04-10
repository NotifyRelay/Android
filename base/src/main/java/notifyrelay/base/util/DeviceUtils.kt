package notifyrelay.base.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 设备相关工具类
 */
object DeviceUtils {

    /**
     * 获取本地设备显示名称
     * 优先级：1. 蓝牙名称 -> 2. Settings.Secure(bluetooth_name) -> 3. Settings.Global(device_name) -> 4. Build.MODEL/DEVICE -> 5. 兜底
     */
    fun getLocalDeviceName(context: Context): String {
        try {
            // 1. 蓝牙名称（Android 12+ 需要 BLUETOOTH_CONNECT 权限）
            try {
                val canReadBt = if (Build.VERSION.SDK_INT >= 31) {
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else true
                if (canReadBt) {
                    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                    @SuppressLint("MissingPermission")
                    val btName = bluetoothManager.adapter?.name
                    if (!btName.isNullOrEmpty()) return sanitizeDisplayName(btName)
                }
            } catch (_: Exception) {}

            // 2. Settings.Secure 中的 bluetooth_name（部分设备/ROM会放在这里）
            try {
                val s = Settings.Secure.getString(context.contentResolver, "bluetooth_name")
                if (!s.isNullOrEmpty()) return sanitizeDisplayName(s)
            } catch (_: Exception) {}

            // 3. Settings.Global 中的 device_name
            try {
                val g = Settings.Global.getString(context.contentResolver, "device_name")
                if (!g.isNullOrEmpty()) return sanitizeDisplayName(g)
            } catch (_: Exception) {}

            // 4. 设备型号/设备名作为兜底
            try {
                val model = Build.MODEL
                if (!model.isNullOrEmpty()) return sanitizeDisplayName(model)
                val device = Build.DEVICE
                if (!device.isNullOrEmpty()) return sanitizeDisplayName(device)
            } catch (_: Exception) {}
        } catch (_: Exception) {}

        return "未知设备"
    }

    /**
     * 清理设备名，去除可能的非法字符
     */
    private fun sanitizeDisplayName(name: String): String {
        // 去除空字符和控制字符
        return name.trim().replace(Regex("\\s+"), " ")
    }
}
