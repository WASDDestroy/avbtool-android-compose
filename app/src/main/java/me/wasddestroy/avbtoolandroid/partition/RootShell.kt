package me.wasddestroy.avbtoolandroid.partition

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Result of one command executed inside a [RootShell] session. */
data class ShellResult(
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: String,
) {
    val success: Boolean get() = exitCode == 0
}

/**
 * Remembered outcome of the last root probe so the Settings entry row can
 * disable itself once this app is known to lack root. Root availability is
 * never persisted: it can change across reboots or manager updates.
 */
object RootProbeCache {
    var lastProbeFailed: Boolean by mutableStateOf(false)
        private set

    fun record(unavailable: Boolean) {
        lastProbeFailed = unavailable
    }
}

/**
 * Minimal persistent `su` session using an echo-marker protocol, compatible
 * with every root manager that follows the Android su convention (Magisk,
 * KernelSU, APatch). Deliberately hand-rolled instead of depending on libsu.
 *
 * Commands run sequentially: [run] writes the command plus an `echo <marker>`
 * line to the shell's stdin and reads stdout until the marker appears, so a
 * long-running command (dd) simply blocks the calling coroutine. A separate
 * daemon thread drains stderr into a bounded tail for error messages.
 *
 * Cancellation of a running command is done with [submit], which writes a raw
 * line (e.g. `kill -9 <pid>`) to the shell without touching marker state.
 */
class RootShell private constructor(private val process: Process) : Closeable {
    private val stdin = process.outputStream
    private val reader = process.inputStream.bufferedReader()
    private val stderrTail = ArrayDeque<String>()
    private val commandCounter = AtomicInteger()
    private val closed = AtomicBoolean(false)

    init {
        val drain = Thread {
            try {
                process.errorStream.bufferedReader().forEachLine { line -> rememberStderr(line) }
            } catch (_: IOException) {
                // Stream closed when the session is torn down.
            }
        }
        drain.isDaemon = true
        drain.name = "avb-root-shell-stderr"
        drain.start()
    }

    private fun rememberStderr(line: String) {
        synchronized(stderrTail) {
            if (stderrTail.size >= STDERR_TAIL_LINES) stderrTail.removeFirst()
            stderrTail.addLast(line)
        }
    }

    private fun stderrSnapshot(): String = synchronized(stderrTail) { stderrTail.joinToString("\n") }

    /**
     * Runs [command] to completion and returns its exit code, stdout lines and
     * a stderr tail. Destroys the session on timeout; the caller must reopen a
     * new session afterwards.
     */
    @Synchronized
    fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult {
        if (closed.get() || !process.isAlive) {
            return ShellResult(exitCode = -1, stdout = emptyList(), stderr = "root shell is closed")
        }
        val marker = MARKER_PREFIX + commandCounter.incrementAndGet() + "_" + System.nanoTime()
        val timedOut = AtomicBoolean(false)
        val watchdog = Thread {
            try {
                Thread.sleep(timeoutMs)
                timedOut.set(true)
                process.destroyForcibly()
            } catch (_: InterruptedException) {
            }
        }
        watchdog.isDaemon = true
        watchdog.start()
        val out = mutableListOf<String>()
        var exitCode = -1
        try {
            stdin.write("$command\n__avb_rc=\$?\necho $marker\$__avb_rc\n".toByteArray())
            stdin.flush()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith(marker)) {
                    exitCode = line.removePrefix(marker).trim().toIntOrNull() ?: -1
                    break
                }
                out.add(line)
            }
        } catch (_: IOException) {
            // Stream torn down by the watchdog or process death.
        } finally {
            watchdog.interrupt()
        }
        if (timedOut.get()) {
            close()
            return ShellResult(exitCode = -1, stdout = out, stderr = "command timed out")
        }
        return ShellResult(exitCode, out, stderrSnapshot())
    }

    /** Writes a raw line to the shell without waiting for output. */
    fun submit(command: String) {
        if (closed.get() || !process.isAlive) return
        runCatching {
            stdin.write("$command\n".toByteArray())
            stdin.flush()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        submit("exit")
        runCatching { process.waitFor(500, TimeUnit.MILLISECONDS) }
        process.destroyForcibly()
    }

    companion object {
        private const val MARKER_PREFIX = "__AVBSH_DONE_"
        private const val STDERR_TAIL_LINES = 20
        const val DEFAULT_TIMEOUT_MS = 20_000L
        private const val PROBE_TIMEOUT_MS = 12_000L

        /** Where su binaries typically live across managers and ROMs. */
        private val SU_CANDIDATES = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/vendor/bin/su",
            "/system/sd/xbin/su",
            "/data/adb/magisk/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ksd/bin/su",
            "/data/adb/ap/bin/su",
            "/debug_ramdisk/su",
        )

        fun hasSuBinary(): Boolean = SU_CANDIDATES.any { File(it).exists() }

        /**
         * Returns the first su binary that runs a command as uid 0, or null.
         * The probe may block on the manager's grant dialog, hence the timeout.
         */
        fun detect(): String? {
            for (path in SU_CANDIDATES) {
                if (!File(path).exists()) continue
                val isRoot = runCatching {
                    val p = ProcessBuilder(path, "-c", "id -u").start()
                    try {
                        if (!p.waitFor(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                            return@runCatching false
                        }
                        p.inputStream.bufferedReader().readText().trim() == "0"
                    } finally {
                        p.destroyForcibly()
                    }
                }.getOrDefault(false)
                if (isRoot) return path
            }
            return null
        }

        fun open(suPath: String): RootShell = RootShell(ProcessBuilder(suPath).start())
    }
}
