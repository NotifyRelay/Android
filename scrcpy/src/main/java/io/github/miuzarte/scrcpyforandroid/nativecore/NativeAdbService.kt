package io.github.miuzarte.scrcpyforandroid.nativecore

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import kotlin.time.Duration

object NativeAdbService {
    private val transport = DirectAdbTransport
    private val mutex = Mutex()

    @Volatile
    private var connection: DirectAdbConnection? = null

    @Volatile
    private var connectedHost: String? = null

    @Volatile
    private var connectedPort: Int? = null

    var keyName: String
        get() = transport.keyName
        set(value) {
            transport.keyName = value
        }

    fun init(context: Context) {
        transport.init(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AdbMdnsDiscoverer.init(context)
        }
    }

    suspend fun pair(host: String, port: Int, pairingCode: String): Boolean = mutex.withLock {
        val h = host.trim()
        val code = pairingCode.trim()
        require(h.isNotBlank()) { "host is blank" }
        require(code.isNotBlank()) { "pairing code is blank" }
        Log.i(TAG, "pair(): host=$h port=$port")
        return@withLock try {
            transport.pair(h, port, code)
        } catch (e: Exception) {
            Log.e(TAG, "pair(): failed host=$h port=$port", e)
            val detail = e.message ?: "${e.javaClass.simpleName} (no message)"
            throw IllegalStateException("ADB pair failed for $h:$port -> $detail", e)
        }
    }

    suspend fun discoverPairingService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
    ): Pair<String, Int>? = mutex.withLock {
        return@withLock try {
            transport.discoverPairingService(timeoutMs, includeLanDevices)
        } catch (e: Exception) {
            Log.w(TAG, "discoverPairingService(): failed", e)
            null
        }
    }

    suspend fun discoverConnectService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
    ): Pair<String, Int>? = mutex.withLock {
        return@withLock try {
            transport.discoverConnectService(timeoutMs, includeLanDevices)
        } catch (e: Exception) {
            Log.w(TAG, "discoverConnectService(): failed", e)
            null
        }
    }

    /**
     * Connect to a remote ADB endpoint. If an existing connection points to the
     * same host:port it is reused; otherwise the previous connection is closed
     * before attempting the new connect.
     */
    suspend fun connect(
        host: String,
        port: Int,
        timeout: Duration = Duration.INFINITE,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            Log.i(TAG, "connect(): host=$host port=$port")

            if (connection != null
                && connection!!.isAlive()
                && connectedHost == host
                && connectedPort == port
            ) {
                return@withLock
            }
            disconnectInternal()

            try {
                val conn = withTimeout(timeout) { transport.connect(host, port) }
                connection = conn
                connectedHost = host
                connectedPort = port
            } catch (e: Exception) {
                Log.e(TAG, "connect(): failed host=$host port=$port", e)
                val detail = e.message ?: "${e.javaClass.simpleName} (no message)"
                throw IllegalStateException("ADB connect failed to $host:$port -> $detail", e)
            }
        }
    }

    /**
     * Close the current ADB connection immediately.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        mutex.withLock {
            disconnectInternal()
        }
    }

    suspend fun isConnected(): Boolean = mutex.withLock {
        connection?.isAlive() == true
    }

    fun isConnectedSync(): Boolean = connection?.isAlive() == true

    suspend fun setTcpPort(port: Int): Boolean = mutex.withLock {
        runCatching {
            requireConnection().shell("setprop service.adb.tcp.port $port")
            true
        }.getOrElse { false }
    }

    /**
     * Execute a shell command on the connected device and return stdout text.
     */
    suspend fun shell(command: String): String = mutex.withLock {
        val response = requireConnection().shell(command)
        Log.d(TAG, "command: $command, response: $response")
        response
    }

    suspend fun startApp(
        packageName: String,
        displayId: Int? = null,
        forceStop: Boolean = false,
    ): String = mutex.withLock {
        val normalizedPackageName = packageName.trim()
        require(normalizedPackageName.isNotBlank()) { "package name is blank" }

        if (forceStop) {
            requireConnection().shell(
                "am force-stop ${quoteShellArg(normalizedPackageName)}"
            )
        }

        val resolveOutput = requireConnection().shell(
            "cmd package resolve-activity --brief ${quoteShellArg(normalizedPackageName)}"
        )
        val componentName = resolveOutput
            .lineSequence()
            .map(String::trim)
            .lastOrNull { '/' in it }
            ?: throw IllegalStateException(
                "Cannot resolve launch activity for $normalizedPackageName"
            )

        val displayArg = displayId
            ?.takeIf { it >= 0 }
            ?.let { " --display $it" }
            .orEmpty()
        val command = "am start-activity$displayArg -n ${quoteShellArg(componentName)}"
        val response = requireConnection().shell(command)
        Log.d(TAG, "startApp(): package=$normalizedPackageName component=$componentName")
        response
    }

    suspend fun openShellStream(command: String): AdbSocketStream = mutex.withLock {
        requireConnection().openStream("shell:$command")
    }

    suspend fun push(localPath: Path, remotePath: String) = mutex.withLock {
        requireConnection().push(localPath.toFile().readBytes(), remotePath)
    }

    suspend fun openAbstractSocket(name: String): AdbSocketStream = mutex.withLock {
        requireConnection().openStream("localabstract:$name")
    }

    suspend fun close() {
        disconnect()
    }

    private fun disconnectInternal() {
        runCatching { connection?.close() }
        connection = null
        connectedHost = null
        connectedPort = null
    }

    private fun requireConnection(): DirectAdbConnection {
        return connection?.takeIf { it.isAlive() }
            ?: throw IllegalStateException("ADB not connected")
    }

    private fun quoteShellArg(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private const val TAG = "NativeAdbService"
}
