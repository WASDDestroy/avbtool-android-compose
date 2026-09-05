package me.wasddestroy.avbtoolandroid.partition

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.DocumentsContract
/**
 * SAF tree-folder helpers built directly on [DocumentsContract] so no extra
 * documentfile dependency is needed. The workspace is one picked directory;
 * dumped images are written there as `<partition>.img`.
 */
internal object WorkspaceFolder {

    /** Directory display name of a tree URI, e.g. "Downloads". */
    fun displayName(context: Context, treeUri: Uri): String? {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return runCatching {
            context.contentResolver.query(
                docUri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    /** Deletes an existing child so it can be recreated under the same name. */
    fun deleteChild(context: Context, treeUri: Uri, name: String): Boolean {
        val docUri = childUri(context, treeUri, name) ?: return false
        return runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, docUri)
        }.getOrDefault(false)
    }

    fun childExists(context: Context, treeUri: Uri, name: String): Boolean =
        childUri(context, treeUri, name) != null

    fun createChild(context: Context, treeUri: Uri, name: String): Uri? {
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return runCatching {
            DocumentsContract.createDocument(
                context.contentResolver,
                dirUri,
                "application/octet-stream",
                name,
            )
        }.getOrNull()
    }

    /**
     * Free bytes of the storage the tree lives on. Only resolvable on API 30+;
     * null means "unknown, skip the check". Some tree URIs cannot be mapped to
     * a storage volume, which throws — treat that as unknown too.
     */
    fun freeSpace(context: Context, treeUri: Uri): Long? {
        if (Build.VERSION.SDK_INT < 30) return null
        val manager = context.getSystemService(StorageManager::class.java) ?: return null
        return runCatching { manager.getStorageVolume(treeUri)?.directory?.usableSpace }.getOrNull()
    }

    private fun childUri(context: Context, treeUri: Uri, name: String): Uri? {
        val parentId = DocumentsContract.getTreeDocumentId(treeUri)
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        return runCatching {
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == name) {
                        return@runCatching DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            cursor.getString(0),
                        )
                    }
                }
                null
            }
        }.getOrNull()
    }
}
