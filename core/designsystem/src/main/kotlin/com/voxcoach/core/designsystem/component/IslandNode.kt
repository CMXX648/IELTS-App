package com.voxcoach.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voxcoach.core.designsystem.theme.VoxAccent
import com.voxcoach.core.designsystem.theme.VoxFog
import com.voxcoach.core.designsystem.theme.VoxGrass
import com.voxcoach.core.designsystem.theme.VoxLocked
import com.voxcoach.core.designsystem.theme.VoxPrimary

enum class IslandState {
    Done,
    Current,
    Locked,
    Distant,
}

/** 闯关小岛节点：按下 0.97 回弹。 */
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
    val size: Dp = when (state) {
        IslandState.Current -> 112.dp
        IslandState.Done -> 88.dp
        IslandState.Locked -> 80.dp
        IslandState.Distant -> 64.dp
    }
    val fill = when (state) {
        IslandState.Done -> VoxGrass
        IslandState.Current -> VoxPrimary
        IslandState.Locked -> VoxLocked
        IslandState.Distant -> VoxFog
    }
    val alpha = when (state) {
        IslandState.Distant -> 0.55f
        else -> 1f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size + if (state == IslandState.Current) 16.dp else 0.dp)
                .then(
                    if (state == IslandState.Current) {
                        Modifier.border(4.dp, VoxPrimary.copy(alpha = 0.35f), CircleShape)
                    } else {
                        Modifier
                    },
                )
                .padding(if (state == IslandState.Current) 6.dp else 0.dp)
                .size(size)
                .background(fill, CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = canPress,
                    onClick = onClick,
                ),
        ) {
            Text(
                text = when (state) {
                    IslandState.Done -> "✓★"
                    IslandState.Current -> "今天练"
                    IslandState.Locked -> "🔒"
                    IslandState.Distant -> "☁"
                },
                style = if (state == IslandState.Current) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = when (state) {
                    IslandState.Current, IslandState.Done -> Color.White
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (!subtitle.isNullOrBlank() && state == IslandState.Current) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (state == IslandState.Current) {
            Text(text = "✦", color = VoxAccent, style = MaterialTheme.typography.titleLarge)
        }
    }
}
