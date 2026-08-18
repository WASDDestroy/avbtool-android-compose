package me.wasddestroy.avbtoolandroid

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
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
        HomeSegment.entries.forEach { segment ->
            val commands = AvbCommands.all.filter { it.group == segment }
            if (commands.isNotEmpty()) {
                preferenceGroup(titleRes = segment.labelRes) {
                    commands.forEach { command ->
                        row(command.id) {
                            CommandRow(command, onOpenCommand)
                        }
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
    "extract_public_key_digest" -> Icons.Filled.Output
    "append_vbmeta_image" -> Icons.Filled.AddCircle
    "set_ab_metadata" -> Icons.Filled.Storage
    "make_vbmeta_image" -> Icons.Filled.DiscFull
    "make_certificate" -> Icons.Filled.VerifiedUser
    "make_cert_permanent_attributes" -> Icons.Filled.Lock
    "make_cert_metadata" -> Icons.Filled.VerifiedUser
    "make_cert_unlock_credential" -> Icons.Filled.LockOpen
    else -> Icons.Filled.Info
}
