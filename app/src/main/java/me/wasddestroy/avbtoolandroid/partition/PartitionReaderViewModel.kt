package me.wasddestroy.avbtoolandroid.partition

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.wasddestroy.avbtoolandroid.AvbCommandResult
import me.wasddestroy.avbtoolandroid.AvbResultStatus
import me.wasddestroy.avbtoolandroid.R
import me.wasddestroy.avbtoolandroid.SettingsStore

enum class RootCheckState {
    CHECKING,
    AVAILABLE,
    /** No su binary exists anywhere on this device. */
    NO_SU,
    /** A su binary exists but the app was not granted root (or timed out). */
    DENIED,
}

enum class PartitionSource { BY_NAME, MAPPER }

data class PartitionEntry(
    val name: String,
    /** Resolved block device, e.g. /dev/block/dm-3; the dedup key. */
    val device: String,
    val sizeBytes: Long,
    val source: PartitionSource,
    val checked: Boolean = false,
)

sealed interface ReadState {
    data object Idle : ReadState

    /** Progress over unique block devices; [writtenBytes] is the dd temp file size. */
    data class Running(
        val currentName: String,
        val deviceIndex: Int,
        val deviceCount: Int,
        val writtenBytes: Long,
        val totalBytes: Long,
    ) : ReadState

    data class Done(
        val savedNames: List<String>,
        val overwrittenNames: List<String>,
    ) : ReadState

    data object Cancelled : ReadState
    data class Error(val message: String) : ReadState
}

data class PartitionReaderUiState(
    val rootState: RootCheckState = RootCheckState.CHECKING,
    val workspaceUri: String? = null,
    val workspaceName: String? = null,
    val loadingPartitions: Boolean = false,
    val byName: List<PartitionEntry> = emptyList(),
    val mapper: List<PartitionEntry> = emptyList(),
    val enumerationError: String? = null,
    val readState: ReadState = ReadState.Idle,
    /** Summary banner shown when a read finishes, mirrors the command screen. */
    val popup: AvbCommandResult? = null,
)

class PartitionReaderViewModel(
    private val context: Context,
    private val store: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartitionReaderUiState())
    val uiState: StateFlow<PartitionReaderUiState> = _uiState.asStateFlow()

    private var shell: RootShell? = null
    private var readJob: Job? = null
    private var cancelRequested = false
    private var currentDdPid: Int? = null

    init {
        checkRoot()
        restoreWorkspace()
    }

    private fun checkRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            val suPath = RootShell.detect()
            if (suPath != null) {
                shell?.close()
                shell = RootShell.open(suPath)
                RootProbeCache.record(false)
                _uiState.update { it.copy(rootState = RootCheckState.AVAILABLE) }
                loadPartitions()
            } else {
                val denied = RootShell.hasSuBinary()
                RootProbeCache.record(true)
                _uiState.update {
                    it.copy(
                        rootState = if (denied) RootCheckState.DENIED else RootCheckState.NO_SU,
                    )
                }
            }
        }
    }

    private fun loadPartitions() {
        val session = shell ?: return
        _uiState.update { it.copy(loadingPartitions = true, enumerationError = null) }
        val byName = enumerate(session, "/dev/block/by-name", PartitionSource.BY_NAME)
        val mapper = enumerate(session, "/dev/block/mapper", PartitionSource.MAPPER)
        _uiState.update {
            it.copy(
                loadingPartitions = false,
                byName = byName ?: emptyList(),
                mapper = mapper ?: emptyList(),
                enumerationError = if (byName == null && mapper == null) {
                    context.getString(R.string.partition_error_enumerate)
                } else {
                    null
                },
            )
        }
    }

    /** Returns null when the directory cannot be listed at all. */
    private fun enumerate(session: RootShell, dir: String, source: PartitionSource): List<PartitionEntry>? {
        val result = session.run(
            "if [ -d '$dir' ]; then for f in '$dir'/*; do r=\"\$(readlink -f \"\$f\")\"; " +
                "printf '%s\\t%s\\t%s\\t%s\\n' \"\${f##*/}\" \"\$r\" " +
                "\"\$(blockdev --getsize64 \"\$r\" 2>/dev/null)\" \"\$(test -b \"\$r\" && echo b || echo o)\"; done; fi",
        )
        if (!result.success && result.stdout.isEmpty()) return null
        return result.stdout.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            val entry = PartitionEntry(
                name = parts[0],
                device = parts[1],
                sizeBytes = parts[2].toLongOrNull() ?: 0L,
                source = source,
            )
            // Not a block device (e.g. the by-uuid directory under mapper).
            if (parts[3] != "b") return@mapNotNull null
            if (!isDumpable(entry)) null else entry
        }.sortedBy { it.name }
    }

    /**
     * Mapper holds non-dumpable entries besides real partitions: the by-uuid
     * directory (size unknown), mirrors named after this app's package, and
     * userdata itself. Only real block devices survive.
     */
    private fun isDumpable(entry: PartitionEntry): Boolean {
        if (entry.source == PartitionSource.MAPPER) {
            if (entry.name == "userdata") return false
            if (entry.name.startsWith(context.packageName)) return false
        }
        return true
    }

    private fun restoreWorkspace() {
        val saved = store.readPartitionWorkspaceUri() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val uri = Uri.parse(saved)
            val persisted = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isWritePermission
            }
            if (!persisted) {
                store.writePartitionWorkspaceUri(null)
                return@launch
            }
            _uiState.update {
                it.copy(workspaceUri = saved, workspaceName = WorkspaceFolder.displayName(context, uri))
            }
        }
    }

    fun setWorkspace(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            store.writePartitionWorkspaceUri(uri.toString())
            _uiState.update {
                it.copy(
                    workspaceUri = uri.toString(),
                    workspaceName = WorkspaceFolder.displayName(context, uri),
                    readState = ReadState.Idle,
                )
            }
        }
    }

    fun togglePartition(entry: PartitionEntry) {
        _uiState.update { state ->
            fun toggle(list: List<PartitionEntry>) = list.map {
                if (it.name == entry.name && it.source == entry.source) {
                    it.copy(checked = !it.checked)
                } else {
                    it
                }
            }
            state.copy(
                byName = toggle(state.byName),
                mapper = toggle(state.mapper),
                readState = ReadState.Idle,
            )
        }
    }

    val canRead: Boolean
        get() {
            val state = _uiState.value
            return state.rootState == RootCheckState.AVAILABLE &&
                state.workspaceUri != null &&
                (state.byName + state.mapper).any { it.checked } &&
                state.readState !is ReadState.Running
        }

    fun startOrCancelRead() {
        if (_uiState.value.readState is ReadState.Running) {
            cancelRead()
        } else {
            startRead()
        }
    }

    private fun startRead() {
        if (!canRead) return
        val state = _uiState.value
        val session = shell ?: return
        val workspace = state.workspaceUri ?: return
        readJob = viewModelScope.launch(Dispatchers.IO) {
            cancelRequested = false
            val selected = (state.byName + state.mapper).filter { it.checked }
            // Dedup: by-name and mapper entries can resolve to the same dm
            // device; read each unique block device once, then copy it out
            // once per partition name.
            val groups = selected.groupBy { it.device }.toList()
            val totalBytes = groups.sumOf { (_, entries) -> entries.first().sizeBytes }

            val tmpDir = context.getExternalFilesDir(null) ?: context.filesDir
            val tmp = File(tmpDir, TEMP_FILE_NAME)
            val needed = formatBytes(totalBytes)
            val tmpFree = tmpDir.usableSpace
            if (tmpFree < totalBytes) {
                failWith(
                    context.getString(
                        R.string.partition_error_tmp_space,
                        needed, formatBytes(tmpFree),
                    ),
                )
                return@launch
            }
            val treeUri = Uri.parse(workspace)
            WorkspaceFolder.freeSpace(context, treeUri)?.let { free ->
                if (free < totalBytes) {
                    failWith(
                        context.getString(
                            R.string.partition_error_workspace_space,
                            needed, formatBytes(free),
                        ),
                    )
                    return@launch
                }
            }

            val savedNames = mutableListOf<String>()
            val overwrittenNames = mutableListOf<String>()
            var cancelled = false

            for ((index, group) in groups.withIndex()) {
                val (device, entries) = group
                val size = entries.first().sizeBytes
                _uiState.update {
                    it.copy(readState = ReadState.Running(
                        currentName = entries.first().name,
                        deviceIndex = index + 1,
                        deviceCount = groups.size,
                        writtenBytes = 0L,
                        totalBytes = size,
                    ))
                }

                tmp.delete()
                val pidLine = shellCommandFor(device, tmp.absolutePath)
                val progressJob = launch {
                    while (isActive) {
                        delay(500)
                        _uiState.update { current ->
                            val running = current.readState as? ReadState.Running ?: return@update current
                            current.copy(readState = running.copy(writtenBytes = tmp.length()))
                        }
                    }
                }
                val result = session.run(pidLine, timeoutMs = DD_TIMEOUT_MS)
                progressJob.cancel()

                val pid = result.stdout.firstOrNull()?.trim()?.toIntOrNull()
                currentDdPid = pid

                if (cancelRequested) {
                    pid?.let { session.submit("kill -9 $it") }
                    // Give dd a moment to die so the next session command is clean.
                    session.run("true", timeoutMs = 5_000)
                    cancelled = true
                    break
                }
                if (!result.success) {
                    failWith(
                        context.getString(R.string.partition_error_dd, device) +
                            result.stderr.takeIf { s -> s.isNotBlank() }?.let { s -> "\n$s" }.orEmpty(),
                    )
                    tmp.delete()
                    return@launch
                }

                for (entry in entries) {
                    val fileName = entry.name + IMG_SUFFIX
                    val copy = copyToWorkspace(tmp, treeUri, fileName)
                    if (copy.error != null) {
                        // A vanished workspace (deleted while reading) produces
                        // an unspecific save failure; probe it once to name the
                        // real cause.
                        val gone = WorkspaceFolder.displayName(context, treeUri) == null
                        failWith(
                            if (gone) {
                                context.getString(R.string.partition_error_workspace_gone)
                            } else {
                                context.getString(R.string.partition_error_copy, fileName) +
                                    "\n" + copy.error
                            },
                        )
                        tmp.delete()
                        return@launch
                    }
                    if (copy.overwritten) {
                        overwrittenNames.add(fileName)
                    } else {
                        savedNames.add(fileName)
                    }
                }
                tmp.delete()
            }

            if (cancelled) {
                _uiState.update {
                    it.copy(
                        readState = ReadState.Cancelled,
                        popup = AvbCommandResult(status = AvbResultStatus.CANCELLED),
                    )
                }
            } else {
                val warnings = if (overwrittenNames.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        context.getString(
                            R.string.partition_done_overwritten,
                            overwrittenNames.joinToString(", "),
                        ),
                    )
                }
                _uiState.update {
                    it.copy(
                        readState = ReadState.Done(savedNames.toList(), overwrittenNames.toList()),
                        popup = AvbCommandResult(status = AvbResultStatus.SUCCESS, warnings = warnings),
                    )
                }
            }
        }
    }

    fun dismissPopup() {
        _uiState.update { it.copy(popup = null) }
    }

    private fun failWith(message: String) {
        _uiState.update {
            it.copy(
                readState = ReadState.Error(message),
                popup = AvbCommandResult(status = AvbResultStatus.FAILED, errors = listOf(message)),
            )
        }
    }

    private fun shellCommandFor(device: String, outputPath: String): String =
        "sh -c 'echo \$\$; exec dd if=\"$device\" of=\"$outputPath\" bs=4194304'"

    private data class CopyResult(val overwritten: Boolean, val error: String? = null)

    private suspend fun copyToWorkspace(tmp: File, treeUri: Uri, fileName: String): CopyResult =
        withContext(Dispatchers.IO) {
            val existed = WorkspaceFolder.childExists(context, treeUri, fileName)
            if (existed && !WorkspaceFolder.deleteChild(context, treeUri, fileName)) {
                return@withContext CopyResult(
                    overwritten = existed,
                    error = "could not replace the existing document",
                )
            }
            val docUri = WorkspaceFolder.createChild(context, treeUri, fileName)
                ?: return@withContext CopyResult(
                    overwritten = existed,
                    error = "could not create the document",
                )
            val output = try {
                context.contentResolver.openOutputStream(docUri, "w")
            } catch (e: Exception) {
                null
            } ?: return@withContext CopyResult(
                overwritten = existed,
                error = "could not open the document for writing",
            )
            try {
                output.use { os ->
                    tmp.inputStream().use { input ->
                        input.copyTo(os, COPY_BUFFER_BYTES)
                    }
                    os.flush()
                }
            } catch (e: Exception) {
                return@withContext CopyResult(
                    overwritten = existed,
                    error = e.message ?: e.javaClass.simpleName,
                )
            }
            CopyResult(overwritten = existed)
        }

    private fun cancelRead() {
        cancelRequested = true
        currentDdPid?.let { pid -> shell?.submit("kill -9 $pid") }
    }

    override fun onCleared() {
        currentDdPid?.let { pid -> shell?.submit("kill -9 $pid") }
        shell?.close()
        super.onCleared()
    }

    companion object {
        private const val TEMP_FILE_NAME = "partition_dump.img"
        private const val IMG_SUFFIX = ".img"
        private const val COPY_BUFFER_BYTES = 1 shl 16
        /** Large partitions can take a long time; effectively "no timeout". */
        private const val DD_TIMEOUT_MS = 6L * 60 * 60 * 1000

        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                PartitionReaderViewModel(
                    context = app.applicationContext,
                    store = SettingsStore(
                        app.getSharedPreferences("application_configs", Context.MODE_PRIVATE),
                    ),
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
}
