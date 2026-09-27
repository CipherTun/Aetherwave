package com.ciphertun.aetherwave.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ciphertun.aetherwave.model.Track
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single app-wide connection to [PlaybackService]. Call [connect] once
 * (e.g. from MainActivity.onStart) before calling [play].
 */
object PlayerManager {

    private var controller: MediaController? = null

    private val _nowPlaying = MutableStateFlow<Track?>(null)
    val nowPlaying: StateFlow<Track?> = _nowPlaying

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    fun connect(context: Context) {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
            })
        }, MoreExecutors.directExecutor())
    }

    fun play(track: Track, uriOverride: String? = null) {
        val item = MediaItem.Builder()
            .setUri(uriOverride ?: track.streamUrl)
            .setMediaId(track.id)
            .build()
        controller?.apply {
            setMediaItem(item)
            prepare()
            play()
        }
        _nowPlaying.value = track
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    /** Current playback position in ms, 0 if nothing is loaded yet. */
    fun currentPositionMs(): Long = controller?.currentPosition?.coerceAtLeast(0) ?: 0L

    /** Total duration in ms, 0 if unknown (e.g. still buffering, or a live/unbounded stream). */
    fun durationMs(): Long {
        val d = controller?.duration ?: return 0L
        return if (d == androidx.media3.common.C.TIME_UNSET) 0L else d
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun releaseConnection() {
        controller?.release()
        controller = null
    }
}
