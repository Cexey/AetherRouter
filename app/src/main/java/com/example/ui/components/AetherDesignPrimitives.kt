package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Shared AetherRouter crystal-and-gold emblem rendered inside a dark squircle badge
 * with a subtle Cobalt-to-Gold hairline border and optional low-glare radial aura.
 */
@Composable
fun AetherCrystalEmblem(
    size: Dp,
    cornerRadius: Dp = (size.value * 0.26f).coerceIn(8f, 24f).dp,
    showHalo: Boolean = false,
    haloSize: Dp = size + 24.dp,
    contentDescription: String? = "AetherRouter",
    modifier: Modifier = Modifier,
) {
    val logoShape = RoundedCornerShape(cornerRadius)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.48f),
            MaterialTheme.colorScheme.outlineVariant,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f),
        ),
    )

    val badgeContent: @Composable (Modifier) -> Unit = { badgeModifier ->
        Surface(
            shape = logoShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, borderBrush),
            shadowElevation = if (showHalo) 6.dp else 2.dp,
            modifier = badgeModifier.size(size),
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_aetherrouter_mark_1795690958658),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(logoShape),
            )
        }
    }

    if (showHalo) {
        val haloBrush = Brush.radialGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                Color.Transparent,
            ),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(haloSize)
                .background(haloBrush, CircleShape),
        ) {
            badgeContent(modifier)
        }
    } else {
        badgeContent(modifier)
    }
}

/**
 * Standard dark blue-slate pill button colors matching the Home quick-action pills.
 */
@Composable
fun aetherSlateButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
)

/**
 * Crisp low-contrast border for dark slate cards, pills, and selected cobalt highlights.
 */
@Composable
fun aetherCardBorder(
    selected: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
): BorderStroke = BorderStroke(
    width = 1.dp,
    color = if (selected) {
        accent.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    },
)

/**
 * Unified dark slate FilterChip styling with Crystal Cobalt active fill and crisp borders.
 */
@Composable
fun aetherFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
fun aetherFilterChipBorder(
    enabled: Boolean = true,
    selected: Boolean,
): BorderStroke = FilterChipDefaults.filterChipBorder(
    enabled = enabled,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outlineVariant,
    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
    borderWidth = 1.dp,
    selectedBorderWidth = 1.dp,
)

/**
 * Unified dark slate text field styling with Crystal Cobalt focus ring and crisp resting border.
 */
@Composable
fun aetherTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * Subtle nocturnal obsidian radial aura used at the top of drawers, lock screens, and studios.
 */
@Composable
fun aetherSubtleAuraBrush(alpha: Float = 0.16f): Brush = Brush.radialGradient(
    colors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha),
        MaterialTheme.colorScheme.background,
    ),
)
