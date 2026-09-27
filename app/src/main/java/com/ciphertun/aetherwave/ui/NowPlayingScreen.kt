package com.ciphertun.aetherwave.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import coil.compose.AsyncImage
import com.ciphertun.aetherwave.model.Track
import com.ciphertun.aetherwave.playback.PlayerManager
import com.ciphertun.aetherwave.ui.theme.NeonCyan
import com.ciphertun.aetherwave.ui.theme.NeonPink
import com.ciphertun.aetherwave.ui.theme.NeonPurple
import com.ciphertun.aetherwave.ui.theme.VoidBlack
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(
    track: Track,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isDownloadable: Boolean,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onCollapse: () -> Unit
) {
    var positionMs by remember(track.id) { mutableFloatStateOf(0f) }
    var durationMs by remember(track.id) { mutableFloatStateOf(0f) }
    var userSeeking by remember { mutableStateOf(false) }

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

            Spacer(Modifier.height(24.dp))

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
