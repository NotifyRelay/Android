package com.xzyht.notifyrelay.util

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast

/**
 * 打开应用私有日志目录的系统文件管理器入口。
 *
 * Android 11 起 externalstorage 文档提供者禁止进入 Android/data，
 * 因此必须由应用自身的 LogDocumentsProvider 暴露该目录；
 * 这里构造该提供者的文档 URI，交给系统文件管理器打开。
 */
object LogDirectoryOpener {
    /**
     * 打开私有日志目录。
     *
     * @param context 上下文
     * @param subDirectory 相对 externalFilesDir 的子目录，例如 logs
     * @return 是否成功唤起文件管理器
     */
    fun openLogDirectory(
        context: Context,
        subDirectory: String = "logs",
    ): Boolean {
        val authority = context.packageName + ".logs"
        // documentId 相对 externalFilesDir 的父目录，形如 files/logs
        val documentId = "files/" + subDirectory
        val intents =
            buildList {
                add(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            DocumentsContract.buildDocumentUri(authority, documentId),
                            DocumentsContract.Document.MIME_TYPE_DIR,
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                add(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            DocumentsContract.buildRootUri(authority, "files"),
                            DocumentsContract.Root.MIME_TYPE_ITEM,
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }

        for (intent in intents) {
            val ok =
                runCatching {
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    /** 唤起失败时提示用户路径并复制。 */
    fun reportFailure(
        context: Context,
        directory: String,
    ) {
        runCatching {
            val cm =
                context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("notifyrelay", directory))
        }
        Toast.makeText(context, "已复制路径，请在文件管理器粘贴访问", Toast.LENGTH_LONG).show()
    }
}
