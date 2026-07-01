package com.momin.pipviewer.ui.library

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.momin.pipviewer.data.FavoriteShelf
import com.momin.pipviewer.data.FolderContents
import com.momin.pipviewer.data.FolderRef
import com.momin.pipviewer.folderRepository
import com.momin.pipviewer.mediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val folders = app.folderRepository
    private val media = app.mediaRepository

    val roots = folders.roots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val starred = folders.starred.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val starredKeys = folders.starredKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * Each starred folder paired with its immediate children (sub-folders + videos), so the home
     * screen can show what's inside a favorite without having to open it. Re-derives whenever the
     * set of favorites changes; the directory query itself runs off the main thread.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteShelves = folders.starred
        .mapLatest { list -> list.map { FavoriteShelf(it, media.listChildren(it)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Current browse path. Empty == home. */
    val path: SnapshotStateList<FolderRef> = emptyList<FolderRef>().toMutableStateList()

    var contents by mutableStateOf(FolderContents.EMPTY)
        private set
    var loading by mutableStateOf(false)
        private set

    val current: FolderRef? get() = path.lastOrNull()

    fun openFolder(folder: FolderRef) {
        path.add(folder)
        refresh()
    }

    /** Returns false when already at home (caller should let the system handle back). */
    fun goBack(): Boolean {
        if (path.isEmpty()) return false
        path.removeAt(path.lastIndex)
        if (path.isNotEmpty()) refresh() else contents = FolderContents.EMPTY
        return true
    }

    fun goHome() {
        path.clear()
        contents = FolderContents.EMPTY
    }

    fun refresh() {
        val folder = path.lastOrNull() ?: return
        loading = true
        viewModelScope.launch {
            contents = media.listChildren(folder)
            loading = false
        }
    }

    fun addRoot(treeUri: Uri) {
        viewModelScope.launch {
            media.persist(treeUri)
            val name = media.rootName(treeUri)
            folders.addRoot(FolderRef.root(treeUri, name))
        }
    }

    fun removeRoot(folder: FolderRef) {
        viewModelScope.launch { folders.removeRoot(folder) }
    }

    fun setStarred(folder: FolderRef, starred: Boolean) {
        viewModelScope.launch { folders.setStarred(folder, starred) }
    }
}
