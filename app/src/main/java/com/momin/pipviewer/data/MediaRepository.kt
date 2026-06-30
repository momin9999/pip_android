package com.momin.pipviewer.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads folders/videos out of a SAF document tree using direct ContentResolver queries. */
class MediaRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    /** Persist read access so the picked tree survives reboots. */
    fun persist(treeUri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(treeUri, flags) }
    }

    /** Display name for a freshly picked tree (falls back to the last path segment). */
    suspend fun rootName(treeUri: Uri): String = withContext(Dispatchers.IO) {
        runCatching { DocumentFile.fromTreeUri(context, treeUri)?.name }.getOrNull()
            ?: treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?: treeUri.toString()
    }

    suspend fun listChildren(folder: FolderRef): FolderContents = withContext(Dispatchers.IO) {
        val folders = ArrayList<FolderRef>()
        val videos = ArrayList<VideoItem>()

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        runCatching {
            resolver.query(folder.childrenUri, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val dateCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (c.moveToNext()) {
                    val docId = c.getString(idCol) ?: continue
                    val name = c.getString(nameCol) ?: docId
                    val mime = c.getString(mimeCol)
                    val size = if (c.isNull(sizeCol)) 0L else c.getLong(sizeCol)
                    val date = if (c.isNull(dateCol)) 0L else c.getLong(dateCol)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        folders.add(FolderRef(folder.treeUri, docId, name))
                    } else if (isVideo(mime, name)) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(folder.treeUri, docId)
                        videos.add(VideoItem(uri, name, size, date, mime))
                    }
                }
            }
        }

        folders.sortBy { it.name.lowercase() }
        videos.sortBy { it.name.lowercase() }
        FolderContents(folders, videos)
    }

    private fun isVideo(mime: String?, name: String): Boolean {
        if (mime != null && mime.startsWith("video/")) return true
        if (mime == "application/x-mpegURL" || mime == "application/vnd.apple.mpegurl") return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "3g2", "ts", "m2ts", "mts",
            "flv", "wmv", "mpg", "mpeg", "ogv", "vob", "f4v", "rmvb", "divx", "m3u8",
        )
    }
}
