package me.wasddestroy.avbtoolandroid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceRow
import me.wasddestroy.avbtoolandroid.ui.components.SettingsList
import me.wasddestroy.avbtoolandroid.ui.components.preferenceGroup

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenCommand: (String) -> Unit,
) {
    SettingsList(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item("header") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.home_avbdroid_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        val imageTools = AvbCommands.all.filter { it.kind == AvbCommandKind.IMAGE_TOOL }
        val otherTools = AvbCommands.all.filter { it.kind != AvbCommandKind.IMAGE_TOOL }

        preferenceGroup {
            imageTools.forEach { command ->
                row(command.id) {
                    CommandRow(command, onOpenCommand)
                }
            }
        }

        if (otherTools.isNotEmpty()) {
            preferenceGroup {
                otherTools.forEach { command ->
                    row(command.id) {
                        CommandRow(command, onOpenCommand)
                    }
                }
            }
        }
    }
}


@Composable
private fun CommandRow(command: AvbCommand, onOpenCommand: (String) -> Unit) {
    PreferenceRow(
        title = stringResource(command.titleRes),
        summary = stringResource(command.descriptionRes),
        iconContent = {
            Icon(
                imageVector = commandIcon(command.id),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        onClick = { onOpenCommand(command.id) },
    )
}

private fun commandIcon(commandId: String): ImageVector = when (commandId) {
    "add_hash_footer" -> Icons.AutoMirrored.Filled.NoteAdd
    "add_hashtree_footer" -> Icons.Filled.AccountTree
    "info_image" -> Icons.Filled.Info
    "erase_footer" -> Icons.Filled.DeleteSweep
    "resize_image" -> Icons.Filled.PhotoSizeSelectLarge
    "extract_vbmeta_image" -> Icons.Filled.Unarchive
    "print_partition_digests" -> Icons.Filled.Fingerprint
    "calculate_vbmeta_digest" -> Icons.Filled.Calculate
    "verify_image" -> Icons.Filled.Verified
    "zero_hashtree" -> Icons.Filled.LayersClear
    "calculate_kernel_cmdline" -> Icons.Filled.KeyboardCommandKey
    "extract_public_key" -> Icons.Filled.Key
    else -> Icons.Filled.Info
}
