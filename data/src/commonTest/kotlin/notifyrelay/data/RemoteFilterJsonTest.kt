package notifyrelay.data

import notifyrelay.data.filter.RemoteFilterRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 远端过滤配置的 Rust 报文契约测试。
 *
 * 断言字段名、`filterMode` 数值映射与 `pkg|keyword` 序列化，防止上移过程改变协议。
 */
class RemoteFilterJsonTest {
    private fun repository() = RemoteFilterRepository(nativeFilter = null, installedPackagesProvider = { emptySet() })

    @Test
    fun filterModeEncodesWhiteAsOne() {
        val repository = repository()
        repository.filterMode = "white"
        assertEquals(1, extractFilterMode(repository.buildRustConfigJson(emptySet())))
    }

    @Test
    fun filterModeEncodesBlackAsTwo() {
        val repository = repository()
        repository.filterMode = "black"
        assertEquals(2, extractFilterMode(repository.buildRustConfigJson(emptySet())))
    }

    @Test
    fun filterModeEncodesNoneAsZero() {
        val repository = repository()
        repository.filterMode = "none"
        assertEquals(0, extractFilterMode(repository.buildRustConfigJson(emptySet())))
    }

    @Test
    fun filterEntrySerializesPackageAndKeyword() {
        val repository = repository()
        assertEquals("com.example.app", repository.serializeFilterEntry("com.example.app", null))
        assertEquals("com.example.app", repository.serializeFilterEntry("com.example.app", ""))
        assertEquals("com.example.app|关键词", repository.serializeFilterEntry("com.example.app", "关键词"))
    }

    @Test
    fun configJsonContainsRequiredFields() {
        val repository = repository()
        repository.filterMode = "black"
        repository.blackList = listOf("com.example.app" to "关键词")
        repository.blackListEnabled = setOf("com.example.app|关键词")
        val json = repository.buildRustConfigJson(setOf("com.example.app"))
        assertTrue(json.contains("\"enablePackageGroupMapping\""))
        assertTrue(json.contains("\"packageGroups\""))
        assertTrue(json.contains("\"groupEnabled\""))
        assertTrue(json.contains("\"filterMode\""))
        assertTrue(json.contains("\"filterList\""))
        assertTrue(json.contains("\"enablePeerMode\""))
        assertTrue(json.contains("\"installedPackages\""))
        assertTrue(json.contains("com.example.app|关键词"))
    }

    /** 从配置 JSON 中取出 filterMode 数值。 */
    private fun extractFilterMode(json: String): Int {
        val marker = "\"filterMode\":"
        val start = json.indexOf(marker) + marker.length
        val rest = json.substring(start)
        val end = rest.indexOfFirst { it == ',' || it == '}' }
        return rest.substring(0, end).trim().toInt()
    }
}
