# Agent Note: 桌面端矢量场景管道写阻塞导致界面线程卡死

Status: implemented

## Problem

JVM 桌面端整窗失去响应：进程存活、CPU 占用不再增长、日志停在某一刻不再追加。线程转储与内核态寄存器采样给出完整链条——`AWT-EventQueue-0` 停在 `ntdll!NtClose`（`NamedPipeSceneServer.closePipe` 的 `CloseHandle`），`DefaultDispatcher-worker-10` 停在 `ntdll!NtWriteFile`（同一服务的 `writeAll`），`overlay-scene-pipe` 停在 `NtFsControlFile`（`LineReader` 的 `PeekNamedPipe`）。三者都停在 `ntdll` 的同一个系统调用分发区，中间没有任何 Java 锁竞争，全进程也没有独占的 ownable synchronizer。

管道以 `PIPE_WAIT` 与同步句柄创建，写入走阻塞式 `WriteFile`。对端（Gamebar 小组件）停止读取后管道缓冲被填满，写线程永久停在 `NtWriteFile`；关闭路径随后在界面线程上调用 `CloseHandle`，而 `CloseHandle` 会等该句柄上的在途 I/O 结束——两者互锁，`CloseHandle` 永远不返回。叠加层的组合树释放回调因此卡死，整窗不再重组。

代码里已经声明了 `WRITE_TIMEOUT_MS = 3_000L`、`FILE_FLAG_OVERLAPPED` 与 `ERROR_IO_PENDING`，但三者都只是定义：管道并未按重叠方式打开，写出也没有任何等待上限。注释写着「不先调 DisconnectNamedPipe，避免 UI 线程卡死」，而同一路径上的 `CloseHandle` 存在同样的阻塞风险。

对端侧同样被动：其读取循环用 `PeekNamedPipe` 轮询到有数据才 `ReadFile`，这个模式本身不会主动制造背压，但一旦小组件停止推进（进程被挂起、休眠唤醒后丢失读取线程），服务端没有机制察觉。

## Decision

**管道按重叠方式打开。** `CreateNamedPipe` 增加 `FILE_FLAG_OVERLAPPED`，随后 `ConnectNamedPipe`、`ReadFile`、`WriteFile` 一律传 `OVERLAPPED`。重叠句柄上以 `null` 调用这三个 API 会被内核按同步语义处理，句柄关闭时无法返回。

**完成事件由调用方显式创建。** JNA 的 `WinBase.OVERLAPPED.write()` 只写结构体字段，不建 `hEvent`；以空句柄等待会直接得到 `WAIT_FAILED`。`OverlappedIo` 负责建手动复位事件、写回结构体并由 `close()` 释放，读写两处共用。

**每次写有 3 秒上限。** 排队成功（`ERROR_IO_PENDING`）时等待完成事件，超时即 `CancelIoEx` 撤掉请求、`GetOverlappedResult` 收尾并判定对端停止读取。`GetOverlappedResult` 与 `CancelIoEx` 在自有接口里声明：JNA 的 `Kernel32` 有重叠重载却没有完成查询与取消入口。

**关闭不阻塞调用线程。** `close()` 先 `CancelIoEx` 撤在途请求再 `CloseHandle`，等待监听线程退出交给独立守护线程。`OverlaySceneBridge.close()` 同样把录制场景的释放转交独立线程，因为它运行在组合树释放回调即界面线程上。

## Alternatives considered

**只把 `closePipe` 挪到后台线程。** 界面线程确实能返回，但在途写仍然永久挂在内核里，监听线程无法退出，下一轮会话会与旧实例争用同名的两个实例位；故障从「整窗卡死」退化成「叠加层永久失效且不可自愈」。

**只加写超时、保留同步句柄。** 同步句柄上的 `WriteFile` 无法被超时打断——超时只能由别的线程观察，阻塞中的线程自身没有可中断点。要真正给予上限，句柄必须是重叠的。

**关闭时先 `DisconnectNamedPipe` 再 `CloseHandle`。** `DisconnectNamedPipe` 在对端未确认断开时会阻塞，且在途写未收敛时同样不返回；原注释已经因此否决过它，换成先取消再关闭才覆盖两个方向。

**服务端心跳里加入消费确认。** 心跳已覆盖「对端是否发送报文」，不覆盖「对端是否消费数据」。要覆盖就得在协议里新增确认报文，属于协议演进；写超时以零协议开销得出同样的结论。

**由 Gamebar 侧兜底。** 对端是 UWP 小组件，由系统按需挂起，其日志显示停止推进发生在系统接管之后。把恢复责任放在会被挂起的一侧无法成立。

## Consequences

对端停止读取时，服务端在 3 秒内放弃本轮会话并重建管道实例；叠加层暂时无画面，但进程保持可用。界面线程任何时候都不会被管道 I/O 拖住，关闭动作的实际耗时与内核在途 I/O 无关。

代价是每次写多一次事件创建与一次 `WaitForSingleObject`。帧率 50 fps 时这是每秒约 50 次额外系统调用，相对整帧录制与像素读回可以忽略。

`OverlappedIo` 的事件句柄必须在每次操作后释放，否则每帧泄漏一个内核对象；读写两侧都用 `try/finally` 保证。取消后以 `GetOverlappedResult(wait = true)` 收尾是必需的：内核释放该 `OVERLAPPED` 之前不能复用其内存。

`close()` 不再同步等待监听线程退出，调用方无法通过返回时间推断会话已完全回收。测试重建同名管道时依赖监听线程在 `CLOSE_JOIN_TIMEOUT_MS` 内自行退出这一既有约束。

## Testing

`NamedPipeStallTest` 覆盖两条：客户端完成握手与订阅后停止读取，服务端推送在写超时后失败而非永久阻塞，且失败发生在超时之后（以首帧成功建立对照）；无客户端连接时 `close()` 在 2 秒内返回。`:desktop:jvmTest` 与 `:desktop:ktlintCheck` 通过。
