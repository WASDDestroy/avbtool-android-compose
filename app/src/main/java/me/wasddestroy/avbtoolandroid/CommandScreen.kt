package me.wasddestroy.avbtoolandroid

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.wasddestroy.avbtoolandroid.ui.components.DialogConfirmButton
import me.wasddestroy.avbtoolandroid.ui.components.DialogDismissButton
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceGroup
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceRow
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceSwitchRow
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceValueRow
import me.wasddestroy.avbtoolandroid.ui.components.SettingsList
import me.wasddestroy.avbtoolandroid.ui.components.preferenceGroup
import java.io.File
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

private fun storageKey(arg: AvbArg): String = if (arg.type == ArgType.IMAGE) IMAGE_STORAGE_KEY else arg.key

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandScreen(
    command: AvbCommand,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bridge = remember { SafFileBridge(context) }
    val runner = remember { AvbTaskRunner(context) }

    var values by remember(command.id) {
        mutableStateOf(
            command.inputs.associate { it.key to "" } +
            command.args.associate { storageKey(it) to (it.defaultValue ?: "") }
        )
    }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AvbCommandResult?>(null) }
    var copyWarning by remember { mutableStateOf(false) }
    var pendingCommand by remember { mutableStateOf<AvbCommand?>(null) }
    var pendingArgKey by remember { mutableStateOf<String?>(null) }
    var pendingArgIndex by remember { mutableStateOf<Int?>(null) }
    var pendingOutputFile by remember { mutableStateOf<File?>(null) }
    var editingArg by remember { mutableStateOf<AvbArg?>(null) }
    var choosingAlgorithm by remember { mutableStateOf(false) }
    var managingFileArg by remember { mutableStateOf<AvbArg?>(null) }
    var managingChainArg by remember { mutableStateOf<AvbArg?>(null) }
    var chainEditor by remember { mutableStateOf<Pair<AvbArg, Int?>?>(null) }
    var chainKeyPickRequest by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }

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
        val src = pendingOutputFile ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
            }
        }
        pendingOutputFile = null
    }

    val chooseImageError = stringResource(R.string.command_choose_image_error)

    fun onRun() {
        val imageInput = command.inputs.firstOrNull { it.key == "--image" }
        val imageUri = if (imageInput != null) values[imageInput.key].orEmpty() else null
        if (imageInput != null && imageUri.isNullOrBlank()) {
            result = AvbCommandResult(
                status = AvbResultStatus.FAILED,
                errors = listOf(chooseImageError),
            )
            return
        }
        pendingCommand = command
        if (command.readOnly) {
            runCommand(
                cmd = command,
                values = values,
                uri = imageUri?.toUri(),
                bridge = bridge,
                runner = runner,
                scope = scope,
                context = context,
                onStart = { running = true },
                onDone = { stdout, stderr, output ->
                    running = false
                    result = parseAvbResult(command.id, stdout, stderr)
                    pendingOutputFile = output
                },
            )
        } else {
            copyWarning = true
        }
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
                    enabled = !running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                ) {
                    Text(stringResource(if (running) R.string.command_running else R.string.command_run))
                }
            }
        },
    ) { contentPadding ->
        SettingsList(
            modifier = Modifier.padding(contentPadding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            val imageArgs = command.args.filter {
                !it.advanced && it.type != ArgType.ALGORITHM && it.key != "--key" && it.type != ArgType.BOOL
            }
            val keyArgs = command.args.filter {
                !it.advanced && (it.key == "--algorithm" || it.key == "--key")
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
                                onEditText = { editingArg = arg },
                                onChooseAlgorithm = { choosingAlgorithm = true },
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
                                        onToggleBoolean = { checked ->
                                            values = values + (arg.key to checked.toString())
                                        },
                                    )
                                }
                            }
                        }
                        row("collapse_advanced") {
                            PreferenceRow(
                                title = stringResource(R.string.command_advanced_hide),
                                iconContent = { RowIcon(Icons.Filled.Tune) },
                                onClick = { advancedExpanded = false },
                            )
                        }
                    } else {
                        row("expand_advanced") {
                            PreferenceRow(
                                title = stringResource(R.string.command_advanced_show),
                                iconContent = { RowIcon(Icons.Filled.Tune) },
                                onClick = { advancedExpanded = true },
                            )
                        }
                    }
                }
            }
            if (!running && result != null) {
                item("result") {
                    ResultView(result = result!!)
                }
            }
        }
    }

    if (copyWarning && pendingCommand != null) {
        val cmdToRun = pendingCommand!!
        AlertDialog(
            onDismissRequest = { copyWarning = false },
            title = { Text(stringResource(R.string.command_modify_title)) },
            text = { Text(stringResource(R.string.command_modify_message)) },
            confirmButton = {
                DialogConfirmButton(onClick = {
                    copyWarning = false
                    runCommand(
                        cmd = cmdToRun,
                        values = values,
                        uri = if (cmdToRun.hasImage) values[IMAGE_STORAGE_KEY].orEmpty().toUri() else null,
                        bridge = bridge,
                        runner = runner,
                        scope = scope,
                        context = context,
                        onStart = { running = true },
                        onDone = { stdout, stderr, output ->
                            running = false
                            result = parseAvbResult(cmdToRun.id, stdout, stderr)
                            pendingOutputFile = output
                        },
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

    if (pendingOutputFile != null) {
        AlertDialog(
            onDismissRequest = { pendingOutputFile = null },
            title = { Text(stringResource(R.string.command_output_file_title)) },
            text = {
                Text(stringResource(R.string.command_output_file_message, pendingOutputFile?.absolutePath ?: ""))
            },
            confirmButton = {
                DialogConfirmButton(onClick = { createDocument.launch("output.img") }) {
                    Text(stringResource(R.string.command_save))
                }
            },
            dismissButton = {
                DialogDismissButton(onClick = { pendingOutputFile = null }) {
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
}

@Composable
private fun ResultView(result: AvbCommandResult) {
    var rawExpanded by remember { mutableStateOf(false) }
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
            PreferenceGroup(title = section.title) {
                section.rows.forEach { rowData ->
                    row(rowData.title) {
                        PreferenceValueRow(
                            title = rowData.title,
                            value = rowData.value,
                            monospace = rowData.monospace,
                        )
                    }
                }
                section.groups.forEach { group ->
                    group.rows.forEach { rowData ->
                        row(group.title + ":" + rowData.title) {
                            PreferenceValueRow(
                                title = rowData.title,
                                value = rowData.value,
                                monospace = rowData.monospace,
                            )
                        }
                    }
                }
            }
        }

        if (result.rawOutput.isNotBlank()) {
            @Suppress("DEPRECATION")
            val clipboard = LocalClipboardManager.current
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
        ArgType.ALGORITHM -> {
            PreferenceRow(
                title = stringResource(arg.labelRes) + if (arg.required) stringResource(R.string.command_required) else "",
                iconContent = { ArgIcon(arg) },
                summary = value.ifBlank { "NONE" },
                onClick = onChooseAlgorithm,
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

private fun runCommand(
    cmd: AvbCommand,
    values: Map<String, String>,
    uri: Uri?,
    bridge: SafFileBridge,
    runner: AvbTaskRunner,
    scope: CoroutineScope,
    context: Context,
    onStart: () -> Unit,
    onDone: (String, String, File?) -> Unit,
) {
    scope.launch {
        onStart()
        val argv = mutableListOf("avbtool", cmd.id)
        val extraFds = mutableListOf<Int>()
        var closeInputFd = false
        var inputFd: Int? = null
        var imagePath: String? = null

        if (cmd.hasImage) {
            val uri = uri
            if (uri == null) {
                onDone("", context.getString(R.string.command_choose_image_error), null)
                return@launch
            }
            // Some avbtool commands derive sibling image paths from the
            // selected image path (chain partitions use os.path.join(image_dir,
            // partition_name + image_ext)). SAF fd pseudo-paths cannot support
            // those derived sibling paths, so those commands run against a
            // private copy of the selected file instead.
            val needsRealDirectory = cmd.id in setOf(
                "verify_image",
                "print_partition_digests",
                "calculate_vbmeta_digest",
                "calculate_kernel_cmdline"
            )
            inputFd = when {
                needsRealDirectory -> null
                cmd.readOnly -> bridge.openRead(uri)
                else -> bridge.openReadWrite(uri)
            }
            if (inputFd != null) {
                imagePath = bridge.pseudoPath(inputFd)
                closeInputFd = true
            } else {
                val copy = bridge.copyToPrivate(uri)
                if (copy == null) {
                    onDone("", context.getString(R.string.command_error_open_file), null)
                    return@launch
                }
                imagePath = copy.absolutePath
            }
            argv += "--image"
            argv += imagePath!!
        }

        cmd.args.forEach { arg ->
            when (arg.type) {
                ArgType.IMAGE -> {
                    // kept for compatibility with legacy model entries
                }
                ArgType.BOOL -> if ((values[arg.key] ?: "").toBooleanStrictOrNull() == true) argv += arg.key
                ArgType.TEXT, ArgType.INT, ArgType.ALGORITHM -> {
                    val raw = values[arg.key].orEmpty()
                    val vals = if (arg.repeatable) raw.lines().filter { it.isNotBlank() } else listOf(raw)
                    vals.forEach { v ->
                        if (v.isNotBlank()) {
                            argv += arg.key
                            argv += v
                        }
                    }
                }
                ArgType.FILE -> {
                    val raw = values[arg.key].orEmpty()
                    val vals = if (arg.repeatable) raw.lines().filter { it.isNotBlank() } else listOf(raw)
                    vals.forEach { v ->
                        if (v.isNotBlank()) {
                            if (v.startsWith("content://")) {
                                val fd = bridge.openRead(v.toUri())
                                if (fd != null) {
                                    extraFds += fd
                                    argv += arg.key
                                    argv += bridge.pseudoPath(fd)
                                }
                            } else {
                                argv += arg.key
                                argv += v
                            }
                        }
                    }
                }
                ArgType.CHAIN_PARTITION -> {
                    val raw = values[arg.key].orEmpty()
                    raw.lines().filter { it.isNotBlank() }.forEach { entry ->
                        val first = entry.indexOf(':')
                        val second = entry.indexOf(':', first + 1)
                        if (first < 0 || second < 0) {
                            argv += arg.key
                            argv += entry
                        } else {
                            val partition = entry.substring(0, first)
                            val rollbackIndex = entry.substring(first + 1, second)
                            val keyPath = entry.substring(second + 1)
                            if (keyPath.startsWith("content://")) {
                                val fd = bridge.openRead(keyPath.toUri())
                                if (fd != null) {
                                    extraFds += fd
                                    argv += arg.key
                                    argv += "$partition:$rollbackIndex:${bridge.pseudoPath(fd)}"
                                }
                            } else {
                                argv += arg.key
                                argv += entry
                            }
                        }
                    }
                }
            }
        }

        var outputFile: File? = null
        if (cmd.hasImage && inputFd == null && !cmd.readOnly && imagePath != null) {
            // Copy fallback: the private file was modified in place.
            outputFile = File(imagePath)
        }
        if (cmd.outputs.isNotEmpty()) {
            val firstOutput = cmd.outputs.first()
            outputFile = bridge.newPrivateOutput(firstOutput.suffix)
            argv += firstOutput.key
            argv += outputFile.absolutePath
        }

        try {
            val result = runner.run(argv)
            onDone(result.stdout, result.stderr, outputFile)
        } catch (e: Exception) {
            onDone("", context.getString(R.string.command_error_running, e.message ?: e.javaClass.simpleName), outputFile)
        } finally {
            if (closeInputFd && inputFd != null) bridge.closeFd(inputFd)
            extraFds.forEach { bridge.closeFd(it) }
        }
    }
}
