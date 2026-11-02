# `:checkupdata`

KMP 库，目标为 `android` 与 `jvm`。全部业务逻辑位于 commonMain；平台差异经 `internal expect` 声明在 androidMain/jvmMain 各自实现，并经同名工厂函数暴露给调用方。

| 源集 | 内容 |
|---|---|
| commonMain | `CheckUpdateManager`、`GitHubApiClient`/`GitHubApiService`、`UpdateCache`、`SystemDownloader`、`CheckUpdateConfig`/`ReleaseInfo`/`UpdateResult`、`VersionComparator`/`VersionRule`、`internal/Platform.kt`（平台 seam 声明）、`internal/JsonSupport.kt` |
| androidMain | `Platform.android.kt`（IO 调度器、时间、OkHttp 请求、文件读写）、`AndroidDownloadStarter`（系统 DownloadManager）、`PlatformFactories.kt`（`CheckUpdateManager(context)`、`SystemDownloader(context)`） |
| jvmMain | `Platform.jvm.kt`、`JvmDownloadStarter`（同步下载到缓存目录）、`PlatformFactories.kt`（`CheckUpdateManager(cacheRootDir)`、`SystemDownloader(cacheRootDir)`） |

| 类 | 职责 |
|---|---|
| `CheckUpdateManager` | 更新检查入口，编排请求/缓存/下载（构造器为 internal，平台侧经同名工厂函数构造） |
| `GitHubApiClient` / `GitHubApiService` | GitHub Release API 客户端与接口定义 |
| `UpdateCache` | 更新检查结果缓存（`saveCache`/`loadCache`/`clearCache`，均接收缓存根目录） |
| `SystemDownloader` | 下载发行包；Android 经系统 DownloadManager，JVM 直接写入缓存目录 |
| `CheckUpdateConfig` / `ReleaseInfo` / `UpdateResult` | 配置、Release 信息、结果（sealed） |
| `VersionComparator` / `VersionRule` | 版本号比较与规则 |

JSON 采用 `kotlinx.serialization`：`ReleaseInfo.toJson()`/`fromJson()` 与 `ReleaseAsset` 同名成员的类型为 `kotlinx.serialization.json.JsonObject`。
