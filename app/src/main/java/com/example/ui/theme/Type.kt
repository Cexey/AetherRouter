package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

val NewsreaderFontFamily = FontFamily(
    Font(R.font.newsreader, FontWeight.Normal),
    Font(R.font.newsreader, FontWeight.Medium),
    Font(R.font.newsreader, FontWeight.SemiBold),
    Font(R.font.newsreader, FontWeight.Bold),
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono, FontWeight.Bold),
)

fun buildAppTypography(
    displayFamily: FontFamily = SpaceGroteskFontFamily,
    bodyFamily: FontFamily = PlusJakartaSansFontFamily,
    zoomScale: Float = 1.0f,
    lineSpacingMultiplier: Float = 1.35f,
): Typography {
    val z = zoomScale.coerceIn(0.8f, 1.6f)
    val ls = lineSpacingMultiplier.coerceIn(1.0f, 2.2f)
    return Typography(
        displaySmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (32f * z).sp,
            lineHeight = (38f * z).sp,
            letterSpacing = (-0.4).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (24f * z).sp,
            lineHeight = (30f * z).sp,
            letterSpacing = (-0.2).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (20f * z).sp,
            lineHeight = (26f * z).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (18f * z).sp,
            lineHeight = (24f * z).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (15.5f * z).sp,
            lineHeight = (21f * z).sp,
        ),
        titleSmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (13.5f * z).sp,
            lineHeight = (18f * z).sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (15.5f * z).sp,
            lineHeight = (15.5f * z * ls).sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (14f * z).sp,
            lineHeight = (14f * z * ls).sp,
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (12.5f * z).sp,
            lineHeight = (12.5f * z * ls).sp,
        ),
        labelLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (13.5f * z).sp,
            lineHeight = (18f * z).sp,
        ),
        labelMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (11.5f * z).sp,
            lineHeight = (15f * z).sp,
            letterSpacing = 0.25.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (10.5f * z).sp,
            lineHeight = (14f * z).sp,
            letterSpacing = 0.35.sp,
        ),
    )
}

val Typography = buildAppTypography()
