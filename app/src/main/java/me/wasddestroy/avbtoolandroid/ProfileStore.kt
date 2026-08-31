package me.wasddestroy.avbtoolandroid

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Manages signing profiles under `<filesDir>/profile/`. Each profile lives in a
 * folder named after its profile id, mirroring the layout of the imported zip
 * archive (manifest.json, profile.json, keys/...).
 */
class ProfileStore(private val context: Context) {

    data class ProfileEntry(
        val id: String,
        val name: String,
        val dir: File,
    )

    val profileDir: File
        get() = File(context.applicationContext.filesDir, "profile")

    fun listProfiles(): List<ProfileEntry> {
        val dir = profileDir
        val entries = mutableListOf<ProfileEntry>()
        val children = dir.listFiles() ?: return entries
        for (child in children.sortedBy { it.name }) {
            if (!child.isDirectory) continue
            val profileJson = File(child, "profile.json")
            if (!profileJson.isFile) continue
            val meta = runCatching { parseProfileMeta(profileJson) }.getOrNull() ?: continue
            if (meta.first != child.name) {
                // Folder name must match the profile id to keep on-disk layout canonical.
                Log.w(TAG, "Skipping profile folder '${child.name}': id mismatch ('$meta')")
                continue
            }
            entries += ProfileEntry(id = meta.first, name = meta.second, dir = child)
        }
        return entries
    }

    fun getProfile(id: String): ProfileEntry? = listProfiles().find { it.id == id }

    fun deleteProfile(id: String): Boolean {
        if (!isValidProfileId(id)) return false
        val dir = File(profileDir, id)
        return dir.exists() && dir.deleteRecursively()
    }

    /**
     * Validates the manifest checksums, then extracts the archive into
     * `profile/<profile_id>/`. The id comes from manifest.json, never from the
     * zip file name. Returns the profile id, or null on validation failure.
     */
    fun importProfile(zipBytes: ByteArray): String? {
        val tmpDir = File(profileDir, "import_tmp_${System.nanoTime()}")
        return try {
            val manifest = extractVerified(zipBytes, tmpDir) ?: return null
            val profileId = manifest.optString("profile_id")
            val schemaVersion = manifest.optInt("schema_version", -1)
            if (!isValidProfileId(profileId) || schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                Log.w(TAG, "Unsupported profile: id='$profileId', schema=$schemaVersion")
                return null
            }
            val target = File(profileDir, profileId)
            if (target.exists() && !target.deleteRecursively()) return null
            if (!tmpDir.renameTo(target)) return null
            profileId
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun extractVerified(zipBytes: ByteArray, destDir: File): JSONObject? {
        destDir.mkdirs()
        val manifest = readManifest(zipBytes) ?: return null
        val files = manifest.optJSONArray("files") ?: return null
        val expected = LinkedHashMap<String, String>()
        for (i in 0 until files.length()) {
            val obj = files.optJSONObject(i) ?: return null
            val path = obj.optString("path")
            val sha = obj.optString("sha256")
            if (!isValidEntryPath(path) || sha.isBlank() || expected.containsKey(path)) return null
            expected[path] = sha
        }
        if (expected.isEmpty()) return null

        ZipInputStream(zipBytes.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                try {
                    if (entry.isDirectory) continue
                    if (!expected.containsKey(entry.name)) continue // zip is manifest-driven
                    val target = File(destDir, entry.name)
                    if (!target.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) return null
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out -> zip.copyTo(out) }
                    if (sha256(target) != expected[entry.name]) return null
                } finally {
                    zip.closeEntry()
                }
            }
        }
        // Every manifest entry must have been extracted and verified.
        for (path in expected.keys) {
            if (!File(destDir, path).isFile) return null
        }
        return manifest
    }

    private fun readManifest(zipBytes: ByteArray): JSONObject? {
        ZipInputStream(zipBytes.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name == "manifest.json") {
                    val text = zip.readBytes().decodeToString()
                    return runCatching { JSONObject(text) }.getOrNull()
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun parseProfileMeta(profileJson: File): Pair<String, String> {
        val obj = JSONObject(profileJson.readText())
        val profile = obj.optJSONObject("profile")
            ?: throw IllegalArgumentException("missing profile object")
        val id = profile.getString("id")
        val name = profile.optString("name", id)
        return id to name
    }

    /**
     * Packs the profile folder into a zip whose layout matches the import
     * format: manifest.json with schema_version and per-entry SHA-256
     * checksums, followed by the profile's files. Entries listed under
     * [excludePaths] are omitted from both the archive and the manifest.
     * Returns the zip bytes, or null if the profile is not importable.
     */
    fun exportProfileZip(id: String, excludePaths: Set<String> = emptySet()): ByteArray? {
        if (!isValidProfileId(id)) return null
        val dir = File(profileDir, id)
        if (!dir.isDirectory) return null

        val files = mutableListOf<Pair<String, File>>()
        dir.walkTopDown()
            .filter { it.isFile }
            .forEach { f ->
                val rel = f.relativeTo(dir).invariantSeparatorsPath
                if (rel != "manifest.json" && rel !in excludePaths) {
                    files += rel to f
                }
            }
        // The profile must still contain its own definition to be usable.
        if (files.none { it.first == "profile.json" }) return null
        if (excludePaths.isNotEmpty() && files.none { it.first == "keys/manifest.json" }) {
            // Keys were excluded: nothing left to re-export as a complete profile.
            return null
        }
        files.sortBy { it.first }

        val manifest = JSONObject().apply {
            put("format_version", 1)
            put("profile_id", id)
            put("schema_version", SUPPORTED_SCHEMA_VERSION)
            put(
                "files",
                JSONArray().apply {
                    files.forEach { (rel, f) ->
                        put(
                            JSONObject().apply {
                                put("path", rel)
                                put("sha256", sha256(f))
                            },
                        )
                    }
                },
            )
        }

        return java.io.ByteArrayOutputStream().use { out ->
            java.util.zip.ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray())
                zip.closeEntry()
                files.forEach { (rel, f) ->
                    zip.putNextEntry(java.util.zip.ZipEntry(rel))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            out.toByteArray()
        }
    }

    companion object {
        private const val TAG = "ProfileStore"
        const val SUPPORTED_SCHEMA_VERSION = 3

        fun isValidProfileId(id: String): Boolean =
            id.isNotEmpty() && id.length <= 128 && id.matches(Regex("[A-Za-z0-9._-]+")) &&
                id != "." && id != ".."

        private fun isValidEntryPath(path: String): Boolean {
            if (path.isBlank() || path.length > 256) return false
            if (path.startsWith("/") || path.contains('\\')) return false
            val parts = path.split('/')
            if (parts.any { it.isEmpty() || it == "." || it == ".." }) return false
            return true
        }
    }
}

/** Persisted "active profile" choice, stored in SharedPreferences. */
class ActiveProfileStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(
        "application_configs", Context.MODE_PRIVATE,
    )

    fun read(): String? = sp.getString(KEY_ACTIVE_PROFILE, null)

    fun write(id: String?) {
        sp.edit().putString(KEY_ACTIVE_PROFILE, id).apply()
    }

    companion object {
        private const val KEY_ACTIVE_PROFILE = "active_profile_id"
    }
}

/**
 * Persisted per-partition image picks, keyed "<profileId>:<partition>".
 * URIs carry persistable SAF grants (taken by the ViewModel), so picks
 * survive app restarts and re-signing needs no re-picking.
 */
class ProfileImageSelectionStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(
        "application_configs", Context.MODE_PRIVATE,
    )

    fun read(): Map<String, String> {
        val json = sp.getString(KEY_IMAGE_SELECTIONS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().filter { obj.optString(it).isNotBlank() }
                .associateWith { obj.optString(it) }
        }.getOrElse { emptyMap() }
    }

    fun write(selections: Map<String, String>) {
        val obj = JSONObject()
        selections.forEach { (key, uri) -> obj.put(key, uri) }
        sp.edit().putString(KEY_IMAGE_SELECTIONS, obj.toString()).apply()
    }

    companion object {
        private const val KEY_IMAGE_SELECTIONS = "profile_image_selections"
    }
}

/** Result of one signing run, ready for the existing result renderer. */
data class ProfileSignResult(
    val profileId: String,
    val profileName: String,
    val result: AvbCommandResult,
)

/**
 * Tracks per-partition input images the user picked for the active profile so
 * they survive recomposition; keyed by partition name.
 */
class ProfileImageSelection {
    private val selections = ConcurrentHashMap<String, String>()

    fun get(partition: String): String? = selections[partition]
    fun put(partition: String, uri: String) {
        selections[partition] = uri
    }
    fun clear() = selections.clear()
}
