package com.momin.pipviewer

import android.app.Application
import com.momin.pipviewer.data.FolderRepository
import com.momin.pipviewer.data.MediaRepository

class App : Application() {
    val folderRepository: FolderRepository by lazy { FolderRepository(this) }
    val mediaRepository: MediaRepository by lazy { MediaRepository(this) }
}

val Application.folderRepository: FolderRepository get() = (this as App).folderRepository
val Application.mediaRepository: MediaRepository get() = (this as App).mediaRepository
