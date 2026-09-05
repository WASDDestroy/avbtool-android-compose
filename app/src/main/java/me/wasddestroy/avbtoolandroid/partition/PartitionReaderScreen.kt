package me.wasddestroy.avbtoolandroid.partition

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.wasddestroy.avbtoolandroid.R
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceCheckboxRow
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceGroup
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceRow
import me.wasddestroy.avbtoolandroid.ui.components.SettingsList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartitionReaderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: PartitionReaderViewModel = viewModel(factory = PartitionReaderViewModel.factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModel.setWorkspace(uri)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.partition_reader_title)) },
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
    ) { contentPadding ->
        when (state.rootState) {
            RootCheckState.CHECKING -> RootBannerContainer {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.partition_root_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            RootCheckState.NO_SU -> RootBannerContainer {
                Text(
                    text = stringResource(R.string.partition_root_no_su),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            RootCheckState.DENIED -> RootBannerContainer {
                Text(
                    text = stringResource(R.string.partition_root_denied),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            RootCheckState.AVAILABLE -> PartitionList(
                state = state,
                onPickWorkspace = { treePicker.launch(null) },
                onToggle = viewModel::togglePartition,
                onReadClick = viewModel::startOrCancelRead,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        }
    }
}

@Composable
private fun ReaderIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
    )
}

@Composable
private fun RootBannerContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun PartitionList(
    state: PartitionReaderUiState,
    onPickWorkspace: () -> Unit,
    onToggle: (PartitionEntry) -> Unit,
    onReadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsList(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item("workspace") {
            PreferenceGroup {
                row("workspace") {
                    PreferenceRow(
                        title = stringResource(R.string.partition_workspace),
                        summary = state.workspaceName ?: stringResource(R.string.partition_workspace_none),
                        iconContent = { ReaderIcon(Icons.Filled.FolderOpen) },
                        onClick = onPickWorkspace,
                    )
                }
            }
        }

        if (state.loadingPartitions) {
            item("loading") {
                Text(
                    text = stringResource(R.string.partition_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                )
            }
        }

        if (state.enumerationError != null) {
            item("enum_error") {
                Text(
                    text = state.enumerationError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                )
            }
        }

        if (!state.loadingPartitions && state.enumerationError == null) {
            item("by_name") {
                PreferenceGroup(title = stringResource(R.string.partition_path_by_name)) {
                    if (state.byName.isEmpty()) {
                        row("empty") {
                            EmptyPartitionRow()
                        }
                    } else {
                        state.byName.forEach { entry ->
                            row(entry.name) {
                                PartitionCheckRow(entry = entry, onToggle = onToggle)
                            }
                        }
                    }
                }
            }
            item("mapper") {
                PreferenceGroup(title = stringResource(R.string.partition_path_mapper)) {
                    if (state.mapper.isEmpty()) {
                        row("empty") {
                            EmptyPartitionRow()
                        }
                    } else {
                        state.mapper.forEach { entry ->
                            row(entry.name) {
                                PartitionCheckRow(entry = entry, onToggle = onToggle)
                            }
                        }
                    }
                }
            }
        }

        item("read") {
            ReadButtonSection(state = state, enabled = readEnabled(state), onClick = onReadClick)
        }
    }
}

private fun readEnabled(state: PartitionReaderUiState): Boolean =
    state.workspaceUri != null &&
        (state.byName + state.mapper).any { it.checked } &&
        state.readState !is ReadState.Running

@Composable
private fun PartitionCheckRow(entry: PartitionEntry, onToggle: (PartitionEntry) -> Unit) {
    PreferenceCheckboxRow(
        checked = entry.checked,
        title = entry.name,
        summary = if (entry.sizeBytes > 0) formatBytes(entry.sizeBytes) else null,
        onCheckedChange = { onToggle(entry) },
    )
}

@Composable
private fun EmptyPartitionRow() {
    PreferenceRow(
        title = stringResource(R.string.partition_empty),
        enabled = false,
    )
}

@Composable
private fun ReadButtonSection(
    state: PartitionReaderUiState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val running = state.readState as? ReadState.Running
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Button(
            onClick = onClick,
            enabled = enabled || running != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (running != null) {
                    stringResource(R.string.partition_cancel)
                } else {
                    stringResource(R.string.partition_read)
                },
            )
        }
        if (running != null) {
            LinearProgressIndicator(
                progress = {
                    if (running.totalBytes > 0) {
                        (running.writtenBytes.toFloat() / running.totalBytes).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            Text(
                text = stringResource(
                    R.string.partition_progress,
                    running.currentName,
                    running.deviceIndex,
                    running.deviceCount,
                    formatBytes(running.writtenBytes),
                    formatBytes(running.totalBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        when (val readState = state.readState) {
            is ReadState.Done -> {
                readState.overwrittenNames.takeIf { it.isNotEmpty() }?.let { overwritten ->
                    Text(
                        text = stringResource(
                            R.string.partition_done_overwritten,
                            overwritten.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            is ReadState.Cancelled -> Text(
                text = stringResource(R.string.partition_cancelled),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            is ReadState.Error -> Text(
                text = readState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> Unit
        }
    }
}
