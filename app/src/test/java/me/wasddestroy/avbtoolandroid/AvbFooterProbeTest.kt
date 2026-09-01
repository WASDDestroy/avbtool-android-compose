package me.wasddestroy.avbtoolandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AvbFooterProbeTest {

    private fun footer(vbmetaOffset: Long): ByteArray {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.put("AVBf".toByteArray(Charsets.US_ASCII))
        buf.putInt(1).putInt(0)
        buf.putLong(4096).putLong(vbmetaOffset).putLong(256)
        return buf.array()
    }

    private fun header(rollbackIndex: BigInteger): ByteArray {
        val bytes = ByteArray(256)
        "AVB0".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        // Write the low 8 bytes so values >= 2^63 keep their uint64 layout;
        // shorter arrays are right-aligned inside the big-endian field.
        val tail = rollbackIndex.toByteArray().takeLast(8)
        tail.forEachIndexed { i, b -> bytes[112 + (8 - tail.size) + i] = b }
        return bytes
    }

    @Test
    fun footerVbmetaOffset_readsBigEndianOffset() {
        assertEquals(4096L, AvbFooterProbe.footerVbmetaOffset(footer(4096)))
        assertEquals(0L, AvbFooterProbe.footerVbmetaOffset(footer(0)))
    }

    @Test
    fun footerVbmetaOffset_rejectsWrongMagicAndTruncation() {
        assertNull(AvbFooterProbe.footerVbmetaOffset(ByteArray(64)))
        assertNull(AvbFooterProbe.footerVbmetaOffset(ByteArray(20)))
    }

    @Test
    fun rollbackIndexFromHeader_readsUnsignedValue() {
        assertEquals(BigInteger.valueOf(1234), AvbFooterProbe.rollbackIndexFromHeader(header(BigInteger.valueOf(1234))))
        // uint64 max must survive as a positive value, not wrap negative.
        assertEquals(
            BigInteger.TWO.pow(64).subtract(BigInteger.ONE),
            AvbFooterProbe.rollbackIndexFromHeader(header(BigInteger.TWO.pow(64).subtract(BigInteger.ONE))),
        )
        // SPL-timestamp shaped value.
        assertEquals(
            BigInteger.valueOf(1_704_067_200L),
            AvbFooterProbe.rollbackIndexFromHeader(header(BigInteger.valueOf(1_704_067_200L))),
        )
    }

    @Test
    fun rollbackIndexFromHeader_rejectsWrongMagicAndTruncation() {
        assertNull(AvbFooterProbe.rollbackIndexFromHeader(ByteArray(256)))
        assertNull(AvbFooterProbe.rollbackIndexFromHeader(ByteArray(120)))
    }
}
