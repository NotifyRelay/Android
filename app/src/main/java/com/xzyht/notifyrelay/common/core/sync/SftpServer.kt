package com.xzyht.notifyrelay.common.core.sync

import android.content.Context
import android.os.Build
import android.util.Base64
import com.xzyht.notifyrelay.common.core.util.Logger
import org.apache.sshd.common.file.FileSystemFactory
import org.apache.sshd.common.file.nativefs.NativeFileSystemFactory
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.session.SessionContext
import org.apache.sshd.common.util.io.PathUtils
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.server.ServerBuilder
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import java.nio.file.Paths
import java.security.KeyPair
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

data class SftpServerInfo(
    val username: String,
    val password: String,
    val ipAddress: String,
    val port: Int
)

object SftpServer {
    private const val TAG = "SftpServer"
    private const val DERIVED_USERNAME_PREFIX = "sftp_"
    private const val DERIVED_PASSWORD_LENGTH = 32

    private val PORT_RANGE = 5151..5169

    private var sshd: org.apache.sshd.server.SshServer? = null
    private var isRunning = AtomicBoolean(false)
    private var serverInfo: SftpServerInfo? = null
    private lateinit var applicationContext: Context
    
    fun setContext(context: Context) {
        this.applicationContext = context.applicationContext
    }

    init {
        System.setProperty(SecurityUtils.SECURITY_PROVIDER_REGISTRARS, "")
        System.setProperty(
            "org.apache.sshd.common.io.IoServiceFactoryFactory",
            "org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory"
        )
        PathUtils.setUserHomeFolderResolver {
            Paths.get("/")
        }
    }

    private fun createSelfSignedKeyPair(): KeyPair {
        val keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }

    private class PfxKeyPairProvider : KeyPairProvider {
        private val keyPair: KeyPair = createSelfSignedKeyPair()

        override fun loadKeys(session: SessionContext?): Iterable<KeyPair> = listOf(keyPair)
    }
    
    private fun createFileSystemFactory(): FileSystemFactory {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 VirtualFileSystemFactory
            VirtualFileSystemFactory(Paths.get("/storage/emulated/0/"))
        } else {
            // Android O(26)+ 使用 NativeFileSystemFactory
            NativeFileSystemFactory()
        }
    }

    private class DerivedPasswordAuthenticator(
        private val validUsername: String,
        private val validPasswordHash: String
    ) : PasswordAuthenticator {
        override fun authenticate(
            username: String?,
            password: String?,
            session: org.apache.sshd.server.session.ServerSession?
        ): Boolean {
            if (username != validUsername) return false
            val inputHash = derivePasswordHash(password ?: "")
            return inputHash == validPasswordHash
        }
    }

    fun deriveCredentialsFromSharedSecret(sharedSecret: String): Pair<String, String> {
        val secretBytes = Base64.decode(sharedSecret, Base64.NO_WRAP)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val derived = sha256.digest(secretBytes)

        val username = DERIVED_USERNAME_PREFIX + Base64.encodeToString(derived.copyOf(8), Base64.NO_WRAP or Base64.URL_SAFE)
            .replace("[^a-zA-Z0-9]".toRegex(), "")
            .take(16)
            .lowercase()

        val password = Base64.encodeToString(derived.copyOf(DERIVED_PASSWORD_LENGTH), Base64.NO_WRAP or Base64.URL_SAFE)
            .replace("[^a-zA-Z0-9]".toRegex(), "")

        return Pair(username, password)
    }

    fun derivePasswordHash(password: String): String {
        val md5 = MessageDigest.getInstance("MD5")
        val hash = md5.digest(password.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun initialize() {
        // 初始化方法不再需要，在start方法中动态创建
    }

    // 定义SFTP启动结果状态
    enum class StartResult {
        SUCCESS,         // 启动成功
        ALREADY_RUNNING, // 已在运行
        PERMISSION_DENIED, // 权限不足
        PORT_IN_USE,     // 端口被占用
        CONFIG_ERROR,    // 配置错误
        FAILED           // 其他失败
    }
    
    data class SftpStartResult(
        val status: StartResult,
        val serverInfo: SftpServerInfo? = null
    )
    
    @Synchronized
    fun start(sharedSecret: String, deviceName: String, context: Context): SftpStartResult {
        Logger.i(TAG, "SFTP 服务器启动请求，设备名称: $deviceName")
        if (isRunning.get()) {
            Logger.i(TAG, "SFTP 服务器已在运行，返回当前服务器信息")
            return SftpStartResult(StartResult.ALREADY_RUNNING, serverInfo)
        }

        // 设置上下文
        this.applicationContext = context.applicationContext

        // 检查文件管理权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Logger.w(TAG, "SFTP 服务需要文件管理权限，当前未授权")
                // 在UI线程中显示Toast提示用户
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    com.xzyht.notifyrelay.common.core.util.ToastUtils.showShortToast(context, "SFTP 服务需要文件管理权限，当前仅能查看文件层级")
                }
                // 即使没有文件权限，也继续启动SFTP服务，PC端可以获取文件层级
            }
        }

        Logger.d(TAG, "从共享密钥派生 SFTP 凭据")
        val (username, password) = deriveCredentialsFromSharedSecret(sharedSecret)
        val passwordHash = derivePasswordHash(password)
        Logger.d(TAG, "派生的用户名: $username")

        Logger.d(TAG, "开始在端口范围 $PORT_RANGE 中尝试启动 SFTP 服务器")

        var lastException: Exception? = null
        var seenBind = false
        var seenPermDenied = false
        var seenConfig = false

        PORT_RANGE.forEach { port ->
            try {
                Logger.d(TAG, "尝试在端口 $port 启动 SFTP 服务器")

                // 创建文件系统工厂
                val fileSystemFactory = createFileSystemFactory()

                sshd = ServerBuilder.builder().apply {
                    fileSystemFactory(fileSystemFactory)
                }.build().apply {
                    this.port = port
                    keyPairProvider = PfxKeyPairProvider()
                    // 禁用公钥认证，强制使用派生的用户名/密码认证
                    publickeyAuthenticator = PublickeyAuthenticator { _, _, _ -> false }
                    passwordAuthenticator = DerivedPasswordAuthenticator(username, passwordHash)
                    subsystemFactories = listOf(SftpSubsystemFactory())
                    start()
                }

                isRunning.set(true)
                val ipAddress = getDeviceIpAddress()
                Logger.i(TAG, "SFTP 服务器在端口 $port 启动成功，IP 地址: $ipAddress")

                serverInfo = SftpServerInfo(
                    username = username,
                    password = password,
                    ipAddress = ipAddress ?: "127.0.0.1",
                    port = port
                )

                Logger.i(TAG, "SFTP server started: $ipAddress on port $port (derived from sharedSecret)")
                return SftpStartResult(StartResult.SUCCESS, serverInfo)
            } catch (e: Exception) {
                lastException = e
                when (e) {
                    is java.net.BindException -> seenBind = true
                    is SecurityException -> seenPermDenied = true
                    is IllegalArgumentException -> seenConfig = true
                }

                Logger.e(TAG, "Failed to start SFTP server on port $port", e)

                // 如果构建了 sshd 实例但 start() 抛出异常，确保清理已分配的资源
                try {
                    sshd?.stop(true)
                } catch (stopEx: Exception) {
                    Logger.w(TAG, "Failed to stop partially-initialized sshd", stopEx)
                }
                sshd = null
                isRunning.set(false)
            }
        }

        Logger.e(TAG, "所有端口尝试失败，无法启动 SFTP 服务器: lastException=${lastException?.javaClass?.name}")

        return when {
            seenPermDenied -> SftpStartResult(StartResult.PERMISSION_DENIED)
            seenConfig -> SftpStartResult(StartResult.CONFIG_ERROR)
            seenBind -> SftpStartResult(StartResult.PORT_IN_USE)
            else -> SftpStartResult(StartResult.FAILED)
        }
    }

    @Synchronized
    fun stop() {
        try {
            if (isRunning.get() || sshd != null) {
                sshd?.stop(true)
                sshd = null
                isRunning.set(false)
                serverInfo = null
                com.xzyht.notifyrelay.common.core.util.Logger.i(TAG, "SFTP server stopped")
            }
        } catch (e: Exception) {
            com.xzyht.notifyrelay.common.core.util.Logger.e(TAG, "Failed to stop SFTP server", e)
        }
    }

    private fun getDeviceIpAddress(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is java.net.Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            com.xzyht.notifyrelay.common.core.util.Logger.e(TAG, "Failed to get device IP address", e)
            null
        }
    }
}