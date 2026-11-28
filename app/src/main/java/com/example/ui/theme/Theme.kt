package com.example.ui.theme

import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.AppearanceConfig
import com.example.data.model.ThemeMode
import java.io.File

private val ExpressiveDarkScheme = darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = OnCobaltPrimaryDark,
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = OnCobaltContainerDark,
    secondary = TealSecondaryDark,
    onSecondary = OnTealSecondaryDark,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = OnTealContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = OnAmberTertiaryDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = ObsidianBg,
    onBackground = ObsidianOnSurface,
    surface = ObsidianSurface,
    onSurface = ObsidianOnSurface,
    surfaceVariant = ObsidianSurfaceContainerHigh,
    onSurfaceVariant = ObsidianOnSurfaceVariant,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurfaceContainerLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceContainerHigh,
    surfaceContainerHighest = ObsidianSurfaceContainerHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = ErrorRoseDark,
)

private val ExpressiveLightScheme = lightColorScheme(
    primary = CobaltPrimaryLight,
    onPrimary = OnCobaltPrimaryLight,
    primaryContainer = CobaltContainerLight,
    onPrimaryContainer = OnCobaltContainerLight,
    secondary = TealSecondaryLight,
    onSecondary = OnTealSecondaryLight,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = OnTealContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = OnAmberTertiaryLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    background = AlabasterBg,
    onBackground = AlabasterOnSurface,
    surface = AlabasterSurface,
    onSurface = AlabasterOnSurface,
    surfaceVariant = AlabasterSurfaceContainerHigh,
    onSurfaceVariant = AlabasterOnSurfaceVariant,
    surfaceContainerLowest = AlabasterSurface,
    surfaceContainerLow = AlabasterSurfaceContainerLow,
    surfaceContainer = AlabasterSurfaceContainer,
    surfaceContainerHigh = AlabasterSurfaceContainerHigh,
    surfaceContainerHighest = AlabasterSurfaceContainerHighest,
    outline = AlabasterOutline,
    outlineVariant = AlabasterOutlineVariant,
    error = ErrorRoseLight,
)

@Immutable
data class AetherStyleConfig(
    val zoomScale: Float = 1.0f,
    val lineSpacingMultiplier: Float = 1.38f,
    val paragraphSpacingDp: Dp = 8.dp,
    val bubbleCornerRadiusDp: Dp = 18.dp,
    val compactDensity: Boolean = false,
    val userBubbleColorOverride: Color? = null,
    val assistantSurfaceOverride: Color? = null,
    val monoFontFamily: FontFamily = JetBrainsMonoFontFamily,
    val isDark: Boolean = true,
)

val LocalAetherStyle = staticCompositionLocalOf { AetherStyleConfig() }

fun parseHexColorOrNull(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val value = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) {
        Color((0xFF000000L or value).toInt())
    } else {
        Color(value.toInt())
    }
}

fun contrastOnColor(bg: Color): Color =
    if (bg.luminance() > 0.45f) Color(0xFF10131C) else Color(0xFFF7F8FC)

fun resolveFontFamily(selectedKey: String, customFontPath: String): FontFamily {
    if (selectedKey == "custom" && customFontPath.isNotBlank()) {
        val file = File(customFontPath)
        if (file.exists()) {
            val tf = runCatching { Typeface.createFromFile(file) }.getOrNull()
            if (tf != null) return FontFamily(tf)
        }
    }
    return when (selectedKey) {
        "kalam" -> KalamFontFamily
        "plus_jakarta_sans" -> PlusJakartaSansFontFamily
        "space_grotesk" -> SpaceGroteskFontFamily
        "newsreader" -> NewsreaderFontFamily
        "jetbrains_mono" -> JetBrainsMonoFontFamily
        else -> KalamFontFamily
    }
}

@Composable
fun AetherRouterTheme(
    appearance: AppearanceConfig = AppearanceConfig(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (appearance.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }

    val baseScheme = when {
        appearance.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> ExpressiveDarkScheme
        else -> ExpressiveLightScheme
    }

    val amoledAdjusted = if (appearance.themeMode == ThemeMode.AMOLED) {
        baseScheme.copy(
            background = AmoledBg,
            surface = AmoledBg,
            surfaceVariant = AmoledSurfaceContainerHigh,
            surfaceContainerLowest = AmoledBg,
            surfaceContainerLow = AmoledSurfaceContainerLow,
            surfaceContainer = AmoledSurfaceContainer,
            surfaceContainerHigh = AmoledSurfaceContainerHigh,
            surfaceContainerHighest = AmoledSurfaceContainerHighest,
            outlineVariant = AmoledOutlineVariant,
        )
    } else {
        baseScheme
    }

    val accentOverride = remember(appearance.customAccentHex) {
        parseHexColorOrNull(appearance.customAccentHex)
    }
    val userBubbleOverride = remember(appearance.userBubbleHex) {
        parseHexColorOrNull(appearance.userBubbleHex)
    }
    val assistantSurfaceOverride = remember(appearance.assistantSurfaceHex) {
        parseHexColorOrNull(appearance.assistantSurfaceHex)
    }

    val finalScheme = if (accentOverride != null) {
        amoledAdjusted.copy(
            primary = accentOverride,
            onPrimary = contrastOnColor(accentOverride),
            primaryContainer = accentOverride.copy(alpha = if (useDark) 0.28f else 0.18f),
            onPrimaryContainer = if (useDark) Color(0xFFF2F5FF) else Color(0xFF0E1325),
        )
    } else {
        amoledAdjusted
    }

    val bodyFontFamily = remember(appearance.selectedFontKey, appearance.customFontPath) {
        resolveFontFamily(appearance.selectedFontKey, appearance.customFontPath)
    }

    val typography = remember(bodyFontFamily, appearance.selectedFontKey, appearance.zoomScale, appearance.lineSpacingMultiplier) {
        buildAppTypography(
            displayFamily = if (appearance.selectedFontKey == "custom" || bodyFontFamily == KalamFontFamily) {
                bodyFontFamily
            } else {
                SpaceGroteskFontFamily
            },
            bodyFamily = bodyFontFamily,
            zoomScale = appearance.zoomScale,
            lineSpacingMultiplier = appearance.lineSpacingMultiplier,
        )
    }

    CompositionLocalProvider(
        LocalAetherStyle provides AetherStyleConfig(
            zoomScale = appearance.zoomScale,
            lineSpacingMultiplier = appearance.lineSpacingMultiplier,
            paragraphSpacingDp = appearance.paragraphSpacingDp.dp,
            bubbleCornerRadiusDp = appearance.bubbleCornerRadiusDp.dp,
            compactDensity = appearance.compactDensity,
            userBubbleColorOverride = userBubbleOverride,
            assistantSurfaceOverride = assistantSurfaceOverride,
            monoFontFamily = JetBrainsMonoFontFamily,
            isDark = useDark,
        ),
    ) {
        MaterialTheme(
            colorScheme = finalScheme,
            typography = typography,
            content = content,
        )
    }
}
