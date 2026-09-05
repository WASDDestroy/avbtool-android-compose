package me.wasddestroy.avbtoolandroid

import android.content.SharedPreferences
import me.wasddestroy.avbtoolandroid.ui.theme.ColorSpecVersion
import me.wasddestroy.avbtoolandroid.ui.theme.ColorVariant
import me.wasddestroy.avbtoolandroid.ui.theme.ThemeMode

class SettingsStore(private val sp: SharedPreferences) {

    fun read(): SettingsUiState = SettingsUiState(
        dynamicThemeColor = sp.getBoolean(KEY_DYNAMIC_THEME, true),
        themeMode = sp.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.FOLLOW_SYSTEM,
        amoledBlack = sp.getBoolean(KEY_AMOLED_BLACK, false),
        predictiveBackGesture = sp.getBoolean(KEY_PREDICTIVE_BACK, true),
        languageMode = sp.getString(KEY_LANGUAGE_MODE, null)
            ?.let { runCatching { LanguageMode.valueOf(it) }.getOrNull() }
            ?: LanguageMode.FOLLOW_SYSTEM,
        showFunctionKeyboard = sp.getBoolean(KEY_SHOW_FUNCTION_KEYBOARD, true),
        colorSpecVersion = sp.getString(KEY_COLOR_SPEC_VERSION, null)
            ?.let { runCatching { ColorSpecVersion.valueOf(it) }.getOrNull() }
            ?: ColorSpecVersion.SPEC_2021,
        colorVariant = sp.getString(KEY_COLOR_VARIANT, null)
            ?.let { runCatching { ColorVariant.valueOf(it) }.getOrNull() }
            ?: ColorVariant.TONAL_SPOT,
        skipProfileArchiveVerification = sp.getBoolean(KEY_SKIP_PROFILE_VERIFICATION, false),
    )

    fun write(s: SettingsUiState) {
        sp.edit()
            .putBoolean(KEY_DYNAMIC_THEME, s.dynamicThemeColor)
            .putString(KEY_THEME_MODE, s.themeMode.name)
            .putBoolean(KEY_AMOLED_BLACK, s.amoledBlack)
            .putBoolean(KEY_PREDICTIVE_BACK, s.predictiveBackGesture)
            .putString(KEY_LANGUAGE_MODE, s.languageMode.name)
            .putBoolean(KEY_SHOW_FUNCTION_KEYBOARD, s.showFunctionKeyboard)
            .putString(KEY_COLOR_SPEC_VERSION, s.colorSpecVersion.name)
            .putString(KEY_COLOR_VARIANT, s.colorVariant.name)
            .putBoolean(KEY_SKIP_PROFILE_VERIFICATION, s.skipProfileArchiveVerification)
            .apply()
    }

    /** SAF tree URI of the partition-dump workspace, persisted independently
     *  of SettingsUiState because only the partition reader uses it. */
    fun readPartitionWorkspaceUri(): String? =
        sp.getString(KEY_PARTITION_WORKSPACE, null)

    fun writePartitionWorkspaceUri(value: String?) {
        sp.edit().putString(KEY_PARTITION_WORKSPACE, value).apply()
    }

    companion object {
        private const val KEY_PARTITION_WORKSPACE = "partition_workspace_uri"
        private const val KEY_DYNAMIC_THEME = "dynamic_theme_color"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AMOLED_BLACK = "amoled_black"
        private const val KEY_PREDICTIVE_BACK = "predictive_back_gesture"
        private const val KEY_LANGUAGE_MODE = "language_mode"
        private const val KEY_SHOW_FUNCTION_KEYBOARD = "show_function_keyboard"
        private const val KEY_COLOR_SPEC_VERSION = "color_spec_version"
        private const val KEY_COLOR_VARIANT = "color_variant"
        private const val KEY_SKIP_PROFILE_VERIFICATION = "skip_profile_archive_verification"
    }
}
