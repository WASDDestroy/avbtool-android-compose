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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
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
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceRow
import me.wasddestroy.avbtoolandroid.ui.components.PreferenceSwitchRow
import me.wasddestroy.avbtoolandroid.ui.components.SettingsList
import me.wasddestroy.avbtoolandroid.ui.components.preferenceGroup
import me.wasddestroy.avbtoolandroid.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    dynamicThemeColor: Boolean,
    themeMode: ThemeMode,
    amoledBlack: Boolean,
    predictiveBackGesture: Boolean,
    languageMode: LanguageMode,
    onDynamicThemeColorChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAmoledBlackChange: (Boolean) -> Unit,
    onPredictiveBackGestureChange: (Boolean) -> Unit,
    onLanguageModeChange: (LanguageMode) -> Unit,
) {
    var showThemeModeDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
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
                    checked = dynamicThemeColor,
                    onCheckedChange = onDynamicThemeColorChange,
                    title = stringResource(R.string.settings_dynamic_theme_color),
                    summary = stringResource(R.string.settings_dynamic_theme_color_summary),
                    iconContent = { SettingsIcon(Icons.Filled.Palette) },
                )
            }
            row("theme_mode") {
                PreferenceRow(
                    title = stringResource(R.string.settings_theme_mode),
                    summary = stringResource(themeMode.labelRes),
                    iconContent = { SettingsIcon(Icons.Filled.Brightness6) },
                    onClick = { showThemeModeDialog = true },
                )
            }
            row("amoled_black") {
                PreferenceSwitchRow(
                    checked = amoledBlack,
                    onCheckedChange = onAmoledBlackChange,
                    title = stringResource(R.string.settings_amoled_black),
                    summary = stringResource(R.string.settings_amoled_black_summary),
                    iconContent = { SettingsIcon(Icons.Filled.DarkMode) },
                )
            }
            row("predictive_back_gesture") {
                PreferenceSwitchRow(
                    checked = predictiveBackGesture,
                    onCheckedChange = onPredictiveBackGestureChange,
                    title = stringResource(R.string.settings_predictive_back),
                    summary = stringResource(R.string.settings_predictive_back_summary),
                    iconContent = { SettingsIcon(Icons.AutoMirrored.Filled.ArrowBack) },
                )
            }
            row("language") {
                PreferenceRow(
                    title = stringResource(R.string.settings_language),
                    summary = stringResource(languageMode.labelRes),
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
            currentMode = themeMode,
            onSelect = {
                onThemeModeChange(it)
                showThemeModeDialog = false
            },
            onDismiss = { showThemeModeDialog = false },
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentMode = languageMode,
            onSelect = {
                onLanguageModeChange(it)
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
            Text(stringResource(R.string.settings_about_dialog_message, versionName, versionCode.toString()))
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
