package com.momin.pipviewer.data

import android.net.Uri
import android.provider.DocumentsContract

/**
 * A reference to a browsable folder living inside a SAF document tree.
 *
 * We keep the owning [treeUri] together with the folder's [documentId] so that we can build a
 * children query for any folder in the tree (root or nested) without holding a heavyweight
 * DocumentFile around.
 */
data class FolderRef(
    val treeUri: Uri,
    val documentId: String,
    val name: String,
) {
    /** Stable, unique key for this folder (its document uri). Used for ★ membership + dedup. */
    val key: String
        get() = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()

    val childrenUri: Uri
        get() = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)

    fun encode(): String = listOf(treeUri.toString(), documentId, name).joinToString(SEP)

    companion object {
        private const val SEP = ""

        fun decode(value: String): FolderRef? {
            val parts = value.split(SEP)
            if (parts.size < 3) return null
            return FolderRef(
                treeUri = Uri.parse(parts[0]),
                documentId = parts[1],
                name = parts.subList(2, parts.size).joinToString(SEP),
            )
        }

        /** Build the root folder ref for a freshly picked tree uri. */
        fun root(treeUri: Uri, name: String): FolderRef = FolderRef(
            treeUri = treeUri,
            documentId = DocumentsContract.getTreeDocumentId(treeUri),
            name = name,
        )
    }
}

data class VideoItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val mimeType: String?,
)

data class FolderContents(
    val folders: List<FolderRef>,
    val videos: List<VideoItem>,
) {
    val isEmpty: Boolean get() = folders.isEmpty() && videos.isEmpty()

    companion object {
        val EMPTY = FolderContents(emptyList(), emptyList())
    }
}
