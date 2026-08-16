package me.wasddestroy.avbtoolandroid

import org.json.JSONObject

enum class AvbResultStatus { RUNNING, SUCCESS, FAILED, CANCELLED }

data class ResultRow(
    val title: String,
    val value: String,
    val monospace: Boolean = false,
)

data class ResultGroup(
    val title: String,
    val rows: List<ResultRow>,
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

private fun parseInfoImage(stdout: String): List<ResultSection> {
    val lines = stdout.lines()

    // Pass 1: collect all unique indent levels (skip blanks)
    val indentLevels = lines
        .filter { it.isNotBlank() }
        .map { it.indexOfFirst { c -> c != ' ' } }
        .distinct()
        .sorted()
        .toIntArray()
    fun levelOf(indent: Int): Int = indentLevels.binarySearch(indent).coerceAtLeast(0)

    // Pass 2: parse using indent levels
    val topRows = mutableListOf<ResultRow>()
    val groups = mutableListOf<ResultGroup>()
    var blockType: String? = null
    var blockLines = mutableListOf<Pair<String, String>>()

    fun flushBlock() {
        val type = blockType ?: return
        if (blockLines.isEmpty()) { blockType = null; return }
        val partitionName = blockLines.firstOrNull { it.first == "Partition Name" }?.second
        val title = if (partitionName != null) "$type: $partitionName" else type
        val summaryLines = blockLines.filter { it.first != "Partition Name" }
            .map { "${it.first}: ${it.second}" }
        groups += ResultGroup(
            title = title,
            rows = listOf(ResultRow(title, summaryLines.joinToString("\n"), monospace = true)),
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
                "--", "Descriptors:", "avb_cert certificate:" -> {}
                else -> {
                    val parts = text.split(":", limit = 2)
                    topRows += if (parts.size == 2) ResultRow(parts[0].trim(), parts[1].trim())
                    else ResultRow(text, "")
                }
            }
            continue
        }

        // level >= 1: inside Descriptors section
        PROP_RE.find(text)?.let { match ->
            flushBlock()
            topRows += ResultRow("Prop: ${match.groupValues[1]}", match.groupValues[2], monospace = true)
            continue
        }

        DESCRIPTOR_RE.find(text)?.let { match ->
            flushBlock()
            blockType = match.groupValues[1]
            blockLines = mutableListOf()
            continue
        }

        if (blockType != null && level >= 2) {
            val parts = text.split(":", limit = 2)
            blockLines += if (parts.size == 2) Pair(parts[0].trim(), parts[1].trim()) else Pair(text, "")
        } else {
            flushBlock()
            val parts = text.split(":", limit = 2)
            topRows += if (parts.size == 2) ResultRow(parts[0].trim(), parts[1].trim(), monospace = true)
            else ResultRow(text, "")
        }
    }
    flushBlock()

    val sections = mutableListOf<ResultSection>()
    if (topRows.isNotEmpty()) {
        sections += ResultSection(title = "Image info", rows = topRows)
    }
    if (groups.isNotEmpty()) {
        sections += ResultSection(title = "Descriptors", groups = groups)
    }
    return sections
}
