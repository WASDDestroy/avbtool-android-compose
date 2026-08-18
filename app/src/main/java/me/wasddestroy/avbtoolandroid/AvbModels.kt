package me.wasddestroy.avbtoolandroid

import androidx.annotation.StringRes


enum class ArgType { IMAGE, TEXT, INT, BOOL, FILE, ALGORITHM, CHAIN_PARTITION }

data class AvbArg(
    val key: String,
    @param:StringRes val labelRes: Int,
    val type: ArgType,
    val required: Boolean = false,
    val defaultValue: String? = null,
    @param:StringRes val helpRes: Int? = null,
    val advanced: Boolean = false,
    val repeatable: Boolean = false,
    @param:StringRes val hintRes: Int? = null
)

enum class AvbCommandKind { IMAGE_TOOL, VBMETA_GENERATOR }

enum class HomeSegment(@param:StringRes val labelRes: Int) {
    IMAGE_TOOLS(R.string.home_segment_image_tools),
    VBMETA(R.string.home_segment_vbmeta),
    OTHERS(R.string.home_segment_others),
}

data class AvbFileInput(
    val key: String,
    @param:StringRes val labelRes: Int,
    val required: Boolean = true,
    val repeatable: Boolean = false,
)

data class AvbFileOutput(
    val key: String,
    @param:StringRes val labelRes: Int,
    val suffix: String = ".img",
    val binary: Boolean = true,
)

data class AvbCommand(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val args: List<AvbArg>,
    val kind: AvbCommandKind = AvbCommandKind.IMAGE_TOOL,
    val group: HomeSegment = HomeSegment.IMAGE_TOOLS,
    val inputs: List<AvbFileInput> = emptyList(),
    val outputs: List<AvbFileOutput> = emptyList(),
    val readOnly: Boolean = true,
    val producesFile: Boolean = false
) {
}

object AvbCommands {
    val all: List<AvbCommand> = listOf(
        AvbCommand(
            id = "add_hash_footer",
            titleRes = R.string.command_add_hash_footer_title,
            descriptionRes = R.string.command_add_hash_footer_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            inputs = listOf(AvbFileInput("--image", R.string.arg_add_hash_footer_image_label, required = true)),
            args = listOf(
                AvbArg("--partition_size", R.string.arg_add_hash_footer_partition_size_label, ArgType.INT),
                AvbArg("--dynamic_partition_size", R.string.arg_add_hash_footer_dynamic_partition_size_label, ArgType.BOOL),
                AvbArg("--partition_name", R.string.arg_add_hash_footer_partition_name_label, ArgType.TEXT),
                AvbArg("--hash_algorithm", R.string.arg_add_hash_footer_hash_algorithm_label, ArgType.TEXT, defaultValue = "sha256"),
                AvbArg("--salt", R.string.arg_add_hash_footer_salt_label, ArgType.TEXT),
                AvbArg("--algorithm", R.string.arg_add_hash_footer_algorithm_label, ArgType.ALGORITHM, defaultValue = "NONE"),
                AvbArg("--key", R.string.arg_add_hash_footer_key_label, ArgType.FILE),
                AvbArg("--calc_max_image_size", R.string.arg_add_hash_footer_calc_max_image_size_label, ArgType.BOOL),
                AvbArg("--do_not_append_vbmeta_image", R.string.arg_add_hash_footer_do_not_append_vbmeta_image_label, ArgType.BOOL),
                AvbArg("--rollback_index", R.string.arg_add_hash_footer_rollback_index_label, ArgType.INT),
                AvbArg("--prop", R.string.arg_add_hash_footer_prop_label, ArgType.TEXT, repeatable = true, hintRes = R.string.arg_add_hash_footer_prop_hint),
                AvbArg("--include_descriptors_from_image", R.string.arg_add_hash_footer_include_descriptors_from_image_label, ArgType.FILE, repeatable = true),
                AvbArg("--flags", R.string.arg_add_hash_footer_flags_label, ArgType.INT),

                AvbArg("--output_vbmeta_image", R.string.arg_add_hash_footer_output_vbmeta_image_label, ArgType.TEXT, advanced = true),
                AvbArg("--signing_helper", R.string.arg_add_hash_footer_signing_helper_label, ArgType.TEXT, advanced = true),
                AvbArg("--signing_helper_with_files", R.string.arg_add_hash_footer_signing_helper_with_files_label, ArgType.TEXT, advanced = true),
                AvbArg("--public_key_metadata", R.string.arg_add_hash_footer_public_key_metadata_label, ArgType.FILE, advanced = true),
                AvbArg("--rollback_index_location", R.string.arg_add_hash_footer_rollback_index_location_label, ArgType.INT, advanced = true),
                AvbArg("--append_to_release_string", R.string.arg_add_hash_footer_append_to_release_string_label, ArgType.TEXT, advanced = true),
                AvbArg("--prop_from_file", R.string.arg_add_hash_footer_prop_from_file_label, ArgType.TEXT, repeatable = true, advanced = true, hintRes = R.string.arg_add_hash_footer_prop_from_file_hint),
                AvbArg("--kernel_cmdline", R.string.arg_add_hash_footer_kernel_cmdline_label, ArgType.TEXT, repeatable = true, advanced = true, hintRes = R.string.arg_add_hash_footer_kernel_cmdline_hint),
                AvbArg("--setup_rootfs_from_kernel", R.string.arg_add_hash_footer_setup_rootfs_from_kernel_label, ArgType.FILE, advanced = true),
                AvbArg("--print_required_libavb_version", R.string.arg_add_hash_footer_print_required_libavb_version_label, ArgType.BOOL, advanced = true),
                AvbArg("--chain_partition", R.string.arg_add_hash_footer_chain_partition_label, ArgType.CHAIN_PARTITION, repeatable = true, advanced = true),
                AvbArg("--chain_partition_do_not_use_ab", R.string.arg_add_hash_footer_chain_partition_do_not_use_ab_label, ArgType.TEXT, repeatable = true, advanced = true, hintRes = R.string.arg_add_hash_footer_chain_partition_do_not_use_ab_hint),
                AvbArg("--set_hashtree_disabled_flag", R.string.arg_add_hash_footer_set_hashtree_disabled_flag_label, ArgType.BOOL, advanced = true),
                AvbArg("--set_verification_disabled_flag", R.string.arg_add_hash_footer_set_verification_disabled_flag_label, ArgType.BOOL, advanced = true),
                AvbArg("--use_persistent_digest", R.string.arg_add_hash_footer_use_persistent_digest_label, ArgType.BOOL, advanced = true),
                AvbArg("--do_not_use_ab", R.string.arg_add_hash_footer_do_not_use_ab_label, ArgType.BOOL, advanced = true)
            ),
            readOnly = false, producesFile = false
        ),
        AvbCommand(
            id = "add_hashtree_footer",
            titleRes = R.string.command_add_hashtree_footer_title,
            descriptionRes = R.string.command_add_hashtree_footer_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            inputs = listOf(AvbFileInput("--image", R.string.arg_add_hashtree_footer_image_label, required = true)),
            args = listOf(
                AvbArg("--partition_size", R.string.arg_add_hashtree_footer_partition_size_label, ArgType.INT),
                AvbArg("--partition_name", R.string.arg_add_hashtree_footer_partition_name_label, ArgType.TEXT),
                AvbArg("--hash_algorithm", R.string.arg_add_hashtree_footer_hash_algorithm_label, ArgType.TEXT, defaultValue = "sha256"),
                AvbArg("--salt", R.string.arg_add_hashtree_footer_salt_label, ArgType.TEXT),
                AvbArg("--algorithm", R.string.arg_add_hashtree_footer_algorithm_label, ArgType.ALGORITHM, defaultValue = "NONE"),
                AvbArg("--key", R.string.arg_add_hashtree_footer_key_label, ArgType.FILE),
                AvbArg("--block_size", R.string.arg_add_hashtree_footer_block_size_label, ArgType.INT, defaultValue = "4096"),
                AvbArg("--do_not_generate_fec", R.string.arg_add_hashtree_footer_do_not_generate_fec_label, ArgType.BOOL),
                AvbArg("--fec_num_roots", R.string.arg_add_hashtree_footer_fec_num_roots_label, ArgType.INT, defaultValue = "2"),
                AvbArg("--calc_max_image_size", R.string.arg_add_hashtree_footer_calc_max_image_size_label, ArgType.BOOL),
                AvbArg("--do_not_append_vbmeta_image", R.string.arg_add_hashtree_footer_do_not_append_vbmeta_image_label, ArgType.BOOL),
                AvbArg("--rollback_index", R.string.arg_add_hashtree_footer_rollback_index_label, ArgType.INT),
                AvbArg("--prop", R.string.arg_add_hashtree_footer_prop_label, ArgType.TEXT, repeatable = true, hintRes = R.string.arg_add_hashtree_footer_prop_hint),
                AvbArg("--chain_partition", R.string.arg_add_hashtree_footer_chain_partition_label, ArgType.CHAIN_PARTITION, repeatable = true),
                AvbArg("--flags", R.string.arg_add_hashtree_footer_flags_label, ArgType.INT),
                AvbArg("--include_descriptors_from_image", R.string.arg_add_hashtree_footer_include_descriptors_from_image_label, ArgType.FILE, repeatable = true),

                AvbArg("--output_vbmeta_image", R.string.arg_add_hashtree_footer_output_vbmeta_image_label, ArgType.TEXT, advanced = true),
                AvbArg("--no_hashtree", R.string.arg_add_hashtree_footer_no_hashtree_label, ArgType.BOOL, advanced = true),
                AvbArg("--check_at_most_once", R.string.arg_add_hashtree_footer_check_at_most_once_label, ArgType.BOOL, advanced = true),
                AvbArg("--setup_as_rootfs_from_kernel", R.string.arg_add_hashtree_footer_setup_as_rootfs_from_kernel_label, ArgType.BOOL, advanced = true),
                AvbArg("--signing_helper", R.string.arg_add_hashtree_footer_signing_helper_label, ArgType.TEXT, advanced = true),
                AvbArg("--signing_helper_with_files", R.string.arg_add_hashtree_footer_signing_helper_with_files_label, ArgType.TEXT, advanced = true),
                AvbArg("--public_key_metadata", R.string.arg_add_hashtree_footer_public_key_metadata_label, ArgType.FILE, advanced = true),
                AvbArg("--rollback_index_location", R.string.arg_add_hashtree_footer_rollback_index_location_label, ArgType.INT, advanced = true),
                AvbArg("--append_to_release_string", R.string.arg_add_hashtree_footer_append_to_release_string_label, ArgType.TEXT, advanced = true),
                AvbArg("--prop_from_file", R.string.arg_add_hashtree_footer_prop_from_file_label, ArgType.TEXT, repeatable = true, advanced = true, hintRes = R.string.arg_add_hashtree_footer_prop_from_file_hint),
                AvbArg("--kernel_cmdline", R.string.arg_add_hashtree_footer_kernel_cmdline_label, ArgType.TEXT, repeatable = true, advanced = true, hintRes = R.string.arg_add_hashtree_footer_kernel_cmdline_hint),
                AvbArg("--setup_rootfs_from_kernel", R.string.arg_add_hashtree_footer_setup_rootfs_from_kernel_label, ArgType.FILE, advanced = true),
                AvbArg("--print_required_libavb_version", R.string.arg_add_hashtree_footer_print_required_libavb_version_label, ArgType.BOOL, advanced = true),
                AvbArg("--chain_partition_do_not_use_ab", R.string.arg_add_hashtree_footer_chain_partition_do_not_use_ab_label, ArgType.TEXT, repeatable = true, advanced = true, hintRes = R.string.arg_add_hashtree_footer_chain_partition_do_not_use_ab_hint),
                AvbArg("--set_hashtree_disabled_flag", R.string.arg_add_hashtree_footer_set_hashtree_disabled_flag_label, ArgType.BOOL, advanced = true),
                AvbArg("--set_verification_disabled_flag", R.string.arg_add_hashtree_footer_set_verification_disabled_flag_label, ArgType.BOOL, advanced = true),
                AvbArg("--use_persistent_digest", R.string.arg_add_hashtree_footer_use_persistent_digest_label, ArgType.BOOL, advanced = true),
                AvbArg("--do_not_use_ab", R.string.arg_add_hashtree_footer_do_not_use_ab_label, ArgType.BOOL, advanced = true)
            ),
            readOnly = false, producesFile = false
        ),
        AvbCommand(
            id = "make_vbmeta_image",
            titleRes = R.string.command_make_vbmeta_image_title,
            descriptionRes = R.string.command_make_vbmeta_image_description,
            kind = AvbCommandKind.VBMETA_GENERATOR,
            group = HomeSegment.VBMETA,
            outputs = listOf(AvbFileOutput("--output", R.string.arg_make_vbmeta_image_output_label, ".img")),
            args = listOf(
                AvbArg("--algorithm", R.string.arg_make_vbmeta_image_algorithm_label, ArgType.ALGORITHM, defaultValue = "NONE"),
                AvbArg("--key", R.string.arg_make_vbmeta_image_key_label, ArgType.FILE),
                AvbArg("--rollback_index", R.string.arg_make_vbmeta_image_rollback_index_label, ArgType.INT),
                AvbArg("--rollback_index_location", R.string.arg_make_vbmeta_image_rollback_index_location_label, ArgType.INT),
                AvbArg("--prop", R.string.arg_make_vbmeta_image_prop_label, ArgType.TEXT, repeatable = true, hintRes = R.string.arg_make_vbmeta_image_prop_hint),
                AvbArg("--include_descriptors_from_image", R.string.arg_make_vbmeta_image_include_descriptors_from_image_label, ArgType.FILE, repeatable = true),
                AvbArg("--chain_partition", R.string.arg_make_vbmeta_image_chain_partition_label, ArgType.CHAIN_PARTITION, repeatable = true),
                AvbArg("--flags", R.string.arg_make_vbmeta_image_flags_label, ArgType.INT),
                AvbArg("--set_hashtree_disabled_flag", R.string.arg_make_vbmeta_image_set_hashtree_disabled_flag_label, ArgType.BOOL),
                AvbArg("--set_verification_disabled_flag", R.string.arg_make_vbmeta_image_set_verification_disabled_flag_label, ArgType.BOOL),
                AvbArg("--padding_size", R.string.arg_make_vbmeta_image_padding_size_label, ArgType.INT, advanced = true),
                AvbArg("--signing_helper", R.string.arg_make_vbmeta_image_signing_helper_label, ArgType.TEXT, advanced = true),
                AvbArg("--signing_helper_with_files", R.string.arg_make_vbmeta_image_signing_helper_with_files_label, ArgType.TEXT, advanced = true),
                AvbArg("--public_key_metadata", R.string.arg_make_vbmeta_image_public_key_metadata_label, ArgType.FILE, advanced = true),
                AvbArg("--append_to_release_string", R.string.arg_make_vbmeta_image_append_to_release_string_label, ArgType.TEXT, advanced = true),
                AvbArg("--prop_from_file", R.string.arg_make_vbmeta_image_prop_from_file_label, ArgType.TEXT, repeatable = true, advanced = true, hintRes = R.string.arg_make_vbmeta_image_prop_from_file_hint),
                AvbArg("--kernel_cmdline", R.string.arg_make_vbmeta_image_kernel_cmdline_label, ArgType.TEXT, repeatable = true, advanced = true, hintRes = R.string.arg_make_vbmeta_image_kernel_cmdline_hint),
                AvbArg("--setup_rootfs_from_kernel", R.string.arg_make_vbmeta_image_setup_rootfs_from_kernel_label, ArgType.FILE, advanced = true),
                AvbArg("--print_required_libavb_version", R.string.arg_make_vbmeta_image_print_required_libavb_version_label, ArgType.BOOL, advanced = true),
                AvbArg("--chain_partition_do_not_use_ab", R.string.arg_make_vbmeta_image_chain_partition_do_not_use_ab_label, ArgType.TEXT, repeatable = true, advanced = true, hintRes = R.string.arg_make_vbmeta_image_chain_partition_do_not_use_ab_hint)
            ),
            readOnly = false, producesFile = true
        ),
        AvbCommand(
            id = "info_image", titleRes = R.string.command_info_image_title, descriptionRes = R.string.command_info_image_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            inputs = listOf(AvbFileInput("--image", R.string.arg_info_image_image_label, required = true)),
            args = listOf(
                AvbArg("--cert", R.string.arg_info_image_cert_label, ArgType.BOOL)
            ),
            readOnly = true, producesFile = false
        ),
        AvbCommand(
            id = "erase_footer", titleRes = R.string.command_erase_footer_title, descriptionRes = R.string.command_erase_footer_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            inputs = listOf(AvbFileInput("--image", R.string.arg_erase_footer_image_label, required = true)),
            args = listOf(
                AvbArg("--keep_hashtree", R.string.arg_erase_footer_keep_hashtree_label, ArgType.BOOL)
            ),
            readOnly = false, producesFile = true
        ),
        AvbCommand(
            id = "resize_image", titleRes = R.string.command_resize_image_title, descriptionRes = R.string.command_resize_image_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            inputs = listOf(AvbFileInput("--image", R.string.arg_resize_image_image_label, required = true)),
            args = listOf(
                AvbArg("--partition_size", R.string.arg_resize_image_partition_size_label, ArgType.INT, required = true)
            ),
            readOnly = false, producesFile = true
        ),
        AvbCommand(
            id = "extract_vbmeta_image", titleRes = R.string.command_extract_vbmeta_image_title, descriptionRes = R.string.command_extract_vbmeta_image_description,
            outputs = listOf(AvbFileOutput("--output", R.string.command_output, ".img")),
            kind = AvbCommandKind.IMAGE_TOOL,
            inputs = listOf(AvbFileInput("--image", R.string.arg_extract_vbmeta_image_image_label, required = true)),
            args = listOf(
                AvbArg("--padding_size", R.string.arg_extract_vbmeta_image_padding_size_label, ArgType.INT, defaultValue = "0")
            ),
            readOnly = true, producesFile = true
        ),
        AvbCommand(
            id = "print_partition_digests", titleRes = R.string.command_print_partition_digests_title, descriptionRes = R.string.command_print_partition_digests_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            inputs = listOf(AvbFileInput("--image", R.string.arg_print_partition_digests_image_label, required = true)),
            args = listOf(
                AvbArg("--json", R.string.arg_print_partition_digests_json_label, ArgType.BOOL)
            ),
            readOnly = true, producesFile = false
        ),
        AvbCommand(
            id = "calculate_vbmeta_digest", titleRes = R.string.command_calculate_vbmeta_digest_title, descriptionRes = R.string.command_calculate_vbmeta_digest_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            inputs = listOf(AvbFileInput("--image", R.string.arg_calculate_vbmeta_digest_image_label, required = true)),
            args = listOf(
                AvbArg("--hash_algorithm", R.string.arg_calculate_vbmeta_digest_hash_algorithm_label, ArgType.TEXT, defaultValue = "sha256")
            ),
            readOnly = true, producesFile = false
        ),
        AvbCommand(
            id = "verify_image", titleRes = R.string.command_verify_image_title, descriptionRes = R.string.command_verify_image_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            inputs = listOf(AvbFileInput("--image", R.string.arg_verify_image_image_label, required = true)),
            args = listOf(
                AvbArg("--key", R.string.arg_verify_image_key_label, ArgType.FILE),
                AvbArg("--accept_zeroed_hashtree", R.string.arg_verify_image_accept_zeroed_hashtree_label, ArgType.BOOL)
            ),
            readOnly = true, producesFile = false
        ),
        AvbCommand(
            id = "zero_hashtree", titleRes = R.string.command_zero_hashtree_title, descriptionRes = R.string.command_zero_hashtree_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            inputs = listOf(AvbFileInput("--image", R.string.arg_zero_hashtree_image_label, required = true)),
            args = listOf(
            ),
            readOnly = false, producesFile = true
        ),
        AvbCommand(
            id = "calculate_kernel_cmdline", titleRes = R.string.command_calculate_kernel_cmdline_title, descriptionRes = R.string.command_calculate_kernel_cmdline_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            group = HomeSegment.OTHERS,
            inputs = listOf(AvbFileInput("--image", R.string.arg_calculate_kernel_cmdline_image_label, required = true)),
            args = listOf(
                AvbArg("--hashtree_disabled", R.string.arg_calculate_kernel_cmdline_hashtree_disabled_label, ArgType.BOOL)
            ),
            readOnly = true, producesFile = false
        ),
        AvbCommand(
            id = "extract_public_key", titleRes = R.string.command_extract_public_key_title, descriptionRes = R.string.command_extract_public_key_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            outputs = listOf(AvbFileOutput("--output", R.string.arg_extract_public_key_output_label, ".bin")),
            args = listOf(
                AvbArg("--key", R.string.arg_extract_public_key_key_label, ArgType.FILE, required = true)
            ),
            readOnly = true, producesFile = true
        ),
        AvbCommand(
            id = "extract_public_key_digest",
            titleRes = R.string.command_extract_public_key_digest_title,
            descriptionRes = R.string.command_extract_public_key_digest_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            outputs = listOf(AvbFileOutput("--output", R.string.arg_extract_public_key_digest_output_label, ".bin")),
            args = listOf(
                AvbArg("--key", R.string.arg_extract_public_key_digest_key_label, ArgType.FILE, required = true)
            ),
            readOnly = true, producesFile = true
        ),
        AvbCommand(
            id = "append_vbmeta_image",
            titleRes = R.string.command_append_vbmeta_image_title,
            descriptionRes = R.string.command_append_vbmeta_image_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            inputs = listOf(AvbFileInput("--image", R.string.arg_append_vbmeta_image_image_label)),
            args = listOf(
                AvbArg("--partition_size", R.string.arg_append_vbmeta_image_partition_size_label, ArgType.INT, required = true),
                AvbArg("--vbmeta_image", R.string.arg_append_vbmeta_image_vbmeta_image_label, ArgType.FILE)
            ),
            readOnly = false, producesFile = false
        ),
        AvbCommand(
            id = "set_ab_metadata",
            titleRes = R.string.command_set_ab_metadata_title,
            descriptionRes = R.string.command_set_ab_metadata_description,
            kind = AvbCommandKind.IMAGE_TOOL,
            group = HomeSegment.OTHERS,
            inputs = listOf(AvbFileInput("--misc_image", R.string.arg_set_ab_metadata_misc_image_label, required = true)),
            args = listOf(
                AvbArg("--slot_data", R.string.arg_set_ab_metadata_slot_data_label, ArgType.TEXT, hintRes = R.string.arg_set_ab_metadata_slot_data_hint)
            ),
            readOnly = false, producesFile = false
        ),
        AvbCommand(
            id = "make_certificate",
            titleRes = R.string.command_make_certificate_title,
            descriptionRes = R.string.command_make_certificate_description,
            kind = AvbCommandKind.VBMETA_GENERATOR,
            group = HomeSegment.OTHERS,
            outputs = listOf(AvbFileOutput("--output", R.string.arg_make_certificate_output_label, ".bin")),
            args = listOf(
                AvbArg("--subject", R.string.arg_make_certificate_subject_label, ArgType.FILE, required = true),
                AvbArg("--subject_key", R.string.arg_make_certificate_subject_key_label, ArgType.FILE, required = true),
                AvbArg("--subject_key_version", R.string.arg_make_certificate_subject_key_version_label, ArgType.INT),
                AvbArg("--authority_key", R.string.arg_make_certificate_authority_key_label, ArgType.FILE),
                AvbArg("--subject_is_intermediate_authority", R.string.arg_make_certificate_subject_is_intermediate_authority_label, ArgType.BOOL),
                AvbArg("--usage", R.string.arg_make_certificate_usage_label, ArgType.TEXT),
                AvbArg("--usage_for_unlock", R.string.arg_make_certificate_usage_for_unlock_label, ArgType.BOOL),
                AvbArg("--signing_helper", R.string.arg_make_certificate_signing_helper_label, ArgType.TEXT, advanced = true),
                AvbArg("--signing_helper_with_files", R.string.arg_make_certificate_signing_helper_with_files_label, ArgType.TEXT, advanced = true)
            ),
            readOnly = false, producesFile = true
        ),
        AvbCommand(
            id = "make_cert_permanent_attributes",
            titleRes = R.string.command_make_cert_permanent_attributes_title,
            descriptionRes = R.string.command_make_cert_permanent_attributes_description,
            kind = AvbCommandKind.VBMETA_GENERATOR,
            group = HomeSegment.OTHERS,
            outputs = listOf(AvbFileOutput("--output", R.string.arg_make_cert_permanent_attributes_output_label, ".bin")),
            args = listOf(
                AvbArg("--root_authority_key", R.string.arg_make_cert_permanent_attributes_root_authority_key_label, ArgType.FILE, required = true),
                AvbArg("--product_id", R.string.arg_make_cert_permanent_attributes_product_id_label, ArgType.FILE, required = true)
            ),
            readOnly = false, producesFile = true
        ),
        AvbCommand(
            id = "make_cert_metadata",
            titleRes = R.string.command_make_cert_metadata_title,
            descriptionRes = R.string.command_make_cert_metadata_description,
            kind = AvbCommandKind.VBMETA_GENERATOR,
            group = HomeSegment.OTHERS,
            outputs = listOf(AvbFileOutput("--output", R.string.arg_make_cert_metadata_output_label, ".bin")),
            args = listOf(
                AvbArg("--intermediate_key_certificate", R.string.arg_make_cert_metadata_intermediate_key_certificate_label, ArgType.FILE, required = true),
                AvbArg("--product_key_certificate", R.string.arg_make_cert_metadata_product_key_certificate_label, ArgType.FILE, required = true)
            ),
            readOnly = false, producesFile = true
        ),
        AvbCommand(
            id = "make_cert_unlock_credential",
            titleRes = R.string.command_make_cert_unlock_credential_title,
            descriptionRes = R.string.command_make_cert_unlock_credential_description,
            kind = AvbCommandKind.VBMETA_GENERATOR,
            group = HomeSegment.OTHERS,
            outputs = listOf(AvbFileOutput("--output", R.string.arg_make_cert_unlock_credential_output_label, ".bin")),
            args = listOf(
                AvbArg("--intermediate_key_certificate", R.string.arg_make_cert_unlock_credential_intermediate_key_certificate_label, ArgType.FILE, required = true),
                AvbArg("--unlock_key_certificate", R.string.arg_make_cert_unlock_credential_unlock_key_certificate_label, ArgType.FILE, required = true),
                AvbArg("--challenge", R.string.arg_make_cert_unlock_credential_challenge_label, ArgType.FILE),
                AvbArg("--unlock_key", R.string.arg_make_cert_unlock_credential_unlock_key_label, ArgType.FILE),
                AvbArg("--signing_helper", R.string.arg_make_cert_unlock_credential_signing_helper_label, ArgType.TEXT, advanced = true),
                AvbArg("--signing_helper_with_files", R.string.arg_make_cert_unlock_credential_signing_helper_with_files_label, ArgType.TEXT, advanced = true)
            ),
            readOnly = false, producesFile = true
        ),
    )

    fun byId(id: String): AvbCommand? = all.find { it.id == id }
}
