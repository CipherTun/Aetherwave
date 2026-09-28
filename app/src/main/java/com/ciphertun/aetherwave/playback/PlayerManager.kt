package com.ciphertun.aetherwave.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ciphertun.aetherwave.model.Track
import com.ciphertun.aetherwave.data.LibraryStore
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Single app-wide connection to [PlaybackService]. Call [connect] once
 * (e.g. from MainActivity.onCreate) before calling [playQueue]/[play].
 *
 * Owns a real queue now, not just a single track: tapping any track in a
 * results list plays that whole list as a queue (see [playQueue]), with
 * working shuffle, repeat, skip, playback speed and a sleep timer — the
 * baseline set of transport features every mainstream player app has.
 */
object PlayerManager {

    private var controller: MediaController? = null
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepTimerJob: Job? = null

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _nowPlaying = MutableStateFlow<Track?>(null)
    val nowPlaying: StateFlow<Track?> = _nowPlaying

    private val _recentlyPlayed = MutableStateFlow<List<Track>>(emptyList())
    /** Session-only (not persisted) — most-recent first, capped at 15, deduped by track id. */
    val recentlyPlayed: StateFlow<List<Track>> = _recentlyPlayed

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled

    /** One of Player.REPEAT_MODE_OFF / REPEAT_MODE_ALL / REPEAT_MODE_ONE. */
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _sleepTimerEndsAtMillis = MutableStateFlow<Long?>(null)
    val sleepTimerEndsAtMillis: StateFlow<Long?> = _sleepTimerEndsAtMillis

    fun connect(context: Context) {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val id = mediaItem?.mediaId ?: return
                    val idx = _queue.value.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        _currentIndex.value = idx
                        _nowPlaying.value = _queue.value[idx]
                        recordRecentlyPlayed(_queue.value[idx])
                    }
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleEnabled.value = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }
            })
        }, MoreExecutors.directExecutor())
    }

    /** Plays [tracks] as a queue starting at [startIndex] — tapping any row in a list should call this. */
    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setUri(track.streamUrl)
                .setMediaId(track.id)
                .build()
        }
        _queue.value = tracks
        _currentIndex.value = startIndex
        _nowPlaying.value = tracks.getOrNull(startIndex)
        recordRecentlyPlayed(tracks.getOrNull(startIndex))
        controller?.apply {
            setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
            prepare()
            play()
        }
    }

    private fun recordRecentlyPlayed(track: Track?) {
        if (track == null) return
        val current = _recentlyPlayed.value.filterNot { it.id == track.id }
        _recentlyPlayed.value = (listOf(track) + current).take(15)
        LibraryStore.recordRecentlyPlayed(track)
    }

    /** Adds a track to the end of the current queue without interrupting playback. */
    fun addToQueue(track: Track) {
        val current = _queue.value
        if (current.any { it.id == track.id }) return
        _queue.value = current + track
        controller?.addMediaItem(
            MediaItem.Builder()
                .setUri(track.streamUrl)
                .setMediaId(track.id)
                .build()
        )
    }

    /** Places a track immediately after the current item. */
    fun playNext(track: Track) {
        if (controller == null) {
            play(track)
            return
        }
        val currentIndex = controller?.currentMediaItemIndex ?: -1
        val insertAt = (currentIndex + 1).coerceAtLeast(0)
        if (_queue.value.any { it.id == track.id }) return
        val nextQueue = _queue.value.toMutableList().apply {
            add(insertAt.coerceAtMost(size), track)
        }
        _queue.value = nextQueue
        controller?.addMediaItem(insertAt, MediaItem.Builder().setUri(track.streamUrl).setMediaId(track.id).build())
    }

    /** Jump directly to a queue item. */
    fun playQueueIndex(index: Int) {
        val c = controller ?: return
        if (index !in _queue.value.indices) return
        c.seekTo(index, 0L)
        c.play()
    }

    /** Removes a queued item without disturbing the rest of playback. */
    fun removeFromQueue(index: Int) {
        if (index !in _queue.value.indices) return
        _queue.value = _queue.value.toMutableList().also { it.removeAt(index) }
        controller?.removeMediaItem(index)
        if (_queue.value.isEmpty()) {
            _currentIndex.value = -1
            _nowPlaying.value = null
        } else if (index < _currentIndex.value) {
            _currentIndex.value = _currentIndex.value - 1
        }
    }

    /** Convenience for "play just this one" (e.g. a single library/offline item). */
    fun play(track: Track) = playQueue(listOf(track), 0)

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipNext() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.takeIf { it.hasPreviousMediaItem() }?.seekToPreviousMediaItem()
    }

    fun hasNext(): Boolean = controller?.hasNextMediaItem() ?: false
    fun hasPrevious(): Boolean = controller?.hasPreviousMediaItem() ?: false

    fun toggleShuffle() {
        val next = !(_shuffleEnabled.value)
        controller?.shuffleModeEnabled = next
        _shuffleEnabled.value = next
    }

    /** Cycles OFF -> ALL -> ONE -> OFF, matching the order most player apps use. */
    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller?.repeatMode = next
        _repeatMode.value = next
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun scheduleSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimerEndsAtMillis.value = endsAt
        sleepTimerJob = managerScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _sleepTimerEndsAtMillis.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerEndsAtMillis.value = null
    }

    /** Current playback position in ms, 0 if nothing is loaded yet. */
    fun currentPositionMs(): Long = controller?.currentPosition?.coerceAtLeast(0) ?: 0L

    /** Total duration in ms, 0 if unknown (e.g. still buffering, or a live/unbounded stream). */
    fun durationMs(): Long {
        val d = controller?.duration ?: return 0L
        return if (d == C.TIME_UNSET) 0L else d
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun releaseConnection() {
        sleepTimerJob?.cancel()
        controller?.release()
        controller = null
    }
}
