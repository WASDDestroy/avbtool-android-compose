package me.wasddestroy.avbtoolandroid.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.dynamiccolor.ColorSpec
import me.wasddestroy.avbtoolandroid.R

enum class ThemeMode(@param:StringRes val labelRes: Int) {
    LIGHT(R.string.settings_theme_mode_light),
    DARK(R.string.settings_theme_mode_dark),
    FOLLOW_SYSTEM(R.string.settings_theme_mode_follow_system),
}

enum class ColorSpecVersion(@param:StringRes val labelRes: Int) {
    SPEC_2021(R.string.settings_color_spec_2021),
    SPEC_2025(R.string.settings_color_spec_2025),
}

fun ColorSpecVersion.toLibrarySpec(): ColorSpec.SpecVersion = when (this) {
    ColorSpecVersion.SPEC_2021 -> ColorSpec.SpecVersion.SPEC_2021
    ColorSpecVersion.SPEC_2025 -> ColorSpec.SpecVersion.SPEC_2025
}

@Composable
fun AVBToolAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = false,
    colorSpecVersion: ColorSpecVersion = ColorSpecVersion.SPEC_2021,
    content: @Composable () -> Unit
) {
    val spec = colorSpecVersion.toLibrarySpec()
    val colorScheme = when {
        darkTheme && amoledBlack -> buildAmoledBlackScheme(spec)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            buildDynamicSchemeColorScheme(context, darkTheme, spec)
        }
        darkTheme -> buildSchemeColorScheme(isDark = true, specVersion = spec)
        else -> buildSchemeColorScheme(isDark = false, specVersion = spec)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// AMOLED: same accent colors as the derived dark scheme, but every surface
// is pure black. Derived via .copy() so accent colors stay in sync with
// the current spec version.
private fun buildAmoledBlackScheme(specVersion: ColorSpec.SpecVersion): ColorScheme {
    val base = buildSchemeColorScheme(isDark = true, specVersion)
    return base.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = Color.Black,
    )
}
