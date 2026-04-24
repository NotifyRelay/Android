package io.github.miuzarte.scrcpyforandroid.nativecore

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.IOException
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RequiresApi(Build.VERSION_CODES.R)
internal object AdbMdnsDiscoverer {

    private var nsdManager: NsdManager? = null

    fun init(context: Context) {
        nsdManager = context.getSystemService(NsdManager::class.java)
    }

    fun discoverConnectService(timeoutMs: Long, includeLanDevices: Boolean): Pair<String, Int>? {
        val manager = nsdManager ?: run {
            Log.e(TAG, "NsdManager not initialized. Call init(context) first.")
            return null
        }
        return discoverService(manager, TLS_CONNECT, timeoutMs, includeLanDevices)
    }

    /**
     * Discover a device that advertises the ADB pairing service via mDNS.
     */
    fun discoverPairingService(timeoutMs: Long, includeLanDevices: Boolean): Pair<String, Int>? {
        val manager = nsdManager ?: run {
            Log.e(TAG, "NsdManager not initialized. Call init(context) first.")
            return null
        }
        return discoverService(manager, TLS_PAIRING, timeoutMs, includeLanDevices)
    }

    private fun discoverService(
        manager: NsdManager,
        serviceType: String,
        timeoutMs: Long,
        includeLanDevices: Boolean,
    ): Pair<String, Int>? {
        val resultPort = AtomicInteger(-1)
        val resultHost = AtomicReference<String?>(null)
        val discoveryFinished = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.v(TAG, "discovery started: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed: $serviceType, error=$errorCode")
                latch.countDown()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.v(TAG, "discovery stopped: $serviceType")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "stop discovery failed: $serviceType, error=$errorCode")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (discoveryFinished.get()) return
                Log.v(TAG, "service found: ${serviceInfo.serviceName}")
                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.v(TAG, "resolve failed: ${serviceInfo.serviceName}, error=$errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (discoveryFinished.get()) return
                        val hostAddress = serviceInfo.host?.hostAddress ?: return
                        if (hostAddress.isBlank()) return

                        if (!includeLanDevices) {
                            val isLocalHost = runCatching {
                                NetworkInterface.getNetworkInterfaces().asSequence().any { intf ->
                                    intf.inetAddresses.asSequence().any { addr ->
                                        addr.hostAddress == hostAddress
                                    }
                                }
                            }.getOrDefault(false)
                            if (!isLocalHost) return
                            if (!isPortOpened(serviceInfo.port)) return
                        }

                        if (resultPort.compareAndSet(-1, serviceInfo.port)) {
                            resultHost.set(hostAddress)
                            discoveryFinished.set(true)
                            latch.countDown()
                        }
                    }
                }
                runCatching {
                    manager.resolveService(serviceInfo, resolveListener)
                }.onFailure { e ->
                    Log.w(TAG, "resolveService failed for ${serviceInfo.serviceName}", e)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.v(TAG, "service lost: ${serviceInfo.serviceName}")
            }
        }

        manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        runCatching { manager.stopServiceDiscovery(discoveryListener) }

        val port = resultPort.get()
        val host = resultHost.get()
        return if (port > 0 && !host.isNullOrBlank()) host to port else null
    }

    private fun isPortOpened(port: Int): Boolean = try {
        ServerSocket().use {
            it.bind(InetSocketAddress("127.0.0.1", port), 1)
            false
        }
    } catch (_: IOException) {
        true
    }

    private const val TAG = "AdbMdnsDiscoverer"
    private const val TLS_CONNECT = "_adb-tls-connect._tcp"
    private const val TLS_PAIRING = "_adb-tls-pairing._tcp"
}
