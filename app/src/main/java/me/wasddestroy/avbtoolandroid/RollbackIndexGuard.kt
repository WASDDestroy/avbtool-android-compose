package me.wasddestroy.avbtoolandroid

import java.math.BigInteger
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * One flagged rollback_index occurrence, labelled for display. The label is
 * a partition name (profile flows) or a localized argument label (command
 * screen).
 */
data class RollbackIndexFinding(
    val label: String,
    val verdict: RollbackIndexVerdict,
)

sealed class RollbackIndexVerdict {
    /** Matches one of the two known schemes; safe to sign. */
    data object Ok : RollbackIndexVerdict()

    /** Matches neither scheme; likely a mis-input or a malicious config. */
    data class Unrecognized(val value: BigInteger) : RollbackIndexVerdict()

    /** Timestamp-shaped, but the decoded date lies in the future. */
    data class FutureDate(val value: BigInteger, val epochSecond: Long?) : RollbackIndexVerdict()

    /** Not a number avbtool can write: outside [0, 2^64-1]. */
    data class Invalid(val raw: String) : RollbackIndexVerdict()
}

/**
 * Classifies a rollback_index value against the two rollback-index schemes
 * observed in vendor images: a small counter incremented per release
 * ("natural" mode), or the UNIX timestamp in seconds of the image's SPL date.
 *
 * Every threshold is anchored to a time-invariant fact, never to "the current
 * year", so the classification cannot expire as SPL timestamps grow:
 *
 *  - [NATURAL_MAX]: even a release-per-week scheme stays far below 65536 over
 *    any device lifetime, and no SPL timestamp is anywhere near that small.
 *  - [TIMESTAMP_MIN]: AVB and Android SPLs did not exist before 2015; the
 *    earliest possible SPL timestamp is 2015-08-01 = 1438300800. The band
 *    between NATURAL_MAX and TIMESTAMP_MIN matches neither scheme and catches
 *    YYYYMMDD date literals (at most ~2.1e7), minute/day-based timestamps and
 *    dropped-digit typos.
 *  - [FUTURE_GRACE_SECONDS]: an SPL is never meaningfully dated more than a
 *    year ahead. The comparison runs against the device clock, so it moves
 *    with time instead of expiring. Any timestamp mis-scaled to milliseconds
 *    or carrying one extra digit lands far beyond now + grace.
 */
object RollbackIndexGuard {

    /** avbtool packs rollback_index as an unsigned 64-bit ('Q') header field. */
    val MAX_VALUE: BigInteger = BigInteger.TWO.pow(64).subtract(BigInteger.ONE)

    private val NATURAL_MAX = BigInteger.valueOf(65_536)
    private val TIMESTAMP_MIN = BigInteger.valueOf(1_400_000_000L)
    private const val FUTURE_GRACE_SECONDS = 366L * 24 * 60 * 60

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

    /** UTC date of a UNIX second count, used to show what a value decodes to. */
    fun formatEpochDate(epochSecond: Long): String = DATE_FORMAT.format(Instant.ofEpochSecond(epochSecond))

    /**
     * Mimics avbtool's parse_number (Python int(string, 0)): optional sign,
     * 0x/0o/0b prefixes, digit underscores. Deliberately lenient — anything
     * this parser rejects avbtool's argparse rejects too, and accepting a
     * Python-only form here merely skips a warning that would fail cleanly
     * at the command line anyway.
     */
    fun parse(raw: String): BigInteger? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        var negative = false
        if (s[0] == '+' || s[0] == '-') {
            negative = s[0] == '-'
            s = s.substring(1)
        }
        val radix: Int
        val digits: String
        when {
            s.length >= 2 && (s.startsWith("0x") || s.startsWith("0X")) -> { radix = 16; digits = s.substring(2) }
            s.length >= 2 && (s.startsWith("0o") || s.startsWith("0O")) -> { radix = 8; digits = s.substring(2) }
            s.length >= 2 && (s.startsWith("0b") || s.startsWith("0B")) -> { radix = 2; digits = s.substring(2) }
            else -> { radix = 10; digits = s }
        }
        val cleaned = digits.replace("_", "")
        if (cleaned.isEmpty()) return null
        val value = runCatching { BigInteger(cleaned, radix) }.getOrNull() ?: return null
        return if (negative) value.negate() else value
    }

    /**
     * Classifies an already-parsed value at UNIX time [nowSeconds]. Values
     * outside [0, 2^64-1] are invalid: avbtool's unsigned header field rejects
     * them at struct.pack time with a raw traceback.
     */
    fun classify(value: BigInteger, nowSeconds: Long): RollbackIndexVerdict {
        if (value.signum() < 0 || value > MAX_VALUE) return RollbackIndexVerdict.Invalid(value.toString())
        if (value <= NATURAL_MAX) return RollbackIndexVerdict.Ok
        if (value < TIMESTAMP_MIN) return RollbackIndexVerdict.Unrecognized(value)
        val epoch: Long? = if (value.bitLength() <= 63) value.longValueExact() else null
        if (epoch == null || epoch > nowSeconds + FUTURE_GRACE_SECONDS) {
            return RollbackIndexVerdict.FutureDate(value, epoch)
        }
        return RollbackIndexVerdict.Ok
    }

    /** Convenience for text-field input: parse then classify. */
    fun classifyText(raw: String, nowSeconds: Long): RollbackIndexVerdict {
        val value = parse(raw) ?: return RollbackIndexVerdict.Invalid(raw.trim())
        return classify(value, nowSeconds)
    }

    /**
     * Scans the given profile partitions for anomalous rollback indexes.
     * [Long] semantics mirror ProfileViewModel.parseProfile, so what this
     * flags is exactly what signing would emit.
     */
    fun scanSpecs(
        specs: List<ProfilePartitionSpec>,
        scope: Set<String> = specs.map { it.partition }.toSet(),
        nowSeconds: Long,
    ): List<RollbackIndexFinding> {
        return specs.filter { it.partition in scope && it.rollbackIndex != null }
            .mapNotNull { spec ->
                val index = spec.rollbackIndex ?: return@mapNotNull null
                val verdict = classify(BigInteger.valueOf(index), nowSeconds)
                if (verdict is RollbackIndexVerdict.Ok) null else RollbackIndexFinding(spec.partition, verdict)
            }
    }
}
