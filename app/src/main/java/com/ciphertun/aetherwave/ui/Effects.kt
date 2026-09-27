package com.ciphertun.aetherwave.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.ciphertun.aetherwave.ui.theme.NeonCyan
import com.ciphertun.aetherwave.ui.theme.NeonPurple
import com.ciphertun.aetherwave.ui.theme.SurfaceElevated

/** A single animated shimmer block — swap in for any placeholder shape while content loads. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, cornerRadius: androidx.compose.ui.unit.Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerTranslate"
    )
    val brush = Brush.linearGradient(
        colors = listOf(SurfaceElevated, SurfaceElevated.copy(alpha = 0.4f), SurfaceElevated),
        start = Offset(translate * 600f - 300f, 0f),
        end = Offset(translate * 600f, 200f)
    )
    androidx.compose.foundation.layout.Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)).background(brush)
    )
}

/** A skeleton row matching TrackRow's layout — shown in place of results while a search is in flight. */
@Composable
fun ShimmerRow() {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(modifier = Modifier.size(52.dp), cornerRadius = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            ShimmerBox(modifier = Modifier.width(160.dp).height(14.dp))
            Spacer(Modifier.height(6.dp))
            ShimmerBox(modifier = Modifier.width(100.dp).height(12.dp))
        }
    }
}

@Composable
fun ShimmerList(rows: Int = 6) {
    Column {
        repeat(rows) { ShimmerRow() }
    }
}

/**
 * Decorative animated equalizer bars — not tied to real audio output (that
 * needs RECORD_AUDIO-adjacent APIs), just a looping height animation that
 * reads as "something is playing" the way most mini-players fake it.
 */
@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = listOf(0.4f, 1f, 0.6f, 0.9f).mapIndexed { index, base ->
        transition.animateFloat(
            initialValue = base * 0.3f,
            targetValue = base,
            animationSpec = infiniteRepeatable(
                tween(380 + index * 90, easing = LinearEasing),
                RepeatMode.Reverse
            ),
            label = "eqBar$index"
        )
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { anim ->
            val fraction = if (playing) anim.value else 0.25f
            androidx.compose.foundation.layout.Box(
                Modifier
                    .width(3.dp)
                    .height((14.dp.value * fraction).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(NeonCyan, NeonPurple)))
            )
        }
    }
}
