package notifyrelay.domain.feature.notification.chat

/**
 * 聊天记忆。
 *
 * 聊天历史是未做 UI 转换的入站/出站通知报文，不做单独持久化，仅在内存中保留。
 *
 * 入站回调运行在 Rust 回调线程，界面读取运行在主线程，
 * 故读写以锁串行化，[getHistory] 返回不可变快照（避免界面读到内部可变列表而不重组）。
 */
object ChatMemory {
    private const val MAX_MEMORY_SIZE = 1000

    private val lock = Any()
    private var memory: MutableList<String> = mutableListOf()

    /** 取聊天历史快照。 */
    fun getHistory(): List<String> = synchronized(lock) { memory.toList() }

    /** 追加一条聊天记录；超过上限时保留最近 [MAX_MEMORY_SIZE] 条。 */
    fun append(message: String) {
        synchronized(lock) {
            memory.add(message)
            if (memory.size > MAX_MEMORY_SIZE) {
                memory = memory.subList(memory.size - MAX_MEMORY_SIZE, memory.size).toMutableList()
            }
        }
    }

    /** 清空聊天记忆。 */
    fun clear() {
        synchronized(lock) { memory = mutableListOf() }
    }
}
