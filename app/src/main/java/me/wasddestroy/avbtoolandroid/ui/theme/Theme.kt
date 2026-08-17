package me.wasddestroy.avbtoolandroid.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

enum class ColorVariant(@param:StringRes val labelRes: Int) {
    TONAL_SPOT(R.string.settings_color_variant_tonal_spot),
    EXPRESSIVE(R.string.settings_color_variant_expressive),
    VIBRANT(R.string.settings_color_variant_vibrant),
    CONTENT(R.string.settings_color_variant_content),
    FIDELITY(R.string.settings_color_variant_fidelity),
    RAINBOW(R.string.settings_color_variant_rainbow),
    FRUIT_SALAD(R.string.settings_color_variant_fruit_salad),
    NEUTRAL(R.string.settings_color_variant_neutral),
    MONOCHROME(R.string.settings_color_variant_monochrome),
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
    colorVariant: ColorVariant = ColorVariant.TONAL_SPOT,
    content: @Composable () -> Unit
) {
    val spec = colorSpecVersion.toLibrarySpec()
    val colorScheme = when {
        darkTheme && amoledBlack -> buildAmoledBlackScheme(spec, colorVariant)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val systemScheme = if (darkTheme) dynamicDarkColorScheme(context)
                               else dynamicLightColorScheme(context)
            val seedArgb = systemScheme.primary.toArgb()
            buildSchemeFromSeed(seedArgb, darkTheme, spec, colorVariant)
        }
        darkTheme -> buildSchemeFromSeed(SEED_COLOR, true, spec, colorVariant)
        else -> buildSchemeFromSeed(SEED_COLOR, false, spec, colorVariant)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private fun buildAmoledBlackScheme(
    specVersion: ColorSpec.SpecVersion,
    variant: ColorVariant,
): ColorScheme {
    val base = buildSchemeFromSeed(SEED_COLOR, isDark = true, specVersion, variant)
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
