package me.wasddestroy.avbtoolandroid

import android.content.Context
import android.content.Intent
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
import java.math.BigInteger

/**
 * One partition of an imported signing profile. Mirrors the config
 * generator's v3 `PartitionConfig` schema (CONFIG_EXPANSION.md §3.2), so
 * every field the generator can write is parsed and mapped onto avbtool
 * flags in [buildAvbArgs].
 */
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
    // hash: partition size is derived from the image instead of fixed.
    val dynamicPartitionSize: Boolean = false,
    // Location of the main vbmeta rollback index (all three commands).
    val rollbackIndexLocation: Long? = null,
    // Footer hash algorithm; the generator defaults to sha256, while bare
    // avbtool would silently fall back to sha1 for hashtree partitions.
    val hashAlgorithm: String = "sha256",
    val propFromFile: List<Pair<String, String>> = emptyList(),
    val setVerificationDisabledFlag: Boolean = false,
    // hashtree-specific
    val blockSize: Long = 4096,
    val doNotGenerateFec: Boolean = false,
    val fecNumRoots: Long = 2,
    val noHashtree: Boolean = false,
    val checkAtMostOnce: Boolean = false,
    val setupAsRootfsFromKernel: Boolean = false,
    // vbmeta / footer common
    val includeDescriptorsFromImage: List<String> = emptyList(),
    val chainPartitionsDoNotUseAb: List<String> = emptyList(),
    val kernelCmdlines: List<String> = emptyList(),
    val setupRootfsFromKernel: String? = null,
    val paddingSize: Long? = null,
    val outputVbmetaImage: String? = null,
    // behavior switches
    val calcMaxImageSize: Boolean = false,
    val doNotAppendVbmetaImage: Boolean = false,
    val printRequiredLibavbVersion: Boolean = false,
    val usePersistentDigest: Boolean = false,
    val doNotUseAb: Boolean = false,
    // signing helper
    val signingHelper: String? = null,
    val signingHelperWithFiles: String? = null,
    val publicKeyMetadata: String? = null,
    val appendToReleaseString: String? = null,
)

/**
 * Data backing the sign-scope dialog: every partition of the active profile
 * with its descriptor type and the live result of probing its input image
 * (footer partitions only). Null while no dialog is shown.
 *
 * [existingRollbackIndex] carries the rollback index each probed image
 * currently stores (info_image parse with a direct header read as fallback);
 * re-signing rewrites that value, so the dialog can warn where the profile
 * sets a different one.
 */
data class SignScopePlan(
    val partitions: List<String> = emptyList(),
    val descriptors: Map<String, String> = emptyMap(),
    val imageAvailable: Map<String, Boolean> = emptyMap(),
    val existingRollbackIndex: Map<String, BigInteger> = emptyMap(),
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
    /** Set while an add-partition parse is in flight; disables the dialog buttons. */
    val addingPartition: Boolean = false,
    /** One-shot outcome of an add-partition attempt; consumed by the screen. */
    val addPartitionEvent: AddPartitionEvent? = null,
    /** Set while the sign-scope probes run; the Sign button shows busy. */
    val probingScope: Boolean = false,
    /** Non-null shows the sign-scope dialog, populated by [ProfileViewModel.prepareSignScope]. */
    val signPlan: SignScopePlan? = null,
    /**
     * Non-null shows the rollback-index warning before a pending action
     * (profile import). The staged archive continues on confirmation.
     */
    val rollbackFindings: List<RollbackIndexFinding>? = null,
    /** Validated archive awaiting the rollback-index confirmation, one-shot. */
    val pendingImport: ProfileStore.StagedProfileImport? = null,
)

/**
 * Outcome of one add-partition attempt. The dialog stays open on
 * [InvalidImage] / [NoImage] / [NameConflict] so the user can pick
 * "use default descriptor" or give up; [Success] closes it.
 */
sealed class AddPartitionEvent {
    /** The picked image carried valid descriptors and was added to the profile. */
    data class Success(val partitionName: String) : AddPartitionEvent()

    /** avbtool ran but the image carries no valid VBMeta descriptors. */
    data object InvalidImage : AddPartitionEvent()

    /** No image file was picked for the new partition. */
    data object NoImage : AddPartitionEvent()

    /** The resolved partition name already exists in the profile. */
    data object NameConflict : AddPartitionEvent()
}

class ProfileViewModel(
    private val appContext: Context,
    private val store: ProfileStore,
    private val activeStore: ActiveProfileStore,
    private val runner: AvbTaskRunner,
    private val bridge: SafFileBridge,
    /** Read fresh at import time, so the dangerous skip-verification toggle applies immediately. */
    private val settings: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(activeId = activeStore.read()))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val imageSelections = mutableMapOf<String, String>()
    private val imageSelectionStore = ProfileImageSelectionStore(appContext)

    init {
        imageSelections.putAll(imageSelectionStore.read())
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
        if (_uiState.value.importing || _uiState.value.pendingImport != null) return
        viewModelScope.launch {
            // Validate the archive (manifest, checksums, schema) and stage it
            // first, so the rollback-index warning below is only ever shown
            // for an import that will actually succeed on confirmation.
            val staged = withContext(Dispatchers.IO) {
                store.stageImport(
                    bytes,
                    verifyChecksums = !settings.read().skipProfileArchiveVerification,
                )
            }
            if (staged == null) {
                _uiState.update { it.copy(message = R.string.profile_import_failed) }
                refresh()
                return@launch
            }
            val findings = withContext(Dispatchers.IO) { scanStagedRollbackIndexes(staged) }
            if (findings.isEmpty()) {
                finishImport(staged)
            } else {
                _uiState.update { it.copy(pendingImport = staged, rollbackFindings = findings) }
            }
        }
    }

    /** Continues an import that was gated by the rollback-index warning. */
    fun confirmRollbackImport() {
        val staged = _uiState.value.pendingImport
        _uiState.update { it.copy(pendingImport = null, rollbackFindings = null) }
        if (staged != null) finishImport(staged)
    }

    /** Aborts an import gated by the rollback-index warning. */
    fun dismissRollbackWarning() {
        val staged = _uiState.value.pendingImport
        _uiState.update { it.copy(pendingImport = null, rollbackFindings = null) }
        staged?.let { pending ->
            viewModelScope.launch {
                withContext(Dispatchers.IO) { store.discardImport(pending) }
            }
        }
    }

    private fun finishImport(staged: ProfileStore.StagedProfileImport) {
        if (_uiState.value.importing) return
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true, message = null) }
            val id = withContext(Dispatchers.IO) { store.commitImport(staged) }
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

    /**
     * Classifies every partition's rollback_index in a staged, already
     * checksum-verified profile. Long semantics match [parseProfile], so what
     * this flags is exactly what signing would later emit; a missing or
     * unreadable profile.json yields no findings.
     */
    private fun scanStagedRollbackIndexes(
        staged: ProfileStore.StagedProfileImport,
    ): List<RollbackIndexFinding> {
        val profileJson = runCatching {
            JSONObject(File(staged.dir, "profile.json").readText())
        }.getOrNull() ?: return emptyList()
        val partitions = profileJson.optJSONObject("partitions") ?: return emptyList()
        val now = System.currentTimeMillis() / 1000
        val findings = mutableListOf<RollbackIndexFinding>()
        for (name in partitions.keys()) {
            val obj = partitions.optJSONObject(name) ?: continue
            if (!obj.has("rollback_index")) continue
            val verdict = RollbackIndexGuard.classify(BigInteger.valueOf(obj.optLong("rollback_index")), now)
            if (verdict !is RollbackIndexVerdict.Ok) {
                findings += RollbackIndexFinding(name, verdict)
            }
        }
        return findings
    }

    /**
     * Creates an empty profile with the given id/name and activates it.
     * Id validity is checked again here so the dialog cannot be bypassed;
     * duplicate ids fail inside [ProfileStore.createProfile].
     */
    fun createProfile(id: String, name: String) {
        if (!ProfileStore.isValidProfileId(id)) {
            _uiState.update { it.copy(message = R.string.profile_create_invalid_id) }
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { store.createProfile(id, name) }
            _uiState.update {
                if (ok) {
                    activeStore.write(id)
                    it.copy(
                        activeId = id,
                        message = R.string.profile_create_success,
                    )
                } else {
                    it.copy(message = R.string.profile_create_failed)
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
            // Drop grants only after removal, so shared URIs survive.
            imageSelections.values.forEach { releasePersistableGrant(it.toUri()) }
            imageSelectionStore.write(imageSelections)
            refresh()
        }
    }

    fun selectProfile(id: String) {
        activeStore.write(id)
        _uiState.update { it.copy(activeId = id) }
        refresh()
    }

    fun consumeAddPartitionEvent() {
        _uiState.update { it.copy(addPartitionEvent = null) }
    }

    /** Next "defaultN" name that avoids all existing partitions of the active profile. */
    private fun nextDefaultPartitionName(existing: Set<String>): String {
        var n = 1
        while ("default$n" in existing) n++
        return "default$n"
    }

    private fun existingPartitionNames(profileId: String): Set<String> {
        val entry = _uiState.value.profiles.find { it.id == profileId } ?: return emptySet()
        return runCatching {
            JSONObject(File(entry.dir, "profile.json").readText())
        }.getOrNull()?.optJSONObject("partitions")?.let { obj ->
            obj.keys().asSequence().toSet()
        } ?: emptySet()
    }

    /**
     * Resolves the partition name for a new entry, per the product rules:
     * the user-entered name (or the image file name when the toggle is on)
     * wins; otherwise the parsed descriptor's Partition Name; otherwise an
     * auto-incremented defaultN. Returns null on collision with an existing
     * partition so the caller can reject the entry.
     */
    private fun resolvePartitionName(
        userInput: String?,
        imageFileName: String?,
        parsedName: String?,
        existing: Set<String>,
    ): String? {
        val candidate = userInput?.trim()?.takeIf { it.isNotEmpty() }
            ?: imageFileName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }
            ?: parsedName?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        if (candidate in existing) return null
        return candidate
    }

    /**
     * Adds a partition from the add-partition dialog inputs. When [uri] is
     * set, the image is registered like a normal per-partition pick and
     * inspected via `avbtool info_image`; a parseable footer/vbmeta is
     * written into profile.json, anything else raises
     * [AddPartitionEvent.InvalidImage]. With [uri] null — or after the user
     * chose "use default descriptor" on the warning dialog — a default
     * entry (hash, auto-incremented defaultN unless a name is available,
     * 4096 bytes, rollback index 0, algorithm NONE, sha256, key "default")
     * is written instead.
     */
    fun addPartition(name: String, useImageFileName: Boolean, imageFileName: String?, uri: Uri?) {
        val profileId = _uiState.value.activeId ?: return
        val trimmed = name.trim()
        val desired = when {
            useImageFileName && !imageFileName.isNullOrBlank() ->
                imageFileName.substringBeforeLast('.').trim()
            else -> trimmed
        }
        // When the toggle is on the user input is disabled and carries no
        // meaning; otherwise it doubles as the descriptor-name fallback.
        val descriptorFallback = if (useImageFileName) null else trimmed.takeIf { it.isNotEmpty() }

        if (uri == null) {
            writeDefaultPartition(profileId, desired, descriptorFallback)
            return
        }

        if (_uiState.value.addingPartition) return
        viewModelScope.launch {
            _uiState.update { it.copy(addingPartition = true, addPartitionEvent = null) }
            val event = withContext(Dispatchers.IO) {
                inspectAndAddPartition(profileId, uri, desired, descriptorFallback)
            }
            _uiState.update {
                it.copy(addingPartition = false, addPartitionEvent = event)
            }
        }
    }

    private suspend fun inspectAndAddPartition(
        profileId: String,
        uri: Uri,
        desiredName: String,
        descriptorFallback: String?,
    ): AddPartitionEvent {
        val fileName = bridge.displayName(uri)
        val fd = bridge.openRead(uri)
        if (fd == null) {
            return AddPartitionEvent.InvalidImage
        }
        val result = try {
            runner.run(listOf("avbtool", "info_image", "--image", bridge.pseudoPath(fd)))
        } finally {
            bridge.closeFd(fd)
        }

        // avbtool exits non-zero for images without a footer/vbmeta; a blank
        // stderr plus parseable descriptors means the image is valid.
        val inspection = if (result.stderr.isBlank()) {
            runCatching { InfoImageParser.inspect(fileName, result.stdout) }.getOrNull()
        } else {
            null
        } ?: return AddPartitionEvent.InvalidImage

        val existing = existingPartitionNames(profileId)
        val partitionName = resolvePartitionName(
            userInput = desiredName.takeIf { it.isNotEmpty() },
            imageFileName = fileName,
            parsedName = inspection.partitionName,
            existing = existing,
        )
        if (partitionName == null) {
            return AddPartitionEvent.NameConflict
        }

        // vbmeta images are generated at sign time — no input image to
        // register. Footer images are registered like any per-partition
        // pick so signing re-uses the existing scratch-copy pipeline.
        if (inspection.descriptor != "vbmeta") {
            setImageInternal(profileId, partitionName, uri)
        }

        val ok = store.updateProfileJson(profileId) { obj ->
            obj.optJSONObject("partitions")?.put(
                partitionName,
                buildPartitionEntry(inspection, partitionName, fileName),
            )
        }
        return if (ok) {
            refresh()
            AddPartitionEvent.Success(partitionName)
        } else {
            // JSON write failed: undo the image pick so no orphan grant/URI
            // survives for a partition the profile does not know about.
            setImageInternal(profileId, partitionName, null)
            AddPartitionEvent.InvalidImage
        }
    }

    /** Writes a fallback default-descriptor partition entry into profile.json. */
    private fun writeDefaultPartition(profileId: String, desiredName: String, descriptorFallback: String?) {
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) {
                val existing = existingPartitionNames(profileId)
                val name = desiredName.takeIf { it.isNotEmpty() && it !in existing }
                    ?: descriptorFallback?.takeIf { it !in existing }
                    ?: nextDefaultPartitionName(existing)
                val entry = defaultPartitionEntry(name)
                store.updateProfileJson(profileId) { obj ->
                    obj.optJSONObject("partitions")?.put(name, entry)
                }.let { name to it }
            }
            _uiState.update { state ->
                if (created.second) {
                    refresh()
                    state.copy(addPartitionEvent = AddPartitionEvent.Success(created.first))
                } else {
                    state.copy(addPartitionEvent = AddPartitionEvent.InvalidImage)
                }
            }
        }
    }

    /**
     * The default descriptor offered when an image is missing or invalid:
     * hash footer, 4096 bytes, rollback index 0, algorithm NONE, sha256,
     * key mapping "default" (the key store is assumed pre-provisioned).
     */
    private fun defaultPartitionEntry(partitionName: String): JSONObject {
        return JSONObject().apply {
            put("image", "$partitionName.img")
            put("descriptor", "hash")
            put("algorithm", "NONE")
            put("key_id", "default")
            put("partition_name", partitionName)
            put("partition_size", 4096)
            put("rollback_index", 0)
            put("hash_algorithm", "sha256")
        }
    }

    /**
     * Maps a parsed info_image inspection onto a v3 partition entry, mirroring
     * the reference project's `_build_auto_partition`: signing algorithm and
     * rollback index come from the vbmeta header, the hash algorithm from the
     * descriptor (defaulting to sha256), flags are preserved as an integer,
     * and hash footers reuse the image's total size as the partition size so
     * `add_hash_footer` has a size to work with.
     */
    private fun buildPartitionEntry(
        inspection: InfoImageParser.ImageInspection,
        partitionName: String,
        imageFileName: String,
    ): JSONObject {
        val flags = inspection.flags ?: 0L
        val entry = JSONObject().apply {
            put("image", imageFileName)
            put("descriptor", inspection.descriptor)
            put("algorithm", inspection.algorithm ?: "NONE")
            put("rollback_index", inspection.rollbackIndex ?: 0L)
            if (flags != 0L) put("flags", flags)
            if (inspection.props.isNotEmpty()) {
                put(
                    "props",
                    JSONArray().apply {
                        inspection.props.forEach { (k, v) ->
                            put(JSONArray().put(k).put(v))
                        }
                    },
                )
            }
        }
        if (inspection.descriptor == "vbmeta") {
            if (inspection.includedPartitions.isNotEmpty()) {
                entry.put(
                    "included_partitions",
                    JSONArray().apply { inspection.includedPartitions.forEach { put(it) } },
                )
            }
            if (inspection.chainPartitions.isNotEmpty()) {
                entry.put(
                    "chain_partitions",
                    JSONArray().apply { inspection.chainPartitions.forEach { put(it) } },
                )
            }
        } else {
            entry.put("partition_name", inspection.partitionName ?: partitionName)
            entry.put("hash_algorithm", inspection.hashAlgorithm ?: "sha256")
            inspection.salt?.let { entry.put("salt", it) }
            if (inspection.descriptor == "hash" && inspection.partitionSize != null && inspection.partitionSize > 0) {
                entry.put("partition_size", inspection.partitionSize)
            }
            if (flags and 1L != 0L) entry.put("set_hashtree_disabled_flag", true)
            if (flags and 2L != 0L) entry.put("set_verification_disabled_flag", true)
        }
        return entry
    }

    /**
     * Deletes the given partitions from the active profile's profile.json and
     * drops their image picks (releasing SAF grants when no longer shared).
     */
    fun deletePartitions(names: Collection<String>) {
        val profileId = _uiState.value.activeId ?: return
        if (names.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.updateProfileJson(profileId) { obj ->
                    obj.optJSONObject("partitions")?.let { partitions ->
                        names.forEach { partitions.remove(it) }
                    }
                }
                names.forEach { name ->
                    imageSelections.remove("$profileId:$name")?.let { uriStr ->
                        releasePersistableGrant(uriStr.toUri())
                    }
                }
                imageSelectionStore.write(imageSelections)
            }
            refresh()
        }
    }

    fun setImage(partition: String, uri: Uri?) {
        val profileId = _uiState.value.activeId ?: return
        setImageInternal(profileId, partition, uri)
    }

    /**
     * Records or clears a per-partition image pick. Persistable grants are
     * taken on record and released on removal — but only when no other
     * partition still references the same URI (grants are app-wide).
     */
    private fun setImageInternal(profileId: String, partition: String, uri: Uri?) {
        if (uri != null) {
            imageSelections["$profileId:$partition"] = uri.toString()
            // Persist read+write so the grant survives reboots and re-signing
            // can open the image in place again without re-picking it.
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        } else {
            val removed = imageSelections.remove("$profileId:$partition")
            removed?.let { releasePersistableGrant(it.toUri()) }
        }
        imageSelectionStore.write(imageSelections)
        publishImageSummaries()
    }

    /**
     * Releases the persistable grant held for [uri] unless it is still
     * referenced by another selection; grants are app-wide and per-URI.
     */
    private fun releasePersistableGrant(uri: Uri) {
        if (imageSelections.values.none { it == uri.toString() }) {
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
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
    fun signActive(scope: Set<String>) {
        val state = _uiState.value
        val profile = state.profiles.find { it.id == state.activeId }
        if (profile == null) {
            _uiState.update { it.copy(message = R.string.profile_sign_no_active) }
            return
        }
        if (state.signing) return

        viewModelScope.launch {
            _uiState.update { it.copy(signing = true, result = null) }
            val outcome = withContext(Dispatchers.IO) { signProfile(profile, scope) }
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

    /**
     * Prepares the sign-scope dialog data: probes every footer partition's
     * picked image by actually opening it (catches deleted files and dead
     * SAF grants that a mere map lookup would miss) and computes each
     * partition's dependency feasibility. vbmeta rows need no input image
     * but demand their image dependencies inside the scope.
     */
    fun prepareSignScope() {
        val state = _uiState.value
        val profile = state.profiles.find { it.id == state.activeId } ?: return
        if (state.signing || state.probingScope) return
        viewModelScope.launch {
            _uiState.update { it.copy(probingScope = true) }
            val plan = withContext(Dispatchers.IO) {
                val raw = runCatching {
                    JSONObject(File(profile.dir, "profile.json").readText())
                }.getOrNull()
                val spec = raw?.let { runCatching { parseProfile(it) }.getOrNull() }
                if (spec == null) {
                    SignScopePlan(partitions = emptyList())
                } else {
                    val probe = spec.partitions
                        .filter { it.descriptor != "vbmeta" }
                        .associate { it.partition to probeImage(it.partition) }
                    SignScopePlan(
                        partitions = spec.partitions.map { it.partition },
                        descriptors = spec.partitions.associate { it.partition to it.descriptor },
                        imageAvailable = probe.mapValues { it.value.first },
                        existingRollbackIndex = buildMap {
                            probe.forEach { (partition, result) ->
                                result.second?.let { put(partition, it) }
                            }
                        },
                    )
                }
            }
            _uiState.update { it.copy(probingScope = false, signPlan = plan) }
        }
    }

    /**
     * Opens and immediately closes the picked image to verify it is readable,
     * then resolves its current rollback index (info_image parse with the
     * direct footer/header read as fallback; null when none is readable).
     */
    private suspend fun probeImage(partition: String): Pair<Boolean, BigInteger?> {
        val uri = getImage(partition)?.toUri() ?: return false to null
        val fd = bridge.openRead(uri)
        if (fd == null) return false to null
        bridge.closeFd(fd)
        val existing = AvbRollbackIndexReader.read(runner, bridge, appContext, uri)
        return true to existing
    }

    fun dismissSignPlan() {
        _uiState.update { it.copy(signPlan = null) }
    }

    private data class SignOutcome(
        val result: ProfileSignResult?,
        val exports: List<File>,
    )

    private suspend fun signProfile(profile: ProfileStore.ProfileEntry, scope: Set<String>): SignOutcome {
        val raw = runCatching {
            JSONObject(File(profile.dir, "profile.json").readText())
        }.getOrElse {
            return failedOutcome(profile, R.string.profile_error_invalid_profile)
        }
        val spec = runCatching { parseProfile(raw) }.getOrElse {
            return failedOutcome(profile, R.string.profile_error_invalid_profile)
        }

        // vbmeta partitions generate their image at sign time; only footer
        // partitions consume a picked input image.
        val missing = spec.partitions
            .filter { it.partition in scope && it.descriptor != "vbmeta" && getImage(it.partition) == null }
        if (missing.isNotEmpty()) {
            return failedOutcome(profile, R.string.profile_error_missing_images)
        }
        val planned = spec.partitions.filter { it.partition in scope }

        val scratch = File(appContext.filesDir, "profile_sign/${profile.id}")
        scratch.deleteRecursively()
        scratch.mkdirs()

        // Partitions whose signed images are pulled into a vbmeta via
        // --include_descriptors_from_image; their scratch copies must exist
        // even when the image itself was signed in place. Computed from the
        // scope: only in-scope vbmeta partitions pull sources this run.
        val includeSources = planned
            .filter { it.descriptor == "vbmeta" }
            .flatMap { it.includedPartitions }
            .toSet()

        val log = StringBuilder()
        var ok = true
        val outputs = mutableListOf<File>()

        try {
            // planned keeps spec.partitions order, which is dependency order
            // (see parseProfile).
            for (p in planned) {
                // vbmeta images are generated, not read: the picked-URI map
                // has no entry for them (the UI no longer offers a picker).
                val srcUri = if (p.descriptor != "vbmeta") getImage(p.partition)!! else null
                val imageDir = File(scratch, p.partition)
                imageDir.mkdirs()
                val target = File(imageDir, p.image)

                // Footer images are modified in place through a SAF read-write
                // fd when the provider allows it; only then does the file fall
                // back to a private copy whose export needs a save dialog.
                // vbmeta images are generated from scratch either way.
                val fd = srcUri?.let { bridge.openReadWrite(it.toUri()) }
                val inPlace = fd != null
                try {
                    if (fd == null && srcUri != null) {
                        val copied = copyUriToFile(srcUri.toUri(), target)
                        if (!copied) {
                            log.appendLine("[${p.partition}] failed to read the selected image")
                            ok = false
                            break
                        }
                    }

                    val args = buildAvbArgs(p, profile, imageDir, scratch, spec, uiState.value.addPropsToVbmeta, fd)
                    val res = runner.run(args)
                    log.appendLine("[${p.partition}] " + (if (res.exitCode != 0) "FAILED" else "OK"))
                    if (res.stdout.isNotBlank()) log.appendLine(res.stdout.trim())
                    if (res.stderr.isNotBlank()) log.appendLine(res.stderr.trim())
                    if (res.exitCode != 0) {
                        ok = false
                        break
                    }

                    when {
                        p.descriptor == "vbmeta" -> outputs += File(imageDir, p.image)
                        inPlace -> {
                            val uri = srcUri!!
                            if (p.partition in includeSources) {
                                copyUriToFile(uri.toUri(), target)
                            }
                        }
                        else -> outputs += target
                    }
                } finally {
                    fd?.let { bridge.closeFd(it) }
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
        inPlaceFd: Int? = null,
    ): List<String> {
        val argv = mutableListOf("avbtool")
        if (p.descriptor == "vbmeta") {
            argv += "make_vbmeta_image"
            // The vbmeta image is generated, not modified: p.image is the output name.
            argv += listOf("--output", File(imageDir, p.image).absolutePath)
            p.rollbackIndex?.let { argv += listOf("--rollback_index", it.toString()) }
            p.rollbackIndexLocation?.let { argv += listOf("--rollback_index_location", it.toString()) }
            p.flags?.let { argv += listOf("--flags", it.toString()) }
            if (p.setHashtreeDisabledFlag) argv += "--set_hashtree_disabled_flag"
            if (p.setVerificationDisabledFlag) argv += "--set_verification_disabled_flag"
            p.paddingSize?.let { argv += listOf("--padding_size", it.toString()) }
            fullSpec?.partitions?.forEach { inc ->
                if (inc.partition in p.includedPartitions) {
                    argv += "--include_descriptors_from_image"
                    argv += File(File(scratchRoot, inc.partition), inc.image).absolutePath
                }
            }
            p.includeDescriptorsFromImage.forEach { entry ->
                argv += "--include_descriptors_from_image"
                argv += resolveImageFile(entry, fullSpec, scratchRoot, profile)
            }
            p.chainPartitions.forEach { entry ->
                argv += "--chain_partition"
                argv += resolveChainEntry(entry, profile)
            }
            p.chainPartitionsDoNotUseAb.forEach { entry ->
                argv += "--chain_partition_do_not_use_ab"
                argv += resolveChainEntry(entry, profile)
            }
            p.kernelCmdlines.forEach { argv += listOf("--kernel_cmdline", it) }
            p.setupRootfsFromKernel?.let {
                argv += listOf("--setup_rootfs_from_kernel", resolveImageFile(it, fullSpec, scratchRoot, profile))
            }
            if (p.printRequiredLibavbVersion) argv += "--print_required_libavb_version"
            p.signingHelper?.let { argv += listOf("--signing_helper", it) }
            p.signingHelperWithFiles?.let { argv += listOf("--signing_helper_with_files", it) }
            p.publicKeyMetadata?.let { argv += listOf("--public_key_metadata", resolveProfileFile(profile, it)) }
            p.appendToReleaseString?.let { argv += listOf("--append_to_release_string", it) }
        } else {
            argv += "add_${p.descriptor}_footer"
            argv += "--image"
            // When signing in place, the input is the SAF fd pseudo-path;
            // android_bridge.py rewinds each open back to the start.
            argv += if (inPlaceFd != null) {
                bridge.pseudoPath(inPlaceFd)
            } else {
                File(imageDir, p.image).absolutePath
            }
            argv += listOf("--partition_name", p.partitionName)
            p.partitionSize?.let { argv += listOf("--partition_size", it.toString()) }
            // Only add_hash_footer knows --dynamic_partition_size; avbtool has
            // no such option for add_hashtree_footer.
            if (p.descriptor == "hash" && p.dynamicPartitionSize) argv += "--dynamic_partition_size"
            // Emitted explicitly: bare avbtool defaults hashtree to sha1,
            // while the config generator (and this app) always mean sha256
            // unless the profile says otherwise.
            argv += listOf("--hash_algorithm", p.hashAlgorithm)
            p.rollbackIndex?.let { argv += listOf("--rollback_index", it.toString()) }
            p.rollbackIndexLocation?.let { argv += listOf("--rollback_index_location", it.toString()) }
            p.salt?.let { argv += listOf("--salt", it) }
            if (p.descriptor == "hashtree") {
                argv += listOf("--block_size", p.blockSize.toString())
                if (p.doNotGenerateFec) argv += "--do_not_generate_fec"
                if (p.fecNumRoots != 2L) argv += listOf("--fec_num_roots", p.fecNumRoots.toString())
                if (p.noHashtree) argv += "--no_hashtree"
                if (p.checkAtMostOnce) argv += "--check_at_most_once"
                if (p.setupAsRootfsFromKernel) argv += "--setup_as_rootfs_from_kernel"
            }
            p.flags?.let { argv += listOf("--flags", it.toString()) }
            if (p.setHashtreeDisabledFlag) argv += "--set_hashtree_disabled_flag"
            if (p.setVerificationDisabledFlag) argv += "--set_verification_disabled_flag"
            if (p.calcMaxImageSize) argv += "--calc_max_image_size"
            if (p.doNotAppendVbmetaImage) argv += "--do_not_append_vbmeta_image"
            p.includeDescriptorsFromImage.forEach { entry ->
                argv += "--include_descriptors_from_image"
                argv += resolveImageFile(entry, fullSpec, scratchRoot, profile)
            }
            p.chainPartitions.forEach { entry ->
                argv += "--chain_partition"
                argv += resolveChainEntry(entry, profile)
            }
            p.chainPartitionsDoNotUseAb.forEach { entry ->
                argv += "--chain_partition_do_not_use_ab"
                argv += resolveChainEntry(entry, profile)
            }
            p.outputVbmetaImage?.let {
                val out = File(imageDir, it)
                out.parentFile?.mkdirs()
                argv += listOf("--output_vbmeta_image", out.absolutePath)
            }
            p.setupRootfsFromKernel?.let {
                argv += listOf("--setup_rootfs_from_kernel", resolveImageFile(it, fullSpec, scratchRoot, profile))
            }
            if (p.printRequiredLibavbVersion) argv += "--print_required_libavb_version"
            if (p.usePersistentDigest) argv += "--use_persistent_digest"
            if (p.doNotUseAb) argv += "--do_not_use_ab"
            p.signingHelper?.let { argv += listOf("--signing_helper", it) }
            p.signingHelperWithFiles?.let { argv += listOf("--signing_helper_with_files", it) }
            p.publicKeyMetadata?.let { argv += listOf("--public_key_metadata", resolveProfileFile(profile, it)) }
            p.appendToReleaseString?.let { argv += listOf("--append_to_release_string", it) }
        }
        argv += listOf("--algorithm", p.algorithm)
        val keyPath = p.keyId?.let { resolveKeyPath(profile, it) }
        if (keyPath != null) {
            argv += listOf("--key", keyPath)
        }
        // avbtool appends every --prop verbatim without deduplicating keys.
        // For footer commands the props land in the partition's reserved size
        // so they cannot grow the image; for make_vbmeta_image each prop
        // extends the generated blob, so adding them is opt-in. The same
        // gate covers --prop_from_file.
        if (p.descriptor != "vbmeta" || addPropsToVbmeta) {
            p.props.forEach { (k, v) ->
                argv += "--prop"
                argv += "$k:$v"
            }
            p.propFromFile.forEach { (k, path) ->
                argv += "--prop_from_file"
                argv += "$k:${resolveProfileFile(profile, path)}"
            }
        }
        return argv
    }

    /** Rewrites "partition:rollback:keyfile.bin", resolving the key file against the profile's keys dir. */
    private fun resolveChainEntry(entry: String, profile: ProfileStore.ProfileEntry): String {
        val parts = entry.split(":")
        if (parts.size < 3) return entry
        val keyFile = File(profile.dir, "keys/${parts.drop(2).joinToString(":")}")
        return "${parts[0]}:${parts[1]}:${keyFile.absolutePath}"
    }

    /**
     * Resolves an image reference from the profile: a staged partition image
     * by file name, else a file inside the profile folder, else the raw value
     * (avbtool reports the missing file).
     */
    private fun resolveImageFile(
        entry: String,
        fullSpec: ProfileSpec?,
        scratchRoot: File,
        profile: ProfileStore.ProfileEntry,
    ): String {
        fullSpec?.partitions?.firstOrNull { it.image == entry }?.let {
            return File(File(scratchRoot, it.partition), it.image).absolutePath
        }
        val inProfile = File(profile.dir, entry)
        if (inProfile.isFile) return inProfile.absolutePath
        return entry
    }

    /** Resolves a loose file reference (public key metadata, prop source) against the profile folder / key store. */
    private fun resolveProfileFile(profile: ProfileStore.ProfileEntry, path: String): String {
        val inProfile = File(profile.dir, path)
        if (inProfile.isFile) return inProfile.absolutePath
        val inKeys = File(profile.dir, "keys/$path")
        if (inKeys.isFile) return inKeys.absolutePath
        return path
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
                props = parsePairs(obj.opt("props")),                setHashtreeDisabledFlag = obj.optBoolean("set_hashtree_disabled_flag", false),
                includedPartitions = obj.optStringList("included_partitions"),
                chainPartitions = obj.optStringList("chain_partitions"),
                dynamicPartitionSize = obj.optBoolean("dynamic_partition_size", false),
                rollbackIndexLocation = obj.optLong("rollback_index_location").takeIf { obj.has("rollback_index_location") },
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
                setupRootfsFromKernel = obj.optString("setup_rootfs_from_kernel").takeIf { it.isNotBlank() },
                paddingSize = obj.optLong("padding_size").takeIf { it > 0 },
                outputVbmetaImage = obj.optString("output_vbmeta_image").takeIf { it.isNotBlank() },
                calcMaxImageSize = obj.optBoolean("calc_max_image_size", false),
                doNotAppendVbmetaImage = obj.optBoolean("do_not_append_vbmeta_image", false),
                printRequiredLibavbVersion = obj.optBoolean("print_required_libavb_version", false),
                usePersistentDigest = obj.optBoolean("use_persistent_digest", false),
                doNotUseAb = obj.optBoolean("do_not_use_ab", false),
                signingHelper = obj.optString("signing_helper").takeIf { it.isNotBlank() },
                signingHelperWithFiles = obj.optString("signing_helper_with_files").takeIf { it.isNotBlank() },
                publicKeyMetadata = obj.optString("public_key_metadata").takeIf { it.isNotBlank() },
                appendToReleaseString = obj.optString("append_to_release_string").takeIf { it.isNotBlank() },
            )
        }
        return ProfileSpec(keyStorePath = raw.optString("key_store_path", "keys"), partitions = orderPartitions(specs))
    }

    /**
     * Parses props/prop_from_file. Accepts the canonical [[k, v], ...] form,
     * a flat [k, v] pair, or a {k: v} object (all three are produced by the
     * config generator or its legacy codecs).
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

    private fun JSONObject.optStringList(name: String): List<String> {
        return optJSONArray(name)?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        } ?: emptyList()
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
                    settings = SettingsStore(
                        app.getSharedPreferences("application_configs", Context.MODE_PRIVATE),
                    ),
                )
            }
        }
    }
}
