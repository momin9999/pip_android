@file:OptIn(UnstableApi::class)

package com.momin.pipviewer.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.momin.pipviewer.R
import com.momin.pipviewer.util.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class Indicator { NONE, BRIGHTNESS, VOLUME }

@Composable
fun PlayerScreen(
    player: ExoPlayer?,
    isInPip: Boolean,
    hasPlaylist: Boolean,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (player != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { it.player = player },
            )
        }

        if (player != null && !isInPip) {
            PlayerControls(
                player = player,
                hasPlaylist = hasPlaylist,
                onClose = onClose,
                onEnterPip = onEnterPip,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    player: ExoPlayer,
    hasPlaylist: Boolean,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val window = activity?.window
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var title by remember { mutableStateOf(player.currentTitle()) }

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var lockHint by remember { mutableStateOf(false) }
    var lockNonce by remember { mutableIntStateOf(0) }

    var brightness by remember { mutableFloatStateOf(initialBrightness(window)) }
    var volume by remember { mutableFloatStateOf(audioManager.volumeFraction(maxVolume)) }

    var indicator by remember { mutableStateOf(Indicator.NONE) }
    var indicatorLevel by remember { mutableFloatStateOf(0f) }
    var indicatorNonce by remember { mutableIntStateOf(0) }

    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    var seekSeconds by remember { mutableIntStateOf(0) }
    var seekNonce by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                durationMs = player.duration.coerceAtLeast(0L)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                title = player.currentTitle()
                positionMs = 0L
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            if (!scrubbing) positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0L)
            delay(400)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, locked) {
        if (controlsVisible && isPlaying && !locked) {
            delay(3800)
            controlsVisible = false
        }
    }

    LaunchedEffect(indicatorNonce) {
        if (indicator != Indicator.NONE) {
            delay(850)
            indicator = Indicator.NONE
        }
    }

    LaunchedEffect(seekNonce) {
        if (seekSeconds != 0) {
            delay(700)
            seekSeconds = 0
        }
    }

    LaunchedEffect(lockNonce) {
        if (lockHint) {
            delay(2500)
            lockHint = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

    // Gesture layer (bottom-most): tap, double-tap seek, vertical drags for brightness/volume.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = {
                        if (locked) {
                            lockHint = true
                            lockNonce++
                        } else {
                            controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!locked) {
                            if (offset.x < size.width / 2f) {
                                player.seekBack()
                                seekSeconds = -10
                            } else {
                                player.seekForward()
                                seekSeconds = 10
                            }
                            positionMs = player.currentPosition
                            seekNonce++
                        }
                    },
                )
            }
            .pointerInput(locked) {
                if (locked) return@pointerInput
                var onLeft = true
                detectVerticalDragGestures(
                    onDragStart = { offset -> onLeft = offset.x < size.width / 2f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val delta = -dragAmount / size.height.toFloat().coerceAtLeast(1f)
                        if (onLeft) {
                            brightness = (brightness + delta).coerceIn(0f, 1f)
                            applyBrightness(window, brightness)
                            indicator = Indicator.BRIGHTNESS
                            indicatorLevel = brightness
                        } else {
                            volume = (volume + delta).coerceIn(0f, 1f)
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (volume * maxVolume).roundToInt(),
                                0,
                            )
                            indicator = Indicator.VOLUME
                            indicatorLevel = volume
                        }
                        indicatorNonce++
                    },
                )
            },
    )

    // Buffering spinner stays visible regardless of controls.
    if (playbackState == Player.STATE_BUFFERING) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
        }
    }

    // Main controls.
    AnimatedVisibility(
        visible = controlsVisible && !locked,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.25f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.7f),
                    )
                ),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )
                IconButton(onClick = {
                    locked = true
                    controlsVisible = false
                }) {
                    Icon(Icons.Rounded.LockOpen, stringResource(R.string.lock), tint = Color.White)
                }
                IconButton(onClick = onEnterPip) {
                    Icon(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.pip), tint = Color.White)
                }
            }

            // Center transport
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (hasPlaylist) {
                    ControlButton(Icons.Rounded.SkipPrevious, R.string.previous, 34.dp) {
                        player.seekToPreviousMediaItem()
                    }
                }
                ControlButton(Icons.Rounded.Replay10, R.string.rewind, 38.dp) {
                    player.seekBack(); positionMs = player.currentPosition
                }
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable {
                            if (isPlaying) player.pause() else player.play()
                            controlsVisible = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        tint = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                }
                ControlButton(Icons.Rounded.Forward10, R.string.fast_forward, 38.dp) {
                    player.seekForward(); positionMs = player.currentPosition
                }
                if (hasPlaylist) {
                    ControlButton(Icons.Rounded.SkipNext, R.string.next, 34.dp) {
                        player.seekToNextMediaItem()
                    }
                }
            }

            // Bottom seek bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                val fraction = when {
                    scrubbing -> scrubFraction
                    durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    else -> 0f
                }
                val shownPosition = if (scrubbing) (scrubFraction * durationMs).toLong() else positionMs
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatDuration(shownPosition),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    Slider(
                        value = fraction,
                        onValueChange = {
                            scrubbing = true
                            scrubFraction = it
                        },
                        onValueChangeFinished = {
                            if (durationMs > 0) player.seekTo((scrubFraction * durationMs).toLong())
                            positionMs = player.currentPosition
                            scrubbing = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                    )
                    Text(
                        formatDuration(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }

    // Brightness / volume indicator
    AnimatedVisibility(
        visible = indicator != Indicator.NONE,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center),
    ) {
        IndicatorCard(indicator, indicatorLevel)
    }

    // Double-tap seek feedback
    AnimatedVisibility(
        visible = seekSeconds != 0,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(if (seekSeconds < 0) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = 48.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (seekSeconds < 0) Icons.Rounded.Replay10 else Icons.Rounded.Forward10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("10s", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }

    // Locked: tap reveals an unlock button only.
    AnimatedVisibility(
        visible = locked && lockHint,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.CenterStart)
            .systemBarsPadding()
            .padding(start = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable {
                    locked = false
                    controlsVisible = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Lock, stringResource(R.string.unlock), tint = Color.White)
        }
    }

    } // end overlay Box
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: Int,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        interactionSource = remember { MutableInteractionSource() },
    ) {
        Icon(
            icon,
            contentDescription = stringResource(contentDesc),
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun IndicatorCard(indicator: Indicator, level: Float) {
    val isAuto = indicator == Indicator.BRIGHTNESS && level <= 0.001f
    val label = when {
        isAuto -> stringResource(R.string.brightness_auto)
        else -> "${(level * 100).roundToInt()}%"
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val icon = when (indicator) {
            Indicator.BRIGHTNESS -> Icons.Rounded.LightMode
            Indicator.VOLUME -> if (level <= 0.001f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp
            Indicator.NONE -> Icons.Rounded.LightMode
        }
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(110.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

/* ---- helpers ---- */

private fun Player.currentTitle(): String =
    currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun initialBrightness(window: Window?): Float {
    val sb = window?.attributes?.screenBrightness ?: -1f
    return if (sb in 0f..1f) sb else 0f
}

private fun applyBrightness(window: Window?, fraction: Float) {
    val lp = window?.attributes ?: return
    lp.screenBrightness = if (fraction <= 0.001f) {
        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    } else {
        fraction.coerceIn(0.01f, 1f)
    }
    window.attributes = lp
}

private fun AudioManager.volumeFraction(max: Int): Float =
    (getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max).coerceIn(0f, 1f)
