package com.momin.pipviewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.momin.pipviewer.data.VideoItem
import com.momin.pipviewer.ui.library.LibraryScreen
import com.momin.pipviewer.ui.library.LibraryViewModel
import com.momin.pipviewer.ui.theme.PIPViewerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PIPViewerTheme(darkTheme = isSystemInDarkTheme()) {
                val vm: LibraryViewModel = viewModel()
                LibraryScreen(
                    vm = vm,
                    onPlay = ::launchPlayer,
                )
            }
        }
    }

    private fun launchPlayer(videos: List<VideoItem>, index: Int) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putStringArrayListExtra(
                PlayerActivity.EXTRA_URIS,
                ArrayList(videos.map { it.uri.toString() }),
            )
            putStringArrayListExtra(
                PlayerActivity.EXTRA_TITLES,
                ArrayList(videos.map { it.name }),
            )
            putExtra(PlayerActivity.EXTRA_INDEX, index)
        }
        startActivity(intent)
    }
}
