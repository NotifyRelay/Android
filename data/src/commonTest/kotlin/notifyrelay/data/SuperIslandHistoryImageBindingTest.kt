package notifyrelay.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import notifyrelay.data.database.AppDatabase
import notifyrelay.data.database.AppDatabaseMigrations
import notifyrelay.data.database.entity.SuperIslandHistoryEntity
import notifyrelay.data.database.repository.DatabaseRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 超级岛历史图片引用的存取契约。
 *
 * 图片不再以 JSON 塞进 `super_island_history` 的一列，而是按（条目, 图片键）落到
 * `super_island_history_images`。这里锁定三条：写入拆成关联行、读回装配成映射、
 * 同内容图片在库里只存一份。
 */
class SuperIslandHistoryImageBindingTest {
    @Test
    fun picMapRoundTripsThroughBindingTable() =
        runBlocking {
            val database = newDatabase()
            val repository = DatabaseRepository(database)
            val entry = historyEntry(id = 1, picMap = mapOf("miui.focus.pic_cover" to "data:image/png;base64,AAA"))

            repository.saveSuperIslandHistory(entry)

            val loaded = repository.getSuperIslandHistoryById(1)
            assertNotNull(loaded)
            // 读回的是图片行 id，原文留在 super_island_images
            assertEquals(1, loaded.picMap.size)
            val imageId = loaded.picMap.getValue("miui.focus.pic_cover").toLongOrNull()
            assertNotNull(imageId)
            assertEquals("data:image/png;base64,AAA", repository.resolveSuperIslandImageById(imageId))
            database.close()
        }

    @Test
    fun identicalImageIsStoredOnce() =
        runBlocking {
            val database = newDatabase()
            val repository = DatabaseRepository(database)
            val same = "data:image/png;base64,SHARED"

            repository.saveSuperIslandHistory(historyEntry(id = 1, picMap = mapOf("a" to same)))
            repository.saveSuperIslandHistory(historyEntry(id = 2, picMap = mapOf("b" to same)))

            val first = repository.getSuperIslandHistoryById(1)?.picMap?.get("a")
            val second = repository.getSuperIslandHistoryById(2)?.picMap?.get("b")
            assertNotNull(first)
            assertEquals(first, second)
            database.close()
        }

    @Test
    fun savingWithoutImagesClearsPreviousBindings() =
        runBlocking {
            val database = newDatabase()
            val repository = DatabaseRepository(database)

            repository.saveSuperIslandHistory(historyEntry(id = 1, picMap = mapOf("a" to "data:image/png;base64,OLD")))
            repository.saveSuperIslandHistory(historyEntry(id = 1, picMap = emptyMap()))

            val loaded = repository.getSuperIslandHistoryById(1)
            assertNotNull(loaded)
            assertTrue(loaded.picMap.isEmpty())
            database.close()
        }

    /** 当前 schema 的内存库。 */
    private fun newDatabase(): AppDatabase =
        Room
            .inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*AppDatabaseMigrations.ALL)
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .build()

    /** 构造一条最小可用的历史条目。 */
    private fun historyEntry(
        id: Long,
        picMap: Map<String, String>,
    ): SuperIslandHistoryEntity =
        SuperIslandHistoryEntity(
            id = id,
            originalPackage = "com.example",
            featureId = "feature-$id",
        ).apply { this.picMap = picMap }
}
