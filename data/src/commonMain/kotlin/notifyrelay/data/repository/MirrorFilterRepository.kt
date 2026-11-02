package notifyrelay.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import notifyrelay.base.config.FilterConfigDefaults
import notifyrelay.data.database.entity.SuperIslandMirrorFilterEntity
import notifyrelay.data.database.repository.DatabaseRepository

/**
 * 超级岛镜像应用过滤条目仓储。
 *
 * 供设置页读写「镜像应用过滤」的排除名单；条目落在 `super_island_mirror_filter` 表。
 */
object MirrorFilterRepository {
    /** 默认镜像过滤包名（过滤列表的固定前置项）。 */
    val defaultPackages: List<String> get() = FilterConfigDefaults.defaultMirrorPackages

    /** 全部条目。 */
    suspend fun getAll(): List<SuperIslandMirrorFilterEntity> =
        withContext(Dispatchers.IO) {
            DatabaseRepository.getInstance().getAllMirrorFilterPackages()
        }

    /** 已启用的包名。 */
    suspend fun getEnabled(): List<String> =
        withContext(Dispatchers.IO) {
            DatabaseRepository.getInstance().getEnabledMirrorFilterPackages()
        }

    /** 新增或更新条目（启用状态一并写入）。 */
    suspend fun upsert(
        packageName: String,
        enabled: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            DatabaseRepository.getInstance().upsertMirrorFilterPackage(
                SuperIslandMirrorFilterEntity(packageName, enabled = enabled),
            )
        }
    }

    /** 设置条目启用状态。 */
    suspend fun setEnabled(
        packageName: String,
        enabled: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            DatabaseRepository.getInstance().setMirrorFilterEnabled(packageName, enabled)
        }
    }

    /** 删除条目。 */
    suspend fun delete(packageName: String) {
        withContext(Dispatchers.IO) {
            DatabaseRepository.getInstance().deleteMirrorFilterPackage(packageName)
        }
    }
}
