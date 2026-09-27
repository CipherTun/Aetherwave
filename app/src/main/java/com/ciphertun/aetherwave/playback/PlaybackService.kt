package com.ciphertun.aetherwave.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Standard Media3 MediaSessionService boilerplate: hosts the real ExoPlayer
 * instance so playback survives the app going to background, and exposes it
 * through a MediaSession so the lock screen / notification get transport
 * controls for free.
 */
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            // Keeps the CPU/Wi-Fi radio awake while something is actually
            // playing — otherwise streaming can stutter or drop mid-track
            // once the screen locks. Requires the WAKE_LOCK permission.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
