# `:data`

KMP 库。源集分布：Room 声明（实体 / DAO / `@Database`）、仓储与存储逻辑、纯配置模型位于 `data/src/commonMain/`；Room 打开与迁移（`AppDatabaseFactory`）、旧文件/首选项迁移（`MigrationHelper`、`PersistenceManager`、`DeviceInfoManager`）位于 `data/src/androidMain/`。

存储统一入口为 `notifyrelay.data.Storage`（无 `Context`）；Android 在进程启动时由 `NotifyRelayApplication` 调用 `DataLayerAndroid.initialize(context)` 注入数据库仓库实例，此后各平台共用同一套存储逻辑。`:app`、`:superislandui`、`:scrcpy` 均已改用该入口；兼容外壳 `StorageManager` 已移除。

| 文件 | 类/对象 | 主要公开方法 |
|---|---|---|
| `PersistenceManager.kt` | `object PersistenceManager` | `readNotificationRecords<T>(...)`, `getAllNotificationFiles(context)`, `getLocalDeviceUuid(context)` |
| `Storage.kt`（commonMain） | `object Storage` | 平台无关存储入口：`initialize(repository)` / `isInitialized()` + `getString/putString/getBoolean/putBoolean/putBooleans/getInt/putInt/getFloat/putFloat/getLong/putLong/putStrings/putInts/getStringSet/putStringSet/remove/clear/migrateData`（按 `Storage.PrefsType` 自动加键前缀；阻塞式，内部经 `runBlockingCommon`） |
| `StorageCore.kt`（commonMain） | `class StorageCore` | 存储业务核心（`suspend` 实现，供 `Storage` 调用） |
| `StringSetJsonCodec.kt`（commonMain） | `object StringSetJsonCodec` | `Set<String>` 的 JSON 数组编解码（与历史 Gson 输出逐字节一致） |
| `Blocking.kt` / `Blocking.android.kt` | 顶层 `expect/actual fun runBlockingCommon` | 公共端阻塞执行入口（Android actual = `runBlocking`） |
| `DataLayerAndroid.kt`（androidMain） | `object DataLayerAndroid` | `initialize(context)` / `ensureInitialized(context)` / `createRepository(context)` |
| `FilterConfigDefaults.kt` | `object FilterConfigDefaults` | 过滤规则默认常量 |
| `config/DeviceInfoManager.kt` | `object DeviceInfoManager` | `generateDeviceInfoFile(context)` |
| `config/ScrcpyDefaults.kt` | `object ScrcpyDefaults` | Scrcpy 配置项默认值 |
| `config/ScrcpyPreferenceKeys.kt` | `object ScrcpyPreferenceKeys` | Scrcpy 偏好键常量 |
| `config/ScrcpyPresets.kt` | `object ScrcpyPresets` | Scrcpy 预设 |
| `model/ScrcpyModels.kt` | `ConnectionTarget`, `OnlineDeviceInfo`, `SelectedDeviceInfo` | 数据模型 |
| `database/AppDatabase.kt`（commonMain） | `abstract class AppDatabase : RoomDatabase` | `@Database(13 实体, version = 9, exportSchema = false)` + `appConfigDao()`, `appDao()`, `appDeviceDao()`, `notificationRecordDao()`, `superIslandHistoryDao()`, `superIslandImageDao()`, `superIslandMirrorFilterDao()`, `filterListDao()` |
| `database/AppDatabaseFactory.kt`（androidMain） | `object AppDatabaseFactory` | `getDatabase(context)`：`Room.databaseBuilder` + onCreate 回调 + 迁移 `MIGRATION_1_2` … `MIGRATION_8_9` + `migrateFromLegacyStorage(...)` |
| `dao/AppConfigDao.kt` | `interface AppConfigDao` | `getAll()`, `getValue(key)`, `insert(config)`, `insertAll(configs)`, `delete(config)`, `deleteByKey(key)` |
| `dao/AppDao.kt` | `interface AppDao` | `getAll(): Flow`, `getByPackageName`, `getByPackageNames`, `insert`, `insertAll`, `delete`, `deleteByPackageName`, `getIconMissingApps(): Flow`, `updateIcon`, `markIconAsMissing`, `getExpiredApps(expiryTime)` |
| `dao/AppDeviceDao.kt` | `interface AppDeviceDao` | `getAll(): Flow`, `getByPackageName(): Flow`, `getByPackageNames`, `getByDeviceUuid(): Flow`, `getByPackageNameAndDeviceUuid`, `insert`, `insertAll`, `delete`, `deleteByPackageName`, `deleteByDeviceUuid`, `deleteByPackageNameAndDeviceUuid` |
| `dao/FilterListDao.kt` | `interface FilterListDao` | 黑/白名单、过滤条目、包名分组的查询与 `replace*` 批量替换 |
| `dao/NotificationRecordDao.kt` | `interface NotificationRecordDao` | `getByDevice`, `getAll`, `getByKey`, `insert`, `insertAll`, `delete`, `deleteByKey`, `deleteByDevice`, `deleteOldRecords`, `countByDevice`, `countByPackageAndDevice`, `deleteByPackageAndDevice`, `getByPackageAndDevice`, `deleteOldestByPackageAndDevice`, `getPagingSourceByDevice`, `getPackageCountByDevice` |
| `dao/SuperIslandHistoryDao.kt` | `interface SuperIslandHistoryDao` | `getAllHistory`, `getAllHistorySummary`, `upsertByFeatureAndContent`, `getLatestByDistinctFeatureId`, `getPackageCount`, `deleteByPackage`, `getCount` 等 20+ 方法 |
| `dao/SuperIslandImageDao.kt` | `interface SuperIslandImageDao` | `getImageIdByHash`, `getImageDataById`, `getImageDataByBinding`, `insertImage`, `upsertBinding`, `touchImage`, `deleteImagesOlderThan`, `deleteImagesKeepingLatest`, `clearAllBindings`, `clearAllImages` |
| `dao/SuperIslandMirrorFilterDao.kt` | `interface SuperIslandMirrorFilterDao` | `getEnabledPackages`, `getAllPackages`, `upsert`, `setEnabled` |
| `migration/MigrationHelper.kt` | `object MigrationHelper` | `migrateAppConfig`, `migrateNotifications`, `migrateSuperIslandHistory`, `migrateApps`（均为 `suspend`）, `cleanupLegacyStorage(context)`, `shouldMigrate(context)` |
| `repository/DatabaseRepository.kt`（commonMain） | `open class DatabaseRepository` | 全部 DAO 的门面封装（81 方法，含 `getBlackList/replaceBlackList`、`getWhiteList/replaceWhiteList`、`getLocalFilterEntries/replaceLocalFilterEntries`、`getPackageGroups/replacePackageGroups`、超级岛图片/历史/镜像过滤等）；`companion` 提供 `initialize(repository)` / `isInitialized()` / `getInstance()` |
| `repository/applist/PinnedAppsRepository.kt` | `object PinnedAppsRepository` | 置顶应用的唯一持有者：`pinnedApps: StateFlow`, `loadPinnedApps(deviceUuid)`, `pinApp(...)`, `unpinApp(...)`, `isAppPinned(...)`（键 `pinned_apps_<uuid>` 存 `Storage`） |
| `filter/LocalFilterRepository.kt` | `object LocalFilterRepository` | 本机过滤条目与开关：`getFilterEntries` / `getEnabledFilterEntries` / `setFilterEntryEnabled` / `addFilterEntry` / `removeFilterEntry` / `getBuiltinKeywords` / `getDefaultPackageFilters` / `getEnabledPackageFilters` / `setPackageEnabled`；内置条目不可删除、表空时初始化 |
| `filter/RemoteFilterRepository.kt` | `class RemoteFilterRepository : RemoteFilterPort` | 远端过滤配置：`load`/`save`（`Mutex` 串行化）、黑白名单与包名组、`buildRustConfigJson(installedPackages)`、`serializeFilterEntry`、`syncToRust`、`mapToLocalPackage`、`checkFilterAccepted`（Rust 与已安装包经 `NativeFilterPort` / `InstalledPackagesPort` 注入，未就绪时 fail-open） |
| `config/SuperIslandConfigPolicy.kt` | `object SuperIslandConfigPolicy` | 超级岛配置的键、编码与互斥推导唯一实现：`isFloatingWindowEnabled` / `setFloatingWindowEnabled` / `isNotificationListMode` / `setNotificationListMode` / `resolveDisplayChannel` / `needClickIntent` / `specInjectionMode` / `isRemoteDisplayEnabled` / `isMirrorFilterEnabled`（平台差异经 `FormFactorPort`） |
| `repository/MirrorFilterRepository.kt` | `object MirrorFilterRepository` | 镜像过滤条目：`defaultPackages`, `getAll()`, `getEnabled()`, `upsert(...)`, `setEnabled(...)`, `delete(...)` |
| `database/paging/SuperIslandHistoryPagingSource.kt` | `class SuperIslandHistoryPagingSource` | 超级岛历史分页源（按包名分组；`picMap` 用 kotlinx.serialization 解析，数字图片 id 反查图片原文） |
| `database/history/SuperIslandHistoryStore.kt` | `object SuperIslandHistoryStore` + `SuperIslandHistoryStoreEntry` | 历史写入与详情：`append(entry)`（图片先绑定为 id）、`loadEntryDetail(id)`、`clearAll()`、`migrateLegacyImages()`（经 `LegacyStoragePort`） |
