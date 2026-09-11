package com.voxcoach.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * +分/连击轻提示：约 200ms 淡入上移，停 1s 再淡出；不拦截点击。
 *
 * @param token 变化时重新播放（例如 answeredCount / turnCount）
 */
@Composable
fun ScoreToast(
    message: String,
    token: Any?,
    modifier: Modifier = Modifier,
    visibleWhen: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(token) {
        if (token == null || !visibleWhen) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        delay(200L + 1000L)
        visible = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible && visibleWhen && message.isNotBlank(),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 },
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.secondary,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
