package me.wasddestroy.avbtoolandroid

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.wasddestroy.avbtoolandroid.ui.components.DialogConfirmButton
import me.wasddestroy.avbtoolandroid.ui.components.DialogDismissButton
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceRow
import me.wasddestroy.avbtoolandroid.ui.components.SettingsList
import me.wasddestroy.avbtoolandroid.ui.components.preferenceGroup

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            Toast.makeText(context, context.getString(R.string.profile_import_failed), Toast.LENGTH_SHORT).show()
        } else {
            viewModel.importProfile(bytes)
        }
    }

    var pendingImagePartition by remember { mutableStateOf<String?>(null) }
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val partition = pendingImagePartition
        pendingImagePartition = null
        if (uri != null && partition != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setImage(partition, uri)
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val src = uiState.pendingExports.firstOrNull()
        if (uri != null && src != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
            }
        }
        viewModel.consumeExport()
    }

    val exportProfileDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        val zip = uiState.pendingProfileZip
        if (uri != null && zip != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(zip.second)
                }
            }
        }
        viewModel.consumeProfileZip()
    }

    val message = uiState.message
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    val exports = uiState.pendingExports
    if (exports.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissExports() },
            title = { Text(stringResource(R.string.profile_sign_export_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.profile_sign_export_message,
                        exports.size,
                        exports.joinToString("\n") { it.absolutePath },
                    ),
                )
            },
            confirmButton = {
                DialogConfirmButton(onClick = {
                    val src = exports.firstOrNull()
                    if (src != null) {
                        createDocument.launch(src.name)
                    } else {
                        viewModel.dismissExports()
                    }
                }) {
                    Text(stringResource(R.string.command_save))
                }
            },
            dismissButton = {
                DialogDismissButton(onClick = { viewModel.dismissExports() }) {
                    Text(stringResource(R.string.command_dismiss))
                }
            },
        )
    }

    val pendingZip = uiState.pendingProfileZip
    if (pendingZip != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissProfileZip() },
            title = { Text(stringResource(R.string.profile_export_title)) },
            text = { Text(stringResource(R.string.profile_export_message, pendingZip.first)) },
            confirmButton = {
                DialogConfirmButton(onClick = {
                    exportProfileDocument.launch(pendingZip.first)
                }) {
                    Text(stringResource(R.string.command_save))
                }
            },
            dismissButton = {
                DialogDismissButton(onClick = { viewModel.dismissProfileZip() }) {
                    Text(stringResource(R.string.command_dismiss))
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        SettingsList(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item("header") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
            preferenceGroup(key = "profiles") {
                row("import_export") {
                    ImportExportRow(
                        onImport = { importLauncher.launch("*/*") },
                        onExport = viewModel::exportActiveProfile,
                        importEnabled = !uiState.importing,
                        exportEnabled = !uiState.exporting && uiState.activeId != null,
                    )
                }
                val profiles = uiState.profiles
                if (profiles.isEmpty()) {
                    row("empty") {
                        PreferenceRow(
                            title = stringResource(R.string.profile_empty),
                        )
                    }
                } else {
                    profiles.forEach { profile ->
                        row(profile.id) {
                            ProfileRow(
                                profile = profile,
                                selected = uiState.activeId == profile.id,
                                onSelect = { viewModel.selectProfile(profile.id) },
                                onDelete = { pendingDeleteId = profile.id },
                            )
                        }
                    }
                }
            }
            if (uiState.activeId != null && uiState.activeSpecs.isNotEmpty()) {
                preferenceGroup(key = "images", titleRes = R.string.profile_group_images) {
                    uiState.activeSpecs.forEach { spec ->
                        row("image_${spec.partition}") {
                            PreferenceRow(
                                title = spec.partition,
                                summary = viewModel.getImage(spec.partition)
                                    ?.let { runCatching { it.toUri().lastPathSegment }.getOrNull() }
                                    ?: stringResource(R.string.profile_image_not_selected),
                                iconContent = {
                                    Icon(
                                        imageVector = Icons.Filled.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                onClick = {
                                    pendingImagePartition = spec.partition
                                    imageLauncher.launch(arrayOf("*/*"))
                                },
                            )
                        }
                    }
                }
            }
            val result = uiState.result
            if (!uiState.signing && result != null) {
                item("profile_result") {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        ProfileResultView(result)
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = viewModel::signActive,
                enabled = !uiState.signing && uiState.activeId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
            ) {
                Text(stringResource(if (uiState.signing) R.string.command_running else R.string.profile_sign))
            }
        }
    }

    pendingDeleteId?.let { id ->
        val profile = uiState.profiles.find { it.id == id }
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = { Text(stringResource(R.string.profile_delete_message, profile?.name ?: id)) },
            confirmButton = {
                DialogConfirmButton(onClick = {
                    viewModel.deleteProfile(id)
                    pendingDeleteId = null
                }) {
                    Text(stringResource(R.string.command_continue))
                }
            },
            dismissButton = {
                DialogDismissButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.command_cancel))
                }
            },
        )
    }
}

@Composable
private fun ImportExportRow(
    onImport: () -> Unit,
    onExport: () -> Unit,
    importEnabled: Boolean,
    exportEnabled: Boolean,
) {
    // First row of the card, split into two halves. Each half is its own
    // Surface so the 2dp gap between them shows the screen background —
    // the same segmentation look as between preference rows. Outer corners
    // match the group's large top radius; the bottom uses the small
    // segmented radius because more rows follow.
    val leftShape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 4.dp,
    )
    val rightShape = RoundedCornerShape(
        topStart = 4.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ActionHalfSurface(
            label = stringResource(R.string.profile_import),
            icon = Icons.Filled.FileDownload,
            enabled = importEnabled,
            onClick = onImport,
            shape = leftShape,
            modifier = Modifier.weight(1f),
        )
        ActionHalfSurface(
            label = stringResource(R.string.profile_export),
            icon = Icons.Filled.FileUpload,
            enabled = exportEnabled,
            onClick = onExport,
            shape = rightShape,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionHalfSurface(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) {
                    LocalContentColor.current
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    LocalContentColor.current
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileStore.ProfileEntry,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    PreferenceRow(
        title = profile.name,
        summary = profile.id,
        iconContent = {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.profile_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                RadioButton(selected = selected, onClick = onSelect)
            }
        },
        onClick = onSelect,
    )
}

@Composable
private fun ProfileResultView(result: ProfileSignResult) {
    var rawExpanded by remember { mutableStateOf(false) }
    val statusTextRes = when (result.result.status) {
        AvbResultStatus.SUCCESS -> R.string.command_result_success
        AvbResultStatus.FAILED -> R.string.command_result_failed
        AvbResultStatus.CANCELLED -> R.string.command_result_cancelled
        AvbResultStatus.RUNNING -> R.string.command_result_running
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = when (result.result.status) {
            AvbResultStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
            AvbResultStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_sign_result_title, result.profileName) + "\n" +
                stringResource(statusTextRes),
            style = MaterialTheme.typography.titleMedium,
            color = when (result.result.status) {
                AvbResultStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                AvbResultStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    if (result.result.errors.isNotEmpty()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            result.result.errors.forEach { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }

    if (result.result.rawOutput.isNotBlank()) {
        androidx.compose.material3.TextButton(
            onClick = { rawExpanded = !rawExpanded },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(
                    if (rawExpanded) R.string.command_raw_output_collapse
                    else R.string.command_raw_output_expand,
                ),
            )
        }
        if (rawExpanded) {
            SelectionContainer {
                Text(
                    text = result.result.rawOutput,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}
