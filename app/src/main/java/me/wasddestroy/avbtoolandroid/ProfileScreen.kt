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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
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
import me.wasddestroy.avbtoolandroid.ui.components.ResultPopupHost
import me.wasddestroy.avbtoolandroid.ui.components.SettingsList
import me.wasddestroy.avbtoolandroid.ui.components.preferenceGroup
import me.wasddestroy.avbtoolandroid.ui.components.preferenceParagraph
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AppContextHolder.resolver = context.applicationContext.contentResolver
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var confirmOverwrite by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddPartitionDialog by remember { mutableStateOf(false) }
    var showDeletePartitionsDialog by remember { mutableStateOf(false) }
    var pendingDeletePartitions by remember { mutableStateOf<Set<String>?>(null) }
    var pendingSignScope by remember { mutableStateOf<Set<String>?>(null) }
    var signRollbackFindings by remember { mutableStateOf<List<RollbackIndexFinding>?>(null) }
    var showAddKeyDialog by remember { mutableStateOf(false) }
    var showDeleteKeysDialog by remember { mutableStateOf(false) }
    var keyMenuTarget by remember { mutableStateOf<ProfileKeyUi?>(null) }
    // Full spec copy for the image long-press sheet; null while closed.
    var imageMenuTarget by remember { mutableStateOf<ProfilePartitionSpec?>(null) }
    var pendingKeyFile by remember { mutableStateOf<Uri?>(null) }
    var pendingKeyFileName by remember { mutableStateOf<String?>(null) }
    // Full spec copy for the partition edit dialog; null while closed.
    var editingPartition by remember { mutableStateOf<ProfilePartitionSpec?>(null) }
    var partitionSaveError by remember { mutableStateOf<PartitionSaveEvent.Failed?>(null) }

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

    val partitionSaveEvent = uiState.partitionSaveEvent
    LaunchedEffect(partitionSaveEvent) {
        when (partitionSaveEvent) {
            PartitionSaveEvent.Success -> {
                partitionSaveError = null
                editingPartition = null
                viewModel.consumePartitionSaveEvent()
            }
            is PartitionSaveEvent.Failed -> {
                // Inline reasons while the dialog is alive; if rotation closed
                // it, fall back to the toast channel so the failure is not lost.
                if (editingPartition != null) {
                    partitionSaveError = partitionSaveEvent
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.profile_partition_save_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                viewModel.consumePartitionSaveEvent()
            }
            null -> Unit
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

    val keyFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        pendingKeyFile = uri
        pendingKeyFileName = uri?.let { resolveDisplayName(it) }
    }

    val keyImportEvent = uiState.keyImportEvent
    LaunchedEffect(keyImportEvent) {
        when (keyImportEvent) {
            KeyImportEvent.Success -> {
                showAddKeyDialog = false
                pendingKeyFile = null
                pendingKeyFileName = null
                viewModel.consumeKeyImportEvent()
            }
            KeyImportEvent.DuplicateId -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.profile_key_id_taken),
                    Toast.LENGTH_SHORT,
                ).show()
                viewModel.consumeKeyImportEvent()
            }
            KeyImportEvent.InvalidKey -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.profile_key_import_invalid),
                    Toast.LENGTH_SHORT,
                ).show()
                viewModel.consumeKeyImportEvent()
            }
            KeyImportEvent.Failed -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.profile_key_import_failed),
                    Toast.LENGTH_SHORT,
                ).show()
                viewModel.consumeKeyImportEvent()
            }
            null -> Unit
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val src = uiState.pendingExports.firstOrNull()
        var saved = false
        if (uri != null && src != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.file.inputStream().use { it.copyTo(out) }
                }
                saved = true
            }
        }
        // The scratch copy is consumed by the ViewModel either way (saved or
        // canceled), but only advance past a save that actually happened —
        // otherwise the next file would silently replace this one in the
        // dialog while its bytes never left private storage.
        viewModel.consumeExport()
        if (!saved && uri != null) {
            viewModel.dismissExports()
        }
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
                        exports.joinToString("\n") { it.file.absolutePath },
                    ),
                )
            },
            confirmButton = {
                DialogConfirmButton(onClick = {
                    val src = exports.firstOrNull()
                    if (src != null) {
                        createDocument.launch(src.file.name)
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
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        Box(modifier = Modifier.weight(1f)) {
            SettingsList(
                contentPadding = PaddingValues(vertical = 8.dp),
                state = listState,
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
                preferenceGroup(key = "keys", titleRes = R.string.profile_group_keys) {
                    val keys = uiState.keys
                    if (keys.isEmpty()) {
                        row("keys_empty") {
                            PreferenceRow(
                                title = stringResource(R.string.profile_keys_empty),
                            )
                        }
                    } else {
                        keys.forEach { key ->
                            row("key_${key.id}") {
                                KeyRow(
                                    key = key,
                                    isDefault = key.id == uiState.defaultKeyId,
                                    onLongClick = { keyMenuTarget = key },
                                )
                            }
                        }
                    }
                    row("key_actions") {
                        SegmentActionsRow(
                            addLabel = stringResource(R.string.profile_key_add),
                            deleteLabel = stringResource(R.string.profile_key_delete),
                            addEnabled = true,
                            deleteEnabled = keys.isNotEmpty(),
                            onAdd = { showAddKeyDialog = true },
                            onDelete = { showDeleteKeysDialog = true },
                        )
                    }
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
                                        onLongClick = { imageMenuTarget = spec },
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
                                        onLongClick = { imageMenuTarget = spec },
                                    )
                                }
                            }
                        }
                    }
                    row("partition_actions") {
                        SegmentActionsRow(
                            addLabel = stringResource(R.string.profile_partition_add),
                            deleteLabel = stringResource(R.string.profile_partition_delete),
                            addEnabled = true,
                            deleteEnabled = uiState.activeSpecs.isNotEmpty(),
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
            if (!uiState.signing) {
                ResultPopupHost(
                    result = uiState.result?.result,
                    onDismiss = { },
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
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
                ?.let { resolveDisplayName(it) },
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

    editingPartition?.let { spec ->
        PartitionEditDialog(
            spec = spec,
            keyIds = uiState.keys.map { it.id },
            defaultKeyId = uiState.defaultKeyId,
            allPartitions = uiState.activeSpecs.map { it.partition },
            saving = uiState.savingPartition,
            saveError = partitionSaveError,
            onDismiss = {
                if (!uiState.savingPartition) {
                    editingPartition = null
                    partitionSaveError = null
                }
            },
            onSave = { edited ->
                partitionSaveError = null
                viewModel.updatePartition(edited)
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

    keyMenuTarget?.let { key ->
        KeyMenuSheet(
            key = key,
            isActive = key.id == uiState.defaultKeyId,
            onDismiss = { keyMenuTarget = null },
            onCopyId = {
                clipboard.setText(AnnotatedString(key.id))
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                keyMenuTarget = null
            },
            onCopyFile = {
                clipboard.setText(AnnotatedString(key.fileName))
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                keyMenuTarget = null
            },
            onActivate = {
                viewModel.activateKey(key.id)
                keyMenuTarget = null
            },
        )
    }

    imageMenuTarget?.let { spec ->
        ImageMenuSheet(
            hasImageSelection = uiState.imageSummaries.containsKey(spec.partition),
            onDismiss = { imageMenuTarget = null },
            onEditConfig = {
                imageMenuTarget = null
                editingPartition = spec
            },
            onClearImage = {
                imageMenuTarget = null
                viewModel.setImage(spec.partition, null)
            },
        )
    }

    if (showAddKeyDialog) {
        AddKeyDialog(
            existingIds = uiState.keys.map { it.id }.toSet(),
            pickedFileName = pendingKeyFileName,
            hasPickedFile = pendingKeyFile != null,
            adding = uiState.addingKey,
            onPickFile = { keyFileLauncher.launch(KEY_FILE_MIME_TYPES) },
            onDismiss = { showAddKeyDialog = false },
            onConfirm = { keyId -> viewModel.addKey(keyId, pendingKeyFile, pendingKeyFileName) },
        )
    }

    if (showDeleteKeysDialog) {
        DeleteKeysDialog(
            keys = uiState.keys,
            onDismiss = { showDeleteKeysDialog = false },
            onConfirm = { selected ->
                showDeleteKeysDialog = false
                viewModel.requestDeleteKeys(selected)
            },
        )
    }

    uiState.pendingKeyDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingKeyDelete,
            title = { Text(stringResource(R.string.profile_key_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.profile_key_delete_message, pending.ids.joinToString(", ")))
                    if (pending.referencedIds.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.profile_key_referenced_warning,
                                pending.referencedIds.sorted().joinToString(", "),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                DialogConfirmButton(onClick = viewModel::confirmDeleteKeys) {
                    Text(stringResource(R.string.command_continue))
                }
            },
            dismissButton = {
                DialogDismissButton(onClick = viewModel::dismissPendingKeyDelete) {
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
            // Clip before clickable so the ripple stays inside the segment's
            // corners (Surface clips after the caller's modifier).
            .clip(shape)
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

/** Identifiers of the single-field edit dialogs in PartitionEditDialog. */
private const val FIELD_IMAGE = "image"
private const val FIELD_PARTITION_NAME = "partition_name"
private const val FIELD_PARTITION_SIZE = "partition_size"
private const val FIELD_SALT = "salt"
private const val FIELD_ROLLBACK_INDEX = "rollback_index"
private const val FIELD_ROLLBACK_LOCATION = "rollback_location"
private const val FIELD_PROPS = "props"
private const val FIELD_BLOCK_SIZE = "block_size"
private const val FIELD_FEC_ROOTS = "fec_roots"
private const val FIELD_PADDING_SIZE = "padding_size"
private const val FIELD_SETUP_ROOTFS_IMAGE = "setup_rootfs_image"
private const val FIELD_OUTPUT_VBMETA = "output_vbmeta"
private const val FIELD_PUBKEY_METADATA = "pubkey_metadata"
private const val FIELD_SIGNING_HELPER = "signing_helper"
private const val FIELD_SIGNING_HELPER_FILES = "signing_helper_files"
private const val FIELD_APPEND_RELEASE = "append_release"

/** MIME types offered to the SAF picker for key files (.pem). */
private val KEY_FILE_MIME_TYPES = arrayOf(
    "application/x-pem-file",
    "application/octet-stream",
)

/**
 * Resolves the display name of a SAF-picked file on the caller's thread —
 * a single cheap provider query, with the URI's last segment as fallback.
 */
private fun resolveDisplayName(uri: Uri): String? {
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

/**
 * One key entry of the key-store card: "<id> (<stored file name>)" plus the
 * SHA-1 of the extracted public key. Rows have no click action — all
 * operations live behind the long-press sheet.
 */
@Composable
private fun KeyRow(
    key: ProfileKeyUi,
    isDefault: Boolean,
    onLongClick: () -> Unit,
) {
    PreferenceRow(
        title = stringResource(R.string.profile_key_title, key.id, key.fileName),
        summaryContent = {
            Text(
                text = when {
                    key.sha1 != null -> stringResource(R.string.profile_key_sha1, key.sha1)
                    key.sha1Failed -> stringResource(R.string.profile_key_sha1_unavailable)
                    else -> stringResource(R.string.profile_key_sha1_computing)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = if (key.sha1 != null) FontFamily.Monospace else null,
            )
        },
        iconContent = {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        trailing = if (isDefault) {
            {
                Text(
                    text = stringResource(R.string.profile_key_default_badge),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        onLongClick = onLongClick,
    )
}

/**
 * Long-press action sheet for a key entry. The "activate" item turns into a
 * disabled row while the key is already the profile's default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyMenuSheet(
    key: ProfileKeyUi,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onCopyId: () -> Unit,
    onCopyFile: () -> Unit,
    onActivate: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            SheetActionRow(
                label = stringResource(R.string.profile_key_menu_copy_id),
                icon = Icons.Filled.ContentCopy,
                onClick = onCopyId,
            )
            SheetActionRow(
                label = stringResource(R.string.profile_key_menu_copy_file),
                icon = Icons.Filled.ContentCopy,
                onClick = onCopyFile,
            )
            SheetActionRow(
                label = stringResource(R.string.profile_key_menu_activate),
                icon = Icons.Filled.Star,
                enabled = !isActive,
                onClick = onActivate,
            )
        }
    }
}

/**
 * Long-press action sheet for an image partition row. "Unselect" is enabled
 * only while the partition actually holds an image pick (the vbmeta output
 * rows never do), mirroring the disabled-activate pattern of [KeyMenuSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageMenuSheet(
    hasImageSelection: Boolean,
    onDismiss: () -> Unit,
    onEditConfig: () -> Unit,
    onClearImage: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            SheetActionRow(
                label = stringResource(R.string.profile_image_menu_edit),
                icon = Icons.Filled.Edit,
                onClick = onEditConfig,
            )
            SheetActionRow(
                label = stringResource(R.string.profile_image_menu_clear),
                icon = Icons.Filled.Close,
                enabled = hasImageSelection,
                onClick = onClearImage,
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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

/**
 * Add-key dialog. A key file must be picked before the import can proceed;
 * the key id either comes from the "use key file name" toggle (file name
 * minus extension, text field disabled) or is typed manually. Both paths
 * run through the same ASCII/uniqueness validation on the effective id.
 */
@Composable
private fun AddKeyDialog(
    existingIds: Set<String>,
    pickedFileName: String?,
    hasPickedFile: Boolean,
    adding: Boolean,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (keyId: String) -> Unit,
) {
    var useFileName by remember { mutableStateOf(true) }
    var id by remember { mutableStateOf("") }

    val effectiveId = if (useFileName) {
        pickedFileName?.substringBeforeLast('.')?.trim().orEmpty()
    } else {
        id.trim()
    }
    val idError = when {
        effectiveId.isEmpty() -> null
        !effectiveId.all { it.code in 0..127 } -> stringResource(R.string.profile_key_id_error)
        effectiveId in existingIds -> stringResource(R.string.profile_key_id_taken)
        else -> null
    }
    val canConfirm = hasPickedFile && adding.not() && idError == null && effectiveId.isNotEmpty()

    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text(stringResource(R.string.profile_key_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text(stringResource(R.string.profile_key_id_label)) },
                    singleLine = true,
                    enabled = !useFileName && !adding,
                    isError = idError != null,
                    supportingText = if (idError != null) {
                        { Text(idError) }
                    } else {
                        null
                    },
                )
                PreferenceSwitchRow(
                    title = stringResource(R.string.profile_key_use_file_name),
                    checked = useFileName,
                    enabled = !adding,
                    onCheckedChange = { useFileName = it },
                )
                PreferenceRow(
                    title = stringResource(R.string.profile_key_pick),
                    summary = pickedFileName?.let {
                        stringResource(R.string.profile_key_selected, it)
                    } ?: stringResource(R.string.profile_key_none),
                    onClick = { if (!adding) onPickFile() },
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = { onConfirm(effectiveId) },
                enabled = canConfirm,
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
}

/** Multi-select dialog over the active profile's key store entries. */
@Composable
private fun DeleteKeysDialog(
    keys: List<ProfileKeyUi>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_key_delete_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                keys.forEach { key ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (key.id in selected) {
                                    selected - key.id
                                } else {
                                    selected + key.id
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = key.id in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + key.id else selected - key.id
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(key.id, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                key.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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

/** Process-wide application context holder, populated from the screen. */
private object AppContextHolder {
    @Volatile
    var resolver: android.content.ContentResolver? = null
}

/**
 * Half-and-half "add / delete" action row closing a preference card —
 * mirrors the import/export row's split-surface look, with the large bottom
 * corners since nothing follows. Delete is disabled while the card has no
 * entries to delete.
 */
@Composable
private fun SegmentActionsRow(
    addLabel: String,
    deleteLabel: String,
    addEnabled: Boolean,
    deleteEnabled: Boolean,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
) {
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
            label = addLabel,
            icon = Icons.Filled.Add,
            enabled = addEnabled,
            onClick = onAdd,
            shape = leftShape,
            modifier = Modifier.weight(1f),
        )
        ActionHalfSurface(
            label = deleteLabel,
            icon = Icons.Filled.Delete,
            enabled = deleteEnabled,
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
                    expanded = rawExpanded,
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
 * disabled with the reason. The checked set always passes
 * [SignScopePlanner.prune]: footer partitions with a readable image start
 * checked, vbmeta ones unchecked (opt-in per run), and unchecking a
 * partition cascades to anything that depends on it.
 */
@Composable
private fun SignScopeDialog(
    plan: SignScopePlan,
    specs: List<ProfilePartitionSpec>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val imagePresent: (String) -> Boolean = { plan.imageAvailable[it] == true }

    fun prune(candidate: Set<String>): Set<String> =
        SignScopePlanner.prune(specs, candidate, imagePresent)

    var scope by remember(plan) {
        mutableStateOf(
            prune(plan.partitions.filter { plan.descriptors[it] != "vbmeta" }.toSet()),
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
                                scope = prune(
                                    if (partition in scope) scope - partition else scope + partition,
                                )
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = partition in scope,
                            onCheckedChange = { checked ->
                                if (f.feasible) {
                                    scope = prune(
                                        if (checked) scope + partition else scope - partition,
                                    )
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
                // How-to-fix hint: only meaningful when something is actually
                // unreachable; an all-images-available scope must not nag.
                if (feasibility.values.any { !it.feasible }) {
                    Text(
                        stringResource(R.string.profile_sign_scope_retry),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
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


/**
 * Long-press edit dialog for one profile partition. The dialog always works
 * on a full [ProfilePartitionSpec] copy — every field is edited in place and
 * the whole spec is handed back to [ProfileViewModel.updatePartition], so
 * fields not shown here survive the write. Validation runs in the ViewModel;
 * [saveError] carries the reasons to show inline, keeping the dialog open.
 */
@Composable
private fun PartitionEditDialog(
    spec: ProfilePartitionSpec,
    keyIds: List<String>,
    defaultKeyId: String?,
    allPartitions: List<String>,
    saving: Boolean,
    saveError: PartitionSaveEvent.Failed?,
    onDismiss: () -> Unit,
    onSave: (ProfilePartitionSpec) -> Unit,
) {
    val isVbmeta = spec.descriptor == "vbmeta"
    val isHash = spec.descriptor == "hash"
    val isHashtree = spec.descriptor == "hashtree"

    // ---- editable copies (basic) -----------------------------------------
    var image by remember(spec) { mutableStateOf(spec.image) }
    var imageTouched by remember(spec) { mutableStateOf(false) }
    var partitionName by remember(spec) { mutableStateOf(spec.partitionName) }
    var algorithm by remember(spec) { mutableStateOf(spec.algorithm) }
    var keyId by remember(spec) { mutableStateOf(spec.keyId) }
    var partitionSize by remember(spec) {
        mutableStateOf(spec.partitionSize?.toString().orEmpty())
    }
    var dynamicPartitionSize by remember(spec) { mutableStateOf(spec.dynamicPartitionSize) }
    var rollbackIndex by remember(spec) {
        mutableStateOf(spec.rollbackIndex?.toString().orEmpty())
    }
    var hashAlgorithm by remember(spec) { mutableStateOf(spec.hashAlgorithm) }
    var salt by remember(spec) { mutableStateOf(spec.salt.orEmpty()) }
    var flags by remember(spec) { mutableStateOf(spec.flags?.toString() ?: "0") }

    // ---- editable copies (advanced) --------------------------------------
    var showAdvanced by remember(spec) { mutableStateOf(false) }
    var rollbackIndexLocation by remember(spec) {
        mutableStateOf(spec.rollbackIndexLocation?.toString().orEmpty())
    }
    var propsText by remember(spec) {
        mutableStateOf(spec.props.joinToString("\n") { "${it.first}:${it.second}" })
    }
    var setHashtreeDisabledFlag by remember(spec) { mutableStateOf(spec.setHashtreeDisabledFlag) }
    var setVerificationDisabledFlag by remember(spec) {
        mutableStateOf(spec.setVerificationDisabledFlag)
    }
    var blockSize by remember(spec) { mutableStateOf(spec.blockSize.toString()) }
    var doNotGenerateFec by remember(spec) { mutableStateOf(spec.doNotGenerateFec) }
    var fecNumRoots by remember(spec) { mutableStateOf(spec.fecNumRoots.toString()) }
    var noHashtree by remember(spec) { mutableStateOf(spec.noHashtree) }
    var checkAtMostOnce by remember(spec) { mutableStateOf(spec.checkAtMostOnce) }
    var setupAsRootfsFromKernel by remember(spec) { mutableStateOf(spec.setupAsRootfsFromKernel) }
    var paddingSize by remember(spec) {
        mutableStateOf(spec.paddingSize?.toString().orEmpty())
    }
    var includedPartitions by remember(spec) {
        mutableStateOf(spec.includedPartitions.toSet())
    }
    var chainPartitions by remember(spec) { mutableStateOf(spec.chainPartitions) }
    var chainPartitionsDoNotUseAb by remember(spec) {
        mutableStateOf(spec.chainPartitionsDoNotUseAb)
    }
    var includeDescriptorsFromImage by remember(spec) {
        mutableStateOf(spec.includeDescriptorsFromImage)
    }
    var kernelCmdlines by remember(spec) { mutableStateOf(spec.kernelCmdlines) }
    var outputVbmetaImage by remember(spec) { mutableStateOf(spec.outputVbmetaImage.orEmpty()) }
    var signingHelper by remember(spec) { mutableStateOf(spec.signingHelper.orEmpty()) }
    var signingHelperWithFiles by remember(spec) {
        mutableStateOf(spec.signingHelperWithFiles.orEmpty())
    }
    var publicKeyMetadata by remember(spec) { mutableStateOf(spec.publicKeyMetadata.orEmpty()) }
    var appendToReleaseString by remember(spec) {
        mutableStateOf(spec.appendToReleaseString.orEmpty())
    }
    var setupRootfsFromKernel by remember(spec) {
        mutableStateOf(spec.setupRootfsFromKernel.orEmpty())
    }

    // ---- sub-dialogs ------------------------------------------------------
    var choosingAlgorithm by remember { mutableStateOf(false) }
    var choosingHashAlgorithm by remember { mutableStateOf(false) }
    var choosingFlags by remember { mutableStateOf(false) }
    var choosingKey by remember { mutableStateOf(false) }
    var choosingIncluded by remember { mutableStateOf(false) }
    var editingChains by remember { mutableStateOf(false) }
    var editingChainsNoAb by remember { mutableStateOf(false) }
    var editingIncludeImages by remember { mutableStateOf(false) }
    var editingCmdlines by remember { mutableStateOf(false) }
    /** Identifies the single-field edit dialog currently open, if any. */
    var editingField by remember { mutableStateOf<String?>(null) }

    val hasImageWarning = imageTouched && image != spec.image && !isVbmeta
    val sizeLong = partitionSize.toLongOrNull()

    fun buildSpec(): ProfilePartitionSpec = spec.copy(
        image = image.trim(),
        partitionName = if (isVbmeta) {
            spec.partitionName
        } else {
            partitionName.trim().ifBlank { spec.partition }
        },
        algorithm = algorithm,
        keyId = keyId?.takeIf { it.isNotBlank() },
        partitionSize = sizeLong?.takeIf { it > 0 },
        dynamicPartitionSize = dynamicPartitionSize,
        rollbackIndex = rollbackIndex.toLongOrNull(),
        hashAlgorithm = hashAlgorithm,
        salt = salt.trim().takeIf { it.isNotBlank() },
        flags = flags.toLongOrNull(),
        rollbackIndexLocation = rollbackIndexLocation.toLongOrNull(),
        props = propsText.lines().mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }.filter { (k, _) -> k.isNotBlank() },
        setHashtreeDisabledFlag = setHashtreeDisabledFlag,
        setVerificationDisabledFlag = setVerificationDisabledFlag,
        blockSize = blockSize.toLongOrNull() ?: 4096L,
        doNotGenerateFec = doNotGenerateFec,
        fecNumRoots = fecNumRoots.toLongOrNull() ?: 2L,
        noHashtree = noHashtree,
        checkAtMostOnce = checkAtMostOnce,
        setupAsRootfsFromKernel = setupAsRootfsFromKernel,
        paddingSize = paddingSize.toLongOrNull()?.takeIf { it > 0 },
        includedPartitions = includedPartitions.toList(),
        chainPartitions = chainPartitions,
        chainPartitionsDoNotUseAb = chainPartitionsDoNotUseAb,
        includeDescriptorsFromImage = includeDescriptorsFromImage,
        kernelCmdlines = kernelCmdlines,
        outputVbmetaImage = outputVbmetaImage.trim().takeIf { it.isNotBlank() },
        signingHelper = signingHelper.trim().takeIf { it.isNotBlank() },
        signingHelperWithFiles = signingHelperWithFiles.trim().takeIf { it.isNotBlank() },
        publicKeyMetadata = publicKeyMetadata.trim().takeIf { it.isNotBlank() },
        appendToReleaseString = appendToReleaseString.trim().takeIf { it.isNotBlank() },
        setupRootfsFromKernel = setupRootfsFromKernel.trim().takeIf { it.isNotBlank() },
    )

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Text(stringResource(R.string.profile_partition_edit_title, spec.partition))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.profile_partition_edit_descriptor,
                        spec.descriptor,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // One clustered paragraph per section, exactly like the
                // image-management list: pickers and switches first, then the
                // text-style rows (each opens a single-field edit dialog).
                preferenceParagraph(
                    listOf<@Composable () -> Unit>(
                        // algorithm picker
                        {
                            PreferenceRow(
                                title = stringResource(
                                    R.string.arg_add_hash_footer_algorithm_label,
                                ),
                                summary = algorithm,
                                onClick = { if (!saving) choosingAlgorithm = true },
                            )
                        },
                        // signing key picker
                        {
                            PreferenceRow(
                                title = stringResource(R.string.profile_partition_edit_key),
                                summary = keyId
                                    ?: stringResource(R.string.profile_partition_edit_key_none),
                                onClick = { if (!saving) choosingKey = true },
                            )
                        },
                    ) + (if (!isVbmeta) {
                        listOf<@Composable () -> Unit>(
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.arg_add_hash_footer_hash_algorithm_label,
                                    ),
                                    summary = hashAlgorithm,
                                    onClick = { if (!saving) choosingHashAlgorithm = true },
                                )
                            },
                        ) + (if (isHash) {
                            listOf<@Composable () -> Unit>(
                                {
                                    PreferenceSwitchRow(
                                        title = stringResource(
                                            R.string.profile_partition_edit_dynamic_size,
                                        ),
                                        checked = dynamicPartitionSize,
                                        enabled = !saving,
                                        onCheckedChange = { dynamicPartitionSize = it },
                                    )
                                },
                            )
                        } else {
                            emptyList()
                        })
                    } else {
                        emptyList()
                    }) + listOf<@Composable () -> Unit>(
                        // flags picker
                        {
                            PreferenceRow(
                                title = stringResource(R.string.profile_partition_edit_flags),
                                summary = FLAGS_OPTIONS.firstOrNull { it.value == flags }
                                    ?.let { stringResource(it.labelRes) } ?: flags,
                                onClick = { if (!saving) choosingFlags = true },
                            )
                        },
                        // image file name
                        {
                            PreferenceRow(
                                title = stringResource(R.string.profile_partition_edit_image),
                                summary = image.ifBlank {
                                    stringResource(R.string.profile_partition_edit_unset)
                                },
                                summaryContent = if (hasImageWarning) {
                                    {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = image,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = stringResource(
                                                    R.string.profile_partition_edit_image_warning,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                                onClick = { if (!saving) editingField = FIELD_IMAGE },
                            )
                        },
                    ) + (if (!isVbmeta) {
                        listOf<@Composable () -> Unit>(
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_partition_name,
                                    ),
                                    summary = partitionName.ifBlank {
                                        stringResource(R.string.profile_partition_edit_unset)
                                    },
                                    onClick = { if (!saving) editingField = FIELD_PARTITION_NAME },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_partition_size,
                                    ),
                                    summary = partitionSize.ifBlank {
                                        stringResource(
                                            if (isHashtree) {
                                                R.string.profile_partition_edit_size_append
                                            } else {
                                                R.string.profile_partition_edit_unset
                                            },
                                        )
                                    },
                                    enabled = !(isHash && dynamicPartitionSize),
                                    onClick = { if (!saving) editingField = FIELD_PARTITION_SIZE },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(R.string.profile_partition_edit_salt),
                                    summary = salt.ifBlank {
                                        stringResource(R.string.profile_partition_edit_salt_random)
                                    },
                                    onClick = { if (!saving) editingField = FIELD_SALT },
                                )
                            },
                        )
                    } else {
                        emptyList()
                    }) + listOf<@Composable () -> Unit>(
                        {
                            PreferenceRow(
                                title = stringResource(
                                    R.string.profile_partition_edit_rollback_index,
                                ),
                                summary = rollbackIndex.ifBlank {
                                    stringResource(R.string.profile_partition_edit_omit)
                                },
                                onClick = { if (!saving) editingField = FIELD_ROLLBACK_INDEX },
                            )
                        },
                    ),
                )

                // ---- advanced segment ------------------------------------
                // The toggle row is the segment's first row: collapsed, the
                // segment holds only this row; expanded, it stays at the top
                // of the same clustered paragraph.
                preferenceParagraph(
                    (listOf<@Composable () -> Unit>(
                        {
                            PreferenceRow(
                                title = stringResource(
                                    R.string.profile_partition_edit_advanced,
                                ),
                                summary = stringResource(
                                    if (showAdvanced) {
                                        R.string.profile_partition_edit_advanced_hide
                                    } else {
                                        R.string.profile_partition_edit_advanced_show
                                    },
                                ),
                                onClick = { showAdvanced = !showAdvanced },
                                expanded = showAdvanced,
                            )
                        },
                    ) + (if (showAdvanced) {
                        (if (isVbmeta) {
                            listOf<@Composable () -> Unit>(
                                {
                                    PreferenceRow(
                                        title = stringResource(
                                            R.string.profile_partition_edit_included,
                                        ),
                                        summary = if (includedPartitions.isEmpty()) {
                                            stringResource(R.string.profile_partition_edit_none_set)
                                        } else {
                                            includedPartitions.sorted().joinToString(", ")
                                        },
                                        onClick = { if (!saving) choosingIncluded = true },
                                    )
                                },
                            )
                        } else {
                            emptyList()
                        }) + listOf<@Composable () -> Unit>(
                            {
                                PreferenceSwitchRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_hashtree_disabled,
                                    ),
                                    checked = setHashtreeDisabledFlag,
                                    enabled = !saving,
                                    onCheckedChange = { setHashtreeDisabledFlag = it },
                                )
                            },
                            {
                                PreferenceSwitchRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_verification_disabled,
                                    ),
                                    checked = setVerificationDisabledFlag,
                                    enabled = !saving,
                                    onCheckedChange = { setVerificationDisabledFlag = it },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_chain,
                                    ),
                                    summary = pluralStringResource(
                                        R.plurals.profile_partition_edit_chain_summary,
                                        chainPartitions.size,
                                        chainPartitions.size,
                                    ),
                                    onClick = { if (!saving) editingChains = true },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_chain_no_ab,
                                    ),
                                    summary = pluralStringResource(
                                        R.plurals.profile_partition_edit_chain_summary,
                                        chainPartitionsDoNotUseAb.size,
                                        chainPartitionsDoNotUseAb.size,
                                    ),
                                    onClick = { if (!saving) editingChainsNoAb = true },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_include_images,
                                    ),
                                    summary = if (includeDescriptorsFromImage.isEmpty()) {
                                        stringResource(R.string.profile_partition_edit_none_set)
                                    } else {
                                        pluralStringResource(
                                            R.plurals.profile_partition_edit_list_count,
                                            includeDescriptorsFromImage.size,
                                            includeDescriptorsFromImage.size,
                                        )
                                    },
                                    onClick = { if (!saving) editingIncludeImages = true },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_cmdlines,
                                    ),
                                    summary = if (kernelCmdlines.isEmpty()) {
                                        stringResource(R.string.profile_partition_edit_none_set)
                                    } else {
                                        pluralStringResource(
                                            R.plurals.profile_partition_edit_list_count,
                                            kernelCmdlines.size,
                                            kernelCmdlines.size,
                                        )
                                    },
                                    onClick = { if (!saving) editingCmdlines = true },
                                )
                            },
                        ) + (if (isHashtree) {
                            listOf<@Composable () -> Unit>(
                                {
                                    PreferenceSwitchRow(
                                        title = stringResource(
                                            R.string.profile_partition_edit_no_fec,
                                        ),
                                        checked = doNotGenerateFec,
                                        enabled = !saving,
                                        onCheckedChange = { doNotGenerateFec = it },
                                    )
                                },
                                {
                                    PreferenceSwitchRow(
                                        title = stringResource(
                                            R.string.profile_partition_edit_no_hashtree,
                                        ),
                                        checked = noHashtree,
                                        enabled = !saving,
                                        onCheckedChange = { noHashtree = it },
                                    )
                                },
                                {
                                    PreferenceSwitchRow(
                                        title = stringResource(
                                            R.string.profile_partition_edit_check_at_most_once,
                                        ),
                                        checked = checkAtMostOnce,
                                        enabled = !saving,
                                        onCheckedChange = { checkAtMostOnce = it },
                                    )
                                },
                                {
                                    PreferenceSwitchRow(
                                        title = stringResource(
                                            R.string.profile_partition_edit_setup_rootfs,
                                        ),
                                        checked = setupAsRootfsFromKernel,
                                        enabled = !saving,
                                        onCheckedChange = { setupAsRootfsFromKernel = it },
                                    )
                                },
                            )
                        } else {
                            emptyList()
                        }) + listOf<@Composable () -> Unit>(
                            // text-style rows, each opening an edit dialog
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_rollback_location,
                                    ),
                                    summary = rollbackIndexLocation.ifBlank {
                                        stringResource(R.string.profile_partition_edit_omit)
                                    },
                                    onClick = {
                                        if (!saving) editingField = FIELD_ROLLBACK_LOCATION
                                    },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(R.string.profile_partition_edit_props),
                                    summary = if (propsText.isBlank()) {
                                        stringResource(R.string.profile_partition_edit_none_set)
                                    } else {
                                        pluralStringResource(
                                            R.plurals.profile_partition_edit_list_count,
                                            propsText.lines().count { it.isNotBlank() },
                                            propsText.lines().count { it.isNotBlank() },
                                        )
                                    },
                                    onClick = { if (!saving) editingField = FIELD_PROPS },
                                )
                            },
                        ) + (if (isHashtree) {
                            listOf<@Composable () -> Unit>(
                                {
                                    PreferenceRow(
                                        title = stringResource(
                                            R.string.profile_partition_edit_block_size,
                                        ),
                                        summary = blockSize,
                                        onClick = { if (!saving) editingField = FIELD_BLOCK_SIZE },
                                    )
                                },
                                {
                                    PreferenceRow(
                                        title = stringResource(
                                            R.string.profile_partition_edit_fec_num_roots,
                                        ),
                                        summary = fecNumRoots,
                                        enabled = !doNotGenerateFec,
                                        onClick = { if (!saving) editingField = FIELD_FEC_ROOTS },
                                    )
                                },
                            )
                        } else {
                            emptyList()
                        }) + (if (isVbmeta) {
                            listOf<@Composable () -> Unit>(
                                {
                                    PreferenceRow(
                                        title = stringResource(
                                            R.string.profile_partition_edit_padding_size,
                                        ),
                                        summary = paddingSize.ifBlank {
                                            stringResource(R.string.profile_partition_edit_omit)
                                        },
                                        onClick = { if (!saving) editingField = FIELD_PADDING_SIZE },
                                    )
                                },
                            )
                        } else {
                            emptyList()
                        }) + listOf<@Composable () -> Unit>(
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_setup_rootfs_image,
                                    ),
                                    summary = setupRootfsFromKernel.ifBlank {
                                        stringResource(R.string.profile_partition_edit_unset)
                                    },
                                    onClick = {
                                        if (!saving) editingField = FIELD_SETUP_ROOTFS_IMAGE
                                    },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_output_vbmeta,
                                    ),
                                    summary = outputVbmetaImage.ifBlank {
                                        stringResource(R.string.profile_partition_edit_unset)
                                    },
                                    onClick = {
                                        if (!saving) editingField = FIELD_OUTPUT_VBMETA
                                    },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_pubkey_metadata,
                                    ),
                                    summary = publicKeyMetadata.ifBlank {
                                        stringResource(R.string.profile_partition_edit_unset)
                                    },
                                    onClick = {
                                        if (!saving) editingField = FIELD_PUBKEY_METADATA
                                    },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_signing_helper,
                                    ),
                                    summary = signingHelper.ifBlank {
                                        stringResource(R.string.profile_partition_edit_unset)
                                    },
                                    onClick = {
                                        if (!saving) editingField = FIELD_SIGNING_HELPER
                                    },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_signing_helper_files,
                                    ),
                                    summary = signingHelperWithFiles.ifBlank {
                                        stringResource(R.string.profile_partition_edit_unset)
                                    },
                                    onClick = {
                                        if (!saving) editingField = FIELD_SIGNING_HELPER_FILES
                                    },
                                )
                            },
                            {
                                PreferenceRow(
                                    title = stringResource(
                                        R.string.profile_partition_edit_append_release,
                                    ),
                                    summary = appendToReleaseString.ifBlank {
                                        stringResource(R.string.profile_partition_edit_unset)
                                    },
                                    onClick = {
                                        if (!saving) editingField = FIELD_APPEND_RELEASE
                                    },
                                )
                            },
                        )
                    } else {
                        emptyList()
                    })),
                )

                saveError?.let { error ->
                    Column {
                        if (error.problems.isEmpty()) {
                            Text(
                                text = stringResource(R.string.profile_partition_save_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            error.problems.forEach { problem ->
                                Text(
                                    text = stringResource(validationMessageRes(problem)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = { onSave(buildSpec()) },
                enabled = !saving,
            ) {
                Text(stringResource(R.string.profile_partition_edit_save))
            }
        },
        dismissButton = {
            DialogDismissButton(onClick = { if (!saving) onDismiss() }) {
                Text(stringResource(R.string.command_cancel))
            }
        },
    )

    if (choosingAlgorithm) {
        SimpleChoiceDialog(
            titleRes = R.string.arg_add_hash_footer_algorithm_label,
            options = SIGNING_ALGORITHMS,
            selected = algorithm,
            onDismiss = { choosingAlgorithm = false },
            onSelect = {
                algorithm = it
                choosingAlgorithm = false
            },
        )
    }
    if (choosingHashAlgorithm) {
        SimpleChoiceDialog(
            titleRes = R.string.arg_add_hash_footer_hash_algorithm_label,
            options = HASH_ALGORITHMS,
            selected = hashAlgorithm,
            onDismiss = { choosingHashAlgorithm = false },
            onSelect = {
                hashAlgorithm = it
                choosingHashAlgorithm = false
            },
        )
    }
    if (choosingFlags) {
        AlertDialog(
            onDismissRequest = { choosingFlags = false },
            title = { Text(stringResource(R.string.profile_partition_edit_flags)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    FLAGS_OPTIONS.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    flags = option.value
                                    choosingFlags = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(selected = option.value == flags, onClick = null)
                            Text(
                                text = stringResource(option.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                DialogDismissButton(onClick = { choosingFlags = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    if (choosingKey) {
        AlertDialog(
            onDismissRequest = { choosingKey = false },
            title = { Text(stringResource(R.string.profile_partition_edit_key)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                keyId = null
                                choosingKey = false
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = keyId == null, onClick = null)
                        Text(
                            text = stringResource(R.string.profile_partition_edit_key_none),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    keyIds.forEach { id ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    keyId = id
                                    choosingKey = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(selected = keyId == id, onClick = null)
                            Text(
                                text = if (id == defaultKeyId) {
                                    stringResource(R.string.profile_key_default_badge) + " $id"
                                } else {
                                    id
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                DialogDismissButton(onClick = { choosingKey = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (choosingIncluded) {
        // Union of the profile's partitions and the stored selection: entries
        // that exist only in the stored selection ("ghost" partitions left
        // over from an imported image) stay visible so they can be unchecked,
        // which removes them from the persisted config on save.
        val candidates = (allPartitions + includedPartitions).distinct()
        val notInProfile = includedPartitions - allPartitions.toSet()
        MultiSelectPartitionsDialog(
            titleRes = R.string.profile_partition_edit_included,
            partitions = candidates,
            notInProfile = notInProfile,
            selected = includedPartitions,
            onDismiss = { choosingIncluded = false },
            onConfirm = {
                includedPartitions = it
                choosingIncluded = false
            },
        )
    }
    if (editingChains || editingChainsNoAb) {
        StringListEditDialog(
            titleRes = if (editingChainsNoAb) {
                R.string.profile_partition_edit_chain_no_ab
            } else {
                R.string.profile_partition_edit_chain
            },
            entries = if (editingChainsNoAb) chainPartitionsDoNotUseAb else chainPartitions,
            formatHint = stringResource(R.string.profile_partition_edit_chain_hint),
            validateEntry = { entry ->
                val parts = entry.split(':')
                parts.size == 3 && parts[1].toLongOrNull()?.let { slot -> slot >= 1 } == true
            },
            onDismiss = {
                editingChains = false
                editingChainsNoAb = false
            },
            onConfirm = {
                if (editingChainsNoAb) {
                    chainPartitionsDoNotUseAb = it
                    editingChainsNoAb = false
                } else {
                    chainPartitions = it
                    editingChains = false
                }
            },
        )
    }
    if (editingIncludeImages) {
        StringListEditDialog(
            titleRes = R.string.profile_partition_edit_include_images,
            entries = includeDescriptorsFromImage,
            formatHint = stringResource(R.string.profile_partition_edit_include_hint),
            validateEntry = { it.isNotBlank() },
            onDismiss = { editingIncludeImages = false },
            onConfirm = {
                includeDescriptorsFromImage = it
                editingIncludeImages = false
            },
        )
    }
    if (editingCmdlines) {
        StringListEditDialog(
            titleRes = R.string.profile_partition_edit_cmdlines,
            entries = kernelCmdlines,
            formatHint = stringResource(R.string.profile_partition_edit_cmdline_hint),
            validateEntry = { it.isNotBlank() },
            onDismiss = { editingCmdlines = false },
            onConfirm = {
                kernelCmdlines = it
                editingCmdlines = false
            },
        )
    }

    when (editingField) {
        FIELD_IMAGE -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_image),
            initialValue = image,
            onDismiss = { editingField = null },
            onConfirm = {
                image = it
                imageTouched = true
                editingField = null
            },
        )
        FIELD_PARTITION_NAME -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_partition_name),
            initialValue = partitionName,
            supportingText = stringResource(R.string.profile_partition_edit_partition_name_hint),
            onDismiss = { editingField = null },
            onConfirm = {
                partitionName = it
                editingField = null
            },
        )
        FIELD_PARTITION_SIZE -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_partition_size),
            initialValue = partitionSize,
            numeric = true,
            supportingText = if (isHashtree) {
                stringResource(R.string.profile_partition_edit_size_append)
            } else {
                stringResource(R.string.profile_partition_edit_partition_size_hint)
            },
            onDismiss = { editingField = null },
            onConfirm = {
                partitionSize = it
                editingField = null
            },
        )
        FIELD_SALT -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_salt),
            initialValue = salt,
            supportingText = stringResource(R.string.profile_partition_edit_salt_random),
            onDismiss = { editingField = null },
            onConfirm = {
                salt = it
                editingField = null
            },
        )
        FIELD_ROLLBACK_INDEX -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_rollback_index),
            initialValue = rollbackIndex,
            numeric = true,
            filterInput = { value -> value.filter { c -> c.isDigit() || c == '-' } },
            supportingText = stringResource(R.string.profile_partition_edit_rollback_index_hint),
            onDismiss = { editingField = null },
            onConfirm = {
                rollbackIndex = it
                editingField = null
            },
        )
        FIELD_ROLLBACK_LOCATION -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_rollback_location),
            initialValue = rollbackIndexLocation,
            numeric = true,
            filterInput = { value -> value.filter { c -> c.isDigit() } },
            supportingText = stringResource(R.string.profile_partition_edit_rollback_location_hint),
            onDismiss = { editingField = null },
            onConfirm = {
                rollbackIndexLocation = it
                editingField = null
            },
        )
        FIELD_PROPS -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_props),
            initialValue = propsText,
            singleLine = false,
            supportingText = stringResource(R.string.profile_partition_edit_props_hint),
            onDismiss = { editingField = null },
            onConfirm = {
                propsText = it
                editingField = null
            },
        )
        FIELD_BLOCK_SIZE -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_block_size),
            initialValue = blockSize,
            numeric = true,
            onDismiss = { editingField = null },
            onConfirm = {
                blockSize = it
                editingField = null
            },
        )
        FIELD_FEC_ROOTS -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_fec_num_roots),
            initialValue = fecNumRoots,
            numeric = true,
            onDismiss = { editingField = null },
            onConfirm = {
                fecNumRoots = it
                editingField = null
            },
        )
        FIELD_PADDING_SIZE -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_padding_size),
            initialValue = paddingSize,
            numeric = true,
            supportingText = stringResource(R.string.profile_partition_edit_omit),
            onDismiss = { editingField = null },
            onConfirm = {
                paddingSize = it
                editingField = null
            },
        )
        FIELD_SETUP_ROOTFS_IMAGE -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_setup_rootfs_image),
            initialValue = setupRootfsFromKernel,
            supportingText = stringResource(R.string.profile_partition_edit_setup_rootfs_image_hint),
            onDismiss = { editingField = null },
            onConfirm = {
                setupRootfsFromKernel = it
                editingField = null
            },
        )
        FIELD_OUTPUT_VBMETA -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_output_vbmeta),
            initialValue = outputVbmetaImage,
            supportingText = stringResource(R.string.profile_partition_edit_output_vbmeta_hint),
            onDismiss = { editingField = null },
            onConfirm = {
                outputVbmetaImage = it
                editingField = null
            },
        )
        FIELD_PUBKEY_METADATA -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_pubkey_metadata),
            initialValue = publicKeyMetadata,
            supportingText = stringResource(R.string.profile_partition_edit_pubkey_metadata_hint),
            onDismiss = { editingField = null },
            onConfirm = {
                publicKeyMetadata = it
                editingField = null
            },
        )
        FIELD_SIGNING_HELPER -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_signing_helper),
            initialValue = signingHelper,
            onDismiss = { editingField = null },
            onConfirm = {
                signingHelper = it
                editingField = null
            },
        )
        FIELD_SIGNING_HELPER_FILES -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_signing_helper_files),
            initialValue = signingHelperWithFiles,
            onDismiss = { editingField = null },
            onConfirm = {
                signingHelperWithFiles = it
                editingField = null
            },
        )
        FIELD_APPEND_RELEASE -> PartitionTextFieldDialog(
            title = stringResource(R.string.profile_partition_edit_append_release),
            initialValue = appendToReleaseString,
            onDismiss = { editingField = null },
            onConfirm = {
                appendToReleaseString = it
                editingField = null
            },
        )
    }
}

/**
 * Single-field edit dialog opened from a partition edit row (mirrors
 * CommandScreen's CommandTextEditDialog): one text field, OK/cancel.
 * [filterInput] sanitizes while typing (e.g. digits-only for numeric fields).
 */
@Composable
private fun PartitionTextFieldDialog(
    title: String,
    initialValue: String,
    singleLine: Boolean = true,
    numeric: Boolean = false,
    filterInput: (String) -> String = { it },
    supportingText: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = filterInput(it) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = supportingText?.let { hint -> { Text(hint) } },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 2,
                maxLines = if (singleLine) 1 else 6,
                keyboardOptions = if (numeric) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
            )
        },
        confirmButton = {
            DialogConfirmButton(onClick = { onConfirm(draft.trim()) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            DialogDismissButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * Checkbox multi-select over [partitions], used by the edit dialog for
 * `included_partitions`. [partitions] is the union of the profile's
 * partitions and the currently stored selection, so entries left over from
 * an imported image ("ghost" partitions with no profile row) stay visible
 * and can be unchecked — confirming drops them from the selection.
 * [notInProfile] holds the names that exist only in the stored selection;
 * those rows carry a suffix marking them. Confirming hands back the
 * selection.
 */
@Composable
private fun MultiSelectPartitionsDialog(
    titleRes: Int,
    partitions: List<String>,
    notInProfile: Set<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var checked by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                partitions.forEach { partition ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                checked = if (partition in checked) {
                                    checked - partition
                                } else {
                                    checked + partition
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = partition in checked,
                            onCheckedChange = null,
                        )
                        Text(
                            text = if (partition in notInProfile) {
                                stringResource(
                                    R.string.profile_partition_edit_included_not_in_profile,
                                    partition,
                                )
                            } else {
                                partition
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(onClick = { onConfirm(checked) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            DialogDismissButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * Line-based string list editor (chain partitions, included image files,
 * kernel cmdlines). One entry per line in the text area; [validateEntry]
 * gates the confirm button per non-blank line. Kept text-area based instead
 * of per-row dialogs because profile entries are plain strings — no file
 * pickers or secrets involved (chain keys are key-store file names).
 */
@Composable
private fun StringListEditDialog(
    titleRes: Int,
    entries: List<String>,
    formatHint: String,
    validateEntry: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var text by remember { mutableStateOf(entries.joinToString("\n")) }
    val parsed = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    val allValid = parsed.all { validateEntry(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    isError = !allValid,
                    supportingText = {
                        Column {
                            Text(formatHint)
                            if (!allValid) {
                                Text(
                                    text = stringResource(R.string.profile_partition_edit_list_invalid),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    enabled = true,
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = { onConfirm(parsed) },
                enabled = allValid,
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            DialogDismissButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun SimpleChoiceDialog(
    titleRes: Int,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(text = option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            DialogDismissButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

/** Maps a codec validation code to its user-facing message. */
private fun validationMessageRes(code: ProfilePartitionCodec.ValidationCode): Int = when (code) {
    ProfilePartitionCodec.ValidationCode.MISSING_PARTITION_SIZE ->
        R.string.profile_partition_err_missing_size
    ProfilePartitionCodec.ValidationCode.PARTITION_SIZE_NOT_MULTIPLE ->
        R.string.profile_partition_err_size_multiple
    ProfilePartitionCodec.ValidationCode.MALFORMED_CHAIN_PARTITION ->
        R.string.profile_partition_err_chain_malformed
    ProfilePartitionCodec.ValidationCode.CHAIN_SLOT_CONFLICT ->
        R.string.profile_partition_err_chain_conflict
    ProfilePartitionCodec.ValidationCode.KEY_REQUIRED ->
        R.string.profile_partition_err_key_required
    ProfilePartitionCodec.ValidationCode.INVALID_SALT ->
        R.string.profile_partition_err_salt
    ProfilePartitionCodec.ValidationCode.MALFORMED_PROP ->
        R.string.profile_partition_err_prop
    ProfilePartitionCodec.ValidationCode.FEC_NUM_ROOTS_OUT_OF_RANGE ->
        R.string.profile_partition_err_fec_roots
    ProfilePartitionCodec.ValidationCode.NEGATIVE_ROLLBACK_INDEX ->
        R.string.profile_partition_err_rollback_negative
    ProfilePartitionCodec.ValidationCode.INVALID_BLOCK_SIZE ->
        R.string.profile_partition_err_block_size
}
