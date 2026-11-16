package notifyrelay.desktop.overlay.scene

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

/**
 * 重叠 I/O 的完成查询与取消。
 *
 * JNA 的 `Kernel32` 声明了 `WriteFile`/`ReadFile` 的重叠重载却没有
 * `GetOverlappedResult` 与 `CancelIoEx`，而这两项是「写超时」与
 * 「关闭不阻塞」的前提：没有它们，未完成的请求只能靠阻塞等待收敛。
 */
internal interface OverlappedApi : StdCallLibrary {
    /**
     * 取重叠操作的结果。
     *
     * [wait] 为 true 时阻塞到请求完成；取消在途请求后必须用它收尾，
     * 否则内核仍持有该 OVERLAPPED 的内存。
     */
    fun GetOverlappedResult(
        hFile: WinNT.HANDLE,
        overlapped: WinBase.OVERLAPPED,
        bytesTransferred: IntByReference,
        wait: Boolean,
    ): Boolean

    /**
     * 取消句柄上尚未完成的 I/O。
     *
     * [overlapped] 传 null 表示取消该句柄全部在途请求。
     */
    fun CancelIoEx(
        hFile: WinNT.HANDLE,
        overlapped: WinBase.OVERLAPPED?,
    ): Boolean
}

/** 管道读写共用的重叠接口实例。 */
internal val overlappedApi: OverlappedApi = Native.load("kernel32", OverlappedApi::class.java)

/**
 * 准备一次重叠 I/O。
 *
 * JNA 的 [WinBase.OVERLAPPED] 不会替调用方建完成事件：`write()` 只写结构体字段，
 * [WinBase.OVERLAPPED.hEvent] 仍为空。以空句柄等待会直接得到 WAIT_FAILED，
 * 因此这里显式建一个手动复位事件，并由 [OverlappedIo.close] 释放。
 */
internal class OverlappedIo {
    val overlapped: WinBase.OVERLAPPED = WinBase.OVERLAPPED()
    private val event: WinNT.HANDLE? = Kernel32.INSTANCE.CreateEvent(null, true, false, null)

    init {
        overlapped.hEvent = event
        overlapped.write()
    }

    /** 事件是否创建成功；失败说明该过程已无法等待完成，只能同步调用。 */
    val hasEvent: Boolean get() = event != null

    fun close() {
        event?.let { Kernel32.INSTANCE.CloseHandle(it) }
    }
}
