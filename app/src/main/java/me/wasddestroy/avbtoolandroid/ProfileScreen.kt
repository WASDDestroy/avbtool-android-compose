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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.math.BigInteger
import me.wasddestroy.avbtoolandroid.ui.components.DialogConfirmButton
import me.wasddestroy.avbtoolandroid.ui.components.DialogDismissButton
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceGroup
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceRow
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceSwitchRow
import me.wasddestroy.avbtoolandroid.ui.components.SettingsList
import me.wasddestroy.avbtoolandroid.ui.components.preferenceGroup

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AppContextHolder.resolver = context.applicationContext.contentResolver
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var confirmOverwrite by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddPartitionDialog by remember { mutableStateOf(false) }
    var showDeletePartitionsDialog by remember { mutableStateOf(false) }
    var pendingDeletePartitions by remember { mutableStateOf<Set<String>?>(null) }
    var pendingSignScope by remember { mutableStateOf<Set<String>?>(null) }
    var signRollbackFindings by remember { mutableStateOf<List<RollbackIndexFinding>?>(null) }

    val addPartitionEvent = uiState.addPartitionEvent
    LaunchedEffect(addPartitionEvent) {
        when (addPartitionEvent) {
            is AddPartitionEvent.Success -> {
                showAddPartitionDialog = false
                viewModel.consumeAddPartitionEvent()
            }
            is AddPartitionEvent.NameConflict -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.profile_partition_add_conflict),
                    Toast.LENGTH_SHORT,
                ).show()
                viewModel.consumeAddPartitionEvent()
            }
            null -> Unit
            // InvalidImage / NoImage keep the dialog open; the warning dialog
            // rendered from uiState offers discard / default descriptor.
            AddPartitionEvent.InvalidImage, AddPartitionEvent.NoImage -> Unit
        }
    }

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
    var pendingAddPartitionImage by remember { mutableStateOf<String?>(null) }
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val partition = pendingImagePartition
        val addPick = pendingAddPartitionImage
        pendingImagePartition = null
        pendingAddPartitionImage = null
        when {
            // The pick feeds the add-partition dialog, not an existing row.
            partition == ADD_PARTITION_KEY -> {
                pendingAddPartitionImage = uri?.toString()
            }
            uri != null && partition != null -> {
                // Persistable-grant handling lives in the ViewModel alongside the
                // selection store (read+write, released on replacement/deletion).
                viewModel.setImage(partition, uri)
            }
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

    uiState.rollbackFindings?.let { findings ->
        // Import is all-or-nothing: entries avbtool cannot write at all block
        // the import; merely anomalous ones need the countdown confirmation.
        val hasInvalid = findings.any { it.verdict is RollbackIndexVerdict.Invalid }
        RollbackIndexWarningDialog(
            findings = findings,
            onDismiss = viewModel::dismissRollbackWarning,
            onContinue = if (hasInvalid) null else viewModel::confirmRollbackImport,
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
                row("import_export") {
                    ImportExportRow(
                        onImport = { importLauncher.launch("*/*") },
                        onExport = viewModel::exportActiveProfile,
                        importEnabled = !uiState.importing,
                        exportEnabled = !uiState.exporting && uiState.activeId != null,
                    )
                }
                row("create") {
                    CreateProfileRow(
                        enabled = !uiState.importing,
                        onClick = { showCreateDialog = true },
                    )
                }
            }
            if (uiState.activeId != null) {
                preferenceGroup(key = "images", titleRes = R.string.profile_group_images) {
                    val specs = uiState.activeSpecs
                    if (specs.isEmpty()) {
                        row("partitions_empty") {
                            PreferenceRow(
                                title = stringResource(R.string.profile_partitions_empty),
                            )
                        }
                    } else {
                        specs.forEach { spec ->
                            row("image_${spec.partition}") {
                                if (spec.descriptor == "vbmeta") {
                                    // vbmeta images are generated at sign
                                    // time; the image field names the
                                    // output, so there is nothing to pick.
                                    PreferenceRow(
                                        title = spec.partition,
                                        summary = stringResource(
                                            R.string.profile_image_output,
                                            spec.image,
                                        ),
                                        iconContent = {
                                            Icon(
                                                imageVector = Icons.Filled.Album,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        },
                                    )
                                } else {
                                    PreferenceRow(
                                        title = spec.partition,
                                        summary = uiState.imageSummaries[spec.partition]
                                            ?: stringResource(R.string.profile_image_not_selected),
                                        iconContent = {
                                            Icon(
                                                imageVector = Icons.Filled.Album,
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
                    row("partition_actions") {
                        PartitionActionsRow(
                            onAdd = { showAddPartitionDialog = true },
                            onDelete = { showDeletePartitionsDialog = true },
                        )
                    }
                }
            }
            if (uiState.activeId != null) {
                preferenceGroup(key = "sign_options", titleRes = R.string.profile_group_sign_options) {
                    row("add_props_to_vbmeta") {
                        PreferenceSwitchRow(
                            title = stringResource(R.string.profile_add_props_to_vbmeta),
                            summary = stringResource(R.string.profile_add_props_to_vbmeta_summary),
                            checked = uiState.addPropsToVbmeta,
                            onCheckedChange = viewModel::setAddPropsToVbmeta,
                        )
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
                onClick = { viewModel.prepareSignScope() },
                enabled = (!uiState.signing && !uiState.probingScope) && uiState.activeId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    stringResource(
                        when {
                            uiState.signing -> R.string.command_running
                            uiState.probingScope -> R.string.profile_sign_probing
                            else -> R.string.profile_sign
                        },
                    ),
                )
            }
        }
    }

    if (confirmOverwrite) {
        val scope = pendingSignScope ?: emptySet()
        // Classify before the run starts: signing accepts the image's rollback
        // index into RPMB, so anomalous values must be confirmed explicitly.
        val findings = RollbackIndexGuard.scanSpecs(
            specs = uiState.activeSpecs,
            scope = scope,
            nowSeconds = System.currentTimeMillis() / 1000,
        )
        AlertDialog(
            onDismissRequest = { confirmOverwrite = false },
            title = { Text(stringResource(R.string.command_modify_title)) },
            text = { Text(stringResource(R.string.profile_sign_modify_message)) },
            confirmButton = {
                DialogConfirmButton(onClick = {
                    confirmOverwrite = false
                    if (findings.isEmpty()) {
                        viewModel.signActive(scope)
                    } else {
                        signRollbackFindings = findings
                    }
                }) {
                    Text(stringResource(R.string.command_continue))
                }
            },
            dismissButton = {
                DialogDismissButton(onClick = { confirmOverwrite = false }) {
                    Text(stringResource(R.string.command_cancel))
                }
            },
        )
    }

    signRollbackFindings?.let { findings ->
        RollbackIndexWarningDialog(
            findings = findings,
            onDismiss = {
                signRollbackFindings = null
                pendingSignScope = null
            },
            onContinue = {
                signRollbackFindings = null
                viewModel.signActive(pendingSignScope ?: emptySet())
            },
        )
    }

    uiState.signPlan?.let { plan ->
        SignScopeDialog(
            plan = plan,
            specs = uiState.activeSpecs,
            onDismiss = { viewModel.dismissSignPlan() },
            onConfirm = { scope ->
                pendingSignScope = scope
                viewModel.dismissSignPlan()
                confirmOverwrite = true
            },
        )
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

    if (showCreateDialog) {
        CreateProfileDialog(
            existingIds = uiState.profiles.map { it.id }.toSet(),
            onDismiss = { showCreateDialog = false },
            onConfirm = { id, name ->
                showCreateDialog = false
                viewModel.createProfile(id, name)
            },
        )
    }

    if (showAddPartitionDialog) {
        AddPartitionDialog(
            existingPartitions = uiState.activeSpecs.map { it.partition }.toSet(),
            adding = uiState.addingPartition,
            warningEvent = uiState.addPartitionEvent,
            onPickImage = {
                pendingImagePartition = ADD_PARTITION_KEY
                imageLauncher.launch(arrayOf("*/*"))
            },
            pickedImageFileName = pendingAddPartitionImage?.toUri()
                ?.let { resolveImageDisplayName(it) },
            pickedImageUri = pendingAddPartitionImage?.toUri(),
            onDismiss = { showAddPartitionDialog = false },
            onConfirm = { name, useImageFileName, imageFileName, uri ->
                viewModel.addPartition(name, useImageFileName, imageFileName, uri)
                // Consume the one-shot pick so reopening starts clean.
                pendingAddPartitionImage = null
            },
            onUseDefault = { name, useImageFileName, imageFileName ->
                showAddPartitionDialog = false
                viewModel.addPartition(name, useImageFileName, imageFileName, null)
                pendingAddPartitionImage = null
            },
            onDiscard = {
                pendingAddPartitionImage = null
            },
        )
    }

    if (showDeletePartitionsDialog) {
        DeletePartitionsDialog(
            partitions = uiState.activeSpecs.map { it.partition },
            onDismiss = { showDeletePartitionsDialog = false },
            onConfirm = { selected ->
                showDeletePartitionsDialog = false
                pendingDeletePartitions = selected
            },
        )
    }

    pendingDeletePartitions?.let { names ->
        AlertDialog(
            onDismissRequest = { pendingDeletePartitions = null },
            title = { Text(stringResource(R.string.profile_partition_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.profile_partition_delete_message,
                        names.joinToString(", "),
                    ),
                )
            },
            confirmButton = {
                DialogConfirmButton(onClick = {
                    pendingDeletePartitions = null
                    viewModel.deletePartitions(names)
                }) {
                    Text(stringResource(R.string.command_continue))
                }
            },
            dismissButton = {
                DialogDismissButton(onClick = { pendingDeletePartitions = null }) {
                    Text(stringResource(R.string.command_cancel))
                }
            },
        )
    }
}

/**
 * New-profile dialog. Both fields are required; the confirm button stays
 * disabled until the id passes [ProfileStore.isValidProfileId] (ASCII only,
 * mirrors the on-disk folder naming rules) and is not taken by an existing
 * profile. Editing existing profiles will reuse this dialog later, so the
 * field wiring is kept self-contained here.
 */
@Composable
private fun CreateProfileDialog(
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (id: String, name: String) -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var idTouched by remember { mutableStateOf(false) }

    val idError = when {
        id.isEmpty() -> null
        !ProfileStore.isValidProfileId(id) -> stringResource(R.string.profile_create_id_error)
        id in existingIds -> stringResource(R.string.profile_create_id_taken)
        else -> null
    }
    val idValid = idError == null
    val canConfirm = id.isNotBlank() && name.isNotBlank() && idValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = {
                        id = it
                        idTouched = true
                    },
                    label = { Text(stringResource(R.string.profile_create_id_label)) },
                    singleLine = true,
                    isError = idTouched && !idValid,
                    supportingText = if (idTouched && idError != null) {
                        { Text(idError) }
                    } else {
                        null
                    },
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_create_name_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = { onConfirm(id.trim(), name.trim()) },
                enabled = canConfirm,
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

@Composable
private fun ImportExportRow(
    onImport: () -> Unit,
    onExport: () -> Unit,
    importEnabled: Boolean,
    exportEnabled: Boolean,
) {
    // Middle row of the card, split into two halves. Each half is its own
    // Surface so the 2dp gap between them shows the screen background —
    // the same segmentation look as between preference rows. Profile rows sit
    // above and the create row below, so all corners stay small.
    val halfShape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ActionHalfSurface(
            label = stringResource(R.string.profile_import),
            icon = Icons.Filled.FileDownload,
            enabled = importEnabled,
            onClick = onImport,
            shape = halfShape,
            modifier = Modifier.weight(1f),
        )
        ActionHalfSurface(
            label = stringResource(R.string.profile_export),
            icon = Icons.Filled.FileUpload,
            enabled = exportEnabled,
            onClick = onExport,
            shape = halfShape,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CreateProfileRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // Last row of the card, below import/export. The profiles above and the
    // import/export row in between keep small corners, so only this row's two
    // bottom corners use the large card radius.
    Surface(
        shape = RoundedCornerShape(
            topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp,
        ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(onClick = onClick, enabled = enabled) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.profile_create))
            }
        }
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

/** Sentinel image-partition key routing a launcher pick into the add-partition dialog. */
private const val ADD_PARTITION_KEY = "\u0000add_partition"

/**
 * Resolves the display name of a SAF-picked image on the caller's thread —
 * a single cheap provider query, with the URI's last segment as fallback.
 */
private fun resolveImageDisplayName(uri: Uri): String? {
    return try {
        val resolver = AppContextHolder.resolver ?: return uri.lastPathSegment
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}

/** Process-wide application context holder, populated from the screen. */
private object AppContextHolder {
    @Volatile
    var resolver: android.content.ContentResolver? = null
}

@Composable
private fun PartitionActionsRow(
    onAdd: () -> Unit,
    onDelete: () -> Unit,
) {
    // Last row of the images card, below the per-partition rows — mirrors
    // the import/export row's split-surface look, but with the large bottom
    // corners since nothing follows.
    val leftShape = RoundedCornerShape(
        topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 4.dp,
    )
    val rightShape = RoundedCornerShape(
        topStart = 4.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 16.dp,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ActionHalfSurface(
            label = stringResource(R.string.profile_partition_add),
            icon = Icons.Filled.Add,
            enabled = true,
            onClick = onAdd,
            shape = leftShape,
            modifier = Modifier.weight(1f),
        )
        ActionHalfSurface(
            label = stringResource(R.string.profile_partition_delete),
            icon = Icons.Filled.Delete,
            enabled = true,
            onClick = onDelete,
            shape = rightShape,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Add-partition dialog. Confirm is always enabled; the outcome decides what
 * happens — a valid image is parsed and stored (dialog dismissed on
 * success), an invalid one or a missing pick raises the warning dialog
 * offering discard / default descriptor.
 */
@Composable
private fun AddPartitionDialog(
    existingPartitions: Set<String>,
    adding: Boolean,
    warningEvent: AddPartitionEvent?,
    pickedImageFileName: String?,
    pickedImageUri: Uri?,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (name: String, useImageFileName: Boolean, imageFileName: String?, uri: Uri?) -> Unit,
    onUseDefault: (name: String, useImageFileName: Boolean, imageFileName: String?) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var useImageFileName by remember { mutableStateOf(false) }
    var nameTouched by remember { mutableStateOf(false) }

    val nameError = when {
        !name.all { it.code in 0..127 } -> stringResource(R.string.profile_partition_name_error)
        !useImageFileName && name.isNotEmpty() && name in existingPartitions ->
            stringResource(R.string.profile_partition_add_conflict)
        else -> null
    }

    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text(stringResource(R.string.profile_partition_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameTouched = true
                    },
                    label = { Text(stringResource(R.string.profile_partition_name_label)) },
                    singleLine = true,
                    enabled = !useImageFileName && !adding,
                    isError = nameTouched && nameError != null,
                    supportingText = if (nameTouched && nameError != null) {
                        { Text(nameError) }
                    } else {
                        null
                    },
                )
                PreferenceSwitchRow(
                    title = stringResource(R.string.profile_partition_use_image_name),
                    checked = useImageFileName,
                    enabled = !adding,
                    onCheckedChange = { useImageFileName = it },
                )
                PreferenceRow(
                    title = stringResource(R.string.profile_partition_pick_image),
                    summary = pickedImageFileName?.let {
                        stringResource(R.string.profile_partition_image_selected, it)
                    } ?: stringResource(R.string.profile_partition_image_none),
                    onClick = { if (!adding) onPickImage() },
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = {
                    onConfirm(name, useImageFileName, pickedImageFileName, pickedImageUri)
                },
                enabled = !adding && nameError == null &&
                    (useImageFileName || name.isNotBlank()),
            ) {
                Text(stringResource(R.string.command_continue))
            }
        },
        dismissButton = {
            DialogDismissButton(onClick = { if (!adding) onDismiss() }) {
                Text(stringResource(R.string.command_cancel))
            }
        },
    )

    if (warningEvent is AddPartitionEvent.InvalidImage ||
        warningEvent is AddPartitionEvent.NoImage
    ) {
        AlertDialog(
            onDismissRequest = {
                onDiscard()
                onDismiss()
            },
            title = { Text(stringResource(R.string.profile_partition_warning_title)) },
            text = {
                Text(
                    stringResource(
                        if (warningEvent is AddPartitionEvent.NoImage) {
                            R.string.profile_partition_warning_no_image
                        } else {
                            R.string.profile_partition_warning_invalid_image
                        },
                    ),
                )
            },
            confirmButton = {
                DialogConfirmButton(onClick = {
                    onUseDefault(name, useImageFileName, pickedImageFileName)
                }) {
                    Text(stringResource(R.string.profile_partition_warning_default))
                }
            },
            dismissButton = {
                DialogDismissButton(onClick = {
                    onDiscard()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.profile_partition_warning_discard))
                }
            },
        )
    }
}

/** Multi-select dialog over the active profile's partitions. */
@Composable
private fun DeletePartitionsDialog(
    partitions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {    var selected by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_partition_delete_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                partitions.forEach { partition ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (partition in selected) {
                                    selected - partition
                                } else {
                                    selected + partition
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = partition in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + partition else selected - partition
                            },
                        )
                        Text(partition, style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun ProfileResultView(result: ProfileSignResult) {
    var rawExpanded by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
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
            text = stringResource(R.string.profile_sign_result_title, result.profileName) + " · " +
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
        PreferenceGroup {
            row("profile_raw_output_toggle") {
                PreferenceRow(
                    title = stringResource(R.string.command_raw_output),
                    summary = stringResource(
                        if (rawExpanded) R.string.command_raw_output_collapse
                        else R.string.command_raw_output_expand,
                    ),
                    onClick = { rawExpanded = !rawExpanded },
                )
            }
            if (rawExpanded) {
                row("profile_raw_output_content") {
                    PreferenceRow(
                        title = "",
                        summaryContent = {
                            SelectionContainer {
                                Text(
                                    text = result.result.rawOutput,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        onLongClick = {
                            clipboard.setText(AnnotatedString(result.result.rawOutput))
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Sign-scope dialog: pick which partitions this run should touch. Rows are
 * gated by [SignScopePlanner.feasibility] — a footer partition without a
 * readable image and a vbmeta whose dependencies fall outside the scope are
 * disabled with the reason. Footer partitions start checked, vbmeta ones
 * unchecked (regenerating vbmeta is opt-in per run).
 */
@Composable
private fun SignScopeDialog(
    plan: SignScopePlan,
    specs: List<ProfilePartitionSpec>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var scope by remember(plan) {
        mutableStateOf(
            plan.partitions.filter { plan.descriptors[it] != "vbmeta" }.toSet(),
        )
    }

    val feasibility = remember(plan, scope) {
        SignScopePlanner.feasibility(
            specs = specs,
            scope = scope,
            imagePresent = { plan.imageAvailable[it] == true },
        )
    }
    // Display file names, not partition keys: the user is told which *file*
    // is gone ("Cannot access init_boot.img"), then how to fix it.
    val imageByPartition = remember(specs) { specs.associate { it.partition to it.image } }
    val anyFeasible = feasibility.values.any { it.feasible }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_sign_scope_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                plan.partitions.forEach { partition ->
                    val f = feasibility[partition] ?: SignScopePlanner.Feasibility(feasible = true)
                    val isVbmeta = plan.descriptors[partition] == "vbmeta"
                    // Re-signing rewrites the image's existing rollback index;
                    // where the profile sets a different one, the user is
                    // warned right where they pick the scope.
                    val spec = specs.firstOrNull { it.partition == partition }
                    val existingIndex = plan.existingRollbackIndex[partition]
                    val rollbackMismatch = spec?.rollbackIndex != null && existingIndex != null &&
                        existingIndex != BigInteger.valueOf(spec.rollbackIndex)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = f.feasible) {
                                scope = if (partition in scope) scope - partition else scope + partition
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = partition in scope,
                            onCheckedChange = { checked ->
                                if (f.feasible) {
                                    scope = if (checked) scope + partition else scope - partition
                                }
                            },
                            enabled = f.feasible,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(partition, style = MaterialTheme.typography.bodyLarge)
                            val note = when {
                                !f.feasible && f.missingDependencies.isNotEmpty() -> stringResource(
                                    R.string.profile_sign_scope_missing,
                                    f.missingDependencies.joinToString(", ") { dep ->
                                        imageByPartition[dep] ?: dep
                                    },
                                )
                                isVbmeta -> stringResource(R.string.profile_sign_scope_vbmeta)
                                else -> null
                            }
                            note?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (rollbackMismatch) {
                                Text(
                                    stringResource(
                                        R.string.rollback_mismatch_scope_note,
                                        existingIndex.toString(),
                                        spec!!.rollbackIndex.toString(),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.profile_sign_scope_retry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = { onConfirm(scope) },
                enabled = anyFeasible && scope.isNotEmpty(),
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
