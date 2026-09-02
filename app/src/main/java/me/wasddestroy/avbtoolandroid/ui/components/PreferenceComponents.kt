package me.wasddestroy.avbtoolandroid.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val PreferenceCardCornerRadius = 16.dp
private val PreferenceSegmentedSmallCornerRadius = 4.dp
private val PreferenceSegmentedGap = 2.dp
private val PreferenceRowMinHeight = 56.dp

private data class PreferenceRowPosition(val index: Int, val count: Int)

private val LocalPreferenceRowPosition = compositionLocalOf<PreferenceRowPosition?> { null }

@Composable
fun SettingsList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        state = state,
        content = content,
    )
}

fun LazyListScope.preferenceGroup(
    key: Any? = null,
    titleRes: Int? = null,
    content: PreferenceGroupScope.() -> Unit,
) {
    item(key = key ?: titleRes) {
        PreferenceGroup(titleRes = titleRes, content = content)
    }
}

@Composable
fun PreferenceGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: PreferenceGroupScope.() -> Unit,
) {
    PreferenceGroupWithTitleText(titleText = title, modifier = modifier, content = content)
}

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    titleRes: Int? = null,
    content: PreferenceGroupScope.() -> Unit,
) {
    PreferenceGroupWithTitleText(titleText = titleRes?.let { stringResource(it) }, modifier = modifier, content = content)
}

@Composable
private fun PreferenceGroupWithTitleText(
    titleText: String?,
    modifier: Modifier = Modifier,
    content: PreferenceGroupScope.() -> Unit,
) {
    val items = mutableListOf<@Composable () -> Unit>()
    PreferenceGroupScope(items).apply(content)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(PreferenceSegmentedGap),
    ) {
        titleText?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        items.forEachIndexed { index, item ->
            CompositionLocalProvider(
                LocalPreferenceRowPosition provides PreferenceRowPosition(index, items.size),
            ) {
                item()
            }
        }
    }
}

class PreferenceGroupScope(private val items: MutableList<@Composable () -> Unit>) {
    @Suppress("unused")
    fun row(key: Any? = null, content: @Composable () -> Unit) {
        items += content
    }
}

/**
 * Renders [rows] as one visually clustered paragraph (like a preference
 * group's rows): first row gets the large top corners, last row the large
 * bottom corners, middle rows small corners. Callers compose it with plain
 * fields after it to keep non-text rows from interleaving with text rows.
 */
@Composable
fun preferenceParagraph(
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PreferenceSegmentedGap),
    ) {
        rows.forEachIndexed { index, row ->
            CompositionLocalProvider(
                LocalPreferenceRowPosition provides PreferenceRowPosition(index, rows.size),
            ) {
                row()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PreferenceRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    iconContent: (@Composable () -> Unit)? = null,
    iconTint: Color? = null,
    summary: String? = null,
    summaryContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = preferenceRowShape(LocalPreferenceRowPosition.current)
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PreferenceRowMinHeight)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val leadingContent = iconContent ?: icon?.let {
                {
                    Icon(
                        painter = painterResource(it),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = iconTint ?: LocalContentColor.current,
                    )
                }
            }
            leadingContent?.invoke()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) {
                        LocalContentColor.current
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
                val summaryBlock = summaryContent ?: summary?.takeIf { it.isNotEmpty() }?.let {
                    {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                summaryBlock?.invoke()
            }
            trailing?.invoke()
            if (trailing == null && onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (onClick != null || onLongClick != null) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick ?: {},
                    onLongClick = onLongClick,
                ),
            content = { content() },
        )
    } else {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = modifier.fillMaxWidth(),
            content = { content() },
        )
    }
}

@Composable
fun PreferenceSwitchRow(
    checked: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    iconContent: (@Composable () -> Unit)? = null,
    summary: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    PreferenceRow(
        title = title,
        modifier = modifier,
        icon = icon,
        iconContent = iconContent,
        summary = summary,
        enabled = enabled,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

@Composable
fun PreferenceValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    PreferenceRow(
        title = title,
        modifier = modifier,
        summary = value,
        summaryContent = if (monospace) {
            {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else {
            null
        },
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

private fun preferenceRowShape(position: PreferenceRowPosition?): Shape {
    val small = PreferenceSegmentedSmallCornerRadius
    val large = PreferenceCardCornerRadius
    return when {
        position == null || position.count == 1 -> RoundedCornerShape(large)
        position.index == 0 -> RoundedCornerShape(
            topStart = large,
            topEnd = large,
            bottomStart = small,
            bottomEnd = small,
        )
        position.index == position.count - 1 -> RoundedCornerShape(
            topStart = small,
            topEnd = small,
            bottomStart = large,
            bottomEnd = large,
        )
        else -> RoundedCornerShape(small)
    }
}

@Composable
fun DialogConfirmButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = FilledTonalButton(
    onClick = onClick,
    enabled = enabled,
    content = content,
)

/**
 * Confirm button for high-severity warnings: shows the remaining seconds and
 * stays disabled for [countdownSeconds], forcing the user to actually read
 * the dialog before continuing. The countdown restarts whenever the button
 * enters composition.
 */
@Composable
fun CountdownDialogConfirmButton(
    text: String,
    countdownText: (remaining: Int) -> String,
    countdownSeconds: Int = 5,
    onClick: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(countdownSeconds) }
    LaunchedEffect(countdownSeconds) {
        while (remaining > 0) {
            kotlinx.coroutines.delay(1000)
            remaining--
        }
    }
    DialogConfirmButton(onClick = onClick, enabled = remaining <= 0) {
        Text(if (remaining > 0) countdownText(remaining) else text)
    }
}

@Suppress("unused")
@Composable
fun DialogNeutralButton(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) = OutlinedButton(
    onClick = onClick,
    content = content,
)

@Composable
fun DialogDismissButton(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) = TextButton(
    onClick = onClick,
    content = content,
)
