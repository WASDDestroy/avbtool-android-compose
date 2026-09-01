package me.wasddestroy.avbtoolandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for the footer-vs-vbmeta classification: hash/hashtree
 * footers embed exactly one descriptor naming the partition (file stem,
 * without extension); vbmeta images embed several descriptors or name a
 * partition other than themselves.
 */
class InfoImageParserTest {

    private fun hashFooterOutput(partition: String = "boot", algorithm: String = "SHA256_RSA4096") =
        """
        Footer version:           1.0
        Image size:               16594432 bytes
        Original image size:      16588800 bytes
        VBMeta offset:            16594432
        VBMeta size:              2688 bytes
        --
        Minimum libavb version:   1.0
        Header Block:             256 bytes
        Authentication Block:     320 bytes
        Auxiliary Block:          1024 bytes
        Public key (sha1):        abcdef0123456789abcdef0123456789abcdef01
        Algorithm:                $algorithm
        Rollback Index:           5
        Flags:                    0
        Rollback Index Location:  0
        Release String:           'avbtool 1.2.0'
        Descriptors:
            Hash descriptor:
              Image Size:            16588800 bytes
              Hash Algorithm:        sha256
              Partition Name:        $partition
              Salt:                  aabbccdd
              Digest:                0123456789abcdef
              Flags:                 0
        """.trimIndent() + "\n"

    private fun hashtreeFooterOutput(partition: String = "system") =
        """
        Footer version:           1.0
        Image size:               19877888 bytes
        Original image size:      16588800 bytes
        VBMeta offset:            19877888
        VBMeta size:              2688 bytes
        --
        Minimum libavb version:   1.0
        Header Block:             256 bytes
        Authentication Block:     320 bytes
        Auxiliary Block:          1024 bytes
        Algorithm:                NONE
        Rollback Index:           7
        Flags:                    3
        Rollback Index Location:  1
        Release String:           'avbtool 1.2.0'
        Descriptors:
            Hashtree descriptor:
              Version of dm-verity:  1
              Image Size:            16588800 bytes
              Tree Offset:           16588800
              Tree Size:             1064960 bytes
              Data Block Size:       4096 bytes
              Hash Block Size:       4096 bytes
              FEC num roots:         2
              FEC offset:            17653760
              FEC size:              1052672 bytes
              Hash Algorithm:        sha256
              Partition Name:        $partition
              Salt:                  eeff0011
              Root Digest:           fedcba9876543210
              Flags:                 0
        """.trimIndent() + "\n"

    private fun vbmetaOutput() =
        """
        Minimum libavb version:   1.0
        Header Block:             256 bytes
        Authentication Block:     576 bytes
        Auxiliary Block:          4096 bytes
        Algorithm:                SHA256_RSA4096
        Rollback Index:           12
        Flags:                    1
        Rollback Index Location:  0
        Release String:           'avbtool 1.2.0'
        Descriptors:
            Chain Partition descriptor:
              Partition Name:          boot
              Rollback Index Location: 2
              Public key (sha1):       00112233445566778899aabbccddeeff00112233
              Flags:                   0
            Hash descriptor:
              Image Size:            16588800 bytes
              Hash Algorithm:        sha256
              Partition Name:        dtbo
              Salt:                  aabbccdd
              Digest:                0123456789abcdef
              Flags:                 0
        """.trimIndent() + "\n"

    @Test
    fun hashFooterWithImageFileName_isHash() {
        val inspection = InfoImageParser.inspect("boot.img", hashFooterOutput())
        assertEquals("hash", inspection.descriptor)
        assertEquals("boot", inspection.partitionName)
        assertEquals("SHA256_RSA4096", inspection.algorithm)
        assertEquals(5L, inspection.rollbackIndex)
        assertEquals("sha256", inspection.hashAlgorithm)
        assertEquals("aabbccdd", inspection.salt)
        // Total image size incl. footer — the partition size to reuse on re-sign.
        assertEquals(16594432L, inspection.partitionSize)
    }

    @Test
    fun hashFooterWithStem_isHash() {
        // Reference project passes the file stem instead of the full name.
        val inspection = InfoImageParser.inspect("boot", hashFooterOutput())
        assertEquals("hash", inspection.descriptor)
        assertEquals("boot", inspection.partitionName)
    }

    @Test
    fun hashtreeFooter_isHashtree() {
        val inspection = InfoImageParser.inspect("system.img", hashtreeFooterOutput())
        assertEquals("hashtree", inspection.descriptor)
        assertEquals("system", inspection.partitionName)
        assertEquals("NONE", inspection.algorithm)
        assertEquals(3L, inspection.flags)
        assertEquals(7L, inspection.rollbackIndex)
        assertEquals("sha256", inspection.hashAlgorithm)
        assertEquals(19877888L, inspection.partitionSize)
    }

    @Test
    fun vbmetaWithMultipleDescriptors_isVbmeta() {
        val inspection = InfoImageParser.inspect("vbmeta.img", vbmetaOutput())
        assertEquals("vbmeta", inspection.descriptor)
        assertNull(inspection.partitionName)
        assertEquals(12L, inspection.rollbackIndex)
        assertEquals(1L, inspection.flags)
        assertEquals(listOf("dtbo"), inspection.includedPartitions)
        assertEquals(listOf("boot:2:default.bin"), inspection.chainPartitions)
        assertNull(inspection.partitionSize)
    }

    @Test
    fun singleDescriptorNamingOtherPartition_isVbmeta() {
        // A vbmeta-style image whose single descriptor points elsewhere.
        val inspection = InfoImageParser.inspect(
            "vbmeta_custom.img",
            hashFooterOutput(partition = "boot"),
        )
        assertEquals("vbmeta", inspection.descriptor)
        assertNull(inspection.partitionName)
    }

    @Test
    fun propsAreParsedFromIndentedLines() {
        val output = hashFooterOutput() +
            "    Prop: com.android.build.boot.os_version -> '12345678'\n" +
            "    Prop: com.android.build.boot.security_patch -> '2026-01-05'\n"
        val parsed = InfoImageParser.parse(output)
        assertEquals(
            listOf(
                "com.android.build.boot.os_version" to "12345678",
                "com.android.build.boot.security_patch" to "2026-01-05",
            ),
            parsed.props,
        )
    }
}
