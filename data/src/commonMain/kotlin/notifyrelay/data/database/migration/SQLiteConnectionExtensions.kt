package notifyrelay.data.database.migration

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/*
 * 迁移脚本执行辅助。
 *
 * Room 2.7+ 的 Migration.migrate 统一接收 SQLiteConnection；
 * 这些扩展把常用的「执行 DDL/DML」「读取单个标量」「绑定参数执行」收敛到一处，
 * 使各版本的迁移代码不必反复展开 prepare/step/close。
 */

/** 执行一条无返回值的 SQL。 */
fun SQLiteConnection.execSQL(sql: String) {
    prepare(sql).use { it.step() }
}

/** 执行一条带位置参数（1-based）的 SQL，参数按 [args] 顺序绑定。 */
fun SQLiteConnection.execSQL(
    sql: String,
    args: List<Any?>,
) {
    prepare(sql).use { statement ->
        statement.bindAll(args)
        statement.step()
    }
}

/** 执行查询并返回首行首列的文本；无结果行时返回 null。 */
fun SQLiteConnection.querySingleString(
    sql: String,
    args: List<Any?> = emptyList(),
): String? =
    prepare(sql).use { statement ->
        statement.bindAll(args)
        if (!statement.step()) return null
        if (statement.isNull(0)) null else statement.getText(0)
    }

/** 执行查询并返回首行首列的整数；无结果行时返回 0。 */
fun SQLiteConnection.querySingleLong(
    sql: String,
    args: List<Any?> = emptyList(),
): Long =
    prepare(sql).use { statement ->
        statement.bindAll(args)
        if (!statement.step()) return 0L
        statement.getLong(0)
    }

/** 按 1-based 顺序绑定参数；支持的运行时类型与迁移脚本实际用到的类型一致。 */
private fun SQLiteStatement.bindAll(args: List<Any?>) {
    args.forEachIndexed { index, value ->
        val i = index + 1
        when (value) {
            null -> bindNull(i)
            is String -> bindText(i, value)
            is Int -> bindLong(i, value.toLong())
            is Long -> bindLong(i, value)
            is Boolean -> bindLong(i, if (value) 1L else 0L)
            is Double -> bindDouble(i, value)
            is ByteArray -> bindBlob(i, value)
            else -> bindText(i, value.toString())
        }
    }
}
