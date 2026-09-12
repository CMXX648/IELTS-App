package com.voxcoach.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxcoach.core.designsystem.R
import com.voxcoach.core.designsystem.theme.VoxCoralGlow
import com.voxcoach.core.designsystem.theme.VoxCoralSoft
import com.voxcoach.core.designsystem.theme.VoxGold
import com.voxcoach.core.designsystem.theme.VoxGoldBorder
import com.voxcoach.core.designsystem.theme.VoxGoldInner
import com.voxcoach.core.designsystem.theme.VoxGoldSoft
import com.voxcoach.core.designsystem.theme.VoxLocked
import com.voxcoach.core.designsystem.theme.VoxLockedBorder
import com.voxcoach.core.designsystem.theme.VoxLockedCard
import com.voxcoach.core.designsystem.theme.VoxLockedInner
import com.voxcoach.core.designsystem.theme.VoxLockedText
import com.voxcoach.core.designsystem.theme.VoxOnBackground
import com.voxcoach.core.designsystem.theme.VoxPrimary
import com.voxcoach.core.designsystem.theme.VoxSuccess
import com.voxcoach.core.designsystem.theme.VoxSuccessSoft
import com.voxcoach.core.designsystem.theme.VoxSurface
import kotlin.math.roundToInt

enum class IslandState {
    Done,
    Current,
    Locked,
    Distant,
}

enum class IslandConnectorTone {
    Success,
    Muted,
}

private val InfoShape = RoundedCornerShape(14.dp)

@Composable
fun StreakPill(
    days: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(VoxCoralSoft)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_island_flame),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "连续学习 ${days} 天",
            color = VoxPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
        )
    }
}

/** Vertical dotted path + chevron between islands. */
@Composable
fun IslandPathConnector(
    modifier: Modifier = Modifier,
    tone: IslandConnectorTone = IslandConnectorTone.Muted,
) {
    val dotColor = if (tone == IslandConnectorTone.Success) VoxSuccess else VoxLockedBorder
    val chevron = if (tone == IslandConnectorTone.Success) {
        R.drawable.ic_island_chevron_down
    } else {
        R.drawable.ic_island_chevron_down_muted
    }
    val dotSize = if (tone == IslandConnectorTone.Success) 7.dp else 6.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .size(dotSize)
                    .background(dotColor, CircleShape),
            )
        }
        Icon(
            painter = painterResource(chevron),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(width = 12.dp, height = 8.dp),
        )
    }
}

/**
 * 闯关小岛：完成 / 当前 / 锁定 / 终极挑战。按下 0.97。
 */
@Composable
fun IslandNode(
    title: String,
    state: IslandState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    cardWidth: Dp? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val canPress = state == IslandState.Done || state == IslandState.Current
    val scale by animateFloatAsState(
        targetValue = if (pressed && canPress) 0.97f else 1f,
        animationSpec = tween(120),
        label = "islandScale",
    )
    val cardMod = if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth()

    Column(
        modifier = modifier
            .then(cardMod)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = canPress,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IslandBadge(state)
        IslandGraphic(state)
        IslandInfoCard(
            title = title,
            state = state,
            progress = progress,
            hint = subtitle,
        )
    }
}

@Composable
private fun IslandBadge(state: IslandState) {
    when (state) {
        IslandState.Done -> {
            Row(
                modifier = Modifier
                    .border(1.dp, VoxSuccess, RoundedCornerShape(100.dp))
                    .background(VoxSuccessSoft, RoundedCornerShape(100.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_island_check),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    text = "已完成",
                    color = VoxSuccess,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                )
            }
        }
        IslandState.Current -> {
            Row(
                modifier = Modifier
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(100.dp),
                        ambientColor = VoxPrimary.copy(alpha = 0.33f),
                        spotColor = VoxPrimary.copy(alpha = 0.33f),
                    )
                    .background(VoxPrimary, RoundedCornerShape(100.dp))
                    .padding(horizontal = 18.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_island_star),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = "今天练 🔥",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        IslandState.Locked -> {
            Row(
                modifier = Modifier
                    .border(1.dp, VoxLockedBorder, RoundedCornerShape(100.dp))
                    .background(VoxLocked, RoundedCornerShape(100.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_island_lock_badge),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    text = "待解锁",
                    color = VoxLockedText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                )
            }
        }
        IslandState.Distant -> {
            Box(
                modifier = Modifier
                    .border(1.dp, VoxGoldBorder, RoundedCornerShape(100.dp))
                    .background(VoxGoldSoft, RoundedCornerShape(100.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🏆 终极挑战",
                    color = VoxGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun IslandGraphic(state: IslandState) {
    when (state) {
        IslandState.Done -> {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = VoxSuccess.copy(alpha = 0.19f),
                        spotColor = VoxSuccess.copy(alpha = 0.19f),
                    )
                    .size(92.dp)
                    .border(2.dp, VoxSuccess, CircleShape)
                    .background(VoxSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(VoxSuccessSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SoundWaves()
                }
            }
        }
        IslandState.Current -> {
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        ambientColor = VoxPrimary.copy(alpha = 0.23f),
                        spotColor = VoxPrimary.copy(alpha = 0.23f),
                    )
                    .size(108.dp)
                    .background(VoxCoralGlow, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .border(3.dp, VoxPrimary, CircleShape)
                        .background(VoxCoralSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .shadow(
                                elevation = 4.dp,
                                shape = CircleShape,
                                ambientColor = VoxPrimary.copy(alpha = 0.33f),
                                spotColor = VoxPrimary.copy(alpha = 0.33f),
                            )
                            .size(76.dp)
                            .background(VoxPrimary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_island_mic),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
        IslandState.Locked -> {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = 0.85f }
                    .shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        ambientColor = VoxOnBackground.copy(alpha = 0.03f),
                        spotColor = VoxOnBackground.copy(alpha = 0.03f),
                    )
                    .size(84.dp)
                    .border(1.5.dp, VoxLockedBorder, CircleShape)
                    .background(VoxLocked, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(VoxLockedInner, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_island_lock),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        IslandState.Distant -> {
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = 0.75f }
                    .shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        ambientColor = VoxGoldBorder.copy(alpha = 0.13f),
                        spotColor = VoxGoldBorder.copy(alpha = 0.13f),
                    )
                    .size(84.dp)
                    .border(1.5.dp, VoxGoldBorder, CircleShape)
                    .background(VoxGoldSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(VoxGoldInner, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_island_lock_gold),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundWaves() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(10.dp, 18.dp, 24.dp, 18.dp, 10.dp).forEach { barHeight ->
            Box(
                Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .background(VoxSuccess, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun IslandInfoCard(
    title: String,
    state: IslandState,
    progress: Float?,
    hint: String?,
) {
    val borderWidth = if (state == IslandState.Current) 1.5.dp else 1.dp
    val (bg, border, titleColor, titleSize, titleWeight, cardAlpha) = when (state) {
        IslandState.Done -> InfoStyle(
            bg = VoxSurface,
            border = VoxSuccess,
            titleColor = VoxOnBackground,
            titleSize = 14.sp,
            titleWeight = FontWeight.ExtraBold,
            alpha = 1f,
        )
        IslandState.Current -> InfoStyle(
            bg = VoxSurface,
            border = VoxPrimary,
            titleColor = VoxPrimary,
            titleSize = 14.sp,
            titleWeight = FontWeight.ExtraBold,
            alpha = 1f,
        )
        IslandState.Locked -> InfoStyle(
            bg = VoxLockedCard,
            border = VoxLockedInner,
            titleColor = VoxLockedText,
            titleSize = 13.sp,
            titleWeight = FontWeight.Bold,
            alpha = 0.9f,
        )
        IslandState.Distant -> InfoStyle(
            bg = VoxGoldSoft,
            border = VoxGoldBorder,
            titleColor = VoxGold,
            titleSize = 13.sp,
            titleWeight = FontWeight.Bold,
            alpha = 0.85f,
        )
    }
    val shadowColor = when (state) {
        IslandState.Done -> VoxSuccess.copy(alpha = 0.08f)
        IslandState.Current -> VoxPrimary.copy(alpha = 0.13f)
        IslandState.Locked -> VoxOnBackground.copy(alpha = 0.02f)
        IslandState.Distant -> VoxGoldBorder.copy(alpha = 0.08f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = cardAlpha }
            .shadow(elevation = 3.dp, shape = InfoShape, ambientColor = shadowColor, spotColor = shadowColor)
            .border(borderWidth, border, InfoShape)
            .background(bg, InfoShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (state == IslandState.Done || state == IslandState.Current) 6.dp else 4.dp),
    ) {
        Text(
            text = title,
            color = titleColor,
            fontWeight = titleWeight,
            fontSize = titleSize,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (progress != null && (state == IslandState.Done || state == IslandState.Current)) {
            val track = if (state == IslandState.Done) VoxSuccessSoft else VoxCoralSoft
            val fill = if (state == IslandState.Done) VoxSuccess else VoxPrimary
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(track),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(fill),
                    )
                }
                Text(
                    text = "${(progress * 100f).roundToInt()}%",
                    color = fill,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                )
            }
        }
        if (!hint.isNullOrBlank()) {
            if (state == IslandState.Current) {
                Text(
                    text = hint,
                    color = VoxPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_island_lock_hint),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = hint,
                        color = VoxLockedBorder,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                    )
                }
            }
        }
    }
}

private data class InfoStyle(
    val bg: Color,
    val border: Color,
    val titleColor: Color,
    val titleSize: androidx.compose.ui.unit.TextUnit,
    val titleWeight: FontWeight,
    val alpha: Float,
)
