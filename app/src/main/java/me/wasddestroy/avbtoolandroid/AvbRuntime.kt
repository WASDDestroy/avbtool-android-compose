package me.wasddestroy.avbtoolandroid

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PythonRuntime {
    @Volatile
    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        if (!started) {
            val py = Python.getInstance()
            val nativeLibDir = context.applicationContext.applicationInfo.nativeLibraryDir
            py.getModule("android_bridge").callAttr("init", nativeLibDir)
            started = true
        }
        cleanupStaleScratch(context.applicationContext)
    }

    /**
     * Removes leftover signing scratch areas and single-command scratch
     * copies from an earlier process. The in-memory export/output lists that
     * make those files reachable die with the process, so anything found on
     * disk at startup can never be saved by the user again.
     */
    private fun cleanupStaleScratch(context: Context) {
        Thread {
            runCatching {
                File(context.filesDir, "profile_sign").deleteRecursively()
                File(context.filesDir, "avb/input").listFiles()?.forEach { it.delete() }
            }
        }.start()
    }

    @Suppress("unused")
    fun isStarted(): Boolean = started
}

data class AvbRunResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)

class AvbTaskRunner(private val context: Context) {
    suspend fun run(argv: List<String>): AvbRunResult = withContext(Dispatchers.IO) {
        try {
            PythonRuntime.start(context.applicationContext)
            val bridge = pyBridge()
            val result = bridge.callAttr("run_avbtool", PyObject.fromJava(argv.toTypedArray()))
            val items = result.asList()
            AvbRunResult(
                stdout = items[0].toString(),
                stderr = items[1].toString(),
                exitCode = if (items[1].toString().isBlank()) 0 else 1
            )
        } catch (e: Exception) {
            AvbRunResult(
                stdout = "",
                stderr = "Python runtime error: " + (e.message ?: e.javaClass.simpleName) +
                    System.lineSeparator() + e.stackTraceToString(),
                exitCode = 1
            )
        }
    }
}

private fun pyBridge(): PyObject {
    return Python.getInstance().getModule("android_bridge")
}

class SafFileBridge(private val context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun pseudoPath(fd: Int): String = "/saf/fd/$fd"

    fun openRead(uri: Uri): Int? {
        return try {
            resolver.openFileDescriptor(uri, "r")?.detachFd()
        } catch (_: Exception) {
            null
        }
    }

    fun openReadWrite(uri: Uri): Int? {
        return try {
            resolver.openFileDescriptor(uri, "rw")?.detachFd()
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("unused")
    fun openWrite(uri: Uri): Int? {
        return try {
            resolver.openFileDescriptor(uri, "rwt")?.detachFd()
        } catch (e: Exception) {
            null
        }
    }

    fun closeFd(fd: Int) {
        if (fd >= 0) {
            try {
                ParcelFileDescriptor.adoptFd(fd).close()
            } catch (_: Exception) {
                // already closed or invalid
            }
        }
    }

    fun displayName(uri: Uri): String {
        return try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment ?: "image.img"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "image.img"
        }
    }

    fun copyToPrivate(uri: Uri): File? {
        return try {
            val name = displayName(uri)
            val dir = File(context.applicationContext.filesDir, "avb/input")
            dir.mkdirs()
            val target = File(dir, name)
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        } catch (_: Exception) {
            null
        }
    }

    fun newPrivateOutput(suffix: String = ".img"): File {
        val dir = File(context.applicationContext.filesDir, "avb/output")
        dir.mkdirs()
        return File(dir, "out_${System.currentTimeMillis()}$suffix")
    }
}
