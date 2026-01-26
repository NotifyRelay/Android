package com.xzyht.notifyrelay.common.core.sync

import android.content.Context
import com.xzyht.notifyrelay.common.core.util.Logger
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.ftplet.UserManager
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

data class ftpServerInfo(
    val username: String,
    val password: String,
    val ipAddress: String,
    val port: Int
)

object ftpServer {
    private const val TAG = "ftpServer"
    // 简化：移除密码生成相关常量，使用匿名登录
    // private const val DERIVED_USERNAME_PREFIX = "ftp_"
    // private const val DERIVED_PASSWORD_LENGTH = 32

    private val PORT_RANGE = 5151..5169

    private var ftpServer: FtpServer? = null
    private var isRunning = AtomicBoolean(false)
    private var serverInfo: ftpServerInfo? = null
    private lateinit var applicationContext: Context
    
    fun setContext(context: Context) {
        this.applicationContext = context.applicationContext
    }

    private fun createTemporaryUserManager(username: String, password: String, context: Context): UserManager {
        try {
            // 创建临时用户管理器
            val userManagerFactory = PropertiesUserManagerFactory()
            
            // 设置临时属性文件
            val tempFile = File.createTempFile("ftpusers", ".properties", context.cacheDir)
            tempFile.deleteOnExit()
            
            // 确保文件存在
            FileOutputStream(tempFile).use { it.write("# FTPServer Users\n".toByteArray()) }
            
            userManagerFactory.file = tempFile
            
            val userManager = userManagerFactory.createUserManager()
            
            // 创建用户
            val user = BaseUser()
            user.name = username
            user.password = password
            user.homeDirectory = "/storage/emulated/0/"
            
            // 设置权限
            val authorities = mutableListOf<Authority>()
            authorities.add(WritePermission())
            user.authorities = authorities
            
            // 添加用户
            userManager.save(user)
            
            // 添加匿名用户支持
            val anonymousUser = BaseUser()
            anonymousUser.name = "anonymous"
            anonymousUser.password = ""
            anonymousUser.homeDirectory = "/storage/emulated/0/"
            anonymousUser.authorities = authorities
            userManager.save(anonymousUser)
            
            return userManager
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create user manager", e)
            throw e
        }
    }

    fun initialize() {
        // 初始化方法不再需要，在start方法中动态创建
    }

    // 定义FTP启动结果状态（保持与原ftp相同的枚举名称）
    enum class StartResult {
        SUCCESS,         // 启动成功
        ALREADY_RUNNING, // 已在运行
        PERMISSION_DENIED, // 权限不足
        PORT_IN_USE,     // 端口被占用
        CONFIG_ERROR,    // 配置错误
        FAILED           // 其他失败
    }
    
    data class ftpStartResult(
        val status: StartResult,
        val serverInfo: ftpServerInfo? = null
    )
    
    @Synchronized
    fun start(sharedSecret: String, deviceName: String, context: Context): ftpStartResult {
        Logger.i(TAG, "FTP 服务器启动请求，设备名称: $deviceName")
        if (isRunning.get()) {
            Logger.i(TAG, "FTP 服务器已在运行，返回当前服务器信息")
            return ftpStartResult(StartResult.ALREADY_RUNNING, serverInfo)
        }

        // 设置上下文
        this.applicationContext = context.applicationContext

        // 检查文件管理权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Logger.w(TAG, "FTP 服务需要文件管理权限，当前未授权")
                // 在UI线程中显示Toast提示用户
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    com.xzyht.notifyrelay.common.core.util.ToastUtils.showShortToast(context, "FTP 服务需要文件管理权限，当前仅能查看文件层级")
                }
                // 即使没有文件权限，也继续启动FTP服务，PC端可以获取文件层级
            }
        }

        // 简化：不再从共享密钥派生凭据，使用固定用户名密码，主要使用匿名登录
        // Logger.d(TAG, "从共享密钥派生 FTP 凭据")
        // val (username, password) = deriveCredentialsFromSharedSecret(sharedSecret)
        val username = "anonymous"
        val password = ""
        Logger.d(TAG, "使用固定用户名: $username")

        Logger.d(TAG, "开始在端口范围 $PORT_RANGE 中尝试启动 FTP 服务器")

        var lastException: Exception? = null
        var seenBind = false
        var seenPermDenied = false
        var seenConfig = false

        PORT_RANGE.forEach { port ->
            try {
                Logger.d(TAG, "尝试在端口 $port 启动 FTP 服务器")

                // 创建FTP服务器工厂
                val serverFactory = FtpServerFactory()
                
                // 创建监听器
                val listenerFactory = ListenerFactory()
                listenerFactory.port = port
                serverFactory.addListener("default", listenerFactory.createListener())
                
                // 创建用户管理器
                val userManager = createTemporaryUserManager(username, password, context)
                serverFactory.userManager = userManager
                
                // 启动服务器
                ftpServer = serverFactory.createServer()
                ftpServer?.start()

                isRunning.set(true)
                val ipAddress = getDeviceIpAddress()
                Logger.i(TAG, "FTP 服务器在端口 $port 启动成功，IP 地址: $ipAddress")

                serverInfo = ftpServerInfo(
                    username = username,
                    password = password,
                    ipAddress = ipAddress ?: "127.0.0.1",
                    port = port
                )

                Logger.i(TAG, "FTP server started: $ipAddress on port $port (derived from sharedSecret)")
                return ftpStartResult(StartResult.SUCCESS, serverInfo)
            } catch (e: Exception) {
                lastException = e
                when (e) {
                    is java.net.BindException -> seenBind = true
                    is SecurityException -> seenPermDenied = true
                    is IllegalArgumentException -> seenConfig = true
                    is FtpException -> seenConfig = true
                    is IOException -> seenConfig = true
                }

                Logger.e(TAG, "Failed to start FTP server on port $port", e)

                // 如果构建了 ftpServer 实例但 start() 抛出异常，确保清理已分配的资源
                try {
                    ftpServer?.stop()
                } catch (stopEx: Exception) {
                    Logger.w(TAG, "Failed to stop partially-initialized ftpServer", stopEx)
                }
                ftpServer = null
                isRunning.set(false)
            }
        }

        Logger.e(TAG, "所有端口尝试失败，无法启动 FTP 服务器: lastException=${lastException?.javaClass?.name}")

        return when {
            seenPermDenied -> ftpStartResult(StartResult.PERMISSION_DENIED)
            seenConfig -> ftpStartResult(StartResult.CONFIG_ERROR)
            seenBind -> ftpStartResult(StartResult.PORT_IN_USE)
            else -> ftpStartResult(StartResult.FAILED)
        }
    }

    @Synchronized
    fun stop() {
        try {
            if (isRunning.get() || ftpServer != null) {
                ftpServer?.stop()
                ftpServer = null
                isRunning.set(false)
                serverInfo = null
                com.xzyht.notifyrelay.common.core.util.Logger.i(TAG, "FTP server stopped")
            }
        } catch (e: Exception) {
            com.xzyht.notifyrelay.common.core.util.Logger.e(TAG, "Failed to stop FTP server", e)
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