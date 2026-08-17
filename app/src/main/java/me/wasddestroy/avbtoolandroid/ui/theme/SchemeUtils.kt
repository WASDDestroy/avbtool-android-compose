package me.wasddestroy.avbtoolandroid.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

const val SEED_COLOR = 0xFF0061A4.toInt()

fun buildSchemeColorScheme(
    isDark: Boolean,
    specVersion: ColorSpec.SpecVersion,
): ColorScheme = schemeToColorScheme(
    SchemeTonalSpot(
        sourceColorHct = Hct.fromInt(SEED_COLOR),
        isDark = isDark,
        contrastLevel = 0.0,
        specVersion = specVersion,
    )
)

fun buildDynamicSchemeColorScheme(
    seedArgb: Int,
    isDark: Boolean,
    specVersion: ColorSpec.SpecVersion,
): ColorScheme = schemeToColorScheme(
    SchemeTonalSpot(
        sourceColorHct = Hct.fromInt(seedArgb),
        isDark = isDark,
        contrastLevel = 0.0,
        specVersion = specVersion,
    )
)

private fun schemeToColorScheme(scheme: SchemeTonalSpot): ColorScheme {
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
