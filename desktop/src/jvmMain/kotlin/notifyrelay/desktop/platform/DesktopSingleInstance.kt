package notifyrelay.desktop.platform

import notifyrelay.base.log.Logger
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

/**
 * 桌面端单实例守卫。
 *
 * 首个实例持有用户目录下的锁文件并监听激活报文；
 * 后续实例发送激活报文后退出，避免同一份数据和同一批端口被多进程同时占用。
 */
object DesktopSingleInstance {
    private const val TAG = "DesktopSingle"

    private const val LOCK_FILE_NAME = "desktop.lock"

    /** 激活报文端口，仅回环地址。 */
    private const val ACTIVATE_PORT = 23334

    /** 激活报文内容。 */
    private const val ACTIVATE_MESSAGE = "activate"

    /** 请求已有实例显示窗口；由宿主在窗口创建后设置。 */
    @Volatile
    var onActivate: (() -> Unit)? = null

    private var lockChannel: FileChannel? = null

    private var lock: FileLock? = null

    /** 用户数据目录，与数据库同根。 */
    val userDir: File
        get() = File(System.getProperty("user.home"), ".notifyrelay")

    /**
     * 取得单实例所有权。
     *
     * @return true 表示本进程是首个实例；false 表示已有实例在运行且已被请求显示窗口
     */
    fun acquire(): Boolean {
        val lockFile = File(userDir, LOCK_FILE_NAME)
        val channel =
            runCatching {
                FileChannel.open(
                    lockFile.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                )
            }.getOrElse {
                Logger.w(TAG, "打开单实例锁失败，跳过检查", it)
                return true
            }
        val acquired = runCatching { channel.tryLock() }.getOrNull()
        if (acquired == null) {
            runCatching { channel.close() }
            requestActivate()
            return false
        }
        lockChannel = channel
        lock = acquired
        startActivateListener()
        return true
    }

    /** 释放锁并停止监听。 */
    fun release() {
        runCatching { lock?.release() }
        runCatching { lockChannel?.close() }
        lock = null
        lockChannel = null
    }

    /** 通知已有实例显示窗口。 */
    private fun requestActivate() {
        runCatching {
            DatagramSocket().use { socket ->
                val payload = ACTIVATE_MESSAGE.toByteArray(Charsets.UTF_8)
                socket.send(
                    DatagramPacket(
                        payload,
                        payload.size,
                        InetAddress.getLoopbackAddress(),
                        ACTIVATE_PORT,
                    ),
                )
            }
        }.onFailure { Logger.w(TAG, "发送激活请求失败", it) }
    }

    /** 监听激活报文。 */
    private fun startActivateListener() {
        thread(isDaemon = true, name = "desktop-single-instance") {
            val server =
                runCatching {
                    DatagramSocket(
                        InetSocketAddress(InetAddress.getLoopbackAddress(), ACTIVATE_PORT),
                    )
                }.getOrElse {
                    Logger.w(TAG, "激活端口占用，重复启动只退出", it)
                    return@thread
                }
            val buffer = ByteArray(64)
            while (!server.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                val received = runCatching { server.receive(packet) }.isSuccess
                if (!received) break
                val message = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                if (message != ACTIVATE_MESSAGE) continue
                Logger.i(TAG, "收到激活请求")
                onActivate?.invoke()
            }
        }
    }
}
