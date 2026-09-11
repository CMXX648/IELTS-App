package com.voxcoach.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voxcoach.core.designsystem.theme.VoxAccent
import com.voxcoach.core.designsystem.theme.VoxFog
import com.voxcoach.core.designsystem.theme.VoxLocked
import com.voxcoach.core.designsystem.theme.VoxMuted
import com.voxcoach.core.designsystem.theme.VoxPrimary

enum class IslandState {
    Done,
    Current,
    Locked,
    Distant,
}

@Composable
fun StreakPill(
    days: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        border = BorderStroke(1.dp, VoxPrimary.copy(alpha = 0.25f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(VoxPrimary, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${days}天连胜",
                style = MaterialTheme.typography.titleMedium,
                color = VoxPrimary,
            )
        }
    }
}

/** Soft coral dotted path segment between islands. */
@Composable
fun IslandPathConnector(
    modifier: Modifier = Modifier,
    zigLeft: Boolean = false,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
    ) {
        val effect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f), 0f)
        val start = Offset(size.width * if (zigLeft) 0.42f else 0.58f, 0f)
        val mid = Offset(size.width * if (zigLeft) 0.58f else 0.42f, size.height * 0.5f)
        val end = Offset(size.width * if (zigLeft) 0.48f else 0.52f, size.height)
        val path = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(mid.x, mid.y, end.x, end.y)
        }
        drawPath(
            path = path,
            color = VoxPrimary.copy(alpha = 0.45f),
            style = Stroke(width = 4f, pathEffect = effect),
        )
    }
}

/**
 * 闯关小岛：完成草岛 / 当前珊瑚环礁 / 锁定岩台 / 迷雾山。按下 0.97。
 */
@Composable
fun IslandNode(
    title: String,
    state: IslandState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val canPress = state == IslandState.Done || state == IslandState.Current
    val scale by animateFloatAsState(
        targetValue = if (pressed && canPress) 0.97f else 1f,
        animationSpec = tween(120),
        label = "islandScale",
    )
    val alpha = if (state == IslandState.Distant) 0.55f else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = canPress,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            IslandState.Done -> DoneIsland()
            IslandState.Current -> CurrentIsland()
            IslandState.Locked -> LockedIsland()
            IslandState.Distant -> DistantIsland()
        }
        if (state == IslandState.Current) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = VoxPrimary,
                shadowElevation = 2.dp,
            ) {
                Text(
                    text = "今天练",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }
        if (title.isNotBlank() && state != IslandState.Current) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = VoxMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (!subtitle.isNullOrBlank() && state == IslandState.Current) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VoxMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DoneIsland() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp, 72.dp)) {
        Canvas(Modifier.matchParentSize()) {
            // lime oval island
            drawOval(
                color = VoxAccent,
                topLeft = Offset(size.width * 0.08f, size.height * 0.28f),
                size = Size(size.width * 0.84f, size.height * 0.55f),
            )
            // darker green spots
            val spot = Color(0xFF8FBF2E)
            drawCircle(spot, radius = size.minDimension * 0.07f, center = Offset(size.width * 0.35f, size.height * 0.48f))
            drawCircle(spot, radius = size.minDimension * 0.05f, center = Offset(size.width * 0.52f, size.height * 0.42f))
            drawCircle(spot, radius = size.minDimension * 0.06f, center = Offset(size.width * 0.62f, size.height * 0.55f))
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-6).dp, y = (-4).dp)
                .size(22.dp)
                .background(VoxPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CurrentIsland() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
        // soft halo
        Box(
            Modifier
                .size(140.dp)
                .background(VoxPrimary.copy(alpha = 0.12f), CircleShape),
        )
        Canvas(Modifier.size(110.dp)) {
            // coral reef ring
            drawCircle(color = VoxPrimary, radius = size.minDimension * 0.48f)
            // inner lagoon
            drawCircle(color = Color(0xFFB8D9F0), radius = size.minDimension * 0.32f)
            // mic badge
            val badgeR = size.minDimension * 0.14f
            drawCircle(color = VoxPrimary, radius = badgeR, center = center)
        }
        Text(
            text = "🎙️",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun LockedIsland() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp, 70.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val path = Path().apply {
                moveTo(size.width * 0.15f, size.height * 0.55f)
                lineTo(size.width * 0.28f, size.height * 0.25f)
                lineTo(size.width * 0.55f, size.height * 0.18f)
                lineTo(size.width * 0.85f, size.height * 0.35f)
                lineTo(size.width * 0.78f, size.height * 0.78f)
                lineTo(size.width * 0.22f, size.height * 0.82f)
                close()
            }
            drawPath(path, color = VoxLocked, style = Fill)
            drawPath(path, color = VoxMuted.copy(alpha = 0.35f), style = Stroke(width = 2f))
        }
        Text("🔒", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DistantIsland() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp, 64.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val path = Path().apply {
                moveTo(size.width * 0.1f, size.height * 0.85f)
                lineTo(size.width * 0.35f, size.height * 0.35f)
                lineTo(size.width * 0.55f, size.height * 0.55f)
                lineTo(size.width * 0.78f, size.height * 0.28f)
                lineTo(size.width * 0.92f, size.height * 0.85f)
                close()
            }
            drawPath(path, color = VoxFog.copy(alpha = 0.85f), style = Fill)
        }
        Text("☆", color = VoxMuted.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium)
    }
}
