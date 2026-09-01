package me.wasddestroy.avbtoolandroid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceRow
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceSwitchRow
import me.wasddestroy.avbtoolandroid.ui.components.SettingsList
import me.wasddestroy.avbtoolandroid.ui.components.preferenceGroup
import me.wasddestroy.avbtoolandroid.ui.theme.ColorSpecVersion
import me.wasddestroy.avbtoolandroid.ui.theme.ColorVariant
import me.wasddestroy.avbtoolandroid.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    var showThemeModeDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showColorSpecDialog by rememberSaveable { mutableStateOf(false) }
    var showVariantDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    SettingsList(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item("header") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        preferenceGroup {
            row("dynamic_theme_color") {
                PreferenceSwitchRow(
                    checked = settings.dynamicThemeColor,
                    onCheckedChange = viewModel::setDynamicThemeColor,
                    title = stringResource(R.string.settings_dynamic_theme_color),
                    summary = stringResource(R.string.settings_dynamic_theme_color_summary),
                    iconContent = { SettingsIcon(Icons.Filled.Palette) },
                )
            }
            row("color_spec_version") {
                PreferenceRow(
                    title = stringResource(R.string.settings_color_spec),
                    summary = stringResource(settings.colorSpecVersion.labelRes),
                    iconContent = { SettingsIcon(Icons.Filled.Tune) },
                    onClick = { showColorSpecDialog = true },
                )
            }
            row("color_variant") {
                PreferenceRow(
                    title = stringResource(R.string.settings_color_variant),
                    summary = stringResource(settings.colorVariant.labelRes),
                    iconContent = { SettingsIcon(Icons.Filled.Style) },
                    onClick = { showVariantDialog = true },
                )
            }
            row("theme_mode") {
                PreferenceRow(
                    title = stringResource(R.string.settings_theme_mode),
                    summary = stringResource(settings.themeMode.labelRes),
                    iconContent = { SettingsIcon(Icons.Filled.Brightness6) },
                    onClick = { showThemeModeDialog = true },
                )
            }
            row("amoled_black") {
                PreferenceSwitchRow(
                    checked = settings.amoledBlack,
                    onCheckedChange = viewModel::setAmoledBlack,
                    title = stringResource(R.string.settings_amoled_black),
                    summary = stringResource(R.string.settings_amoled_black_summary),
                    iconContent = { SettingsIcon(Icons.Filled.DarkMode) },
                )
            }
            row("predictive_back_gesture") {
                PreferenceSwitchRow(
                    checked = settings.predictiveBackGesture,
                    onCheckedChange = viewModel::setPredictiveBackGesture,
                    title = stringResource(R.string.settings_predictive_back),
                    summary = stringResource(R.string.settings_predictive_back_summary),
                    iconContent = { SettingsIcon(Icons.AutoMirrored.Filled.ArrowBack) },
                )
            }
            row("function_keyboard") {
                PreferenceSwitchRow(
                    checked = settings.showFunctionKeyboard,
                    onCheckedChange = viewModel::setShowFunctionKeyboard,
                    title = stringResource(R.string.settings_function_keyboard),
                    summary = stringResource(R.string.settings_function_keyboard_summary),
                    iconContent = { SettingsIcon(Icons.Filled.Keyboard) },
                )
            }
            row("skip_profile_archive_verification") {
                PreferenceSwitchRow(
                    checked = settings.skipProfileArchiveVerification,
                    onCheckedChange = viewModel::setSkipProfileArchiveVerification,
                    title = stringResource(R.string.settings_skip_profile_verification),
                    summary = stringResource(R.string.settings_skip_profile_verification_summary),
                    iconContent = { SettingsIcon(Icons.Filled.Warning) },
                )
            }
            row("language") {
                PreferenceRow(
                    title = stringResource(R.string.settings_language),
                    summary = stringResource(settings.languageMode.labelRes),
                    iconContent = { SettingsIcon(Icons.Filled.Language) },
                    onClick = { showLanguageDialog = true },
                )
            }
            row("about") {
                PreferenceRow(
                    title = stringResource(R.string.settings_about),
                    iconContent = { SettingsIcon(Icons.Filled.Info) },
                    onClick = { showAboutDialog = true },
                )
            }
        }
    }

    if (showThemeModeDialog) {
        ThemeModeDialog(
            currentMode = settings.themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                showThemeModeDialog = false
            },
            onDismiss = { showThemeModeDialog = false },
        )
    }

    if (showColorSpecDialog) {
        ColorSpecDialog(
            currentSpec = settings.colorSpecVersion,
            onSelect = {
                viewModel.setColorSpecVersion(it)
                showColorSpecDialog = false
            },
            onDismiss = { showColorSpecDialog = false },
        )
    }

    if (showVariantDialog) {
        ColorVariantDialog(
            currentVariant = settings.colorVariant,
            onSelect = {
                viewModel.setColorVariant(it)
                showVariantDialog = false
            },
            onDismiss = { showVariantDialog = false },
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentMode = settings.languageMode,
            onSelect = {
                viewModel.setLanguageMode(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
    )
}

@Composable
private fun ThemeModeDialog(
    currentMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme_mode_dialog_title)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = null,
                        )
                        Text(
                            text = stringResource(mode.labelRes),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_ok))
            }
        },
    )
}

@Composable
private fun ColorSpecDialog(
    currentSpec: ColorSpecVersion,
    onSelect: (ColorSpecVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_color_spec_dialog_title)) },
        text = {
            Column {
                ColorSpecVersion.entries.forEach { spec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(spec) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = spec == currentSpec,
                            onClick = null,
                        )
                        Text(
                            text = stringResource(spec.labelRes),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_ok))
            }
        },
    )
}

@Composable
private fun ColorVariantDialog(
    currentVariant: ColorVariant,
    onSelect: (ColorVariant) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_color_variant_dialog_title)) },
        text = {
            Column {
                ColorVariant.entries.forEach { variant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(variant) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = variant == currentVariant,
                            onClick = null,
                        )
                        Text(
                            text = stringResource(variant.labelRes),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_ok))
            }
        },
    )
}

@Composable
private fun LanguageDialog(
    currentMode: LanguageMode,
    onSelect: (LanguageMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_dialog_title)) },
        text = {
            Column {
                LanguageMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = null,
                        )
                        Text(
                            text = stringResource(mode.labelRes),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_ok))
            }
        },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val packageInfo = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName ?: stringResource(R.string.settings_about_version_unknown)
    val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    val aboutGitHubUrl = stringResource(R.string.settings_about_github_url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.settings_about_dialog_title))
        },
        text = {
            Column {
                Text(stringResource(R.string.settings_about_dialog_message, versionName, versionCode.toString()))
                Text(
                    text = stringResource(R.string.settings_about_license_notice),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    runCatching { uriHandler.openUri(aboutGitHubUrl) }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.settings_about_github))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_ok))
            }
        },
    )
}
