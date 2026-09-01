package me.wasddestroy.avbtoolandroid

/**
 * Pure helper for the sign-scope dialog: extracts image dependencies from
 * partition specs and computes each partition's feasibility for a given
 * scope. No Android or I/O dependencies — the caller supplies the result of
 * the URI probes.
 *
 * Dependency sources mirror [ProfileViewModel.resolveImageFile] semantics:
 * an entry counts as an image dependency only when it names another
 * partition's image; references resolvable inside the profile folder
 * (keys, loose files) do not. Chain partitions reference key files only.
 */
object SignScopePlanner {

    data class Feasibility(
        /**
         * "Could this partition be checked and succeed right now?" — drives
         * the dialog row's enabled state, independent of the current check.
         */
        val feasible: Boolean,
        /** Populated when infeasible: partitions whose images are missing/unavailable. */
        val missingDependencies: Set<String> = emptySet(),
    )

    /**
     * Dependency-aware feasibility for the scope dialog.
     *
     * A partition is feasible when checking it would succeed: footer
     * partitions need their image present now; vbmeta partitions need every
     * image dependency inside the scope and feasible (recursively, with
     * cycle protection).
     *
     * @param specs all partitions of the profile (any order)
     * @param scope partitions the user checked for this run
     * @param imagePresent predicate answering "can partition X's image be
     *   opened right now" (false when nothing was picked, the file is gone,
     *   or the SAF grant is dead)
     */
    fun feasibility(
        specs: List<ProfilePartitionSpec>,
        scope: Set<String>,
        imagePresent: (String) -> Boolean,
    ): Map<String, Feasibility> {
        val imageByPartition = specs.associate { it.partition to it.image }
        fun owningPartition(entry: String): String? {
            imageByPartition.forEach { (partition, image) ->
                if (image == entry) return partition
            }
            return null
        }

        val deps = specs.associate { spec ->
            val direct = mutableSetOf<String>()
            if (spec.descriptor == "vbmeta") {
                direct += spec.includedPartitions
            }
            (spec.includeDescriptorsFromImage + listOfNotNull(spec.setupRootfsFromKernel))
                .forEach { entry -> owningPartition(entry)?.let { direct += it } }
            direct.remove(spec.partition)
            spec.partition to direct
        }

        val result = mutableMapOf<String, Feasibility>()
        fun evaluate(partition: String, visiting: MutableSet<String>): Feasibility {
            result[partition]?.let { return it }
            if (!visiting.add(partition)) {
                // Cycle: cannot be satisfied; the sign log makes it obvious.
                return Feasibility(false, setOf(partition))
            }
            val spec = specs.first { it.partition == partition }
            val missing = mutableSetOf<String>()
            if (spec.descriptor != "vbmeta" && !imagePresent(partition)) {
                missing += partition
            }
            for (dep in deps.getValue(partition)) {
                if (dep !in scope) {
                    // Strict rule: a dependency outside the scope makes the
                    // dependent infeasible, even when its image is present.
                    missing += dep
                    continue
                }
                val depResult = evaluate(dep, visiting)
                if (!depResult.feasible) {
                    missing += depResult.missingDependencies
                }
            }
            visiting.remove(partition)
            val f = Feasibility(missing.isEmpty(), missing)
            result[partition] = f
            return f
        }
        specs.forEach { evaluate(it.partition, mutableSetOf()) }
        return result
    }

    /**
     * Reduces [candidate] to its self-consistent core: repeatedly drops
     * partitions that are infeasible within the current set (missing image,
     * or dependencies outside the set) until the set stabilizes. The dialog
     * runs every default and toggle through this so the scope can never
     * contain a partition that would fail — including the disabled-row trap
     * where an infeasible partition starts checked and cannot be unchecked.
     */
    fun prune(
        specs: List<ProfilePartitionSpec>,
        candidate: Set<String>,
        imagePresent: (String) -> Boolean,
    ): Set<String> {
        var current = candidate
        while (true) {
            val f = feasibility(specs, current, imagePresent)
            val next = current.filter { f[it]?.feasible == true }.toSet()
            if (next.size == current.size) return current
            current = next
        }
    }
}
