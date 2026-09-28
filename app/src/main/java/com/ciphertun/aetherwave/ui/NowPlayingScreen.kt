package com.ciphertun.aetherwave.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.ciphertun.aetherwave.model.Track
import com.ciphertun.aetherwave.playback.PlayerManager
import com.ciphertun.aetherwave.ui.theme.NeonCyan
import com.ciphertun.aetherwave.ui.theme.NeonPink
import com.ciphertun.aetherwave.ui.theme.NeonPurple
import com.ciphertun.aetherwave.ui.theme.VoidBlack
import kotlinx.coroutines.delay

private val SPEED_PRESETS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val SLEEP_TIMER_OPTIONS = listOf(10, 20, 30, 45, 60)

@Composable
fun NowPlayingScreen(
    track: Track,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isDownloadable: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    playbackSpeed: Float,
    sleepTimerEndsAtMillis: Long?,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onCollapse: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onScheduleSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit
) {
    var positionMs by remember(track.id) { mutableFloatStateOf(0f) }
    var durationMs by remember(track.id) { mutableFloatStateOf(0f) }
    var userSeeking by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var sleepMenuOpen by remember { mutableStateOf(false) }

    // Poll the controller for real position/duration — Media3 doesn't push
    // per-second updates on its own, so this is the standard approach.
    LaunchedEffect(track.id, isPlaying) {
        while (true) {
            if (!userSeeking) {
                positionMs = PlayerManager.currentPositionMs().toFloat()
                val d = PlayerManager.durationMs().toFloat()
                if (d > 0f) durationMs = d
            }
            delay(500)
        }
    }

    val breathScale by rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.035f else 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breatheScale"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VoidBlack, MaterialTheme.colorScheme.surface, VoidBlack)
                )
            )
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse")
                }
                Spacer(Modifier.weight(1f))
                Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp)) // balances the collapse button
            }

            Spacer(Modifier.weight(1f))

            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f)
                    .graphicsLayer { scaleX = breathScale; scaleY = breathScale }
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            )

            Spacer(Modifier.height(28.dp))

            AnimatedContent(targetState = track.id, label = "trackInfo") { _ ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Slider(
                value = if (durationMs > 0f) (positionMs / durationMs).coerceIn(0f, 1f) else 0f,
                onValueChange = {
                    userSeeking = true
                    positionMs = it * durationMs
                },
                onValueChangeFinished = {
                    PlayerManager.seekTo(positionMs.toLong())
                    userSeeking = false
                },
                colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(positionMs.toLong()), style = MaterialTheme.typography.bodyMedium)
                Text(formatDuration(durationMs.toLong()), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))

            // Primary transport row — shuffle/prev/play/next/repeat, the
            // layout every mainstream player uses.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSkipPrevious, enabled = hasPrevious) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = if (hasPrevious) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { PlayerManager.togglePlayPause() },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Brush.linearGradient(listOf(NeonCyan, NeonPurple)))
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = VoidBlack,
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = onSkipNext, enabled = hasNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = if (hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Secondary row — favorite, speed, sleep timer, download.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) NeonPink else MaterialTheme.colorScheme.onSurface
                    )
                }

                Box {
                    IconButton(onClick = { speedMenuOpen = true }) {
                        Icon(Icons.Filled.Speed, contentDescription = "Playback speed")
                    }
                    DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                        SPEED_PRESETS.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x" + if (speed == 1f) " (Normal)" else "") },
                                onClick = { onSetSpeed(speed); speedMenuOpen = false }
                            )
                        }
                    }
                }
                if (playbackSpeed != 1f) {
                    Text("${playbackSpeed}x", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                }

                Box {
                    IconButton(onClick = { sleepMenuOpen = true }) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = "Sleep timer",
                            tint = if (sleepTimerEndsAtMillis != null) NeonCyan else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(expanded = sleepMenuOpen, onDismissRequest = { sleepMenuOpen = false }) {
                        if (sleepTimerEndsAtMillis != null) {
                            DropdownMenuItem(
                                text = { Text("Turn off timer") },
                                onClick = { onCancelSleepTimer(); sleepMenuOpen = false }
                            )
                        }
                        SLEEP_TIMER_OPTIONS.forEach { minutes ->
                            DropdownMenuItem(
                                text = { Text("$minutes min") },
                                onClick = { onScheduleSleepTimer(minutes); sleepMenuOpen = false }
                            )
                        }
                    }
                }

                IconButton(onClick = onDownload, enabled = isDownloadable) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Download",
                        tint = if (isDownloadable) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                track.licenseNote,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
