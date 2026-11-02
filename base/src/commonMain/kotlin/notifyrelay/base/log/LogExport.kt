package notifyrelay.base.log

/**
 * 把日志目录下的全部分片打包为单个归档文件。
 *
 * 用于「导出日志」：用户只需回传一个文件即可完整还原现场。
 *
 * @param sourceDirectory 日志目录路径
 * @param targetArchive 目标归档文件路径
 * @return 归档是否成功
 */
expect fun zipLogShards(
    sourceDirectory: String,
    targetArchive: String,
): Boolean
