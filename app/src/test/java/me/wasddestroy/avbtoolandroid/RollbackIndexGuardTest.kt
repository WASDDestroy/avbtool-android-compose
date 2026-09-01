package me.wasddestroy.avbtoolandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class RollbackIndexGuardTest {

    // 2025-06-15T15:46:40Z; all classification tests pin the clock so they
    // never depend on the real current date.
    private val now = 1_750_000_000L
    private val graceEnd = now + 366L * 24 * 60 * 60

    private fun classify(value: Long) = RollbackIndexGuard.classify(BigInteger.valueOf(value), now)

    private fun classify(value: String) = RollbackIndexGuard.classifyText(value, now)

    @Test
    fun parse_mimics_int_base_0() {
        assertEquals(BigInteger("123"), RollbackIndexGuard.parse("123"))
        assertEquals(BigInteger("255"), RollbackIndexGuard.parse("0xff"))
        assertEquals(BigInteger("255"), RollbackIndexGuard.parse("0XFF"))
        assertEquals(BigInteger("8"), RollbackIndexGuard.parse("0o10"))
        assertEquals(BigInteger("5"), RollbackIndexGuard.parse("0b101"))
        assertEquals(BigInteger("1000"), RollbackIndexGuard.parse("1_000"))
        assertEquals(BigInteger("-7"), RollbackIndexGuard.parse("-7"))
        assertEquals(BigInteger("7"), RollbackIndexGuard.parse("+7"))
        assertEquals(BigInteger("1704067200"), RollbackIndexGuard.parse(" 1704067200 "))
        assertNull(RollbackIndexGuard.parse(""))
        assertNull(RollbackIndexGuard.parse("abc"))
        assertNull(RollbackIndexGuard.parse("0x"))
        assertNull(RollbackIndexGuard.parse("12.5"))
    }

    @Test
    fun naturalMode_bandIsAccepted() {
        assertEquals(RollbackIndexVerdict.Ok, classify(0L))
        assertEquals(RollbackIndexVerdict.Ok, classify(1L))
        assertEquals(RollbackIndexVerdict.Ok, classify(300L))
        assertEquals(RollbackIndexVerdict.Ok, classify(65_536L))
    }

    @Test
    fun deadZone_matchesNeitherScheme() {
        assertEquals(RollbackIndexVerdict.Unrecognized::class, classify(65_537L)::class)
        assertEquals(RollbackIndexVerdict.Unrecognized::class, classify(1_000_000L)::class)
        // A YYYYMMDD date literal typed into the field: ~2e7 seconds.
        assertEquals(RollbackIndexVerdict.Unrecognized::class, classify(20_240_305L)::class)
        assertEquals(RollbackIndexVerdict.Unrecognized::class, classify(1_399_999_999L)::class)
    }

    @Test
    fun splTimestamps_inPastAreAccepted() {
        // First Android SPL, 2015-08-01.
        assertEquals(RollbackIndexVerdict.Ok, classify(1_438_300_800L))
        // A next-month SPL.
        assertEquals(RollbackIndexVerdict.Ok, classify(graceEnd))
        assertEquals(RollbackIndexVerdict.Ok, classify(1_750_000_000L))
    }

    @Test
    fun futureTimestamps_areFlagged() {
        val oneSecondPastGrace = BigInteger.valueOf(graceEnd + 1)
        val verdict = RollbackIndexGuard.classify(oneSecondPastGrace, now)
        assertTrue(verdict is RollbackIndexVerdict.FutureDate)
        assertEquals(graceEnd + 1, (verdict as RollbackIndexVerdict.FutureDate).epochSecond)
    }

    @Test
    fun millisecondTimestamps_areFlagged() {
        // 2024-01-01 in milliseconds typed as if seconds.
        val verdict = classify(1_704_067_200_000L)
        assertTrue(verdict is RollbackIndexVerdict.FutureDate)
    }

    @Test
    fun extraDigitTypos_areFlagged() {
        assertTrue(classify(17_040_672_000L) is RollbackIndexVerdict.FutureDate)
    }

    @Test
    fun valuesBeyondLong_areFutureWithoutDecodableDate() {
        val twoPow63 = BigInteger.TWO.pow(63)
        val verdict = RollbackIndexGuard.classify(twoPow63, now)
        assertTrue(verdict is RollbackIndexVerdict.FutureDate)
        assertNull((verdict as RollbackIndexVerdict.FutureDate).epochSecond)

        val maxUint64 = RollbackIndexGuard.MAX_VALUE
        val verdict2 = RollbackIndexGuard.classify(maxUint64, now)
        assertTrue(verdict2 is RollbackIndexVerdict.FutureDate)
        assertNull((verdict2 as RollbackIndexVerdict.FutureDate).epochSecond)
    }

    @Test
    fun outOfUint64Range_isInvalid() {
        assertTrue(classify(-1L) is RollbackIndexVerdict.Invalid)
        assertTrue(classify("-5") is RollbackIndexVerdict.Invalid)
        assertTrue(
            RollbackIndexGuard.classify(RollbackIndexGuard.MAX_VALUE.add(BigInteger.ONE), now)
                is RollbackIndexVerdict.Invalid,
        )
    }

    @Test
    fun scanSpecs_flagsOnlyScopedNonNullFindings() {
        val specs = listOf(
            spec("boot", rollbackIndex = 0L),
            spec("system", rollbackIndex = null),
            spec("vendor", rollbackIndex = 1_704_067_200_000L),
            spec("vbmeta", rollbackIndex = 123L),
        )
        val findings = RollbackIndexGuard.scanSpecs(specs, nowSeconds = now)
        assertEquals(listOf("vendor"), findings.map { it.label })

        // Out-of-scope partitions are not flagged even when anomalous.
        val scoped = RollbackIndexGuard.scanSpecs(specs, scope = setOf("boot", "system"), nowSeconds = now)
        assertTrue(scoped.isEmpty())
    }

    private fun spec(partition: String, rollbackIndex: Long?) = ProfilePartitionSpec(
        partition = partition,
        image = "$partition.img",
        descriptor = "hash",
        algorithm = "NONE",
        keyId = null,
        partitionName = partition,
        partitionSize = 4096L,
        rollbackIndex = rollbackIndex,
        salt = null,
        flags = 0L,
        props = emptyList(),
        setHashtreeDisabledFlag = false,
        includedPartitions = emptyList(),
        chainPartitions = emptyList(),
    )
}
