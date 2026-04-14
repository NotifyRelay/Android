package io.github.miuzarte.scrcpyforandroid

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import io.github.miuzarte.scrcpyforandroid.nativecore.AnnexBDecoder
import io.github.miuzarte.scrcpyforandroid.nativecore.PersistentVideoRenderer
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.Codec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArraySet

object NativeCoreFacade {
    private val sessionLifecycleMutex = Mutex()
    private val renderer = PersistentVideoRenderer()
    private var activeSurfaceId: Int? = null
    private var decoder: AnnexBDecoder? = null
    private val videoSizeListeners = CopyOnWriteArraySet<(Int, Int) -> Unit>()
    private val videoFpsListeners = CopyOnWriteArraySet<(Float) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bootstrapLock = Any()
    private val bootstrapPackets = ArrayDeque<CachedPacket>()

    @Volatile
    private var latestConfigPacket: CachedPacket? = null
    private var packetCount: Long = 0

    @Volatile
    private var currentSessionInfo: Scrcpy.Session.SessionInfo? = null

    suspend fun close() {
        sessionLifecycleMutex.withLock {
            releaseAllDecoders()
            renderer.release()
        }
    }

    suspend fun attachVideoSurface(surface: Surface) {
        sessionLifecycleMutex.withLock {
            if (!surface.isValid) {
                Log.w(TAG, "attachVideoSurface(): skip invalid surface")
                return
            }
            val newId = System.identityHashCode(surface)
            if (activeSurfaceId == newId && decoder != null) {
                return
            }
            Log.i(TAG, "attachVideoSurface(): surfaceId=$newId oldSurfaceId=$activeSurfaceId")
            activeSurfaceId = newId
            renderer.attachDisplaySurface(surface)
            val session = currentSessionInfo ?: return
            val currentDecoder = decoder
            if (currentDecoder != null) {
                Log.i(TAG, "attachVideoSurface(): try switch decoder output to persistent surface")
                val switched = currentDecoder.switchOutputSurface(renderer.getDecoderSurface())
                Log.i(TAG, "attachVideoSurface(): switchOutputSurface success=$switched")
                if (switched) {
                    return
                }
            }
            createOrReplaceDecoder(session)
        }
    }

    fun detachVideoSurface(surface: Surface? = null, releaseDecoder: Boolean = false) {
        kotlinx.coroutines.runBlocking {
            sessionLifecycleMutex.withLock {
                val currentId = activeSurfaceId
                val requestId = surface?.let { System.identityHashCode(it) }
                if (requestId != null && currentId != null && requestId != currentId) {
                    Log.i(
                        TAG,
                        "detachVideoSurface(): skip stale request requestSurfaceId=$requestId currentSurfaceId=$currentId"
                    )
                    return@withLock
                }
                Log.i(
                    TAG,
                    "detachVideoSurface(): surfaceId=$requestId releaseDecoder=$releaseDecoder"
                )
                activeSurfaceId = null
                renderer.detachDisplaySurface(surface, releaseSurface = false)
                if (releaseDecoder) {
                    Log.i(TAG, "detachVideoSurface(): releasing decoder with destroyed surface")
                    decoder?.release()
                    decoder = null
                }
            }
        }
    }

    fun addVideoSizeListener(listener: (Int, Int) -> Unit) = videoSizeListeners.add(listener)
    fun removeVideoSizeListener(listener: (Int, Int) -> Unit) = videoSizeListeners.remove(listener)
    fun addVideoFpsListener(listener: (Float) -> Unit) = videoFpsListeners.add(listener)
    fun removeVideoFpsListener(listener: (Float) -> Unit) = videoFpsListeners.remove(listener)

    suspend fun onScrcpySessionStarted(
        session: Scrcpy.Session.SessionInfo,
        sessionMgr: Scrcpy.Session
    ) = sessionLifecycleMutex.withLock {
        currentSessionInfo = session
        releaseAllDecoders()
        synchronized(bootstrapLock) {
            bootstrapPackets.clear()
            latestConfigPacket = null
        }
        if (activeSurfaceId != null) {
            Log.i(TAG, "onScrcpySessionStarted(): bind decoder to persistent surface")
            createOrReplaceDecoder(session)
        }
        packetCount = 0
        sessionMgr.attachVideoConsumer { packet ->
            cacheBootstrapPacket(packet)
            packetCount += 1
            if (packetCount == 1L || packetCount % 120L == 0L) {
                Log.i(
                    TAG,
                    "videoFeed(): packets=$packetCount key=${packet.isKeyFrame} cfg=${packet.isConfig} decoder=${decoder != null}"
                )
            }
            val currentDecoder = decoder ?: return@attachVideoConsumer
            if (activeSurfaceId == null) return@attachVideoConsumer
            runCatching {
                currentDecoder.feedAnnexB(
                    packet.data,
                    packet.ptsUs,
                    packet.isKeyFrame,
                    packet.isConfig
                )
            }
        }
    }

    suspend fun onScrcpySessionStopped() = sessionLifecycleMutex.withLock {
        releaseAllDecoders()
        synchronized(bootstrapLock) {
            bootstrapPackets.clear()
            latestConfigPacket = null
        }
        currentSessionInfo = null
    }

    private const val TAG = "NativeCoreFacade"
    private const val MAX_BOOTSTRAP_PACKETS = 90

    private data class CachedPacket(
        val data: ByteArray,
        val ptsUs: Long,
        val isConfig: Boolean,
        val isKeyFrame: Boolean,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CachedPacket

            if (ptsUs != other.ptsUs) return false
            if (isConfig != other.isConfig) return false
            if (isKeyFrame != other.isKeyFrame) return false
            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = ptsUs.hashCode()
            result = 31 * result + isConfig.hashCode()
            result = 31 * result + isKeyFrame.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    private fun createOrReplaceDecoder(session: Scrcpy.Session.SessionInfo) {
        val surface = renderer.getDecoderSurface()
        decoder?.release()
        decoder = null
        Log.i(
            TAG,
            "createOrReplaceDecoder(): " +
                    "codec=${session.codec?.string ?: "null"}, " +
                    "size=${session.width}x${session.height}, " +
                    "persistent=true"
        )
        val newDecoder = AnnexBDecoder(
            width = session.width,
            height = session.height,
            outputSurface = surface,
            mimeType = when (session.codec) {
                Codec.H264 -> "video/avc"
                Codec.H265 -> "video/hevc"
                Codec.AV1 -> "video/av01"
                else -> "video/avc"
            },
            onOutputSizeChanged = { width, height ->
                val current = currentSessionInfo
                if (current == null || (current.width == width && current.height == height)) {
                    return@AnnexBDecoder
                }
                Log.i(
                    TAG,
                    "videoSizeChanged(): ${current.width}x${current.height} -> ${width}x${height}"
                )
                currentSessionInfo = current.copy(width = width, height = height)
                mainHandler.post {
                    videoSizeListeners.forEach { listener ->
                        runCatching { listener(width, height) }
                    }
                }
            },
            onFpsUpdated = { fps ->
                mainHandler.post {
                    videoFpsListeners.forEach { listener ->
                        runCatching { listener(fps) }
                    }
                }
            },
        )
        decoder = newDecoder
        replayBootstrapPackets(newDecoder)
    }

    private fun replayBootstrapPackets(decoder: AnnexBDecoder) {
        val snapshot = synchronized(bootstrapLock) { bootstrapPackets.toList() }
        if (snapshot.isEmpty()) {
            return
        }
        Log.i(TAG, "replayBootstrapPackets(): count=${snapshot.size}")
        snapshot.forEach { packet ->
            runCatching {
                decoder.feedAnnexB(packet.data, packet.ptsUs, packet.isKeyFrame, packet.isConfig)
            }
        }
    }

    private fun cacheBootstrapPacket(packet: Scrcpy.Session.VideoPacket) {
        val cached = CachedPacket(
            data = packet.data.copyOf(),
            ptsUs = packet.ptsUs,
            isConfig = packet.isConfig,
            isKeyFrame = packet.isKeyFrame,
        )
        synchronized(bootstrapLock) {
            if (cached.isConfig) {
                latestConfigPacket = cached
                bootstrapPackets.clear()
                bootstrapPackets.addLast(cached)
                return
            }

            if (cached.isKeyFrame) {
                bootstrapPackets.clear()
                latestConfigPacket?.let { bootstrapPackets.addLast(it) }
                bootstrapPackets.addLast(cached)
                return
            }

            while (bootstrapPackets.size >= MAX_BOOTSTRAP_PACKETS) {
                bootstrapPackets.removeFirst()
            }
            bootstrapPackets.addLast(cached)
        }
    }

    private fun releaseAllDecoders() {
        runCatching { decoder?.release() }
        decoder = null
    }
}
