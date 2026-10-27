package com.v2ray.ang.ui.shroxy.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.v2ray.ang.ui.compose.AppTheme

/**
 * Shimmer placeholder for [ShroxySubscriptionGroup]: same chrome (layer1 Surface, 16dp
 * radius, 16dp horizontal margin, header row, divider, traffic info strip) so the swap
 * to the real card does not jump. Pure presentation — no state, no logic. The shimmer is
 * an infinite alpha/translation sweep over a linear gradient; the card is decorative, so
 * its semantics are cleared to keep it out of the accessibility tree.
 */
@Composable
fun ShroxySubscriptionGroupSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shroxy_skeleton")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = SHIMMER_TRAVEL_PX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )
    val shimmer = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurface.copy(alpha = SHIMMER_HIGHLIGHT_ALPHA),
            MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        start = Offset(x = translate - SHIMMER_TRAVEL_PX / 2f, y = 0f),
        end = Offset(x = translate + SHIMMER_TRAVEL_PX / 2f, y = SHIMMER_TRAVEL_PX),
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clearAndSetSemantics { },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Chevron placeholder (24dp, like the real expand icon).
                ShimmerBar(shimmer, Modifier.size(24.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    // Title bar (titleMedium line) then subtitle bar (bodySmall line).
                    ShimmerBar(shimmer, Modifier.fillMaxWidth(0.45f).height(20.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBar(shimmer, Modifier.fillMaxWidth(0.3f).height(14.dp))
                }
                // Three 48dp action zones (refresh / test / overflow IconButtons).
                repeat(3) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ShimmerBar(shimmer, Modifier.size(24.dp))
                    }
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Layer 2 strip; corner rounding comes from the card's Surface shape.
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerBar(shimmer, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                ShimmerBar(shimmer, Modifier.weight(1f).height(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                ShimmerBar(shimmer, Modifier.width(48.dp).height(18.dp))
            }
        }
    }
}

@Composable
private fun ShimmerBar(brush: Brush, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(brush, ShimmerBarShape),
    )
}

private val ShimmerBarShape: Shape = RoundedCornerShape(4.dp)

private const val SHIMMER_DURATION_MS = 1200
private const val SHIMMER_TRAVEL_PX = 900f
private const val SHIMMER_HIGHLIGHT_ALPHA = 0.22f

@Preview(showBackground = true, backgroundColor = 0xFF0D0F12)
@Composable
private fun ShroxySubscriptionGroupSkeletonPreview() {
    AppTheme {
        ShroxySubscriptionGroupSkeleton()
    }
}
