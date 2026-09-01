package me.wasddestroy.avbtoolandroid

import android.content.Context
import android.net.Uri
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves the rollback index an image currently carries.
 *
 * Preferred path is `avbtool info_image` parsing: authoritative (it validates
 * the whole footer/vbmeta structure) and also handles bare vbmeta blobs. When
 * avbtool cannot parse the image — or prints a rollback index that does not
 * fit a signed Long, which [InfoImageParser] then reports as null — the direct
 * footer/vbmeta header read from [AvbFooterProbe] is tried instead.
 *
 * Returns null when neither path yields a rollback index; callers keep the
 * magnitude-only check in that case.
 */
object AvbRollbackIndexReader {

    suspend fun read(
        runner: AvbTaskRunner,
        bridge: SafFileBridge,
        appContext: Context,
        uri: Uri,
    ): BigInteger? = withContext(Dispatchers.IO) {
        val fd = bridge.openRead(uri)
        if (fd != null) {
            try {
                val result = runner.run(listOf("avbtool", "info_image", "--image", bridge.pseudoPath(fd)))
                if (result.stderr.isBlank()) {
                    val inspection = runCatching { InfoImageParser.inspect("", result.stdout) }.getOrNull()
                    val index = inspection?.rollbackIndex
                    if (index != null) return@withContext BigInteger.valueOf(index)
                }
            } finally {
                bridge.closeFd(fd)
            }
        }
        runCatching { AvbFooterProbe.readRollbackIndex(appContext.contentResolver, uri) }.getOrNull()
    }
}
