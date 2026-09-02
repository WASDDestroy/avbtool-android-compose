package me.wasddestroy.avbtoolandroid

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [ProfilePartitionCodec]. The unit-test compile classpath puts
 * android.jar (whose org.json stub lacks `similar()`) ahead of the Maven
 * json jar, so structural comparison uses the hand-rolled [jsonEquals]
 * helper — never string equality (key order / number formatting differ
 * between implementations).
 */
class ProfilePartitionCodecTest {

    private fun fixture(): JSONObject =
        JSONObject(
            javaClass.classLoader!!
                .getResourceAsStream("profile_tb710fu.json")!!
                .readBytes()
                .decodeToString(),
        )

    /**
     * Structural equality for two JSON values: same types, same keys, same
     * arrays in the same order. Only uses API present in both the android
     * org.json stub and the Maven implementation.
     */
    private fun jsonEquals(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject ->
            a.keys().asSequence().toSet() == b.keys().asSequence().toSet() &&
                a.keys().asSequence().all { key -> jsonEquals(a.opt(key), b.opt(key)) }
        a is org.json.JSONArray && b is org.json.JSONArray ->
            a.length() == b.length() &&
                (0 until a.length()).all { i -> jsonEquals(a.opt(i), b.opt(i)) }
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        a == null || b == null -> a == null && b == null
        else -> a == b
    }

    // ---- round-trip -------------------------------------------------------

    @Test
    fun `fixture partitions survive parse-encode-parse idempotently`() {
        val partitions = fixture().getJSONObject("partitions")
        for (name in partitions.keys()) {
            val original = partitions.getJSONObject(name)
            val once = ProfilePartitionCodec.parse(name, original)
            val encoded = ProfilePartitionCodec.encode(once)
            val twice = ProfilePartitionCodec.parse(name, encoded)

            assertEquals(name, once, twice)
            // The encoded entry must be structurally equal to the original:
            // fields absent in the original stay absent (sparse encode).
            assertTrue(
                "round-trip mismatch for partition $name",
                jsonEquals(encoded, original),
            )
        }
    }

    @Test
    fun `explicit zero rollback index is preserved as explicit zero`() {
        val entry = JSONObject()
            .put("image", "vbmeta.img")
            .put("descriptor", "vbmeta")
            .put("algorithm", "NONE")
            .put("rollback_index", 0)
        val spec = ProfilePartitionCodec.parse("vbmeta", entry)
        assertEquals(0L, spec.rollbackIndex)
        val encoded = ProfilePartitionCodec.encode(spec)
        assertTrue(encoded.has("rollback_index"))
        assertEquals(0L, encoded.getLong("rollback_index"))
    }

    @Test
    fun `absent rollback index stays absent and partition size zero is omitted`() {
        val entry = JSONObject()
            .put("image", "boot.img")
            .put("descriptor", "hash")
            .put("algorithm", "NONE")
            .put("partition_size", 0)
        val spec = ProfilePartitionCodec.parse("boot", entry)
        assertEquals(null, spec.rollbackIndex)
        assertEquals(null, spec.partitionSize)
        val encoded = ProfilePartitionCodec.encode(spec)
        assertTrue(!encoded.has("rollback_index"))
        assertTrue(!encoded.has("partition_size"))
        // hash_algorithm equals the sha256 parse default, so it is omitted;
        // the sign pipeline falls back to sha256 when the field is absent.
        assertTrue(!encoded.has("hash_algorithm"))
    }

    @Test
    fun `legacy props object form is parsed and normalized to pair arrays`() {
        val props = JSONObject().put("k1", "v1").put("k2", "v:2")
        val entry = JSONObject()
            .put("image", "boot.img")
            .put("descriptor", "hash")
            .put("algorithm", "NONE")
            .put("props", props)
        val spec = ProfilePartitionCodec.parse("boot", entry)
        assertEquals(listOf("k1" to "v1", "k2" to "v:2"), spec.props)
        val encoded = ProfilePartitionCodec.encode(spec)
        val arr = encoded.getJSONArray("props")
        assertEquals(2, arr.length())
        assertEquals("v:2", arr.getJSONArray(1).getString(1))
    }

    @Test
    fun `defaults equal to parse defaults are omitted on encode`() {
        val entry = JSONObject()
            .put("image", "system.img")
            .put("descriptor", "hashtree")
            .put("algorithm", "NONE")
        val spec = ProfilePartitionCodec.parse("system", entry)
        val encoded = ProfilePartitionCodec.encode(spec)
        assertTrue(!encoded.has("block_size"))
        assertTrue(!encoded.has("fec_num_roots"))
        assertTrue(!encoded.has("do_not_generate_fec"))
        // hash_algorithm equals the sha256 default and is omitted; a non-default
        // value must be written back explicitly.
        assertTrue(!encoded.has("hash_algorithm"))
        assertTrue(
            ProfilePartitionCodec.encode(spec.copy(hashAlgorithm = "sha512"))
                .has("hash_algorithm"),
        )
        // partition_name / image are always written.
        assertTrue(encoded.has("partition_name"))
    }

    // ---- validation -------------------------------------------------------

    @Test
    fun `hash partition without size or dynamic size is rejected`() {
        val spec = ProfilePartitionSpec(
            partition = "boot", image = "boot.img", descriptor = "hash",
            algorithm = "NONE", keyId = null, partitionName = "boot",
            partitionSize = null, rollbackIndex = null, salt = null, flags = null,
            props = emptyList(), setHashtreeDisabledFlag = false,
            includedPartitions = emptyList(), chainPartitions = emptyList(),
        )
        assertTrue(spec.let { ProfilePartitionCodec.validate(it) }
            .contains(ProfilePartitionCodec.ValidationCode.MISSING_PARTITION_SIZE))
    }

    @Test
    fun `hash partition with dynamic size passes even without size`() {
        val spec = ProfilePartitionSpec(
            partition = "boot", image = "boot.img", descriptor = "hash",
            algorithm = "NONE", keyId = null, partitionName = "boot",
            partitionSize = null, rollbackIndex = null, salt = null, flags = null,
            props = emptyList(), setHashtreeDisabledFlag = false,
            includedPartitions = emptyList(), chainPartitions = emptyList(),
            dynamicPartitionSize = true,
        )
        assertTrue(ProfilePartitionCodec.validate(spec).isEmpty())
    }

    @Test
    fun `hashtree partition without size is legal`() {
        val spec = ProfilePartitionSpec(
            partition = "system", image = "system.img", descriptor = "hashtree",
            algorithm = "NONE", keyId = null, partitionName = "system",
            partitionSize = null, rollbackIndex = null, salt = null, flags = null,
            props = emptyList(), setHashtreeDisabledFlag = false,
            includedPartitions = emptyList(), chainPartitions = emptyList(),
        )
        assertTrue(ProfilePartitionCodec.validate(spec).isEmpty())
    }

    @Test
    fun `partition size not multiple of 4096 is rejected`() {
        val base = ProfilePartitionSpec(
            partition = "boot", image = "boot.img", descriptor = "hash",
            algorithm = "NONE", keyId = null, partitionName = "boot",
            partitionSize = 4097, rollbackIndex = null, salt = null, flags = null,
            props = emptyList(), setHashtreeDisabledFlag = false,
            includedPartitions = emptyList(), chainPartitions = emptyList(),
        )
        assertTrue(ProfilePartitionCodec.validate(base)
            .contains(ProfilePartitionCodec.ValidationCode.PARTITION_SIZE_NOT_MULTIPLE))
        // ... but tolerated when the size is recomputed by avbtool.
        assertTrue(ProfilePartitionCodec.validate(base.copy(dynamicPartitionSize = true)).isEmpty())
    }

    @Test
    fun `chain partition validation covers malformed, slot range and conflicts`() {
        fun chainSpec(vararg chains: String, riLocation: Long? = null) =
            ProfilePartitionSpec(
                partition = "vbmeta", image = "vbmeta.img", descriptor = "vbmeta",
                algorithm = "NONE", keyId = null, partitionName = "vbmeta",
                partitionSize = null, rollbackIndex = null, salt = null, flags = null,
                props = emptyList(), setHashtreeDisabledFlag = false,
                includedPartitions = emptyList(), chainPartitions = chains.toList(),
                rollbackIndexLocation = riLocation,
            )

        assertTrue(
            ProfilePartitionCodec.validate(chainSpec("boot"))
                .contains(ProfilePartitionCodec.ValidationCode.MALFORMED_CHAIN_PARTITION),
        )
        assertTrue(
            ProfilePartitionCodec.validate(chainSpec("boot:0:k.bin"))
                .contains(ProfilePartitionCodec.ValidationCode.MALFORMED_CHAIN_PARTITION),
        )
        assertTrue(
            ProfilePartitionCodec.validate(chainSpec("boot:x:k.bin"))
                .contains(ProfilePartitionCodec.ValidationCode.MALFORMED_CHAIN_PARTITION),
        )
        assertTrue(
            ProfilePartitionCodec.validate(chainSpec("boot:1:a.bin", "recovery:1:b.bin"))
                .contains(ProfilePartitionCodec.ValidationCode.CHAIN_SLOT_CONFLICT),
        )
        assertTrue(
            ProfilePartitionCodec.validate(chainSpec("boot:3:k.bin", riLocation = 3L))
                .contains(ProfilePartitionCodec.ValidationCode.CHAIN_SLOT_CONFLICT),
        )
        assertTrue(ProfilePartitionCodec.validate(chainSpec("boot:3:k.bin", riLocation = 0L)).isEmpty())
    }

    @Test
    fun `algorithm NONE needs no key but others do`() {
        val spec = ProfilePartitionSpec(
            partition = "boot", image = "boot.img", descriptor = "hash",
            algorithm = "SHA256_RSA4096", keyId = null, partitionName = "boot",
            partitionSize = 4096, rollbackIndex = null, salt = null, flags = null,
            props = emptyList(), setHashtreeDisabledFlag = false,
            includedPartitions = emptyList(), chainPartitions = emptyList(),
        )
        assertTrue(ProfilePartitionCodec.validate(spec)
            .contains(ProfilePartitionCodec.ValidationCode.KEY_REQUIRED))
        assertTrue(ProfilePartitionCodec.validate(spec.copy(keyId = "release")).isEmpty())
        assertTrue(
            ProfilePartitionCodec.validate(spec.copy(algorithm = "NONE", keyId = null))
                .isEmpty(),
        )
    }

    @Test
    fun `salt must be even-length hex and empty prop keys are rejected`() {
        val spec = ProfilePartitionSpec(
            partition = "boot", image = "boot.img", descriptor = "hash",
            algorithm = "NONE", keyId = null, partitionName = "boot",
            partitionSize = 4096, rollbackIndex = null, salt = "xyz", flags = null,
            props = listOf("" to "value"), setHashtreeDisabledFlag = false,
            includedPartitions = emptyList(), chainPartitions = emptyList(),
        )
        val problems = ProfilePartitionCodec.validate(spec)
        assertTrue(problems.contains(ProfilePartitionCodec.ValidationCode.INVALID_SALT))
        assertTrue(problems.contains(ProfilePartitionCodec.ValidationCode.MALFORMED_PROP))
        // Even-length hex passes.
        assertTrue(
            ProfilePartitionCodec.validate(spec.copy(salt = "a1b2c3d4e5f6"))
                .contains(ProfilePartitionCodec.ValidationCode.INVALID_SALT)
                .not(),
        )
        // Odd-length hex fails too.
        assertTrue(
            ProfilePartitionCodec.validate(spec.copy(salt = "abc"))
                .contains(ProfilePartitionCodec.ValidationCode.INVALID_SALT),
        )
    }

    @Test
    fun `fec roots range and block size power of two`() {
        val spec = ProfilePartitionSpec(
            partition = "system", image = "system.img", descriptor = "hashtree",
            algorithm = "NONE", keyId = null, partitionName = "system",
            partitionSize = null, rollbackIndex = null, salt = null, flags = null,
            props = emptyList(), setHashtreeDisabledFlag = false,
            includedPartitions = emptyList(), chainPartitions = emptyList(),
            fecNumRoots = 255,
        )
        assertTrue(ProfilePartitionCodec.validate(spec)
            .contains(ProfilePartitionCodec.ValidationCode.FEC_NUM_ROOTS_OUT_OF_RANGE))
        assertTrue(
            ProfilePartitionCodec.validate(spec.copy(fecNumRoots = 1))
                .isEmpty(),
        )
        assertTrue(
            ProfilePartitionCodec.validate(spec.copy(fecNumRoots = 2, blockSize = 1000))
                .contains(ProfilePartitionCodec.ValidationCode.INVALID_BLOCK_SIZE),
        )
        assertTrue(
            ProfilePartitionCodec.validate(spec.copy(fecNumRoots = 2, blockSize = 512))
                .isEmpty(),
        )
    }

    @Test
    fun `negative rollback index is rejected`() {
        val spec = ProfilePartitionSpec(
            partition = "boot", image = "boot.img", descriptor = "hash",
            algorithm = "NONE", keyId = null, partitionName = "boot",
            partitionSize = 4096, rollbackIndex = -1L, salt = null, flags = null,
            props = emptyList(), setHashtreeDisabledFlag = false,
            includedPartitions = emptyList(), chainPartitions = emptyList(),
        )
        assertTrue(ProfilePartitionCodec.validate(spec)
            .contains(ProfilePartitionCodec.ValidationCode.NEGATIVE_ROLLBACK_INDEX))
    }
}
