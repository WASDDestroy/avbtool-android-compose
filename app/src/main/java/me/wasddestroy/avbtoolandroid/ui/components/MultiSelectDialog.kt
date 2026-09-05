package me.wasddestroy.avbtoolandroid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.wasddestroy.avbtoolandroid.R

/** One selectable row in [MultiSelectDialog]. */
data class MultiSelectItem<T>(
    val id: T,
    val title: String,
    val summary: String? = null,
)

/**
 * Reusable multi-select dialog: a checkbox list with a confirm button that
 * stays disabled until at least one entry is picked. The caller owns the
 * follow-up action (destructive actions still need their own confirmation
 * dialog after [onConfirm] hands over the selection).
 */
@Composable
fun <T> MultiSelectDialog(
    title: String,
    items: List<MultiSelectItem<T>>,
    onDismiss: () -> Unit,
    onConfirm: (Set<T>) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<T>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (item.id in selected) {
                                    selected - item.id
                                } else {
                                    selected + item.id
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = item.id in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + item.id else selected - item.id
                            },
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge)
                            if (item.summary != null) {
                                Text(
                                    item.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = { onConfirm(selected) },
                enabled = selected.isNotEmpty(),
            ) {
                Text(stringResource(R.string.command_continue))
            }
        },
        dismissButton = {
            DialogDismissButton(onClick = onDismiss) {
                Text(stringResource(R.string.command_cancel))
            }
        },
    )
}
