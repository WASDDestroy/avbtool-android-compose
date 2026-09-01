package me.wasddestroy.avbtoolandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignScopePlannerTest {

    private fun hashSpec(partition: String, image: String = "$partition.img") =
        ProfilePartitionSpec(
            partition = partition,
            image = image,
            descriptor = "hash",
            algorithm = "SHA256_RSA4096",
            keyId = "default",
            partitionName = partition,
            partitionSize = 4096L,
            rollbackIndex = 0L,
            salt = null,
            flags = 0L,
            props = emptyList(),
            setHashtreeDisabledFlag = false,
            includedPartitions = emptyList(),
            chainPartitions = emptyList(),
        )

    private fun vbmetaSpec(
        partition: String = "vbmeta",
        included: List<String> = emptyList(),
        includeDesc: List<String> = emptyList(),
        rootfs: String? = null,
    ) = ProfilePartitionSpec(
        partition = partition,
        image = "vbmeta.img",
        descriptor = "vbmeta",
        algorithm = "SHA256_RSA4096",
        keyId = "default",
        partitionName = partition,
        partitionSize = null,
        rollbackIndex = 0L,
        salt = null,
        flags = 0L,
        props = emptyList(),
        setHashtreeDisabledFlag = false,
        includedPartitions = included,
        chainPartitions = emptyList(),
        includeDescriptorsFromImage = includeDesc,
        setupRootfsFromKernel = rootfs,
    )

    private val present = mutableSetOf("boot", "dtbo", "init_boot")

    private val imagePresent: (String) -> Boolean = { it in present }

    @Test
    fun footerWithoutImage_inScope_isInfeasible() {
        present.remove("boot")
        val specs = listOf(hashSpec("boot"))
        val result = SignScopePlanner.feasibility(specs, setOf("boot"), imagePresent)
        assertFalse(result.getValue("boot").feasible)
        assertEquals(setOf("boot"), result.getValue("boot").missingDependencies)
    }

    @Test
    fun footerWithoutImage_rowDisabledEvenOutOfScope() {
        present.remove("boot")
        val specs = listOf(hashSpec("boot"), hashSpec("dtbo"))
        val result = SignScopePlanner.feasibility(specs, setOf("dtbo"), imagePresent)
        // Row enabled state means "could it succeed if checked": boot has no
        // image, so its row stays disabled regardless of the scope.
        assertFalse(result.getValue("boot").feasible)
        assertEquals(setOf("boot"), result.getValue("boot").missingDependencies)
        assertTrue(result.getValue("dtbo").feasible)
    }

    @Test
    fun vbmetaDependencyOutsideScope_isInfeasible() {
        val specs = listOf(hashSpec("dtbo"), hashSpec("init_boot"), vbmetaSpec(included = listOf("dtbo", "init_boot")))
        val result = SignScopePlanner.feasibility(specs, setOf("vbmeta", "dtbo"), imagePresent)
        assertFalse(result.getValue("vbmeta").feasible)
        assertEquals(setOf("init_boot"), result.getValue("vbmeta").missingDependencies)
    }

    @Test
    fun vbmetaWithAllDependenciesInScope_isFeasible() {
        val specs = listOf(hashSpec("dtbo"), hashSpec("init_boot"), vbmetaSpec(included = listOf("dtbo", "init_boot")))
        val result = SignScopePlanner.feasibility(
            specs,
            setOf("vbmeta", "dtbo", "init_boot"),
            imagePresent,
        )
        assertTrue(result.getValue("vbmeta").feasible)
    }

    @Test
    fun includeDescriptorsFromImageEntry_mapsToPartitionDependency() {
        val specs = listOf(hashSpec("boot"), vbmetaSpec(includeDesc = listOf("boot.img")))
        val result = SignScopePlanner.feasibility(specs, setOf("vbmeta"), imagePresent)
        assertFalse(result.getValue("vbmeta").feasible)
        assertEquals(setOf("boot"), result.getValue("vbmeta").missingDependencies)
    }

    @Test
    fun setupRootfsFromKernelEntry_mapsToPartitionDependency() {
        val specs = listOf(hashSpec("boot"), vbmetaSpec(rootfs = "boot.img"))
        val result = SignScopePlanner.feasibility(specs, setOf("vbmeta"), imagePresent)
        assertFalse(result.getValue("vbmeta").feasible)
        assertEquals(setOf("boot"), result.getValue("vbmeta").missingDependencies)
    }

    @Test
    fun profileLooseFileReference_isNotAnImageDependency() {
        // References that resolve inside the profile folder (not a partition
        // image name) impose no dependency.
        val specs = listOf(vbmetaSpec(includeDesc = listOf("certs/pkmd.bin")))
        val result = SignScopePlanner.feasibility(specs, setOf("vbmeta"), imagePresent)
        assertTrue(result.getValue("vbmeta").feasible)
    }

    @Test
    fun dependencyChainThroughVbmeta_isTransitive() {
        // boot depends on nothing; vbmeta1 needs boot; vbmeta2 needs vbmeta1.
        val v1 = vbmetaSpec(partition = "vbmeta1", included = listOf("boot"))
        val v2 = vbmetaSpec(partition = "vbmeta2", included = listOf("boot")).let {
            // make vbmeta2 include vbmeta1's output image
            it.copy(includeDescriptorsFromImage = listOf("vbmeta.img"))
        }
        val specs = listOf(hashSpec("boot"), v1, v2)
        val result = SignScopePlanner.feasibility(
            specs,
            setOf("vbmeta2", "vbmeta1"),
            imagePresent,
        )
        // vbmeta2 → vbmeta1 → boot; boot not in scope.
        assertFalse(result.getValue("vbmeta2").feasible)
        assertEquals(setOf("boot"), result.getValue("vbmeta2").missingDependencies)
    }

    @Test
    fun dependencyCycle_doesNotHang() {
        val a = vbmetaSpec(partition = "a", included = listOf("b"))
        val b = vbmetaSpec(partition = "b", included = listOf("a"))
        val specs = listOf(a, b)
        val result = SignScopePlanner.feasibility(specs, setOf("a", "b"), imagePresent)
        assertFalse(result.getValue("a").feasible || result.getValue("b").feasible)
    }

    @Test
    fun emptyScope_vbmetaRowsAllInfeasibleFooterRowsStandalone() {
        val specs = listOf(hashSpec("boot"), vbmetaSpec(included = listOf("boot")))
        val result = SignScopePlanner.feasibility(specs, emptySet(), imagePresent)
        // Footer row could still be signed alone; vbmeta depends on boot
        // which is outside the (empty) scope.
        assertTrue(result.getValue("boot").feasible)
        assertFalse(result.getValue("vbmeta").feasible)
    }
}

class SignScopePlannerPruneTest {

    private fun hashSpec(partition: String) = ProfilePartitionSpec(
        partition = partition,
        image = "$partition.img",
        descriptor = "hash",
        algorithm = "SHA256_RSA4096",
        keyId = "default",
        partitionName = partition,
        partitionSize = 4096L,
        rollbackIndex = 0L,
        salt = null,
        flags = 0L,
        props = emptyList(),
        setHashtreeDisabledFlag = false,
        includedPartitions = emptyList(),
        chainPartitions = emptyList(),
    )

    private fun vbmetaSpec(included: List<String>) = ProfilePartitionSpec(
        partition = "vbmeta",
        image = "vbmeta.img",
        descriptor = "vbmeta",
        algorithm = "SHA256_RSA4096",
        keyId = "default",
        partitionName = "vbmeta",
        partitionSize = null,
        rollbackIndex = 0L,
        salt = null,
        flags = 0L,
        props = emptyList(),
        setHashtreeDisabledFlag = false,
        includedPartitions = included,
        chainPartitions = emptyList(),
    )

    private val specs = listOf(hashSpec("boot"), hashSpec("dtbo"), hashSpec("init_boot"), vbmetaSpec(listOf("dtbo", "init_boot")))
    private val imagePresent: (String) -> Boolean = { it != "init_boot" }

    @Test
    fun defaultWithoutPrune_leaksInfeasiblePartitions() {
        // The pre-fix dialog defaulted to every footer partition; the prune
        // must drop partitions without a readable image.
        val raw = specs.filter { it.descriptor != "vbmeta" }.map { it.partition }.toSet()
        val pruned = SignScopePlanner.prune(specs, raw, imagePresent)
        assertEquals(setOf("boot", "dtbo"), pruned)
    }

    @Test
    fun uncheckingDependency_cascadesToDependents() {
        val pruned = SignScopePlanner.prune(
            specs,
            setOf("boot", "dtbo", "vbmeta"),
            imagePresent,
        )
        // vbmeta stays only while both of its dependencies are present.
        assertEquals(setOf("boot", "dtbo"), pruned)

        val afterDrop = SignScopePlanner.prune(
            specs,
            setOf("boot", "dtbo", "init_boot", "vbmeta"),
            imagePresent,
        )
        // vbmeta is feasible with everything checked despite init_boot's
        // missing file? No: its image cannot be opened, so it is pruned.
        assertEquals(setOf("boot", "dtbo"), afterDrop)
    }

    @Test
    fun consistentScope_survivesPrune() {
        val pruned = SignScopePlanner.prune(
            specs,
            setOf("boot", "dtbo"),
            imagePresent,
        )
        assertEquals(setOf("boot", "dtbo"), pruned)
    }
}
