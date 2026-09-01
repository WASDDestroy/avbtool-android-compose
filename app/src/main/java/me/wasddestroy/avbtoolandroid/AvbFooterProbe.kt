package me.wasddestroy.avbtoolandroid

import android.content.ContentResolver
import android.net.Uri
import java.io.EOFException
import java.io.FileInputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Reads the existing rollback index out of a signed image without invoking
 * avbtool: the AVB footer ('AVBf') in the last 64 bytes points at the vbmeta
 * blob ('AVB0'), whose header stores the rollback index at byte offset 112
 * as a big-endian uint64 (AvbVBMetaHeader.FORMAT_STRING).
 *
 * Re-signing with add_hash_footer/add_hashtree_footer truncates an existing
 * footer away and rewrites the whole vbmeta blob, so this value is what the
 * image currently carries and what the requested rollback index replaces.
 *
 * Returns null when the image carries no valid footer — callers keep the
 * magnitude-only check path in that case.
 */
object AvbFooterProbe {

    private const val FOOTER_MAGIC = "AVBf"
    private const val VBMETA_MAGIC = "AVB0"
    private const val FOOTER_SIZE = 64L
    private const val HEADER_SIZE = 256L

    /** Offset of vbmeta_offset inside AvbFooter ('!4s2LQQ...'). */
    private const val VBMETA_OFFSET_IN_FOOTER = 20

    /** Offset of rollback_index inside AvbVBMetaHeader. */
    private const val ROLLBACK_INDEX_IN_HEADER = 112

    /** Existing rollback index of the image, or null when there is no readable AVB footer. */
    fun readRollbackIndex(resolver: ContentResolver, uri: Uri): BigInteger? {
        return runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                if (size < FOOTER_SIZE + HEADER_SIZE) return@use null
                FileInputStream(pfd.fileDescriptor).use { input ->
                    val channel = input.channel
                    val footer = ByteArray(FOOTER_SIZE.toInt())
                    channel.position(size - FOOTER_SIZE)
                    readFully(channel, footer)
                    val vbmetaOffset = footerVbmetaOffset(footer) ?: return@use null
                    if (vbmetaOffset < 0 || vbmetaOffset + HEADER_SIZE > size) return@use null
                    val header = ByteArray(HEADER_SIZE.toInt())
                    channel.position(vbmetaOffset)
                    readFully(channel, header)
                    rollbackIndexFromHeader(header)
                }
            }
        }.getOrNull()
    }

    /** vbmeta_offset from footer bytes, or null when the footer magic is missing. */
    internal fun footerVbmetaOffset(footer: ByteArray): Long? {
        if (footer.size < VBMETA_OFFSET_IN_FOOTER + 8) return null
        if (!footer.startsWithAscii(FOOTER_MAGIC)) return null
        return ByteBuffer.wrap(footer, VBMETA_OFFSET_IN_FOOTER, 8)
            .order(ByteOrder.BIG_ENDIAN)
            .long
    }

    /** Rollback index from vbmeta header bytes, or null when the header magic is missing. */
    internal fun rollbackIndexFromHeader(header: ByteArray): BigInteger? {
        if (header.size < ROLLBACK_INDEX_IN_HEADER + 8) return null
        if (!header.startsWithAscii(VBMETA_MAGIC)) return null
        return BigInteger(1, header.copyOfRange(ROLLBACK_INDEX_IN_HEADER, ROLLBACK_INDEX_IN_HEADER + 8))
    }

    private fun ByteArray.startsWithAscii(prefix: String): Boolean {
        val bytes = prefix.toByteArray(Charsets.US_ASCII)
        if (size < bytes.size) return false
        for (i in bytes.indices) {
            if (this[i] != bytes[i]) return false
        }
        return true
    }

    private fun readFully(channel: FileChannel, target: ByteArray) {
        var read = 0
        while (read < target.size) {
            val n = channel.read(ByteBuffer.wrap(target, read, target.size - read))
            if (n < 0) throw EOFException("unexpected end of image")
            read += n
        }
    }
}
