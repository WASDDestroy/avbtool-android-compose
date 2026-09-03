package me.wasddestroy.avbtoolandroid.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.wasddestroy.avbtoolandroid.AvbCommandResult
import me.wasddestroy.avbtoolandroid.AvbResultStatus
import me.wasddestroy.avbtoolandroid.R

private val SuccessAutoDismissMillis = 4_000L

/**
 * In-app top result banner. Show it over the screen content when a command
 * finishes: success hides itself after a short delay, failure/cancel stays
 * until dismissed or replaced, so the user never has to scroll to the bottom
 * of a long form just to learn the outcome. The detailed result sections stay
 * at the bottom of the page; tapping the banner jumps there via [onClick].
 */
@Composable
fun ResultPopupHost(
    result: AvbCommandResult?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    var shownResult by remember { mutableStateOf<AvbCommandResult?>(null) }

    LaunchedEffect(result) {
        if (result == null) {
            visible = false
            return@LaunchedEffect
        }
        if (result !== shownResult) {
            shownResult = result
            visible = true
        }
    }

    LaunchedEffect(shownResult) {
        if (shownResult?.status == AvbResultStatus.SUCCESS) {
            delay(SuccessAutoDismissMillis)
            visible = false
            onDismiss()
        }
    }

    val banner = shownResult
    AnimatedVisibility(
        visible = visible && banner != null,
        modifier = modifier,
        enter = slideInVertically(animationSpec = tween(250)) { -it } + fadeIn(tween(250)),
        exit = slideOutVertically(animationSpec = tween(200)) { -it } + fadeOut(tween(200)),
    ) {
        if (banner != null) {
            ResultPopupCard(
                result = banner,
                onDismiss = {
                    visible = false
                    onDismiss()
                },
                onClick = onClick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultPopupCard(
    result: AvbCommandResult,
    onDismiss: () -> Unit,
    onClick: (() -> Unit)?,
) {
    val style = when (result.status) {
        AvbResultStatus.SUCCESS -> PopupStyle(
            icon = Icons.Filled.CheckCircle,
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        AvbResultStatus.FAILED -> PopupStyle(
            icon = Icons.Filled.Error,
            container = MaterialTheme.colorScheme.errorContainer,
            onContainer = MaterialTheme.colorScheme.onErrorContainer,
        )
        else -> PopupStyle(
            icon = Icons.Filled.Info,
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            onContainer = MaterialTheme.colorScheme.onSurface,
        )
    }

    val firstError = result.errors.firstOrNull()
    val statusText = when (result.status) {
        AvbResultStatus.SUCCESS -> stringResource(R.string.command_result_success)
        AvbResultStatus.FAILED -> stringResource(R.string.command_result_failed)
        AvbResultStatus.CANCELLED -> stringResource(R.string.command_result_cancelled)
        AvbResultStatus.RUNNING -> stringResource(R.string.command_result_running)
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = style.container,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .let {
                if (onClick != null) {
                    // Clip before clickable so the ripple stays inside the
                    // banner's corners (Surface clips after the caller's
                    // modifier).
                    it.clip(MaterialTheme.shapes.large).combinedClickable(onClick = onClick)
                } else {
                    it
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.onContainer,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    color = style.onContainer,
                )
                if (firstError != null) {
                    Text(
                        text = firstError,
                        style = MaterialTheme.typography.bodySmall,
                        color = style.onContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.result_popup_close),
                    tint = style.onContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private data class PopupStyle(
    val icon: ImageVector,
    val container: Color,
    val onContainer: Color,
)
