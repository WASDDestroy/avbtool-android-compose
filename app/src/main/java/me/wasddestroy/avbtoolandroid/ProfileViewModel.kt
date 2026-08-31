package me.wasddestroy.avbtoolandroid

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ProfilePartitionSpec(
    val partition: String,
    val image: String,
    val descriptor: String,
    val algorithm: String,
    val keyId: String?,
    val partitionName: String,
    val partitionSize: Long?,
    val rollbackIndex: Long?,
    val salt: String?,
    val flags: Long?,
    val props: List<Pair<String, String>>,
    val setHashtreeDisabledFlag: Boolean,
    val includedPartitions: List<String>,
    val chainPartitions: List<String>,
)

data class ProfileUiState(
    val profiles: List<ProfileStore.ProfileEntry> = emptyList(),
    val activeId: String? = null,
    /** Partitions of the active profile, for the per-partition image picker. */
    val activeSpecs: List<ProfilePartitionSpec> = emptyList(),
    val importing: Boolean = false,
    val signing: Boolean = false,
    val exporting: Boolean = false,
    val result: ProfileSignResult? = null,
    /** Output files awaiting "save via SAF"; consumed by the CreateDocument launcher. */
    val pendingExports: List<File> = emptyList(),
    /** Zip bytes awaiting "save via SAF" after an export-profile action, one-shot. */
    val pendingProfileZip: Pair<String, ByteArray>? = null,
    /** Whether generated vbmeta images also get the profile's configured props. */
    val addPropsToVbmeta: Boolean = false,
    /** Per-partition display names of the picked input images (active profile only). */
    val imageSummaries: Map<String, String> = emptyMap(),
    /** Toast event, one-shot. */
    val message: Int? = null,
)

class ProfileViewModel(
    private val appContext: Context,
    private val store: ProfileStore,
    private val activeStore: ActiveProfileStore,
    private val runner: AvbTaskRunner,
    private val bridge: SafFileBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(activeId = activeStore.read()))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val imageSelections = mutableMapOf<String, String>()

    init {
        refresh()
    }

    fun refresh() {
        val profiles = store.listProfiles()
        val activeId = activeStore.read()?.takeIf { id -> profiles.any { p -> p.id == id } }
        val specs = activeId?.let { id ->
            profiles.find { it.id == id }?.let { entry ->
                runCatching {
                    parseProfile(JSONObject(File(entry.dir, "profile.json").readText()))
                }.getOrNull()?.partitions
            }
        } ?: emptyList()
        _uiState.update {
            it.copy(
                profiles = profiles,
                activeId = activeId,
                activeSpecs = specs,
            )
        }
        publishImageSummaries()
    }

    fun importProfile(bytes: ByteArray) {
        if (_uiState.value.importing) return
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true, message = null) }
            val id = withContext(Dispatchers.IO) { store.importProfile(bytes) }
            _uiState.update {
                if (id != null) {
                    // First imported profile becomes active automatically.
                    val newActive = it.activeId ?: id
                    if (newActive != it.activeId) activeStore.write(newActive)
                    it.copy(
                        importing = false,
                        activeId = newActive,
                        message = R.string.profile_import_success,
                    )
                } else {
                    it.copy(importing = false, message = R.string.profile_import_failed)
                }
            }
            refresh()
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.deleteProfile(id) }
            if (_uiState.value.activeId == id) {
                activeStore.write(null)
            }
            imageSelections.keys.removeAll { it.startsWith("$id:") }
            refresh()
        }
    }

    fun selectProfile(id: String) {
        activeStore.write(id)
        _uiState.update { it.copy(activeId = id) }
        refresh()
    }

    fun setImage(partition: String, uri: Uri?) {
        val profileId = _uiState.value.activeId ?: return
        if (uri != null) {
            imageSelections["$profileId:$partition"] = uri.toString()
        } else {
            imageSelections.remove("$profileId:$partition")
        }
        publishImageSummaries()
    }

    private fun publishImageSummaries() {
        val profileId = _uiState.value.activeId ?: return
        val summaries = imageSelections.entries
            .filter { it.key.startsWith("$profileId:") }
            .associate {
                val partition = it.key.removePrefix("$profileId:")
                partition to (it.value.toUri().lastPathSegment ?: it.value)
            }
        _uiState.update { it.copy(imageSummaries = summaries) }
    }

    fun getImage(partition: String): String? {
        val profileId = _uiState.value.activeId ?: return null
        return imageSelections["$profileId:$partition"]
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun setAddPropsToVbmeta(enabled: Boolean) {
        _uiState.update { it.copy(addPropsToVbmeta = enabled) }
    }

    fun dismissExports() {
        _uiState.update { it.copy(pendingExports = emptyList()) }
    }

    fun dismissProfileZip() {
        _uiState.update { it.copy(pendingProfileZip = null) }
    }

    fun consumeProfileZip() {
        _uiState.update { it.copy(pendingProfileZip = null) }
    }

    /**
     * Packs the active profile into an import-format zip. Partition images
     * the user picked are copied to a separate scratch dir during signing and
     * never live inside the profile folder, so nothing else needs excluding.
     */
    fun exportActiveProfile() {
        val state = _uiState.value
        val profile = state.profiles.find { it.id == state.activeId }
        if (profile == null) {
            _uiState.update { it.copy(message = R.string.profile_sign_no_active) }
            return
        }
        if (state.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true, message = null) }
            val zip = withContext(Dispatchers.IO) {
                store.exportProfileZip(profile.id, excludePaths = imageEntryPaths(profile.id))
            }
            _uiState.update {
                if (zip != null) {
                    it.copy(
                        exporting = false,
                        pendingProfileZip = "${profile.id}.zip" to zip,
                    )
                } else {
                    it.copy(exporting = false, message = R.string.profile_export_failed)
                }
            }
        }
    }

    /** Files the sign pipeline copied into the profile folder, as zip-relative paths. */
    private fun imageEntryPaths(profileId: String): Set<String> {
        return imageSelections.keys
            .filter { it.startsWith("$profileId:") }
            .mapNotNull { key ->
                val partition = key.removePrefix("$profileId:")
                val fileName = imageSelections[key]?.toUri()?.lastPathSegment ?: return@mapNotNull null
                "$partition/$fileName"
            }
            .toSet()
    }

    fun consumeExport() {
        _uiState.update { it.copy(pendingExports = it.pendingExports.drop(1)) }
    }

    fun currentProfile(): ProfileStore.ProfileEntry? {
        val s = _uiState.value
        return s.profiles.find { it.id == s.activeId }
    }

    /**
     * Signs all configured partitions of the active profile in dependency
     * order. Images are copied to a scratch dir named after the image file so
     * chain-partition sibling lookups resolve inside the profile; the vbmeta
     * output is exported via SAF afterwards.
     */
    fun signActive() {
        val state = _uiState.value
        val profile = state.profiles.find { it.id == state.activeId }
        if (profile == null) {
            _uiState.update { it.copy(message = R.string.profile_sign_no_active) }
            return
        }
        if (state.signing) return

        viewModelScope.launch {
            _uiState.update { it.copy(signing = true, result = null) }
            val outcome = withContext(Dispatchers.IO) { signProfile(profile) }
            val (signResult, exports) = outcome
            _uiState.update {
                it.copy(
                    signing = false,
                    result = signResult,
                    pendingExports = exports,
                )
            }
            refresh()
        }
    }

    private data class SignOutcome(
        val result: ProfileSignResult?,
        val exports: List<File>,
    )

    private suspend fun signProfile(profile: ProfileStore.ProfileEntry): SignOutcome {
        val raw = runCatching {
            JSONObject(File(profile.dir, "profile.json").readText())
        }.getOrElse {
            return failedOutcome(profile, R.string.profile_error_invalid_profile)
        }
        val spec = runCatching { parseProfile(raw) }.getOrElse {
            return failedOutcome(profile, R.string.profile_error_invalid_profile)
        }

        val missing = spec.partitions.filter { getImage(it.partition) == null }
        if (missing.isNotEmpty()) {
            return failedOutcome(profile, R.string.profile_error_missing_images)
        }

        val scratch = File(appContext.filesDir, "profile_sign/${profile.id}")
        scratch.deleteRecursively()
        scratch.mkdirs()

        val log = StringBuilder()
        var ok = true
        val outputs = mutableListOf<File>()

        try {
            // spec.partitions is already in dependency order (see parseProfile).
            for (p in spec.partitions) {
                val srcUri = getImage(p.partition)!!
                val imageDir = File(scratch, p.partition)
                imageDir.mkdirs()
                val target = File(imageDir, p.image)
                if (p.descriptor != "vbmeta") {
                    // Only footer commands take a pre-existing input image;
                    // vbmeta images are generated from scratch.
                    val copied = copyUriToFile(srcUri.toUri(), target)
                    if (!copied) {
                        log.appendLine("[${p.partition}] failed to read the selected image")
                        ok = false
                        break
                    }
                }

                val args = buildAvbArgs(p, profile, imageDir, scratch, spec, uiState.value.addPropsToVbmeta)
                val res = runner.run(args)
                log.appendLine("[${p.partition}] " + (if (res.exitCode != 0) "FAILED" else "OK"))
                if (res.stdout.isNotBlank()) log.appendLine(res.stdout.trim())
                if (res.stderr.isNotBlank()) log.appendLine(res.stderr.trim())
                if (res.exitCode != 0) {
                    ok = false
                    break
                }

                if (p.descriptor == "vbmeta") {
                    outputs += File(imageDir, p.image)
                } else {
                    outputs += target
                }
            }
        } finally {
            if (!ok) {
                outputs.clear()
            }
        }

        val status = if (ok) AvbResultStatus.SUCCESS else AvbResultStatus.FAILED
        val result = AvbCommandResult(
            status = status,
            sections = listOf(
                ResultSection(
                    title = "Sign log",
                    rows = listOf(
                        ResultRow(
                            title = "Log",
                            value = log.toString().trim(),
                            monospace = true,
                        ),
                    ),
                ),
            ),
            rawOutput = log.toString().trim(),
        )
        val signResult = ProfileSignResult(
            profileId = profile.id,
            profileName = profile.name,
            result = result,
        )
        return SignOutcome(
            result = signResult,
            exports = if (ok) outputs else emptyList(),
        )
    }

    private fun failedOutcome(
        profile: ProfileStore.ProfileEntry,
        messageRes: Int,
    ): SignOutcome {
        return SignOutcome(
            result = ProfileSignResult(
                profileId = profile.id,
                profileName = profile.name,
                result = AvbCommandResult(
                    status = AvbResultStatus.FAILED,
                    errors = listOf(appContext.getString(messageRes)),
                ),
            ),
            exports = emptyList(),
        )
    }

    private fun copyUriToFile(uri: Uri, target: File): Boolean {
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: false
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildAvbArgs(
        p: ProfilePartitionSpec,
        profile: ProfileStore.ProfileEntry,
        imageDir: File,
        scratchRoot: File,
        fullSpec: ProfileSpec?,
        addPropsToVbmeta: Boolean,
    ): List<String> {
        val argv = mutableListOf("avbtool")
        if (p.descriptor == "vbmeta") {
            argv += "make_vbmeta_image"
            // The vbmeta image is generated, not modified: p.image is the output name.
            argv += listOf("--output", File(imageDir, p.image).absolutePath)
            p.rollbackIndex?.let { argv += listOf("--rollback_index", it.toString()) }
            p.flags?.let { argv += listOf("--flags", it.toString()) }
            if (p.setHashtreeDisabledFlag) argv += "--set_hashtree_disabled_flag"
            fullSpec?.partitions?.forEach { inc ->
                if (inc.partition in p.includedPartitions) {
                    argv += "--include_descriptors_from_image"
                    argv += File(File(scratchRoot, inc.partition), inc.image).absolutePath
                }
            }
            p.chainPartitions.forEach { entry ->
                // entry = "partition:rollback:keyfile.bin"; the key file lives
                // in the profile's keys dir.
                val parts = entry.split(":")
                if (parts.size >= 3) {
                    val keyFile = File(profile.dir, "keys/${parts.drop(2).joinToString(":")}")
                    argv += "--chain_partition"
                    argv += "${parts[0]}:${parts[1]}:${keyFile.absolutePath}"
                } else {
                    argv += "--chain_partition"
                    argv += entry
                }
            }
        } else {
            argv += "add_${p.descriptor}_footer"
            argv += "--image"
            argv += File(imageDir, p.image).absolutePath
            argv += listOf("--partition_name", p.partitionName)
            p.partitionSize?.let { argv += listOf("--partition_size", it.toString()) }
            if (p.descriptor == "hash") {
                p.rollbackIndex?.let { argv += listOf("--rollback_index", it.toString()) }
            }
            p.salt?.let {
                argv += listOf("--salt", it)
            }
        }
        argv += listOf("--algorithm", p.algorithm)
        val keyPath = p.keyId?.let { resolveKeyPath(profile, it) }
        if (keyPath != null) {
            argv += listOf("--key", keyPath)
        }
        // avbtool appends every --prop verbatim without deduplicating keys.
        // For footer commands the props land in the partition's reserved size
        // so they cannot grow the image; for make_vbmeta_image each prop
        // extends the generated blob, so adding them is opt-in.
        if (p.descriptor != "vbmeta" || addPropsToVbmeta) {
            p.props.forEach { (k, v) ->
                argv += "--prop"
                argv += "$k:$v"
            }
        }
        return argv
    }

    /**
     * Orders partitions so every dependency is signed before its dependents:
     * a vbmeta partition must wait for the partitions it chains to and the
     * ones it pulls descriptors from (their signed outputs must exist first),
     * footer partitions keep their JSON order. Cycle-free by construction for
     * well-formed profiles; a cycle would just leave partitions unsorted at
     * the end, which the per-sign failure log makes obvious.
     */
    private fun orderPartitions(specs: List<ProfilePartitionSpec>): List<ProfilePartitionSpec> {
        val byName = specs.associateBy { it.partition }
        val deps = specs.associate { spec ->
            spec.partition to buildSet {
                if (spec.descriptor == "vbmeta") {
                    spec.chainPartitions.forEach { entry ->
                        // entry = "partition:rollback:keyfile.bin"
                        val name = entry.substringBefore(':')
                        if (name in byName) add(name)
                    }
                    spec.includedPartitions.forEach { name ->
                        if (name in byName) add(name)
                    }
                }
            }
        }
        val ordered = mutableListOf<ProfilePartitionSpec>()
        val placed = mutableSetOf<String>()
        var remaining = specs.toList()
        // A pass places every partition whose deps are all placed; JSON order
        // is preserved among candidates. Repeat until nothing more can move.
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { p -> deps[p.partition]!!.all { it in placed } }
            if (ready.isEmpty()) break
            ordered += ready
            placed += ready.map { it.partition }
            remaining -= ready.toSet()
        }
        ordered += remaining
        return ordered
    }

    private fun parseProfile(raw: JSONObject): ProfileSpec {
        val partitionsJson = raw.getJSONObject("partitions")
        val specs = mutableListOf<ProfilePartitionSpec>()
        for (name in partitionsJson.keys()) {
            val obj = partitionsJson.getJSONObject(name)
            specs += ProfilePartitionSpec(
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
                props = obj.optJSONArray("props")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val pair = arr.optJSONArray(i) ?: return@mapNotNull null
                        if (pair.length() >= 2) pair.optString(0) to pair.optString(1) else null
                    }
                } ?: emptyList(),
                setHashtreeDisabledFlag = obj.optBoolean("set_hashtree_disabled_flag", false),
                includedPartitions = obj.optJSONArray("included_partitions")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList(),
                chainPartitions = obj.optJSONArray("chain_partitions")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList(),
            )
        }
        return ProfileSpec(keyStorePath = raw.optString("key_store_path", "keys"), partitions = orderPartitions(specs))
    }

    private fun resolveKeyPath(profile: ProfileStore.ProfileEntry, keyId: String): String? {
        val keysManifest = File(profile.dir, "keys/manifest.json")
        if (keysManifest.isFile) {
            runCatching {
                val obj = JSONObject(keysManifest.readText())
                val entry = obj.optJSONObject(keyId)
                if (entry != null) {
                    val priv = entry.optString("private_key")
                    if (priv.isNotBlank()) {
                        return File(profile.dir, "keys/$priv").absolutePath
                    }
    }
            }.getOrNull()
        }
        return null
    }

    private data class ProfileSpec(
        val keyStorePath: String,
        val partitions: List<ProfilePartitionSpec>,
    )

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                ProfileViewModel(
                    appContext = app,
                    store = ProfileStore(app),
                    activeStore = ActiveProfileStore(app),
                    runner = AvbTaskRunner(app),
                    bridge = SafFileBridge(app),
                )
            }
        }
    }
}
