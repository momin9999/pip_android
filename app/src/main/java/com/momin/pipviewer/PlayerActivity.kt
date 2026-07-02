@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.momin.pipviewer

import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.momin.pipviewer.ui.player.PlayerScreen
import com.momin.pipviewer.ui.theme.PIPViewerTheme

class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private val playerState = mutableStateOf<ExoPlayer?>(null)
    private val inPipState = mutableStateOf(false)

    private var mediaItems: List<MediaItem> = emptyList()
    private var startIndex = 0
    private var startPositionMs = 0L
    private var playWhenReady = true
    private var videoAspect = Rational(16, 9)

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                videoAspect = clampAspect(videoSize.width, videoSize.height)
                updatePipParams()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePipParams()
        }

        override fun onPlayerError(error: PlaybackException) {
            // A single broken/unsupported file shouldn't freeze the whole playlist:
            // skip to the next item when possible, otherwise tell the user.
            val exo = player ?: return
            if (exo.hasNextMediaItem()) {
                exo.seekToNextMediaItem()
                exo.prepare()
            } else {
                Toast.makeText(this@PlayerActivity, R.string.playback_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PIP_CONTROL) return
            when (intent.getIntExtra(EXTRA_CONTROL, 0)) {
                CONTROL_PLAY -> player?.play()
                CONTROL_PAUSE -> player?.pause()
                CONTROL_REPLAY -> player?.seekBack()
                CONTROL_FORWARD -> player?.seekForward()
            }
            updatePipParams()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        if (!loadIntent(intent)) {
            Toast.makeText(this, R.string.cannot_open_video, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            IntentFilter(ACTION_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            PIPViewerTheme(darkTheme = true) {
                PlayerScreen(
                    player = playerState.value,
                    isInPip = inPipState.value,
                    hasPlaylist = mediaItems.size > 1,
                    onClose = { finish() },
                    onEnterPip = { enterPip() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        releasePlayer()
        if (loadIntent(intent)) initPlayer()
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onStop() {
        super.onStop()
        // While a PIP window is still on screen (e.g. the user opened another app)
        // the activity is stopped but the PIP surface keeps rendering, so we must
        // keep the player alive — otherwise playback would blank out / the PIP
        // would close. A truly finishing activity is torn down in onDestroy().
        if (!isInPictureInPictureMode) {
            releasePlayer()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // On API 31+ the system auto-enters via setAutoEnterEnabled.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && player?.isPlaying == true) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipState.value = isInPictureInPictureMode
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(pipReceiver) }
        releasePlayer()
    }

    /* ---- player lifecycle ---- */

    private fun initPlayer() {
        if (player != null || mediaItems.isEmpty()) return
        val exo = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        exo.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), startPositionMs)
        exo.playWhenReady = playWhenReady
        exo.addListener(playerListener)
        exo.prepare()
        player = exo
        playerState.value = exo
        updatePipParams()
    }

    private fun releasePlayer() {
        player?.let {
            startPositionMs = it.currentPosition
            startIndex = it.currentMediaItemIndex
            playWhenReady = it.playWhenReady
            it.removeListener(playerListener)
            it.release()
        }
        player = null
        playerState.value = null
    }

    /* ---- PIP ---- */

    private fun enterPip() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { enterPictureInPictureMode(buildPipParams()) }
    }

    private fun updatePipParams() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { setPictureInPictureParams(buildPipParams()) }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(videoAspect)
            .setActions(buildPipActions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun buildPipActions(): List<RemoteAction> {
        val playing = player?.isPlaying == true
        val playPause = if (playing) {
            remoteAction(R.drawable.ic_pause, R.string.pause, CONTROL_PAUSE)
        } else {
            remoteAction(R.drawable.ic_play_arrow, R.string.play, CONTROL_PLAY)
        }
        return listOf(
            remoteAction(R.drawable.ic_replay_10, R.string.rewind, CONTROL_REPLAY),
            playPause,
            remoteAction(R.drawable.ic_forward_10, R.string.fast_forward, CONTROL_FORWARD),
        )
    }

    private fun remoteAction(iconRes: Int, labelRes: Int, control: Int): RemoteAction {
        val label = getString(labelRes)
        val pending = android.app.PendingIntent.getBroadcast(
            this,
            control,
            Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_CONTROL, control),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(this, iconRes), label, label, pending)
    }

    private fun clampAspect(width: Int, height: Int): Rational {
        val raw = width.toDouble() / height.toDouble()
        val clamped = raw.coerceIn(0.42, 2.38)
        return Rational((clamped * 1000).toInt(), 1000)
    }

    /* ---- intent ---- */

    private fun loadIntent(intent: Intent): Boolean {
        val uris = intent.getStringArrayListExtra(EXTRA_URIS)
        if (!uris.isNullOrEmpty()) {
            val titles = intent.getStringArrayListExtra(EXTRA_TITLES)
            mediaItems = uris.mapIndexed { i, u -> mediaItem(Uri.parse(u), titles?.getOrNull(i)) }
            startIndex = intent.getIntExtra(EXTRA_INDEX, 0)
            startPositionMs = 0L
            return true
        }
        val data = intent.data
        if (data != null) {
            mediaItems = listOf(mediaItem(data, queryDisplayName(data)))
            startIndex = 0
            startPositionMs = 0L
            return true
        }
        return false
    }

    private fun mediaItem(uri: Uri, title: String?): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        if (!title.isNullOrBlank()) {
            builder.setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        }
        return builder.build()
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) return c.getString(0) }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        const val EXTRA_URIS = "extra_uris"
        const val EXTRA_TITLES = "extra_titles"
        const val EXTRA_INDEX = "extra_index"

        private const val ACTION_PIP_CONTROL = "com.momin.pipviewer.PIP_CONTROL"
        private const val EXTRA_CONTROL = "control"
        private const val CONTROL_PLAY = 1
        private const val CONTROL_PAUSE = 2
        private const val CONTROL_REPLAY = 3
        private const val CONTROL_FORWARD = 4
    }
}
