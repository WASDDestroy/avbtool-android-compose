package me.wasddestroy.avbtoolandroid

import android.util.Log
import androidx.annotation.StringRes
import org.json.JSONObject

private const val TAG = "AvbResultParser"

enum class AvbResultStatus { RUNNING, SUCCESS, FAILED, CANCELLED }

data class ResultRow(
    val title: String,
    val value: String,
    val monospace: Boolean = false,
    @param:StringRes val localizedNameRes: Int? = null,
)

data class ResultGroup(
    val title: String,
    val rows: List<ResultRow>,
    @param:StringRes val localizedNameRes: Int? = null,
    val nameFormatArg: String? = null,
)

data class ResultSection(
    val title: String,
    val rows: List<ResultRow> = emptyList(),
    val groups: List<ResultGroup> = emptyList(),
)

data class AvbCommandResult(
    val status: AvbResultStatus,
    val sections: List<ResultSection> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val rawOutput: String = "",
)

fun parseAvbResult(commandId: String, stdout: String, stderr: String): AvbCommandResult {
    val rawOutput = buildString {
        append(stdout)
        if (stderr.isNotBlank()) {
            if (stdout.isNotBlank()) append("\n")
            append("[stderr]\n")
            append(stderr)
        }
    }
    val stderrLines = stderr.lines().filter { it.isNotBlank() }
    val failed = stderrLines.any { isErrorLine(it) } || stderr.contains("Traceback")
    val errors = if (failed) stderrLines else stderrLines.filter { isErrorLine(it) }
    val warnings = if (failed) emptyList() else stderrLines.filter { !isErrorLine(it) }
    val status = if (failed) AvbResultStatus.FAILED else AvbResultStatus.SUCCESS

    val sections = when (commandId) {
        "info_image" -> parseInfoImage(stdout)
        "print_partition_digests" -> parsePartitionDigests(stdout)
        "calculate_vbmeta_digest" -> listOf(singleValueSection("VBMeta digest", stdout.trim()))
        "calculate_kernel_cmdline" -> listOf(singleValueSection("Kernel cmdline", stdout.trim()))
        "verify_image" -> parseVerifyImage(stdout)
        else -> {
            val trimmed = stdout.trim()
            if (trimmed.isBlank()) emptyList()
            else listOf(singleValueSection("Result", trimmed))
        }
    }

    return AvbCommandResult(
        status = status,
        sections = sections,
        warnings = warnings,
        errors = errors,
        rawOutput = rawOutput,
    )
}

private fun isErrorLine(line: String): Boolean {
    val lower = line.lowercase()
    return lower.contains("traceback") ||
        lower.contains("avbtool:") ||
        lower.contains("error:") ||
        lower.contains("failed") ||
        lower.contains("signature check failed")
}

private fun singleValueSection(title: String, value: String): ResultSection {
    return ResultSection(
        title = "Result",
        rows = listOf(ResultRow(title = title, value = value, monospace = true)),
    )
}

private fun parsePartitionDigests(stdout: String): List<ResultSection> {
    val rows = mutableListOf<ResultRow>()
    val trimmed = stdout.trim()
    if (trimmed.startsWith("{")) {
        runCatching {
            val json = JSONObject(trimmed)
            val arr = json.optJSONArray("partitions") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                val digest = obj.optString("digest")
                if (name.isNotBlank() && digest.isNotBlank()) {
                    rows += ResultRow(name, digest, monospace = true)
                }
            }
        }
    } else {
        stdout.lines().forEach { line ->
            val parts = line.trim().split(":", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                rows += ResultRow(parts[0].trim(), parts[1].trim(), monospace = true)
            }
        }
    }
    if (rows.isEmpty()) return emptyList()
    return listOf(ResultSection(title = "Partition digests", rows = rows))
}

private fun parseVerifyImage(stdout: String): List<ResultSection> {
    val rows = stdout.lines().filter { it.isNotBlank() }.map { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("Verifying image") -> ResultRow("Image", trimmed.removePrefix("Verifying image ").trim())
            trimmed.contains("Successfully verified") -> {
                val prefix = trimmed.substringBefore(": Successfully verified", missingDelimiterValue = "")
                ResultRow(
                    title = prefix.ifBlank { "Verification" },
                    value = "Success",
                )
            }
            trimmed == "--" -> ResultRow("--", "")
            else -> ResultRow("Output", trimmed)
        }
    }
    if (rows.isEmpty()) return emptyList()
    return listOf(ResultSection(title = "Verification", rows = rows))
}

private val DESCRIPTOR_RE = Regex("""((?:Chain Partition|Hash|Hashtree) descriptor):""")
private val PROP_RE = Regex("""Prop:\s*(.+?)\s*->\s*'(.*)'$""")

private val INFO_IMAGE_KEY_RES = mapOf(
    // Top-level keys
    "Minimum libavb version" to R.string.info_key_minimum_libavb_version,
    "Header Block" to R.string.info_key_header_block,
    "Authentication Block" to R.string.info_key_authentication_block,
    "Auxiliary Block" to R.string.info_key_auxiliary_block,
    "Public key (sha1)" to R.string.info_key_public_key_sha1,
    "Algorithm" to R.string.info_key_algorithm,
    "Rollback Index" to R.string.info_key_rollback_index,
    "Flags" to R.string.info_key_flags,
    "Rollback Index Location" to R.string.info_key_rollback_index_location,
    "Release String" to R.string.info_key_release_string,
    // Prop prefix
    "Prop:" to R.string.info_key_prop,
    // Descriptor field keys
    "Partition Name" to R.string.info_key_partition_name,
    "Image Size" to R.string.info_key_image_size,
    "Hash Algorithm" to R.string.info_key_hash_algorithm,
    "Salt" to R.string.info_key_salt,
    "Digest" to R.string.info_key_digest,
    "Root Digest" to R.string.info_key_root_digest,
    "Version of dm-verity" to R.string.info_key_version_of_dm_verity,
    "Tree Offset" to R.string.info_key_tree_offset,
    "Tree Size" to R.string.info_key_tree_size,
    "Data Block Size" to R.string.info_key_data_block_size,
    "Hash Block Size" to R.string.info_key_hash_block_size,
    "FEC num roots" to R.string.info_key_fec_num_roots,
    "FEC offset" to R.string.info_key_fec_offset,
    "FEC size" to R.string.info_key_fec_size,
)

private val DESCRIPTOR_TYPE_RES = mapOf(
    "Chain Partition descriptor" to R.string.info_desc_chain_partition,
    "Hash descriptor" to R.string.info_desc_hash,
    "Hashtree descriptor" to R.string.info_desc_hashtree,
)

private val DESCRIPTOR_FMT_RES = mapOf(
    "Chain Partition descriptor" to R.string.info_desc_chain_partition_fmt,
    "Hash descriptor" to R.string.info_desc_hash_fmt,
    "Hashtree descriptor" to R.string.info_desc_hashtree_fmt,
)

private fun parseInfoImage(stdout: String): List<ResultSection> {
    val lines = stdout.lines()

    // Pass 1: collect all unique indent levels (skip blanks)
    val indentLevels = lines
        .filter { it.isNotBlank() }
        .map { it.indexOfFirst { c -> c != ' ' } }
        .distinct()
        .sorted()
        .toIntArray()
    Log.d(TAG, "indentLevels=${indentLevels.contentToString()}")
    fun levelOf(indent: Int): Int = indentLevels.binarySearch(indent).coerceAtLeast(0)

    // Pass 2: parse using indent levels
    val topRows = mutableListOf<ResultRow>()
    val groups = mutableListOf<ResultGroup>()
    var blockType: String? = null
    var blockLines = mutableListOf<Pair<String, String>>()

    fun flushBlock() {
        val type = blockType ?: return
        if (blockLines.isEmpty()) {
            Log.d(TAG, "flushBlock: skip (empty) type=$type")
            blockType = null; return
        }
        val partitionName = blockLines.firstOrNull { it.first == "Partition Name" }?.second
        val title = if (partitionName != null) "$type: $partitionName" else type
        val summaryLines = blockLines.filter { it.first != "Partition Name" }
            .map { "${it.first}: ${it.second}" }
        Log.d(TAG, "flushBlock: group='$title', fields=${blockLines.size}")
        groups += ResultGroup(
            title = title,
            rows = listOf(ResultRow(title, summaryLines.joinToString("\n"), monospace = true)),
            localizedNameRes = DESCRIPTOR_FMT_RES[type] ?: DESCRIPTOR_TYPE_RES[type],
            nameFormatArg = partitionName,
        )
        blockType = null
        blockLines = mutableListOf()
    }

    for (rawLine in lines) {
        if (rawLine.isBlank()) continue
        val indent = rawLine.indexOfFirst { it != ' ' }
        val text = rawLine.trim()
        val level = levelOf(indent)

        if (level == 0) {
            flushBlock()
            when (text) {
                "--", "Descriptors:", "avb_cert certificate:" -> {
                    Log.d(TAG, "L0 marker: '$text'")
                }
                else -> {
                    val parts = text.split(":", limit = 2)
                    val key = if (parts.size == 2) parts[0].trim() else text
                    val value = if (parts.size == 2) parts[1].trim() else ""
                    val row = ResultRow(key, value, localizedNameRes = INFO_IMAGE_KEY_RES[key])
                    Log.d(TAG, "L0 row: '${row.title}' = '${row.value}'")
                    topRows += row
                }
            }
            continue
        }

        // level >= 1: inside Descriptors section
        val propMatch = PROP_RE.find(text)
        if (propMatch != null) {
            flushBlock()
            val row = ResultRow("Prop: ${propMatch.groupValues[1]}", propMatch.groupValues[2], monospace = true, localizedNameRes = INFO_IMAGE_KEY_RES["Prop:"])
            Log.d(TAG, "L$level prop: '${row.title}' = '${row.value}'")
            topRows += row
            continue
        }

        val descMatch = DESCRIPTOR_RE.find(text)
        if (descMatch != null) {
            flushBlock()
            blockType = descMatch.groupValues[1]
            blockLines = mutableListOf()
            Log.d(TAG, "L$level block start: type='$blockType'")
            continue
        }

        if (blockType != null && level >= 2) {
            val parts = text.split(":", limit = 2)
            val pair = if (parts.size == 2) Pair(parts[0].trim(), parts[1].trim()) else Pair(text, "")
            blockLines += pair
            Log.d(TAG, "L$level block field: '${pair.first}' = '${pair.second}'")
        } else {
            flushBlock()
            val parts = text.split(":", limit = 2)
            val row = if (parts.size == 2) ResultRow(parts[0].trim(), parts[1].trim(), monospace = true)
            else ResultRow(text, "")
            Log.d(TAG, "L$level fallback row: '${row.title}' = '${row.value}'")
            topRows += row
        }
    }
    flushBlock()

    Log.d(TAG, "result: topRows=${topRows.size}, groups=${groups.size}")
    topRows.forEach { Log.d(TAG, "  topRow: '${it.title}'") }
    groups.forEach { g ->
        Log.d(TAG, "  group: '${g.title}', rows=${g.rows.size}")
        g.rows.forEach { Log.d(TAG, "    row: '${it.title}' valueLen=${it.value.length}") }
    }

    val sections = mutableListOf<ResultSection>()
    if (topRows.isNotEmpty()) {
        sections += ResultSection(title = "Image info", rows = topRows)
    }
    if (groups.isNotEmpty()) {
        sections += ResultSection(title = "Descriptors", groups = groups)
    }
    return sections
}
