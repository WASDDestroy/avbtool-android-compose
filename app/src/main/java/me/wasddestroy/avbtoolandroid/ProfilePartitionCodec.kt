package me.wasddestroy.avbtoolandroid

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure-Kotlin codec for one v3 `PartitionConfig` entry of `profile.json`:
 * parse (JSON → [ProfilePartitionSpec]), encode ([ProfilePartitionSpec] → JSON)
 * and save-time validation. No Android framework dependencies beyond org.json,
 * so round-trip and validation tests run on the JVM.
 *
 * Encoding is sparse: fields equal to their parse default are omitted, with
 * three exceptions that carry "explicitness" — [ProfilePartitionSpec.rollbackIndex],
 * [ProfilePartitionSpec.flags] and [ProfilePartitionSpec.rollbackIndexLocation]
 * are written whenever non-null (including 0, matching parse's `has()` checks).
 * `partition_name` and `image` are always written (the generator emits both for
 * every partition). `hash_algorithm` is only written when it differs from the
 * sha256 default — the sign pipeline already falls back to sha256 when absent.
 */
object ProfilePartitionCodec {

    /** Reasons a [ProfilePartitionSpec] cannot be saved. */
    enum class ValidationCode {
        /** hash partition without partition_size and without dynamic_partition_size. */
        MISSING_PARTITION_SIZE,

        /** partition_size set but not a multiple of 4096. */
        PARTITION_SIZE_NOT_MULTIPLE,

        /** A chain entry is not PART:SLOT:KEY with an integer slot >= 1. */
        MALFORMED_CHAIN_PARTITION,

        /** Two chain entries share a slot, or a slot equals the partition's own rollback_index_location. */
        CHAIN_SLOT_CONFLICT,

        /** Algorithm needs a key but key_id is null/blank. */
        KEY_REQUIRED,

        /** salt is non-empty but not even-length hex. */
        INVALID_SALT,

        /** A props/prop_from_file entry has no colon. */
        MALFORMED_PROP,

        /** fec_num_roots outside 1..254. */
        FEC_NUM_ROOTS_OUT_OF_RANGE,

        /** rollback_index is negative. */
        NEGATIVE_ROLLBACK_INDEX,

        /** block_size is not a power of two >= 512. */
        INVALID_BLOCK_SIZE,
    }

    /**
     * Parses one partition entry. Mirrors ProfileViewModel.parseProfile field
     * semantics exactly — `has()` distinguishes explicit 0 from absent for
     * rollback_index / flags / rollback_index_location, size fields use `> 0`,
     * blank strings are treated as unset.
     */
    fun parse(name: String, obj: JSONObject): ProfilePartitionSpec {
        return ProfilePartitionSpec(
            partition = name,
            image = obj.getString("image"),
            descriptor = obj.getString("descriptor"),
            algorithm = obj.getString("algorithm"),
            keyId = obj.optString("key_id").takeIf { it.isNotBlank() },
            partitionName = obj.optString("partition_name", name),
            partitionSize = obj.optLong("partition_size").takeIf { it > 0 },
            rollbackIndex = obj.optLong("rollback_index").takeIf { obj.has("rollback_index") },
            salt = obj.optString("salt").takeIf { it.isNotBlank() },
            flags = obj.optLong("flags").takeIf { obj.has("flags") },
            props = parsePairs(obj.opt("props")),
            setHashtreeDisabledFlag = obj.optBoolean("set_hashtree_disabled_flag", false),
            includedPartitions = obj.optStringList("included_partitions"),
            chainPartitions = obj.optStringList("chain_partitions"),
            dynamicPartitionSize = obj.optBoolean("dynamic_partition_size", false),
            rollbackIndexLocation = obj.optLong("rollback_index_location")
                .takeIf { obj.has("rollback_index_location") },
            hashAlgorithm = obj.optString("hash_algorithm", "sha256").ifBlank { "sha256" },
            propFromFile = parsePairs(obj.opt("prop_from_file")),
            setVerificationDisabledFlag = obj.optBoolean("set_verification_disabled_flag", false),
            blockSize = obj.optLong("block_size", 4096),
            doNotGenerateFec = obj.optBoolean("do_not_generate_fec", false),
            fecNumRoots = obj.optLong("fec_num_roots", 2),
            noHashtree = obj.optBoolean("no_hashtree", false),
            checkAtMostOnce = obj.optBoolean("check_at_most_once", false),
            setupAsRootfsFromKernel = obj.optBoolean("setup_as_rootfs_from_kernel", false),
            includeDescriptorsFromImage = obj.optStringList("include_descriptors_from_image"),
            chainPartitionsDoNotUseAb = obj.optStringList("chain_partitions_do_not_use_ab"),
            kernelCmdlines = obj.optStringList("kernel_cmdlines"),
            setupRootfsFromKernel = obj.optString("setup_rootfs_from_kernel")
                .takeIf { it.isNotBlank() },
            paddingSize = obj.optLong("padding_size").takeIf { it > 0 },
            outputVbmetaImage = obj.optString("output_vbmeta_image").takeIf { it.isNotBlank() },
            calcMaxImageSize = obj.optBoolean("calc_max_image_size", false),
            doNotAppendVbmetaImage = obj.optBoolean("do_not_append_vbmeta_image", false),
            printRequiredLibavbVersion = obj.optBoolean("print_required_libavb_version", false),
            usePersistentDigest = obj.optBoolean("use_persistent_digest", false),
            doNotUseAb = obj.optBoolean("do_not_use_ab", false),
            signingHelper = obj.optString("signing_helper").takeIf { it.isNotBlank() },
            signingHelperWithFiles = obj.optString("signing_helper_with_files")
                .takeIf { it.isNotBlank() },
            publicKeyMetadata = obj.optString("public_key_metadata").takeIf { it.isNotBlank() },
            appendToReleaseString = obj.optString("append_to_release_string")
                .takeIf { it.isNotBlank() },
        )
    }

    /**
     * Encodes [spec] into a v3 partition entry. Sparse: defaults are omitted
     * (see class KDoc for the explicit-null exceptions). Output shape is the
     * canonical `[[k,v],...]` for props, even when the input used one of the
     * legacy forms parse accepts.
     */
    fun encode(spec: ProfilePartitionSpec): JSONObject {
        val entry = JSONObject().apply {
            put("image", spec.image)
            put("descriptor", spec.descriptor)
            put("algorithm", spec.algorithm)
            spec.keyId?.let { put("key_id", it) }
            // Always written: the generator emits it for every partition and
            // round-tripping a profile that spells it out explicitly (even
            // when it equals the JSON key) must not lose the field.
            put("partition_name", spec.partitionName)
            spec.partitionSize?.let { put("partition_size", it) }
            spec.rollbackIndex?.let { put("rollback_index", it) }
            spec.salt?.let { put("salt", it) }
            spec.flags?.let { put("flags", it) }
            if (spec.props.isNotEmpty()) put("props", encodePairs(spec.props))
            if (spec.setHashtreeDisabledFlag) put("set_hashtree_disabled_flag", true)
            if (spec.includedPartitions.isNotEmpty()) {
                put("included_partitions", JSONArray(spec.includedPartitions))
            }
            if (spec.chainPartitions.isNotEmpty()) {
                put("chain_partitions", JSONArray(spec.chainPartitions))
            }
            if (spec.dynamicPartitionSize) put("dynamic_partition_size", true)
            spec.rollbackIndexLocation?.let { put("rollback_index_location", it) }
            // buildAvbArgs always passes --hash_algorithm (reading the parse
            // default sha256 when absent), so the JSON field is only needed
            // when it differs from that default — writing it unconditionally
            // would add a field to profiles that never had one.
            if (spec.descriptor != "vbmeta" && spec.hashAlgorithm != "sha256") {
                put("hash_algorithm", spec.hashAlgorithm)
            }
            if (spec.propFromFile.isNotEmpty()) put("prop_from_file", encodePairs(spec.propFromFile))
            if (spec.setVerificationDisabledFlag) put("set_verification_disabled_flag", true)
            if (spec.blockSize != 4096L) put("block_size", spec.blockSize)
            if (spec.doNotGenerateFec) put("do_not_generate_fec", true)
            if (spec.fecNumRoots != 2L) put("fec_num_roots", spec.fecNumRoots)
            if (spec.noHashtree) put("no_hashtree", true)
            if (spec.checkAtMostOnce) put("check_at_most_once", true)
            if (spec.setupAsRootfsFromKernel) put("setup_as_rootfs_from_kernel", true)
            if (spec.includeDescriptorsFromImage.isNotEmpty()) {
                put("include_descriptors_from_image", JSONArray(spec.includeDescriptorsFromImage))
            }
            if (spec.chainPartitionsDoNotUseAb.isNotEmpty()) {
                put("chain_partitions_do_not_use_ab", JSONArray(spec.chainPartitionsDoNotUseAb))
            }
            if (spec.kernelCmdlines.isNotEmpty()) {
                put("kernel_cmdlines", JSONArray(spec.kernelCmdlines))
            }
            spec.setupRootfsFromKernel?.let { put("setup_rootfs_from_kernel", it) }
            spec.paddingSize?.let { put("padding_size", it) }
            spec.outputVbmetaImage?.let { put("output_vbmeta_image", it) }
            if (spec.calcMaxImageSize) put("calc_max_image_size", true)
            if (spec.doNotAppendVbmetaImage) put("do_not_append_vbmeta_image", true)
            if (spec.printRequiredLibavbVersion) put("print_required_libavb_version", true)
            if (spec.usePersistentDigest) put("use_persistent_digest", true)
            if (spec.doNotUseAb) put("do_not_use_ab", true)
            spec.signingHelper?.let { put("signing_helper", it) }
            spec.signingHelperWithFiles?.let { put("signing_helper_with_files", it) }
            spec.publicKeyMetadata?.let { put("public_key_metadata", it) }
            spec.appendToReleaseString?.let { put("append_to_release_string", it) }
        }
        return entry
    }

    /**
     * Save-time validation aligned with vendored avbtool's hard errors plus
     * the app's domain conventions. Returns all violations so the edit dialog
     * can show them together.
     */
    fun validate(spec: ProfilePartitionSpec): List<ValidationCode> {
        val problems = mutableListOf<ValidationCode>()
        if (spec.descriptor == "hash") {
            if (spec.partitionSize == null && !spec.dynamicPartitionSize) {
                problems += ValidationCode.MISSING_PARTITION_SIZE
            }
            // With dynamic_partition_size avbtool recomputes (and rounds) the
            // size itself, so the stored value is never multiplicity-checked.
            val size = spec.partitionSize
            if (size != null && !spec.dynamicPartitionSize && size % 4096L != 0L) {
                problems += ValidationCode.PARTITION_SIZE_NOT_MULTIPLE
            }
        } else if (spec.descriptor == "hashtree") {
            // Null means "append to end" — legal for hashtree.
            spec.partitionSize?.let { size ->
                if (size % 4096L != 0L) problems += ValidationCode.PARTITION_SIZE_NOT_MULTIPLE
            }
        }
        if (spec.rollbackIndex != null && spec.rollbackIndex < 0L) {
            problems += ValidationCode.NEGATIVE_ROLLBACK_INDEX
        }
        spec.salt?.let { salt ->
            if (salt.length % 2 != 0 || salt.any { it.lowercaseChar() !in "0123456789abcdef" }) {
                problems += ValidationCode.INVALID_SALT
            }
        }
        (spec.props + spec.propFromFile).forEach { (key, _) ->
            if (key.isBlank()) problems += ValidationCode.MALFORMED_PROP
        }
        if (spec.descriptor == "hashtree" && spec.fecNumRoots !in 1L..254L) {
            problems += ValidationCode.FEC_NUM_ROOTS_OUT_OF_RANGE
        }
        if (spec.descriptor == "hashtree" &&
            (spec.blockSize < 512L || spec.blockSize and (spec.blockSize - 1) != 0L)
        ) {
            problems += ValidationCode.INVALID_BLOCK_SIZE
        }
        val chainSlots = (spec.chainPartitions + spec.chainPartitionsDoNotUseAb)
            .map { entry ->
                val parts = entry.split(':')
                if (parts.size != 3 || parts[1].toLongOrNull() == null || parts[1].toLong() < 1L) {
                    problems += ValidationCode.MALFORMED_CHAIN_PARTITION
                    null
                } else {
                    parts[1].toLong()
                }
            }
            .filterNotNull()
        if (chainSlots.size != chainSlots.distinct().size) {
            problems += ValidationCode.CHAIN_SLOT_CONFLICT
        }
        spec.rollbackIndexLocation?.let { loc ->
            if (loc != 0L && loc in chainSlots) {
                problems += ValidationCode.CHAIN_SLOT_CONFLICT
            }
        }
        if (spec.algorithm != "NONE" && spec.keyId.isNullOrBlank()) {
            problems += ValidationCode.KEY_REQUIRED
        }
        return problems
    }

    /**
     * Parses props/prop_from_file. Accepts the canonical [[k, v], ...] form,
     * a flat [k, v] pair, or a {k: v} object (all three are produced by the
     * config generator or its legacy codecs). prop entries are split on the
     * first colon only; the value may itself contain colons.
     */
    private fun parsePairs(value: Any?): List<Pair<String, String>> {
        return when (value) {
            is JSONObject -> value.keys().asSequence().map { k -> k to value.optString(k) }.toList()
            is JSONArray -> {
                // A flat pair has two scalar elements, no nested array.
                if (value.length() == 2 && value.optJSONArray(0) == null) {
                    listOf(value.optString(0) to value.optString(1))
                } else {
                    (0 until value.length()).mapNotNull { i ->
                        val pair = value.optJSONArray(i) ?: return@mapNotNull null
                        if (pair.length() >= 2) pair.optString(0) to pair.optString(1) else null
                    }
                }
            }
            else -> emptyList()
        }
    }

    private fun encodePairs(pairs: List<Pair<String, String>>): JSONArray {
        return JSONArray().apply {
            pairs.forEach { (k, v) ->
                put(JSONArray().put(k).put(v))
            }
        }
    }

    private fun JSONObject.optStringList(name: String): List<String> {
        return optJSONArray(name)?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        } ?: emptyList()
    }
}
