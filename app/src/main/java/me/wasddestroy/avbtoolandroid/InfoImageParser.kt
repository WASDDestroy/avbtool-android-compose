package me.wasddestroy.avbtoolandroid

/**
 * Parses the stdout of `avbtool info_image` into a profile partition entry.
 *
 * Port of the reference project's output parser (avbpowertool2
 * `infrastructure/avbtool/output_parser.py`), but classification differs
 * deliberately: type is decided by footer presence, not by comparing the
 * descriptor's partition name with the file name. A signed partition image
 * always carries an AvbFooter and its own Hash/Hashtree descriptor; a
 * vbmeta image is a bare blob whose descriptors (if any) name other
 * partitions. File-name heuristics break when the user stores a partition
 * image under an arbitrary name.
 */
object InfoImageParser {

    data class DescriptorBlock(
        val type: String,
        val fields: Map<String, String>,
    )

    data class ParsedInfoImage(
        val header: Map<String, String>,
        val descriptors: List<DescriptorBlock>,
        val props: List<Pair<String, String>>,
    )

    data class ImageInspection(
        /** "hash" / "hashtree" / "vbmeta" — the profile.json descriptor value. */
        val descriptor: String,
        val partitionName: String?,
        /** Signing algorithm from the vbmeta header, e.g. "NONE" / "SHA256_RSA4096". */
        val algorithm: String?,
        val rollbackIndex: Long?,
        val flags: Long?,
        val hashAlgorithm: String?,
        val salt: String?,
        val props: List<Pair<String, String>>,
        /** Partition names embedded in a vbmeta image (its own descriptors). */
        val includedPartitions: List<String>,
        /** "partition:rollback:keyfile.bin" entries built from chain descriptors. */
        val chainPartitions: List<String>,
        /**
         * Total image size in bytes ("Image size" footer line), i.e. the
         * partition size the image was signed for; null for bare vbmeta.
         */
        val partitionSize: Long?,
        /**
         * "Public key (sha1)" header line — lowercase hex digest of the
         * AVB public key the image was signed with; null when unsigned
         * (algorithm NONE prints no key line).
         */
        val publicKeySha1: String?,
    )

    fun parse(text: String): ParsedInfoImage {
        val header = mutableMapOf<String, String>()
        val descBlocks = mutableListOf<DescriptorBlock>()
        val props = mutableListOf<Pair<String, String>>()

        var currentType: String? = null
        var currentFields = mutableMapOf<String, String>()
        var inDescriptors = false

        for (rawLine in text.split("\n")) {
            if (rawLine.isBlank()) continue
            val indent = rawLine.length - rawLine.trimStart().length
            val stripped = rawLine.trim()

            if (indent == 0) {
                if (stripped.startsWith("Descriptors:")) {
                    inDescriptors = true
                    continue
                }
                if (!inDescriptors && ":" in stripped) {
                    val (k, v) = stripped.split(":", limit = 2)
                    header[k.trim()] = v.trim()
                }
            } else if (indent == 4 && inDescriptors) {
                if (currentType != null) {
                    descBlocks += DescriptorBlock(currentType!!, currentFields.toMap())
                    currentFields = mutableMapOf()
                }
                when {
                    stripped.startsWith("Prop:") -> {
                        val content = stripped.removePrefix("Prop:").trim()
                        val arrow = content.indexOf("->")
                        if (arrow >= 0) {
                            val k = content.substring(0, arrow).trim()
                            val v = content.substring(arrow + 2).trim().trim('\'')
                            props += k to v
                        }
                        currentType = null
                    }
                    stripped == "(none)" -> currentType = null
                    else -> currentType = detectDescriptorType(stripped)
                }
            } else if (indent == 6 && currentType != null && ":" in stripped) {
                val (k, v) = stripped.split(":", limit = 2)
                currentFields[k.trim()] = v.trim()
            }
        }
        if (currentType != null) {
            descBlocks += DescriptorBlock(currentType!!, currentFields.toMap())
        }
        return ParsedInfoImage(header, descBlocks, props)
    }

    fun inspect(imageFileName: String, text: String): ImageInspection {
        val parsed = parse(text)
        val header = parsed.header
        val descs = parsed.descriptors

        val includedNames = mutableListOf<String>()
        val chainEntries = mutableListOf<String>()
        for (block in descs) {
            val name = block.fields["Partition Name"] ?: continue
            if (block.type == "Chain Partition") {
                val rollback = block.fields["Rollback Index Location"] ?: "0"
                // Key file is filled in later by the profile editor; the
                // entry keeps the canonical three-part shape.
                chainEntries += "$name:$rollback:default.bin"
            } else {
                includedNames += name
            }
        }

        val partitionSize = header["Image size"]?.removeSuffix("bytes")?.trim()?.toLongOrNull()

        // Footer presence is the type signal, not the file name: a signed
        // partition image always carries an AvbFooter (info_image prints the
        // "Footer version:" block), while `make_vbmeta_image` output is a
        // bare vbmeta blob without one. File-name-based heuristics break
        // whenever the user stores a partition image under an arbitrary
        // name (e.g. product.img as super3.img).
        val hasFooter = header.containsKey("Footer version")
        if (hasFooter) {
            // Partition image: its own descriptor is the first Hash/Hashtree
            // block (a footer image can additionally embed props, but its
            // chain/included descriptors belong to a separate vbmeta).
            val own = descs.firstOrNull { it.type == "Hash" || it.type == "Hashtree" }
            if (own != null) {
                return ImageInspection(
                    descriptor = own.type.toDescriptorKey(),
                    partitionName = own.fields["Partition Name"],
                    algorithm = header["Algorithm"],
                    rollbackIndex = header["Rollback Index"]?.toLongOrNull(),
                    flags = header["Flags"]?.toLongOrNull(),
                    hashAlgorithm = own.fields["Hash Algorithm"]?.lowercase(),
                    salt = own.fields["Salt"]?.takeIf { it.isNotBlank() },
                    props = parsed.props,
                    includedPartitions = emptyList(),
                    chainPartitions = emptyList(),
                    partitionSize = partitionSize,
                    publicKeySha1 = header["Public key (sha1)"]?.lowercase(),
                )
            }
            // Footer present but only exotic descriptors — fall through and
            // treat it like a vbmeta-style container below.
        }

        // Bare vbmeta blob (or degenerate footer image): descriptors name
        // other partitions.
        return ImageInspection(
            descriptor = "vbmeta",
            partitionName = null,
            algorithm = header["Algorithm"],
            rollbackIndex = header["Rollback Index"]?.toLongOrNull(),
            flags = header["Flags"]?.toLongOrNull(),
            hashAlgorithm = null,
            salt = null,
            props = parsed.props,
            includedPartitions = includedNames,
            chainPartitions = chainEntries,
            partitionSize = partitionSize,
            publicKeySha1 = header["Public key (sha1)"]?.lowercase(),
        )
    }

    /** "Hash descriptor" -> "hash", "Hashtree descriptor" -> "hashtree". */
    private fun String.toDescriptorKey(): String = when {
        contains("hashtree", ignoreCase = true) -> "hashtree"
        contains("hash", ignoreCase = true) -> "hash"
        else -> "hash"
    }

    private fun detectDescriptorType(stripped: String): String {
        val lower = stripped.lowercase()
        return when {
            "chain partition" in lower -> "Chain Partition"
            "hashtree" in lower -> "Hashtree"
            "hash" in lower -> "Hash"
            "kernel cmdline" in lower -> "Kernel Cmdline"
            else -> stripped.removeSuffix(":")
        }
    }
}
