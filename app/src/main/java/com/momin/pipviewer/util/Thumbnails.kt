package com.momin.pipviewer.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class ThumbInfo(val bitmap: Bitmap?, val durationMs: Long)

/**
 * Lazily extracts a downscaled cover frame + duration for a video uri, with a small in-memory cache
 * and bounded concurrency so scrolling a big folder never spins up dozens of decoders at once.
 */
object ThumbnailCache {

    private const val TARGET_WIDTH = 480

    private val cache = object : LruCache<String, ThumbInfo>(80) {
        override fun sizeOf(key: String, value: ThumbInfo): Int = 1
    }

    // Limit simultaneous MediaMetadataRetriever instances.
    private val gate = Semaphore(3)

    fun cached(uri: Uri): ThumbInfo? = cache.get(uri.toString())

    suspend fun load(context: Context, uri: Uri): ThumbInfo = withContext(Dispatchers.IO) {
        cache.get(uri.toString())?.let { return@withContext it }
        val info = gate.withPermit { extract(context, uri) }
        cache.put(uri.toString(), info)
        info
    }

    private fun extract(context: Context, uri: Uri): ThumbInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val srcW = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val srcH = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0

            // Grab a frame a little into the clip to avoid black intros.
            val frameUs = if (duration > 2000) 1_000_000L else duration * 1000 / 3

            val bitmap = if (srcW > 0 && srcH > 0) {
                val dstW = TARGET_WIDTH.coerceAtMost(srcW)
                val dstH = (dstW.toFloat() / srcW * srcH).toInt().coerceAtLeast(1)
                retriever.getScaledFrameAtTime(
                    frameUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    dstW,
                    dstH,
                ) ?: retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                retriever.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }

            ThumbInfo(bitmap, duration)
        } catch (t: Throwable) {
            ThumbInfo(null, 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/** Returns the cover frame/duration for [uri], loading it off the main thread on first composition. */
@Composable
fun rememberVideoThumbnail(uri: Uri): ThumbInfo? {
    val context = LocalContext.current
    var info by remember(uri) { mutableStateOf(ThumbnailCache.cached(uri)) }
    LaunchedEffect(uri) {
        if (info == null) info = ThumbnailCache.load(context, uri)
    }
    return info
}
