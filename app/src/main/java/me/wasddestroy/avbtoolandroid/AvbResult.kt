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
    val errors = stderrLines.filter { isErrorLine(it) }
    val warnings = stderrLines.filter { !isErrorLine(it) }
    val failed = errors.isNotEmpty() || stderr.contains("Traceback")
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

private fun parseInfoImage(stdout: String): List<ResultSection> {
    val topRows = mutableListOf<ResultRow>()
    val groups = mutableListOf<ResultGroup>()
    var currentGroup: MutableList<ResultRow>? = null
    var currentGroupTitle = ""

    fun closeGroup() {
        val rows = currentGroup
        if (!rows.isNullOrEmpty()) {
            groups += ResultGroup(title = currentGroupTitle, rows = rows.toList())
        }
        currentGroup = null
        currentGroupTitle = ""
    }

    stdout.lines().forEach { rawLine ->
        if (rawLine.isBlank()) return@forEach
        val indent = rawLine.indexOfFirst { it != ' ' }
        val text = rawLine.trim()
        if (indent == 0) {
            closeGroup()
            when (text) {
                "--" -> {
                    // info_image prints a separator between footer and vbmeta.
                }
                "Descriptors:", "avb_cert certificate:" -> {
                    // section marker, not a row
                }
                else -> {
                    val parts = text.split(":", limit = 2)
                    topRows += if (parts.size == 2) {
                        ResultRow(parts[0].trim(), parts[1].trim())
                    } else {
                        ResultRow(text, "")
                    }
                }
            }
        } else if (indent <= 4 && text.endsWith(":") && !text.contains(": ")) {
            closeGroup()
            currentGroupTitle = text.removeSuffix(":")
            currentGroup = mutableListOf()
        } else {
            val row = parseIndentedRow(text)
            if (currentGroup != null) currentGroup!!.add(row) else topRows += row
        }
    }
    closeGroup()

    val sections = mutableListOf<ResultSection>()
    if (topRows.isNotEmpty()) {
        sections += ResultSection(title = "Image info", rows = topRows)
    }
    if (groups.isNotEmpty()) {
        sections += ResultSection(title = "Descriptors", groups = groups)
    }
    return sections
}

private fun parseIndentedRow(text: String): ResultRow {
    val parts = text.split(":", limit = 2)
    return if (parts.size == 2) {
        ResultRow(parts[0].trim(), parts[1].trim(), monospace = true)
    } else {
        ResultRow(text, "")
    }
}
