package me.wasddestroy.avbtoolandroid

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.wasddestroy.avbtoolandroid.ui.components.DialogConfirmButton
import me.wasddestroy.avbtoolandroid.ui.components.DialogDismissButton
import me.wasddestroy.avbtoolandroid.ui.components.DialogNeutralButton
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceGroup
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceRow
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceValueRow
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceSwitchRow
import me.wasddestroy.avbtoolandroid.ui.components.SettingsList
import me.wasddestroy.avbtoolandroid.ui.components.preferenceGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

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
        mutableStateOf(command.args.associate { storageKey(it) to (it.defaultValue ?: "") })
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
    var advancedExpanded by remember { mutableStateOf(false) }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
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

    fun onRun() {
        val imageUri = values[IMAGE_STORAGE_KEY].orEmpty()
        if (imageUri.isBlank()) {
            result = AvbCommandResult(
                status = AvbResultStatus.FAILED,
                errors = listOf(context.getString(R.string.command_choose_image_error)),
            )
            return
        }
        pendingCommand = command
        if (command.readOnly) {
            runCommand(
                cmd = command,
                values = values,
                uri = Uri.parse(imageUri),
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

            if (imageArgs.isNotEmpty()) {
                preferenceGroup(titleRes = R.string.command_section_image_configs) {
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
                preferenceGroup(titleRes = R.string.command_section_key_configs) {
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
                preferenceGroup(titleRes = R.string.command_section_options) {
                    switchArgs.forEach { arg ->
                        row(arg.key) {
                            PreferenceSwitchRow(
                                checked = (values[arg.key] ?: "").toBooleanStrictOrNull() == true,
                                title = stringResource(arg.labelRes),
                                iconContent = { RowIcon(argIcon(arg)) },
                                onCheckedChange = { checked ->
                                    values = values + (arg.key to checked.toString())
                                },
                            )
                        }
                    }
                }
            }
    
            if (advancedArgs.isNotEmpty()) {
                preferenceGroup(titleRes = R.string.command_section_advanced) {
                    if (advancedExpanded) {
                        advancedArgs.forEach { arg ->
                            row(arg.key) {
                                if (arg.type == ArgType.BOOL) {
                                    PreferenceSwitchRow(
                                        checked = (values[arg.key] ?: "").toBooleanStrictOrNull() == true,
                                        title = stringResource(arg.labelRes),
                                        iconContent = { RowIcon(argIcon(arg)) },
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
                        uri = Uri.parse(values[IMAGE_STORAGE_KEY].orEmpty()),
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
                    row(group.title) {
                        PreferenceValueRow(
                            title = group.title,
                            value = "",
                        )
                    }
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
            PreferenceGroup {
                row("raw_output_toggle") {
                    PreferenceRow(
                        title = stringResource(R.string.command_raw_output),
                        summary = stringResource(
                            if (rawExpanded) R.string.command_raw_output_collapse
                            else R.string.command_raw_output_expand
                        ),
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
private fun CommandArgRow(
    arg: AvbArg,
    value: String,
    values: Map<String, String>,
    onPickFile: (String, Int?) -> Unit,
    onManageFile: () -> Unit,
    onEditText: () -> Unit,
    onChooseAlgorithm: () -> Unit,
    onToggleBoolean: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    when (arg.type) {
        ArgType.IMAGE, ArgType.FILE -> {
            val key = storageKey(arg)
            val lines = value.lines().filter { it.isNotBlank() }
            val summary = if (arg.repeatable) {
                if (lines.isEmpty()) context.getString(R.string.command_choose_file)
                else context.resources.getQuantityString(R.plurals.command_files_selected, lines.size, lines.size)
            } else {
                val fileName = lines.firstOrNull()?.let { runCatching { Uri.parse(it).lastPathSegment }.getOrNull() }
                when {
                    fileName.isNullOrBlank() -> context.getString(R.string.command_choose_file)
                    else -> fileName
                }
            }
            PreferenceRow(
                title = stringResource(arg.labelRes) + if (arg.required) stringResource(R.string.command_required) else "",
                iconContent = { RowIcon(argIcon(arg)) },
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
                iconContent = { RowIcon(argIcon(arg)) },
                summary = value.ifBlank { null },
                onClick = onEditText,
            )
        }
        ArgType.ALGORITHM -> {
            PreferenceRow(
                title = stringResource(arg.labelRes) + if (arg.required) stringResource(R.string.command_required) else "",
                iconContent = { RowIcon(argIcon(arg)) },
                summary = value.ifBlank { "NONE" },
                onClick = onChooseAlgorithm,
            )
        }
        ArgType.BOOL -> {
            PreferenceSwitchRow(
                checked = (values[arg.key] ?: "").toBooleanStrictOrNull() == true,
                title = stringResource(arg.labelRes),
                iconContent = { RowIcon(argIcon(arg)) },
                onCheckedChange = onToggleBoolean,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(title) },
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
    val context = LocalContext.current
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
                    val fileName = runCatching { Uri.parse(item).lastPathSegment }.getOrNull()
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

private fun argvPreview(cmd: AvbCommand, values: Map<String, String>): List<String> {
    val argv = mutableListOf("avbtool", cmd.id, "--image", values[IMAGE_STORAGE_KEY] ?: "")
    cmd.args.forEach { arg ->
        when (arg.type) {
            ArgType.BOOL -> if ((values[arg.key] ?: "").toBooleanStrictOrNull() == true) argv += arg.key
            ArgType.TEXT, ArgType.INT, ArgType.FILE, ArgType.ALGORITHM -> {
                val raw = values[arg.key].orEmpty()
                val vals = if (arg.repeatable) raw.lines().filter { it.isNotBlank() } else listOf(raw)
                vals.forEach { v ->
                    if (v.isNotBlank()) {
                        argv += arg.key
                        argv += v
                    }
                }
            }
            ArgType.IMAGE -> Unit
        }
    }
    return argv
}

private fun buildResult(argv: List<String>, stdout: String, stderr: String): String {
    return buildString {
        append("> ${argv.joinToString(" ")}\n\n")
        append(stdout)
        if (stderr.isNotBlank()) {
            append("\n[stderr]\n")
            append(stderr)
        }
    }
}

private fun runCommand(
    cmd: AvbCommand,
    values: Map<String, String>,
    uri: Uri,
    bridge: SafFileBridge,
    runner: AvbTaskRunner,
    scope: CoroutineScope,
    context: Context,
    onStart: () -> Unit,
    onDone: (String, String, File?) -> Unit,
) {
    scope.launch {
        onStart()
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
        val inputFd: Int? = when {
            needsRealDirectory -> null
            cmd.readOnly -> bridge.openRead(uri)
            else -> bridge.openReadWrite(uri)
        }
        val imagePath: String
        var closeInputFd = false
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

        val argv = mutableListOf("avbtool", cmd.id)
        val extraFds = mutableListOf<Int>()
        cmd.args.forEach { arg ->
            when (arg.type) {
                ArgType.IMAGE -> {
                    argv += arg.key
                    argv += imagePath
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
                                val fd = bridge.openRead(Uri.parse(v))
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
            }
        }

        var outputFile: File? = null
        if (inputFd == null && !cmd.readOnly) {
            // Copy fallback: the private file was modified in place.
            outputFile = File(imagePath)
        }
        if (cmd.id == "extract_vbmeta_image") {
            outputFile = bridge.newPrivateOutput(".img")
            argv += "--output"
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
