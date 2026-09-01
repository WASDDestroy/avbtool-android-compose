package me.wasddestroy.avbtoolandroid

import androidx.annotation.StringRes
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.math.BigInteger
import me.wasddestroy.avbtoolandroid.ui.components.DialogConfirmButton
import me.wasddestroy.avbtoolandroid.ui.components.DialogDismissButton
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceGroup
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceRow
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceSwitchRow
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceValueRow
import me.wasddestroy.avbtoolandroid.ui.components.SettingsList
import me.wasddestroy.avbtoolandroid.ui.components.preferenceGroup
import androidx.core.net.toUri

private const val IMAGE_STORAGE_KEY = "__image__"

private val ALGORITHMS = listOf(
    "NONE",
    "SHA256_RSA2048",
    "SHA256_RSA4096",
    "SHA256_RSA8192",
    "SHA512_RSA2048",
    "SHA512_RSA4096",
    "SHA512_RSA8192",
)

private val HASH_ALGORITHMS = listOf("sha256", "sha512", "sha1", "blake2b-256")

private data class FlagOption(val value: String, @param:StringRes val labelRes: Int)
private val FLAGS_OPTIONS = listOf(
    FlagOption("0", R.string.flags_0),
    FlagOption("1", R.string.flags_1),
    FlagOption("2", R.string.flags_2),
    FlagOption("3", R.string.flags_3),
)

private fun storageKey(arg: AvbArg): String = if (arg.type == ArgType.IMAGE) IMAGE_STORAGE_KEY else arg.key

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandScreen(
    command: AvbCommand,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: CommandViewModel = viewModel(
        key = command.id,
        factory = CommandViewModel.factory(context),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var values by remember(command.id) {
        mutableStateOf(
            command.inputs.associate { it.key to "" } +
            command.args.associate { storageKey(it) to (it.defaultValue ?: "") } +
            command.args.filter { it.type == ArgType.SIZE }.associate { "${it.key}__unit" to "MiB" }
        )
    }
    var copyWarning by remember { mutableStateOf(false) }
    var pendingArgKey by remember { mutableStateOf<String?>(null) }
    var pendingArgIndex by remember { mutableStateOf<Int?>(null) }
    var editingArg by remember { mutableStateOf<AvbArg?>(null) }
    var choosingAlgorithm by remember { mutableStateOf(false) }
    var choosingHashAlgorithm by remember { mutableStateOf(false) }
    var choosingFlags by remember { mutableStateOf(false) }
    var managingFileArg by remember { mutableStateOf<AvbArg?>(null) }
    var managingChainArg by remember { mutableStateOf<AvbArg?>(null) }
    var chainEditor by remember { mutableStateOf<Pair<AvbArg, Int?>?>(null) }
    var chainKeyPickRequest by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    var editingSlotData by remember { mutableStateOf(false) }
    var editingSizeArg by remember { mutableStateOf<AvbArg?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var previewExpanded by remember { mutableStateOf(false) }
    var pendingRollbackVerdict by remember { mutableStateOf<RollbackIndexVerdict?>(null) }
    var pendingRollbackMismatch by remember { mutableStateOf<Pair<BigInteger, BigInteger>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        chainKeyPickRequest?.let { callback ->
            chainKeyPickRequest = null
            if (uri != null) callback(uri)
            return@rememberLauncherForActivityResult
        }
        val key = pendingArgKey ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        val index = pendingArgIndex
        val old = values[key] ?: ""
        val lines = if (old.isBlank()) mutableListOf() else old.lines().toMutableList()
        if (index != null && index >= 0 && index < lines.size) {
            lines[index] = uri.toString()
        } else {
            lines.add(uri.toString())
        }
        values = values + (key to lines.joinToString("\n"))
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val src = uiState.outputFile ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
            }
        }
        viewModel.dismissOutputFile()
    }

    fun proceedAfterRollbackChecks() {
        val firstInput = command.inputs.firstOrNull()
        val inputUri = if (firstInput != null) values[firstInput.key].orEmpty() else null
        if (command.readOnly) {
            viewModel.run(command, values, inputUri?.toUri())
        } else {
            copyWarning = true
        }
    }

    // Re-signing rewrites the whole vbmeta blob (avbtool truncates an existing
    // footer away first), so when the picked image carries one, its existing
    // rollback index is compared against the requested value and a mismatch
    // must be confirmed explicitly.
    fun checkExistingFooterThenProceed(requested: BigInteger) {
        val firstInput = command.inputs.firstOrNull()
        val raw = firstInput?.let { values[it.key].orEmpty() }.orEmpty()
        if (raw.isBlank()) {
            proceedAfterRollbackChecks()
            return
        }
        coroutineScope.launch {
            val existing = viewModel.readExistingRollbackIndex(raw.toUri())
            if (existing != null && existing != requested) {
                pendingRollbackMismatch = existing to requested
            } else {
                proceedAfterRollbackChecks()
            }
        }
    }

    fun onRun() {
        val firstInput = command.inputs.firstOrNull()
        val inputUri = if (firstInput != null) values[firstInput.key].orEmpty() else null
        if (firstInput != null && firstInput.required && inputUri.isNullOrBlank()) {
            viewModel.failWithMissingImage()
            return
        }
        // The rollback index is the only AVB value written to RPMB, so its
        // value is classified before any signing prompt: values matching
        // neither known scheme, or dated beyond the local clock, must be
        // confirmed deliberately.
        val rollbackArg = command.args.firstOrNull { it.key == "--rollback_index" } ?: run {
            proceedAfterRollbackChecks()
            return
        }
        val rawRollback = values[rollbackArg.key].orEmpty().trim()
        val requested = RollbackIndexGuard.parse(rawRollback) ?: BigInteger.ZERO
        if (rawRollback.isNotEmpty()) {
            when (val verdict = RollbackIndexGuard.classifyText(rawRollback, System.currentTimeMillis() / 1000)) {
                is RollbackIndexVerdict.Ok -> Unit
                else -> {
                    pendingRollbackVerdict = verdict
                    return
                }
            }
        }
        checkExistingFooterThenProceed(requested)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(command.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.command_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = ::onRun,
                    enabled = !uiState.running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                ) {
                    Text(stringResource(if (uiState.running) R.string.command_running else R.string.command_run))
                }
            }
        },
    ) { contentPadding ->
        @Suppress("DEPRECATION")
        val clipboard = LocalClipboardManager.current
        val previewText = remember(values) { formatArgv(buildArgv(command, values)) }
        SettingsList(
            modifier = Modifier.padding(contentPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (command.id == "make_certificate") {
                item {
                    Text(
                        text = stringResource(R.string.make_certificate_usage_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            val imageArgs = command.args.filter {
                !it.advanced && it.type != ArgType.ALGORITHM && it.type != ArgType.HASH_ALGORITHM && it.key != "--key" && it.type != ArgType.BOOL
            }
            val keyArgs = command.args.filter {
                !it.advanced && (it.type == ArgType.ALGORITHM || it.type == ArgType.HASH_ALGORITHM || it.key == "--key")
            }
            val switchArgs = command.args.filter { !it.advanced && it.type == ArgType.BOOL }
            val advancedArgs = command.args.filter { it.advanced }
            val imageSectionTitle = if (command.kind == AvbCommandKind.IMAGE_TOOL) {
                R.string.command_section_image_configs
            } else {
                R.string.command_section_options
            }

            if (command.inputs.isNotEmpty() || imageArgs.isNotEmpty()) {
                preferenceGroup(key = "main_options", titleRes = imageSectionTitle) {
                    command.inputs.forEach { input ->
                        row(input.key) {
                            FileInputRow(
                                input = input,
                                value = values[input.key].orEmpty(),
                                onPickFile = { key, index ->
                                    pendingArgKey = key
                                    pendingArgIndex = index
                                    openDocument.launch(arrayOf("*/*"))
                                },
                            )
                        }
                    }
                    imageArgs.forEach { arg ->
                        row(arg.key) {
                            CommandArgRow(
                                arg = arg,
                                value = values[storageKey(arg)].orEmpty(),
                                values = values,
                                onPickFile = { key, index ->
                                    pendingArgKey = key
                                    pendingArgIndex = index
                                    openDocument.launch(arrayOf("*/*"))
                                },
                                onManageFile = { managingFileArg = arg },
                                onManageChain = { managingChainArg = arg },
                                onEditText = {
                                    when {
                                        arg.key == "--slot_data" -> editingSlotData = true
                                        arg.type == ArgType.SIZE -> editingSizeArg = arg
                                        else -> editingArg = arg
                                    }
                                },
                                onChooseAlgorithm = { choosingAlgorithm = true },
                                onChooseHashAlgorithm = { choosingHashAlgorithm = true },
                                onChooseFlags = { choosingFlags = true },
                                onToggleBoolean = { checked ->
                                    values = values + (arg.key to checked.toString())
                                },
                            )
                        }
                    }
                }
            }
            if (keyArgs.isNotEmpty()) {
                preferenceGroup(key = "key_configs", titleRes = R.string.command_section_key_configs) {
                    keyArgs.forEach { arg ->
                        row(arg.key) {
                            CommandArgRow(
                                arg = arg,
                                value = values[storageKey(arg)].orEmpty(),
                                values = values,
                                onPickFile = { key, index ->
                                    pendingArgKey = key
                                    pendingArgIndex = index
                                    openDocument.launch(arrayOf("*/*"))
                                },
                                onManageFile = { managingFileArg = arg },
                                onManageChain = { managingChainArg = arg },
                                onEditText = { editingArg = arg },
                                onChooseAlgorithm = { choosingAlgorithm = true },
                                onChooseHashAlgorithm = { choosingHashAlgorithm = true },
                                onChooseFlags = { choosingFlags = true },
                                onToggleBoolean = { checked ->
                                    values = values + (arg.key to checked.toString())
                                },
                            )
                        }
                    }
                }
            }
            if (switchArgs.isNotEmpty()) {
                preferenceGroup(key = "switch_options", titleRes = R.string.command_section_options) {
                    switchArgs.forEach { arg ->
                        row(arg.key) {
                            PreferenceSwitchRow(
                                checked = (values[arg.key] ?: "").toBooleanStrictOrNull() == true,
                                title = stringResource(arg.labelRes),
                                iconContent = { ArgIcon(arg) },
                                onCheckedChange = { checked ->
                                    values = values + (arg.key to checked.toString())
                                },
                            )
                        }
                    }
                }
            }
    
            if (advancedArgs.isNotEmpty()) {
                preferenceGroup(key = "advanced_options", titleRes = R.string.command_section_advanced) {
                    row("toggle_advanced") {
                        PreferenceRow(
                            title = stringResource(
                                if (advancedExpanded) R.string.command_advanced_hide
                                else R.string.command_advanced_show
                            ),
                            iconContent = { RowIcon(Icons.Filled.Tune) },
                            onClick = { advancedExpanded = !advancedExpanded },
                        )
                    }
                    if (advancedExpanded) {
                        advancedArgs.forEach { arg ->
                            row(arg.key) {
                                if (arg.type == ArgType.BOOL) {
                                    PreferenceSwitchRow(
                                        checked = (values[arg.key] ?: "").toBooleanStrictOrNull() == true,
                                        title = stringResource(arg.labelRes),
                                        iconContent = { ArgIcon(arg) },
                                        onCheckedChange = { checked ->
                                            values = values + (arg.key to checked.toString())
                                        },
                                    )
                                } else {
                                    CommandArgRow(
                                        arg = arg,
                                        value = values[storageKey(arg)].orEmpty(),
                                        values = values,
                                        onPickFile = { key, index ->
                                            pendingArgKey = key
                                            pendingArgIndex = index
                                            openDocument.launch(arrayOf("*/*"))
                                        },
                                        onManageFile = { managingFileArg = arg },
                                        onManageChain = { managingChainArg = arg },
                                        onEditText = { editingArg = arg },
                                        onChooseAlgorithm = { choosingAlgorithm = true },
                                        onChooseHashAlgorithm = { choosingHashAlgorithm = true },
                                onChooseFlags = { choosingFlags = true },
                                        onToggleBoolean = { checked ->
                                            values = values + (arg.key to checked.toString())
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            preferenceGroup(key = "command_preview") {
                row("preview_toggle") {
                    PreferenceRow(
                        title = stringResource(R.string.command_preview),
                        summary = stringResource(
                            if (previewExpanded) R.string.command_preview_collapse
                            else R.string.command_preview_expand
                        ),
                        trailing = {
                            IconButton(onClick = { clipboard.setText(AnnotatedString(previewText)) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                            }
                        },
                        onClick = { previewExpanded = !previewExpanded },
                    )
                }
                if (previewExpanded) {
                    row("preview_note") {
                        PreferenceRow(
                            title = "",
                            summary = stringResource(R.string.command_preview_note),
                        )
                    }
                    row("preview_content") {
                        PreferenceRow(
                            title = "",
                            summaryContent = {
                                SelectionContainer {
                                    Text(
                                        text = previewText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            },
                        )
                    }
                }
            }
            val currentResult = uiState.result
            if (!uiState.running && currentResult != null) {
                item("result") {
                    ResultView(result = currentResult)
                }
            }
        }
    }

    if (copyWarning) {
        AlertDialog(
            onDismissRequest = { copyWarning = false },
            title = { Text(stringResource(R.string.command_modify_title)) },
            text = { Text(stringResource(R.string.command_modify_message)) },
            confirmButton = {
                DialogConfirmButton(onClick = {
                    copyWarning = false
                    viewModel.run(
                        command,
                        values,
                        command.inputs.firstOrNull()?.let { values[it.key].orEmpty().toUri() },
                    )
                }) {
                    Text(stringResource(R.string.command_continue))
                }
            },
            dismissButton = {
                DialogDismissButton(onClick = { copyWarning = false }) {
                    Text(stringResource(R.string.command_cancel))
                }
            },
        )
    }

    pendingRollbackVerdict?.let { verdict ->
        val label = command.args.firstOrNull { it.key == "--rollback_index" }
            ?.let { stringResource(it.labelRes) } ?: "--rollback_index"
        RollbackIndexWarningDialog(
            findings = listOf(RollbackIndexFinding(label, verdict)),
            onDismiss = { pendingRollbackVerdict = null },
            // Invalid values cannot be written at all, so there is nothing to
            // confirm; anomalies continue into the footer comparison and the
            // normal run flow.
            onContinue = if (verdict is RollbackIndexVerdict.Invalid) {
                null
            } else {
                {
                    pendingRollbackVerdict = null
                    val raw = values["--rollback_index"].orEmpty().trim()
                    checkExistingFooterThenProceed(RollbackIndexGuard.parse(raw) ?: BigInteger.ZERO)
                }
            },
        )
    }

    pendingRollbackMismatch?.let { (existing, requested) ->
        AlertDialog(
            onDismissRequest = { pendingRollbackMismatch = null },
            title = { Text(stringResource(R.string.rollback_mismatch_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.rollback_mismatch_message,
                        existing.toString(),
                        requested.toString(),
                    ),
                )
            },
            confirmButton = {
                DialogConfirmButton(onClick = {
                    pendingRollbackMismatch = null
                    proceedAfterRollbackChecks()
                }) {
                    Text(stringResource(R.string.command_continue))
                }
            },
            dismissButton = {
                DialogDismissButton(onClick = { pendingRollbackMismatch = null }) {
                    Text(stringResource(R.string.command_cancel))
                }
            },
        )
    }

    val outputFile = uiState.outputFile
    if (outputFile != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissOutputFile() },
            title = { Text(stringResource(R.string.command_output_file_title)) },
            text = {
                Text(stringResource(R.string.command_output_file_message, outputFile.absolutePath))
            },
            confirmButton = {
                DialogConfirmButton(onClick = { createDocument.launch("output.img") }) {
                    Text(stringResource(R.string.command_save))
                }
            },
            dismissButton = {
                DialogDismissButton(onClick = { viewModel.dismissOutputFile() }) {
                    Text(stringResource(R.string.command_dismiss))
                }
            },
        )
    }

    editingArg?.let { arg ->
        CommandTextEditDialog(
            arg = arg,
            initialValue = values[storageKey(arg)].orEmpty(),
            onDismiss = { editingArg = null },
            onConfirm = { newValue ->
                values = values + (storageKey(arg) to newValue)
                editingArg = null
            },
        )
    }

    if (editingSlotData) {
        val current = values["--slot_data"].orEmpty()
        SlotDataEditDialog(
            initialValue = current.ifBlank { "15:7:0:14:7:0" },
            onDismiss = { editingSlotData = false },
            onConfirm = { newValue ->
                values = values + ("--slot_data" to newValue)
                editingSlotData = false
            },
        )
    }

    editingSizeArg?.let { arg ->
        val key = storageKey(arg)
        SizeEditDialog(
            label = stringResource(arg.labelRes),
            initialValue = values[key].orEmpty(),
            initialUnit = values["${arg.key}__unit"] ?: "MiB",
            onDismiss = { editingSizeArg = null },
            onConfirm = { number, unit ->
                values = values + (key to number) + ("${arg.key}__unit" to unit)
                editingSizeArg = null
            },
        )
    }

    managingFileArg?.let { arg ->
        CommandFileListDialog(
            arg = arg,
            value = values[storageKey(arg)].orEmpty(),
            onDismiss = { managingFileArg = null },
            onPickFile = { index ->
                pendingArgKey = storageKey(arg)
                pendingArgIndex = index
                openDocument.launch(arrayOf("*/*"))
            },
            onRemove = { index ->
                val key = storageKey(arg)
                val old = values[key].orEmpty()
                val lines = old.lines().filter { it.isNotBlank() }.toMutableList()
                if (index in lines.indices) lines.removeAt(index)
                values = values + (key to lines.joinToString("\n"))
            },
        )
    }

    managingChainArg?.let { arg ->
        ChainPartitionListDialog(
            arg = arg,
            value = values[storageKey(arg)].orEmpty(),
            onDismiss = { managingChainArg = null },
            onAdd = {
                managingChainArg = null
                chainEditor = arg to null
            },
            onEdit = { index ->
                managingChainArg = null
                chainEditor = arg to index
            },
            onRemove = { index ->
                val key = storageKey(arg)
                val old = values[key].orEmpty()
                val lines = old.lines().filter { it.isNotBlank() }.toMutableList()
                if (index in lines.indices) lines.removeAt(index)
                values = values + (key to lines.joinToString("\n"))
            },
        )
    }

    chainEditor?.let { (arg, editIndex) ->
        val key = storageKey(arg)
        val entries = (values[key].orEmpty()).lines().filter { it.isNotBlank() }
        val initial = if (editIndex != null && editIndex in entries.indices) entries[editIndex] else ""
        ChainPartitionEditDialog(
            initial = initial,
            onDismiss = { chainEditor = null },
            onPickKey = { callback ->
                chainKeyPickRequest = callback
                openDocument.launch(arrayOf("*/*"))
            },
            onConfirm = { entry ->
                val newEntries = entries.toMutableList()
                if (editIndex != null && editIndex in newEntries.indices) {
                    newEntries[editIndex] = entry
                } else {
                    newEntries.add(entry)
                }
                values = values + (key to newEntries.joinToString("\n"))
                chainEditor = null
            },
        )
    }

    if (choosingAlgorithm) {
        val algorithmArg = command.args.firstOrNull { it.type == ArgType.ALGORITHM }
        if (algorithmArg != null) {
            val current = values[algorithmArg.key].orEmpty().ifBlank { "NONE" }
            AlgorithmChoiceDialog(
                selected = current,
                onDismiss = { choosingAlgorithm = false },
                onSelect = { algorithm ->
                    values = values + (algorithmArg.key to algorithm)
                    choosingAlgorithm = false
                },
            )
        }
    }

    if (choosingHashAlgorithm) {
        val hashAlgArg = command.args.firstOrNull { it.type == ArgType.HASH_ALGORITHM }
        if (hashAlgArg != null) {
            val current = values[hashAlgArg.key].orEmpty().ifBlank { hashAlgArg.defaultValue ?: "" }
            HashAlgorithmChoiceDialog(
                selected = current,
                onDismiss = { choosingHashAlgorithm = false },
                onSelect = { alg ->
                    values = values + (hashAlgArg.key to alg)
                    choosingHashAlgorithm = false
                },
            )
        }
    }

    if (choosingFlags) {
        val flagsArg = command.args.firstOrNull { it.type == ArgType.FLAGS }
        if (flagsArg != null) {
            val current = values[flagsArg.key].orEmpty().ifBlank { "0" }
            FlagsChoiceDialog(
                selected = current,
                onDismiss = { choosingFlags = false },
                onSelect = { v ->
                    values = values + (flagsArg.key to v)
                    choosingFlags = false
                },
            )
        }
    }
}

@Composable
private fun ResultView(result: AvbCommandResult) {
    var rawExpanded by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        val statusTextRes = when (result.status) {
            AvbResultStatus.SUCCESS -> R.string.command_result_success
            AvbResultStatus.FAILED -> R.string.command_result_failed
            AvbResultStatus.CANCELLED -> R.string.command_result_cancelled
            AvbResultStatus.RUNNING -> R.string.command_result_running
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when (result.status) {
                AvbResultStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                AvbResultStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(statusTextRes),
                style = MaterialTheme.typography.titleMedium,
                color = when (result.status) {
                    AvbResultStatus.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
                    AvbResultStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (result.errors.isNotEmpty()) {
            PreferenceGroup {
                result.errors.forEachIndexed { index, error ->
                    row("error_$index") {
                        PreferenceRow(
                            title = stringResource(R.string.command_result_error),
                            summaryContent = {
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = FontFamily.Monospace,
                                )
                            },
                        )
                    }
                }
            }
        }

        result.warnings.forEach { warning ->
            Text(
                text = "Warning: $warning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        result.sections.forEach { section ->
            val sectionTitle = section.localizedNameRes?.let { stringResource(it) } ?: section.title
            PreferenceGroup(title = sectionTitle) {
                section.rows.forEach { rowData ->
                    row(rowData.title) {
                        val displayTitle = rowData.localizedNameRes?.let { stringResource(it) } ?: rowData.title
                        val finalTitle = if (rowData.title.startsWith("Prop:")) {
                            displayTitle + rowData.title.removePrefix("Prop:")
                        } else {
                            displayTitle
                        }
                        PreferenceValueRow(
                            title = finalTitle,
                            value = rowData.value,
                            monospace = rowData.monospace,
                            onLongClick = {
                                clipboard.setText(AnnotatedString("$finalTitle: ${rowData.value}"))
                                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
                section.groups.forEach { group ->
                    group.rows.forEach { rowData ->
                        row(group.title + ":" + rowData.title) {
                            val groupDisplayTitle = group.localizedNameRes?.let { res ->
                                if (group.nameFormatArg != null) stringResource(res, group.nameFormatArg)
                                else stringResource(res)
                            } ?: group.title
                            val displayValue = if (rowData.localizedLines != null) {
                                buildString {
                                    rowData.localizedLines.forEachIndexed { i, line ->
                                        if (i > 0) append('\n')
                                        val locKey = line.keyRes?.let { stringResource(it) } ?: line.rawKey
                                        append(locKey); append(": "); append(line.value)
                                    }
                                }
                            } else rowData.value
                            PreferenceValueRow(
                                title = groupDisplayTitle,
                                value = displayValue,
                                monospace = rowData.monospace,
                                onLongClick = {
                                    clipboard.setText(AnnotatedString("$groupDisplayTitle: $displayValue"))
                                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
            }
        }

        if (result.rawOutput.isNotBlank()) {
            PreferenceGroup {
                row("raw_output_toggle") {
                    PreferenceRow(
                        title = stringResource(R.string.command_raw_output),
                        summary = stringResource(
                            if (rawExpanded) R.string.command_raw_output_collapse
                            else R.string.command_raw_output_expand
                        ),
                        trailing = {
                            IconButton(onClick = { clipboard.setText(AnnotatedString(result.rawOutput)) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                            }
                        },
                        onClick = { rawExpanded = !rawExpanded },
                    )
                }
                if (rawExpanded) {
                    row("raw_output_content") {
                        PreferenceRow(
                            title = "",
                            summaryContent = {
                                SelectionContainer {
                                    Text(
                                        text = result.rawOutput,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInputRow(
    input: AvbFileInput,
    value: String,
    onPickFile: (String, Int?) -> Unit,
) {
    val lines = value.lines().filter { it.isNotBlank() }
    val summary = if (input.repeatable) {
        if (lines.isEmpty()) stringResource(R.string.command_choose_file)
        else pluralStringResource(R.plurals.command_files_selected, lines.size, lines.size)
    } else {
        val fileName = lines.firstOrNull()?.let { runCatching { it.toUri().lastPathSegment }.getOrNull() }
        when {
            fileName.isNullOrBlank() -> stringResource(R.string.command_choose_file)
            else -> fileName
        }
    }
    PreferenceRow(
        title = stringResource(input.labelRes) + if (input.required) stringResource(R.string.command_required) else "",
        iconContent = { RowIcon(Icons.Filled.Image) },
        summary = summary,
        onClick = {
            if (input.repeatable) {
                onPickFile(input.key, null)
            } else {
                onPickFile(input.key, 0)
            }
        },
    )
}

@Composable
private fun CommandArgRow(
    arg: AvbArg,
    value: String,
    values: Map<String, String>,
    onPickFile: (String, Int?) -> Unit,
    onManageFile: () -> Unit,
    onManageChain: () -> Unit,
    onEditText: () -> Unit,
    onChooseAlgorithm: () -> Unit,
    onChooseHashAlgorithm: () -> Unit,
    onChooseFlags: () -> Unit,
    onToggleBoolean: (Boolean) -> Unit,
) {
    when (arg.type) {
        ArgType.IMAGE, ArgType.FILE -> {
            val key = storageKey(arg)
            val lines = value.lines().filter { it.isNotBlank() }
            val summary = if (arg.repeatable) {
                if (lines.isEmpty()) stringResource(R.string.command_choose_file)
                else pluralStringResource(R.plurals.command_files_selected, lines.size, lines.size)
            } else {
                val fileName = lines.firstOrNull()?.let { runCatching { it.toUri().lastPathSegment }.getOrNull() }
                when {
                    fileName.isNullOrBlank() -> stringResource(R.string.command_choose_file)
                    else -> fileName
                }
            }
            PreferenceRow(
                title = stringResource(arg.labelRes) + if (arg.required) stringResource(R.string.command_required) else "",
                iconContent = { ArgIcon(arg) },
                summary = summary,
                onClick = {
                    if (arg.repeatable) {
                        onManageFile()
                    } else {
                        onPickFile(key, 0)
                    }
                },
            )
        }
        ArgType.TEXT, ArgType.INT -> {
            PreferenceRow(
                title = stringResource(arg.labelRes) + if (arg.required) stringResource(R.string.command_required) else "",
                iconContent = { ArgIcon(arg) },
                summary = value.ifBlank { null },
                onClick = onEditText,
            )
        }
        ArgType.SIZE -> {
            val unit = values["${arg.key}__unit"] ?: "MiB"
            val display = if (value.isNotBlank()) "$value $unit" else null
            PreferenceRow(
                title = stringResource(arg.labelRes) + if (arg.required) stringResource(R.string.command_required) else "",
                iconContent = { ArgIcon(arg) },
                summary = display,
                onClick = onEditText,
            )
        }
        ArgType.ALGORITHM -> {
            PreferenceRow(
                title = stringResource(arg.labelRes) + if (arg.required) stringResource(R.string.command_required) else "",
                iconContent = { ArgIcon(arg) },
                summary = value.ifBlank { "NONE" },
                onClick = onChooseAlgorithm,
            )
        }
        ArgType.HASH_ALGORITHM -> {
            PreferenceRow(
                title = stringResource(arg.labelRes) + if (arg.required) stringResource(R.string.command_required) else "",
                iconContent = { ArgIcon(arg) },
                summary = value.ifBlank { arg.defaultValue ?: "" },
                onClick = onChooseHashAlgorithm,
            )
        }
        ArgType.FLAGS -> {
            val display = FLAGS_OPTIONS.firstOrNull { it.value == value }?.let { stringResource(it.labelRes) }
                ?: value.ifBlank { "0" }
            PreferenceRow(
                title = stringResource(arg.labelRes) + if (arg.required) stringResource(R.string.command_required) else "",
                iconContent = { ArgIcon(arg) },
                summary = display,
                onClick = onChooseFlags,
            )
        }
        ArgType.CHAIN_PARTITION -> {
            val entries = value.lines().filter { it.isNotBlank() }
            PreferenceRow(
                title = stringResource(arg.labelRes) + if (arg.required) stringResource(R.string.command_required) else "",
                iconContent = { RowIcon(Icons.Filled.Link) },
                summary = if (entries.isEmpty()) stringResource(R.string.command_chain_partition_summary_empty)
                          else pluralStringResource(R.plurals.command_chain_partitions_count, entries.size, entries.size),
                onClick = onManageChain,
            )
        }
        ArgType.BOOL -> {
            PreferenceSwitchRow(
                checked = (values[arg.key] ?: "").toBooleanStrictOrNull() == true,
                title = stringResource(arg.labelRes),
                iconContent = { ArgIcon(arg) },
                onCheckedChange = onToggleBoolean,
            )
        }
    }
}

private val DISABLED_ARG_KEYS = setOf(
    "--hashtree_disabled",
    "--no_hashtree",
    "--set_hashtree_disabled_flag",
    "--set_verification_disabled_flag",
)

@Composable
private fun ArgIcon(arg: AvbArg) {
    val icon = argIcon(arg)
    if (arg.key in DISABLED_ARG_KEYS) {
        IconWithSlash(icon)
    } else {
        RowIcon(icon)
    }
}

@Composable
private fun IconWithSlash(icon: ImageVector) {
    val color = LocalContentColor.current
    androidx.compose.foundation.layout.Box(Modifier.size(24.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            tint = color,
        )
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                color = color,
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
    )
}

private fun argIcon(arg: AvbArg): ImageVector = when {
    arg.key == "--key" -> Icons.Filled.Key
    arg.key == "--algorithm" -> Icons.Filled.Lock
    arg.key == "--cert" -> Icons.Filled.Verified
    arg.key == "--json" -> Icons.Filled.DataObject
    arg.key == "--dynamic_partition_size" -> Icons.Filled.Calculate
    arg.key == "--calc_max_image_size" -> Icons.Filled.Calculate
    arg.key == "--do_not_append_vbmeta_image" -> Icons.Filled.LayersClear
    arg.key == "--output_vbmeta_image" -> Icons.Filled.Folder
    arg.key == "--print_required_libavb_version" -> Icons.Filled.Print
    arg.key == "--setup_as_rootfs_from_kernel" -> Icons.Filled.Terminal
    arg.key == "--hashtree_disabled" || arg.key == "--no_hashtree" ||
            arg.key == "--set_hashtree_disabled_flag" -> Icons.Filled.AccountTree
    arg.key == "--set_verification_disabled_flag" -> Icons.Filled.Verified
    arg.type == ArgType.IMAGE -> Icons.Filled.Image
    arg.type == ArgType.FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
    arg.key == "--partition_name" -> Icons.AutoMirrored.Filled.Label
    arg.type == ArgType.INT -> Icons.Filled.Numbers
    arg.type == ArgType.BOOL -> Icons.Filled.CheckBox
    else -> Icons.Filled.TextFields
}

@Composable
private fun CommandTextEditDialog(
    arg: AvbArg,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember(arg.key) { mutableStateOf(initialValue) }
    val title = stringResource(arg.labelRes) + if (arg.required) stringResource(R.string.command_required) else ""
    val keyboardType = if (arg.type == ArgType.INT) KeyboardType.Number else KeyboardType.Text
    val hint = arg.hintRes?.let { stringResource(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(hint ?: title) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = !arg.repeatable,
                minLines = if (arg.repeatable) 3 else 1,
                shape = OutlinedTextFieldDefaults.shape,
                colors = OutlinedTextFieldDefaults.colors(),
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

@Composable
private fun CommandFileListDialog(
    arg: AvbArg,
    value: String,
    onDismiss: () -> Unit,
    onPickFile: (Int?) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val lines = value.lines().filter { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(arg.labelRes)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                lines.forEachIndexed { index, item ->
                    val fileName = runCatching { item.toUri().lastPathSegment }.getOrNull()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = fileName ?: item,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { onRemove(index) }) {
                            Text(stringResource(R.string.command_remove_file))
                        }
                    }
                }
                TextButton(onClick = { onPickFile(null) }) {
                    Text(stringResource(R.string.command_add_file))
                }
            }
        },
        confirmButton = {
            DialogDismissButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun AlgorithmChoiceDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.command_section_key_configs)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                ALGORITHMS.forEach { algorithm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(algorithm) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = algorithm == selected,
                            onClick = null,
                        )
                        Text(text = algorithm, style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun HashAlgorithmChoiceDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arg_add_hash_footer_hash_algorithm_label)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                HASH_ALGORITHMS.forEach { alg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(alg) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = alg == selected,
                            onClick = null,
                        )
                        Text(text = alg, style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun FlagsChoiceDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.flags_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                FLAGS_OPTIONS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.value) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = option.value == selected,
                            onClick = null,
                        )
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                        )
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

@Composable
private fun ChainPartitionListDialog(
    arg: AvbArg,
    value: String,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val entries = value.lines().filter { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.command_chain_partition_edit_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entries.forEachIndexed { index, entry ->
                    val keyPart = entry.substringAfterLast(':')
                    val keyName = runCatching { keyPart.toUri().lastPathSegment }.getOrNull()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = entry.replace(keyPart, keyName ?: keyPart),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { onEdit(index) }) {
                            Text(stringResource(R.string.command_edit))
                        }
                        TextButton(onClick = { onRemove(index) }) {
                            Text(stringResource(R.string.command_remove_file))
                        }
                    }
                }
                TextButton(onClick = onAdd) {
                    Text(stringResource(R.string.command_add_chain_partition))
                }
            }
        },
        confirmButton = {
            DialogDismissButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun ChainPartitionEditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onPickKey: ((Uri) -> Unit) -> Unit,
    onConfirm: (String) -> Unit,
) {
    val tokens = initial.split(":")
    var partition by remember { mutableStateOf(tokens.getOrElse(0) { "" }) }
    var rollbackIndex by remember { mutableStateOf(tokens.getOrElse(1) { "" }) }
    var keyUri by remember { mutableStateOf(tokens.drop(2).joinToString(":")) }
    val keyName = runCatching { keyUri.toUri().lastPathSegment }.getOrNull() ?: keyUri

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.command_chain_partition_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = partition,
                    onValueChange = { partition = it },
                    label = { Text(stringResource(R.string.command_chain_partition_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rollbackIndex,
                    onValueChange = { rollbackIndex = it },
                    label = { Text(stringResource(R.string.command_chain_partition_index)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.command_chain_partition_key) + ": " + keyName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onPickKey { uri -> keyUri = uri.toString() } }) {
                        Text(stringResource(R.string.command_choose_file))
                    }
                }
            }
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = {
                    val p = partition.trim()
                    val r = rollbackIndex.trim()
                    val k = keyUri.trim()
                    if (p.isNotBlank() && r.isNotBlank() && k.isNotBlank()) {
                        onConfirm("$p:$r:$k")
                    }
                },
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
private fun SlotDataEditDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val tokens = initialValue.split(":")
    var aPriority by remember { mutableStateOf(tokens.getOrElse(0) { "15" }) }
    var aTries by remember { mutableStateOf(tokens.getOrElse(1) { "7" }) }
    var aSuccess by remember { mutableStateOf(tokens.getOrElse(2) { "0" }) }
    var bPriority by remember { mutableStateOf(tokens.getOrElse(3) { "14" }) }
    var bTries by remember { mutableStateOf(tokens.getOrElse(4) { "7" }) }
    var bSuccess by remember { mutableStateOf(tokens.getOrElse(5) { "0" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.slot_data_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SlotSection(
                    label = stringResource(R.string.slot_data_slot_a),
                    priority = aPriority, onPriorityChange = { aPriority = it },
                    tries = aTries, onTriesChange = { aTries = it },
                    success = aSuccess, onSuccessChange = { aSuccess = it },
                )
                SlotSection(
                    label = stringResource(R.string.slot_data_slot_b),
                    priority = bPriority, onPriorityChange = { bPriority = it },
                    tries = bTries, onTriesChange = { bTries = it },
                    success = bSuccess, onSuccessChange = { bSuccess = it },
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = {
                    val result = "${aPriority.trim()}:${aTries.trim()}:${aSuccess.trim()}:" +
                            "${bPriority.trim()}:${bTries.trim()}:${bSuccess.trim()}"
                    onConfirm(result)
                },
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
private fun SlotSection(
    label: String,
    priority: String, onPriorityChange: (String) -> Unit,
    tries: String, onTriesChange: (String) -> Unit,
    success: String, onSuccessChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = priority,
            onValueChange = onPriorityChange,
            label = { Text(stringResource(R.string.slot_data_priority)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = tries,
            onValueChange = onTriesChange,
            label = { Text(stringResource(R.string.slot_data_tries_remaining)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = success,
            onValueChange = onSuccessChange,
            label = { Text(stringResource(R.string.slot_data_successful_boot)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val SIZE_UNITS = listOf("B", "KiB", "MiB", "GiB")
private val SIZE_UNIT_MULTIPLIERS = mapOf("B" to 1L, "KiB" to 1024L, "MiB" to 1024L * 1024, "GiB" to 1024L * 1024 * 1024)
private const val MAX_BYTES = Long.MAX_VALUE

@Composable
private fun SizeEditDialog(
    label: String,
    initialValue: String,
    initialUnit: String,
    onDismiss: () -> Unit,
    onConfirm: (number: String, unit: String) -> Unit,
) {
    var number by remember { mutableStateOf(initialValue) }
    var unit by remember { mutableStateOf(initialUnit) }
    var unitExpanded by remember { mutableStateOf(false) }
    var isDecimal by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var computedBytes by remember { mutableStateOf<String?>(null) }

    fun validate() {
        val trimmed = number.trim()
        isDecimal = trimmed.contains('.')
        val n = trimmed.toLongOrNull()
        val mult = SIZE_UNIT_MULTIPLIERS[unit] ?: 1L
        overflow = n != null && n > MAX_BYTES / mult
        computedBytes = when {
            trimmed.isBlank() -> null
            isDecimal -> null
            n == null -> null
            else -> (n * mult).toString()
        }
    }

    val hasError = isDecimal || overflow
    val supportingText: @Composable (() -> Unit)? = when {
        isDecimal -> {{ Text(stringResource(R.string.size_decimal_warning)) }}
        overflow -> {{ Text(stringResource(R.string.size_overflow_warning)) }}
        computedBytes != null -> {{ Text(stringResource(R.string.size_bytes_preview, computedBytes!!)) }}
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Box {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it; validate() },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = hasError,
                    supportingText = supportingText,
                    trailingIcon = {
                        Box {
                            Row(
                                modifier = Modifier
                                    .clickable { unitExpanded = true }
                                    .padding(start = 8.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = unit,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                )
                            }
                            DropdownMenu(
                                expanded = unitExpanded,
                                onDismissRequest = { unitExpanded = false },
                            ) {
                                SIZE_UNITS.forEach { u ->
                                    DropdownMenuItem(
                                        text = { Text(u) },
                                        onClick = {
                                            unit = u
                                            unitExpanded = false
                                            validate()
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        confirmButton = {
            DialogConfirmButton(
                onClick = { if (!hasError) onConfirm(number.trim(), unit) },
                enabled = number.isNotBlank() && !hasError,
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
