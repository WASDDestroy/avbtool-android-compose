package me.wasddestroy.avbtoolandroid

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CommandUiState(
    val running: Boolean = false,
    val result: AvbCommandResult? = null,
    val outputFile: File? = null,
)

private data class RunOutcome(
    val stdout: String,
    val stderr: String,
    val outputFile: File?,
)

class CommandViewModel(
    private val runner: AvbTaskRunner,
    private val bridge: SafFileBridge,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommandUiState())
    val uiState: StateFlow<CommandUiState> = _uiState.asStateFlow()

    /** 用户未选择镜像时的前置校验失败。与原 CommandScreen.onRun 的行为一致。 */
    fun failWithMissingImage() {
        _uiState.update {
            it.copy(
                running = false,
                result = AvbCommandResult(
                    status = AvbResultStatus.FAILED,
                    errors = listOf(appContext.getString(R.string.command_choose_image_error)),
                ),
            )
        }
    }

    fun dismissOutputFile() {
        _uiState.update { it.copy(outputFile = null) }
    }

    fun run(cmd: AvbCommand, values: Map<String, String>, uri: Uri?) {
        if (_uiState.value.running) return
        viewModelScope.launch {
            _uiState.update { it.copy(running = true) }
            val outcome = execute(cmd, values, uri)
            _uiState.value = CommandUiState(
                running = false,
                result = parseAvbResult(cmd.id, outcome.stdout, outcome.stderr),
                outputFile = outcome.outputFile,
            )
        }
    }

    private suspend fun execute(
        cmd: AvbCommand,
        values: Map<String, String>,
        uri: Uri?,
    ): RunOutcome = withContext(Dispatchers.IO) {
        val argv = mutableListOf("avbtool", cmd.id)
        val extraFds = mutableListOf<Int>()
        var closeInputFd = false
        var inputFd: Int? = null
        var imagePath: String? = null

        val firstInput = cmd.inputs.firstOrNull()

        if (firstInput != null) {
            if (uri == null) {
                return@withContext RunOutcome(
                    "",
                    appContext.getString(R.string.command_choose_image_error),
                    null,
                )
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
                "calculate_kernel_cmdline",
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
                    ?: return@withContext RunOutcome(
                        "",
                        appContext.getString(R.string.command_error_open_file),
                        null,
                    )
                imagePath = copy.absolutePath
            }
            argv += firstInput.key
            argv += imagePath
        }

        cmd.args.forEach { arg ->
            when (arg.type) {
                ArgType.IMAGE -> {
                    // kept for compatibility with legacy model entries
                }
                ArgType.BOOL ->
                    if ((values[arg.key] ?: "").toBooleanStrictOrNull() == true) argv += arg.key
                ArgType.TEXT, ArgType.INT, ArgType.ALGORITHM, ArgType.HASH_ALGORITHM -> {
                    val raw = values[arg.key].orEmpty()
                    val vals = if (arg.repeatable) raw.lines().filter { it.isNotBlank() } else listOf(raw)
                    vals.forEach { v ->
                        if (v.isNotBlank()) {
                            argv += arg.key
                            argv += v
                        }
                    }
                }
                ArgType.SIZE -> {
                    val raw = values[arg.key].orEmpty()
                    if (raw.isNotBlank()) {
                        val unit = values["${arg.key}__unit"] ?: "MiB"
                        val multiplier = when (unit) {
                            "KiB" -> 1024L
                            "MiB" -> 1024L * 1024
                            "GiB" -> 1024L * 1024 * 1024
                            else -> 1L
                        }
                        val n = raw.toLongOrNull()
                        if (n != null) {
                            argv += arg.key
                            argv += (n * multiplier).toString()
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
        if (firstInput != null && inputFd == null && !cmd.readOnly && imagePath != null) {
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
            RunOutcome(result.stdout, result.stderr, outputFile)
        } catch (e: Exception) {
            RunOutcome(
                "",
                appContext.getString(
                    R.string.command_error_running,
                    e.message ?: e.javaClass.simpleName,
                ),
                outputFile,
            )
        } finally {
            if (closeInputFd && inputFd != null) bridge.closeFd(inputFd)
            extraFds.forEach { bridge.closeFd(it) }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    CommandViewModel(
                        runner = AvbTaskRunner(appContext),
                        bridge = SafFileBridge(appContext),
                        appContext = appContext,
                    )
                }
            }
        }
    }
}
