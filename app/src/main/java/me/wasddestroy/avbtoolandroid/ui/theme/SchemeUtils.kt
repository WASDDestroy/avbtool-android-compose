package me.wasddestroy.avbtoolandroid.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

const val SEED_COLOR = 0xFF0061A4.toInt()

fun buildSchemeFromSeed(
    seedArgb: Int,
    isDark: Boolean,
    specVersion: ColorSpec.SpecVersion,
    variant: ColorVariant,
): ColorScheme {
    val hct = Hct.fromInt(seedArgb)
    val scheme: DynamicScheme = when (variant) {
        ColorVariant.TONAL_SPOT -> SchemeTonalSpot(hct, isDark, 0.0, specVersion)
        ColorVariant.EXPRESSIVE -> SchemeExpressive(hct, isDark, 0.0, specVersion)
        ColorVariant.VIBRANT -> SchemeVibrant(hct, isDark, 0.0, specVersion)
        ColorVariant.CONTENT -> SchemeContent(hct, isDark, 0.0, specVersion)
        ColorVariant.FIDELITY -> SchemeFidelity(hct, isDark, 0.0, specVersion)
        ColorVariant.RAINBOW -> SchemeRainbow(hct, isDark, 0.0, specVersion)
        ColorVariant.FRUIT_SALAD -> SchemeFruitSalad(hct, isDark, 0.0, specVersion)
        ColorVariant.NEUTRAL -> SchemeNeutral(hct, isDark, 0.0, specVersion)
        ColorVariant.MONOCHROME -> SchemeMonochrome(hct, isDark, 0.0, specVersion)
    }
    return schemeToColorScheme(scheme)
}

private fun schemeToColorScheme(scheme: DynamicScheme): ColorScheme {
    val c = MaterialDynamicColors()
    val builder = if (scheme.isDark) darkColorScheme() else lightColorScheme()
    return builder.copy(
        primary = Color(scheme.getArgb(c.primary())),
        onPrimary = Color(scheme.getArgb(c.onPrimary())),
        primaryContainer = Color(scheme.getArgb(c.primaryContainer())),
        onPrimaryContainer = Color(scheme.getArgb(c.onPrimaryContainer())),
        secondary = Color(scheme.getArgb(c.secondary())),
        onSecondary = Color(scheme.getArgb(c.onSecondary())),
        secondaryContainer = Color(scheme.getArgb(c.secondaryContainer())),
        onSecondaryContainer = Color(scheme.getArgb(c.onSecondaryContainer())),
        tertiary = Color(scheme.getArgb(c.tertiary())),
        onTertiary = Color(scheme.getArgb(c.onTertiary())),
        tertiaryContainer = Color(scheme.getArgb(c.tertiaryContainer())),
        onTertiaryContainer = Color(scheme.getArgb(c.onTertiaryContainer())),
        error = Color(scheme.getArgb(c.error())),
        onError = Color(scheme.getArgb(c.onError())),
        errorContainer = Color(scheme.getArgb(c.errorContainer())),
        onErrorContainer = Color(scheme.getArgb(c.onErrorContainer())),
        background = Color(scheme.getArgb(c.background())),
        onBackground = Color(scheme.getArgb(c.onBackground())),
        surface = Color(scheme.getArgb(c.surface())),
        onSurface = Color(scheme.getArgb(c.onSurface())),
        surfaceVariant = Color(scheme.getArgb(c.surfaceVariant())),
        onSurfaceVariant = Color(scheme.getArgb(c.onSurfaceVariant())),
        outline = Color(scheme.getArgb(c.outline())),
        outlineVariant = Color(scheme.getArgb(c.outlineVariant())),
        surfaceContainerLowest = Color(scheme.getArgb(c.surfaceContainerLowest())),
        surfaceContainerLow = Color(scheme.getArgb(c.surfaceContainerLow())),
        surfaceContainer = Color(scheme.getArgb(c.surfaceContainer())),
        surfaceContainerHigh = Color(scheme.getArgb(c.surfaceContainerHigh())),
        surfaceContainerHighest = Color(scheme.getArgb(c.surfaceContainerHighest())),
        surfaceDim = Color(scheme.getArgb(c.surfaceDim())),
        surfaceBright = Color(scheme.getArgb(c.surfaceBright())),
        inverseSurface = Color(scheme.getArgb(c.inverseSurface())),
        inverseOnSurface = Color(scheme.getArgb(c.inverseOnSurface())),
        inversePrimary = Color(scheme.getArgb(c.inversePrimary())),
        scrim = Color(scheme.getArgb(c.scrim())),
    )
}
