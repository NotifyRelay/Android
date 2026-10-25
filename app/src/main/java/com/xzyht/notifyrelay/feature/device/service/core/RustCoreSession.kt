package com.xzyht.notifyrelay.feature.device.service.core

import android.content.Context
import com.xzyht.notifyrelay.feature.device.service.callback.DeviceCallbackHost
import com.xzyht.notifyrelay.feature.device.service.callback.RustCallbackInstaller
import com.xzyht.notifyrelay.nativecore.NativeCore
import notifyrelay.base.util.Logger
import notifyrelay.data.Storage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rust core 上下文的一次性初始化与启动。
 *
 * 职责边界（平台端**必须**保留的部分）：
 * - 创建 core 实例并交给 `NativeCore` 单例持有；
 * - 解析本机 uuid（升级用户沿用旧平台 uuid 作为迁移种子，全新安装由 core 生成并落库）；
 * - 导入旧平台加密状态 blob、确保 ECDH 密钥存在；
 * - 注册 UniFFI 回调；
 * - 统一启动 core（TCP、心跳调度、离线检测、发送队列、已知设备扫描、重连状态机），并做防重入。
 *
 * 密钥与本机 uuid 的持久化由 Rust 私有库负责，平台端不落盘、不生成。
 *
 * 时序：
 * ```mermaid
 * sequenceDiagram
 *     participant App as DeviceConnectionManager.init
 *     participant S as RustCoreSession
 *     participant Core as NativeCore
 *     participant Cb as RustCallbackInstaller
 *
 *     App->>S: initialize(host, legacyUuid)
 *     S->>Core: createContext()
 *     Core-->>S: 就绪
 *     S->>Core: getLocalUuid()（旧 uuid 作迁移种子）
 *     S->>Core: deserializeLocalState（旧平台 blob，解密与导入配对）
 *     S->>Core: hasKeypair() / generateKeypair() / getPublicKey()
 *     S->>Cb: install(host)
 *     S-->>App: InitResult(uuid, pubKey, legacyStateEnc)
 *     App->>S: startCore(uuid, pubKey, name, battery, port)
 *     S->>Core: startCore(...)
 *     Core-->>App: 就绪（senderQueueReady）
 * ```
 */
class RustCoreSession(
    private val context: Context,
) {
    companion object {
        private const val TAG = "死神-NotifyRelay"

        /** 旧平台 SP 键：本机 uuid（迁移种子） */
        private const val PREF_LEGACY_UUID = "device_uuid"

        /** 旧平台 SP 键：加密状态 blob */
        private const val PREF_LEGACY_STATE = "rust_core_state"

        private const val DEVICE_TYPE = "android"
    }

    /** [initialize] 的结果。 */
    data class InitResult(
        /** 以 core 持久化为准的本机 uuid（core 不可用时可能沿用旧平台 uuid） */
        val localUuid: String,
        /** 本机长期 ECDH 公钥（Base64 编码的 65 字节未压缩点） */
        val localPublicKey: String,
        /** 旧平台加密状态 blob（迁移成功后由 [LegacyDeviceMigrator] 清除） */
        val legacyStateEnc: String,
    )

    // startCore 防重入节流（避免快速重启时重复启动核心服务）
    private val coreStarted = AtomicBoolean(false)

    /**
     * 创建并初始化 core 实例，注册全部回调。
     *
     * @param host 回调宿主
     * @param legacyUuid 旧平台 SP 中保存的 uuid（全新安装时为空）
     */
    fun initialize(
        host: DeviceCallbackHost,
        legacyUuid: String,
    ): InitResult {
        val legacyStateEnc = Storage.getString(PREF_LEGACY_STATE)
        var localUuid = legacyUuid
        var pubKey = ""
        try {
            NativeCore.createContext()

            if (localUuid.isEmpty()) {
                localUuid = resolveLocalUuid(legacyUuid)
            }

            // 旧平台存储迁移：加密状态 blob（含本机密钥与设备 AES）导入 Rust 内存
            // （空数据不迁移；核心库自动 load 优先，blob 仅补充/引导导入）
            if (legacyStateEnc.isNotEmpty()) {
                NativeCore.deserializeLocalState(legacyStateEnc, localUuid, encrypted = 1)
            }
            if (!NativeCore.hasKeypair()) {
                NativeCore.generateKeypair()
            }
            pubKey = NativeCore.getPublicKey() ?: ""
            Logger.d(TAG, "Rust core 上下文已初始化")
            RustCallbackInstaller.install(host)
        } catch (e: Exception) {
            Logger.e(TAG, "Rust core 初始化失败", e)
        }
        return InitResult(localUuid, pubKey, legacyStateEnc)
    }

    /**
     * 统一启动 core：TCP、心跳调度、离线检测、发送队列、已知设备扫描、重连状态机。
     *
     * @return 实际上报给 core 的带符号电量；前置条件不满足或重复启动时返回 null
     */
    fun startCore(
        uuid: String,
        publicKey: String,
        name: String,
        battery: Int,
        tcpPort: Int,
    ): Int? {
        if (!NativeCore.isReady()) return null
        if (publicKey.isEmpty()) {
            Logger.e(TAG, "本机 ECDH 公钥为空，跳过 Rust Core 启动")
            return null
        }
        if (uuid.isEmpty()) {
            Logger.e(TAG, "本机 UUID 为空，跳过 Rust Core 启动")
            return null
        }
        if (!coreStarted.compareAndSet(false, true)) {
            Logger.w(TAG, "Rust Core 已启动，跳过重复启动")
            return null
        }
        return try {
            val started =
                NativeCore.startCore(
                    uuid = uuid,
                    name = name,
                    battery = battery,
                    deviceType = DEVICE_TYPE,
                    tcpPort = tcpPort.toShort(),
                    pubkey = publicKey,
                )
            if (!started) {
                coreStarted.set(false)
                Logger.e(TAG, "Rust Core 启动失败，重置 coreStarted 允许重试")
            }
            battery
        } catch (e: Exception) {
            coreStarted.set(false)
            Logger.e(TAG, "启动 Rust Core 失败", e)
            battery
        }
    }

    /** 解析本机 uuid：core 优先，失败时沿用旧平台 uuid 兜底（仅本次进程，不持久化）。 */
    private fun resolveLocalUuid(legacyUuid: String): String =
        try {
            val rustUuid = NativeCore.getLocalUuid()
            when {
                !rustUuid.isNullOrEmpty() -> rustUuid
                legacyUuid.isNotEmpty() -> {
                    Logger.w(TAG, "Rust 私有库未就绪，沿用旧平台 UUID 兜底")
                    legacyUuid
                }
                else -> ""
            }
        } catch (e: Exception) {
            if (legacyUuid.isNotEmpty()) {
                Logger.w(TAG, "读取 Rust UUID 失败，沿用旧平台 UUID 兜底", e)
                legacyUuid
            } else {
                ""
            }
        }

    /** 旧平台 uuid 的 SP 键（供调用方读取迁移种子）。 */
    fun readLegacyUuid(): String = Storage.getString(PREF_LEGACY_UUID)
}
