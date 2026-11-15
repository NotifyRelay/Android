package notifyrelay.data

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
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
 * `super_island_history_images`。这里锁定两类事实：日常存取的往返，以及 v9→v10 迁移
 * 是否真的搬走了旧列并保留了数据。
 */
class SuperIslandHistoryImageBindingTest {
    @Test
    fun picMapRoundTripsThroughBindingTable() =
        runBlocking {
            val database = newDatabase()
            val repository = DatabaseRepository(database)
            repository.saveSuperIslandHistory(historyEntry(id = 1, picMap = mapOf("miui.focus.pic_cover" to "data:image/png;base64,AAA")))

            val loaded = repository.getSuperIslandHistoryById(1)
            assertNotNull(loaded)
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

    /**
     * v9→v10：`picMap` 列必须消失，历史行必须原样保留，能对上图片行的引用必须搬进关联表。
     *
     * 直接调用迁移函数而不是整库打开：Room 的版本校验会比对全部实体，而本用例只关心
     * 这两张表的结构与数据搬运。
     */
    @Test
    fun migrationDropsPicMapColumnAndCarriesBindings() {
        val driver = BundledSQLiteDriver()
        val connection: SQLiteConnection = driver.open(":memory:")
        connection.use { c ->
            c.exec("CREATE TABLE super_island_history (id INTEGER NOT NULL PRIMARY KEY, sourceDeviceUuid TEXT, originalPackage TEXT, mappedPackage TEXT, appName TEXT, title TEXT, text TEXT, paramV2Raw TEXT, picMap TEXT NOT NULL, rawPayload TEXT, featureId TEXT)")
            c.exec("CREATE TABLE super_island_images (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, contentHash TEXT NOT NULL, data TEXT NOT NULL, lastUpdated INTEGER NOT NULL)")
            c.exec("INSERT INTO super_island_images (id, contentHash, data, lastUpdated) VALUES (7, 'h7', 'data:image/png;base64,SEVEN', 1)")
            c.exec("INSERT INTO super_island_history (id, title, picMap) VALUES (10, 'kept', '{\"cover\":\"7\"}')")
            c.exec("INSERT INTO super_island_history (id, title, picMap) VALUES (11, 'no-image', '{\"cover\":\"https://example.com/a.png\"}')")

            val migration = AppDatabaseMigrations.ALL.first { it.startVersion == 9 && it.endVersion == 10 }
            migration.migrate(c)

            assertTrue(columnNames(c, "super_island_history").none { it == "picMap" })
            assertEquals(2, c.scalarLong("SELECT COUNT(*) FROM super_island_history"))
            assertEquals("kept", c.scalarString("SELECT title FROM super_island_history WHERE id = 10"))
            assertEquals(1L, c.scalarLong("SELECT COUNT(*) FROM super_island_history_images"))
            assertEquals(7L, c.scalarLong("SELECT imageId FROM super_island_history_images WHERE historyId = 10"))
        }
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

    /** 执行一条无返回值的 SQL。 */
    private fun SQLiteConnection.exec(sql: String) {
        prepare(sql).use { it.step() }
    }

    /** 取首行首列的整数。 */
    private fun SQLiteConnection.scalarLong(sql: String): Long =
        prepare(sql).use {
            it.step()
            it.getLong(0)
        }

    /** 取首行首列的文本。 */
    private fun SQLiteConnection.scalarString(sql: String): String =
        prepare(sql).use {
            it.step()
            it.getText(0)
        }

    /** 列出某张表的列名。 */
    private fun columnNames(
        connection: SQLiteConnection,
        table: String,
    ): List<String> =
        connection.prepare("PRAGMA table_info($table)").use { statement ->
            val names = mutableListOf<String>()
            while (statement.step()) names.add(statement.getText(1))
            names
        }
}
