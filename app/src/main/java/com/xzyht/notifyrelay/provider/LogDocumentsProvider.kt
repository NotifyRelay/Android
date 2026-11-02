package com.xzyht.notifyrelay.provider

import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File

/**
 * 暴露应用私有目录的 DocumentsProvider。
 *
 * Android 11 起第三方文件管理器与 externalstorage 文档提供者都无法进入 Android/data，
 * 应用自身的私有目录只能由自己的 DocumentsProvider 对外提供，
 * 否则「打开日志目录」在系统文件管理器中必然失败。
 *
 * 暴露范围限定在 externalFilesDir，不包含 databases/shared_prefs 等敏感目录。
 */
class LogDocumentsProvider : DocumentsProvider() {
    companion object {
        private const val MIME_TYPE_WILDCARD = "*/*"
        private const val MIME_TYPE_DIR = "vnd.android.document/directory"

        private val DEFAULT_ROOT_PROJECTION =
            arrayOf(
                Root.COLUMN_ROOT_ID,
                Root.COLUMN_FLAGS,
                Root.COLUMN_ICON,
                Root.COLUMN_TITLE,
                Root.COLUMN_DOCUMENT_ID,
                Root.COLUMN_MIME_TYPES,
            )

        private val DEFAULT_DOCUMENT_PROJECTION =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_FLAGS,
                Document.COLUMN_SIZE,
            )
    }

    private lateinit var baseDir: File
    private lateinit var docIdPrefix: String

    private val File.docId
        get() = absolutePath.removePrefix(docIdPrefix)

    private fun fileFromDocId(docId: String) = File(docIdPrefix, docId)

    /** 应用显示名，作为文档提供者条目标题。 */
    private fun appName(): String {
        val context = context ?: return "NotifyRelay"
        return runCatching {
            val appInfo = context.applicationInfo
            context.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault("NotifyRelay")
    }

    /** 应用图标资源 id，作为文档提供者条目图标。 */
    private fun appIconRes(): Int {
        val context = context ?: return android.R.mipmap.sym_def_app_icon
        return runCatching { context.applicationInfo.icon }
            .getOrDefault(android.R.mipmap.sym_def_app_icon)
    }

    override fun onCreate(): Boolean {
        val context = context ?: return false
        // 根节点直接指向应用存储目录（externalFilesDir），
        // docId 因此相对其父目录，形如 files、files/logs。
        baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        docIdPrefix = baseDir.parent + File.separator
        return true
    }

    override fun queryRoots(projection: Array<String>?): android.database.Cursor =
        MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION).apply {
            newRow().apply {
                add(Root.COLUMN_ROOT_ID, baseDir.docId)
                add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_IS_CHILD)
                add(Root.COLUMN_ICON, appIconRes())
                add(Root.COLUMN_TITLE, appName())
                add(Root.COLUMN_DOCUMENT_ID, baseDir.docId)
                add(Root.COLUMN_MIME_TYPES, MIME_TYPE_WILDCARD)
            }
        }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): android.database.Cursor =
        MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
            fillRow(fileFromDocId(documentId))?.let { addRow(it) }
        }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): android.database.Cursor =
        MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).apply {
            fileFromDocId(parentDocumentId)
                .listFiles()
                ?.sortedBy { it.name }
                ?.forEach { file -> fillRow(file)?.let { addRow(it) } }
        }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor = ParcelFileDescriptor.open(fileFromDocId(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean = documentId.startsWith(parentDocumentId)

    private fun fillRow(file: File): Array<Any?>? {
        if (!file.exists()) return null
        return arrayOf(
            file.docId,
            if (file.isDirectory) MIME_TYPE_DIR else MIME_TYPE_WILDCARD,
            file.name,
            file.lastModified(),
            0,
            if (file.isFile) file.length() else 0L,
        )
    }
}
