package notifyrelay.shared.feature.device.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import notifyrelay.shared.feature.device.model.DeviceInfo
import notifyrelay.shared.feature.device.service.contract.DevicePairingPort
import notifyrelay.shared.feature.device.service.contract.HandshakeWaiterPort
import notifyrelay.shared.log.Logger

/** 配对失败原因码：core 版本不兼容。 */
const val REASON_VERSION_MISMATCH = "version_mismatch"

/** 配对请求方（CLIENT）的流程状态。 */
data class PairingUiState(
    val displayCode: String? = null,
    val isPairing: Boolean = false,
    val errorMessage: String? = null,
    val done: Boolean = false,
    val success: Boolean = false,
    val message: String = "",
)

/**
 * 设备配对流程承载（平台无关）。
 *
 * 电量由调用方注入：Android 侧从系统电池服务取值后传入。
 */
class PairingViewModel(
    private val core: PairingCorePort,
    private val pairingPort: DevicePairingPort,
    private val handshakePort: HandshakeWaiterPort,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    /** 生成请求方配对码；核心未就绪时返回 null。 */
    fun ensureDisplayCode(): String? {
        val existing = _state.value.displayCode
        if (existing != null) return existing
        val code = if (core.isReady()) core.generatePairingCode() else null
        _state.value = _state.value.copy(displayCode = code)
        return code
    }

    /** 把 core 上抛的失败原因码转换为用户可读提示。 */
    fun failureMessage(
        reason: String?,
        fallback: String,
    ): String =
        when (reason) {
            REASON_VERSION_MISMATCH -> "core 版本不兼容：两端需升级到同一版本（major.minor 一致）"
            else -> fallback
        }

    /**
     * 请求方发起配对。
     *
     * @param battery 本机带符号电量（正=充电，负=放电）
     */
    fun startClientPairing(
        targetDevice: DeviceInfo,
        battery: Int,
        onComplete: (success: Boolean, message: String) -> Unit,
    ) {
        viewModelScope.launch {
            val displayCode = ensureDisplayCode()
            if (displayCode == null) {
                onComplete(false, "配对码生成失败：核心未初始化")
                return@launch
            }
            _state.value = _state.value.copy(isPairing = true)
            val deferred = handshakePort.register(targetDevice.uuid)
            try {
                val initSuccess =
                    kotlinx.coroutines.withContext(dispatcher) {
                        if (!core.isReady()) {
                            false
                        } else {
                            core.sendPairingInit(
                                pairingPort.localUuid,
                                targetDevice.uuid,
                                displayCode,
                                battery,
                                "android",
                            ) == 0
                        }
                    }
                if (!initSuccess) {
                    onComplete(false, "配对初始化失败")
                    return@launch
                }
                val result = withTimeoutOrNull(90_000L) { deferred.await() }
                when (result) {
                    true -> onComplete(true, "配对成功")
                    false -> onComplete(false, failureMessage(handshakePort.failureReason(targetDevice.uuid), "配对码验证失败"))
                    else -> onComplete(false, "配对超时")
                }
            } catch (e: Exception) {
                Logger.e("PairingViewModel", "请求方配对异常", e)
                onComplete(false, "配对异常: " + (e.message ?: ""))
            } finally {
                handshakePort.cancel(targetDevice.uuid, deferred)
                _state.value = _state.value.copy(isPairing = false)
            }
        }
    }

    /**
     * 响应方提交配对码。
     *
     * @param battery 本机带符号电量（正=充电，负=放电）
     */
    fun submitServerPairing(
        remoteUuid: String,
        remoteIp: String,
        code: String,
        battery: Int,
        onResult: (message: String, success: Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isPairing = true, errorMessage = null)
            val deferred = handshakePort.register(remoteUuid)
            try {
                val result =
                    kotlinx.coroutines.withContext(dispatcher) {
                        try {
                            if (!core.isReady()) {
                                "配对失败：未初始化"
                            } else {
                                val ltPubKey = pairingPort.localPublicKey
                                val sendOk = core.sendPairingResp(remoteUuid, ltPubKey, code, remoteIp, battery, "android")
                                if (sendOk != 0) {
                                    "配对失败：发送响应失败"
                                } else {
                                    val success = withTimeoutOrNull(30_000L) { deferred.await() }
                                    when (success) {
                                        true -> "配对成功"
                                        false -> failureMessage(handshakePort.failureReason(remoteUuid), "配对失败：对方拒绝了配对")
                                        else -> "配对超时"
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            "配对失败: " + (e.message ?: "")
                        }
                    }
                val ok = result == "配对成功"
                _state.value = _state.value.copy(isPairing = false, success = ok, message = result, errorMessage = if (ok) null else result)
                onResult(result, ok)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isPairing = false, errorMessage = e.message)
                onResult("配对失败: " + (e.message ?: ""), false)
            } finally {
                handshakePort.cancel(remoteUuid, deferred)
            }
        }
    }

    /** 清理核心侧配对码。 */
    fun clearCode() {
        if (core.isReady()) core.clearPairingCode()
        _state.value = _state.value.copy(displayCode = null)
    }
}
