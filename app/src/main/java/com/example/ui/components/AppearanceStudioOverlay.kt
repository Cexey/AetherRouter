package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppearanceConfig
import com.example.data.model.ThemeMode
import com.example.ui.theme.LocalAetherStyle
import com.example.ui.theme.contrastOnColor
import com.example.ui.theme.parseHexColorOrNull
import kotlin.math.roundToInt

private val ACCENT_SWATCHES = listOf(
    "" to "Default",
    "#5B7CFA" to "Cobalt",
    "#20C9B7" to "Teal",
    "#9B6DFF" to "Violet",
    "#F46A8B" to "Rose",
    "#F5A623" to "Amber",
    "#38BDF8" to "Sky",
    "#22C55E" to "Emerald",
)

private val BUBBLE_SWATCHES = listOf(
    "" to "Auto",
    "#25356B" to "Deep Navy",
    "#184B47" to "Pine Teal",
    "#4B296B" to "Plum",
    "#6B2737" to "Burgundy",
    "#1F2937" to "Slate",
)

private val SURFACE_SWATCHES = listOf(
    "" to "Auto",
    "#111420" to "Obsidian",
    "#08090E" to "Void",
    "#181B29" to "Midnight",
    "#F2F4FA" to "Alabaster",
)

private val BUNDLED_FONTS = listOf(
    "plus_jakarta_sans" to "Plus Jakarta Sans",
    "space_grotesk" to "Space Grotesk",
    "newsreader" to "Newsreader (Serif)",
    "jetbrains_mono" to "JetBrains Mono",
)

@Composable
fun AppearanceStudioOverlay(
    appearance: AppearanceConfig,
    onClose: () -> Unit,
    onUpdateAppearance: ((AppearanceConfig) -> AppearanceConfig) -> Unit,
    onUploadCustomFontClick: () -> Unit,
    onResetDefaults: () -> Unit,
) {
    BackHandler(onBack = onClose)

    var accentHexInput by remember(appearance.customAccentHex) { mutableStateOf(appearance.customAccentHex) }
    var userBubbleHexInput by remember(appearance.userBubbleHex) { mutableStateOf(appearance.userBubbleHex) }
    var surfaceHexInput by remember(appearance.assistantSurfaceHex) { mutableStateOf(appearance.assistantSurfaceHex) }
    val style = LocalAetherStyle.current

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("appearance_studio_overlay"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("appearance_close_button"),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Appearance & Typography",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.testTag("appearance_reset_button"),
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Live Miniature Chat Preview
                LiveChatPreviewCard(
                    cornerRadiusDp = style.bubbleCornerRadiusDp,
                    userBubbleOverride = style.userBubbleColorOverride,
                    assistantSurfaceOverride = style.assistantSurfaceOverride,
                    paragraphSpacingDp = style.paragraphSpacingDp,
                )

                // Theme Mode & Dynamic Color
                SectionHeader("Theme & Material You")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.AMOLED -> "AMOLED #000"
                        }
                        FilterChip(
                            selected = appearance.themeMode == mode,
                            onClick = { onUpdateAppearance { it.copy(themeMode = mode) } },
                            label = { Text(label) },
                            modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}"),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Material You Dynamic Color", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Match your Android 12+ wallpaper palette automatically",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    Switch(
                        checked = appearance.useDynamicColor,
                        onCheckedChange = { checked -> onUpdateAppearance { it.copy(useDynamicColor = checked) } },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Custom Color Overrides
                SectionHeader("Custom Palette Overrides")
                ColorSwatchPickerRow(
                    title = "Accent Color",
                    swatches = ACCENT_SWATCHES,
                    selectedHex = appearance.customAccentHex,
                    hexInput = accentHexInput,
                    onHexInputChange = { next ->
                        accentHexInput = next
                        if (next.isBlank() || parseHexColorOrNull(next) != null) {
                            onUpdateAppearance { it.copy(customAccentHex = next.trim()) }
                        }
                    },
                    onSelectHex = { hex ->
                        accentHexInput = hex
                        onUpdateAppearance { it.copy(customAccentHex = hex) }
                    },
                )

                ColorSwatchPickerRow(
                    title = "User Bubble Color",
                    swatches = BUBBLE_SWATCHES,
                    selectedHex = appearance.userBubbleHex,
                    hexInput = userBubbleHexInput,
                    onHexInputChange = { next ->
                        userBubbleHexInput = next
                        if (next.isBlank() || parseHexColorOrNull(next) != null) {
                            onUpdateAppearance { it.copy(userBubbleHex = next.trim()) }
                        }
                    },
                    onSelectHex = { hex ->
                        userBubbleHexInput = hex
                        onUpdateAppearance { it.copy(userBubbleHex = hex) }
                    },
                )

                ColorSwatchPickerRow(
                    title = "Assistant Card Surface",
                    swatches = SURFACE_SWATCHES,
                    selectedHex = appearance.assistantSurfaceHex,
                    hexInput = surfaceHexInput,
                    onHexInputChange = { next ->
                        surfaceHexInput = next
                        if (next.isBlank() || parseHexColorOrNull(next) != null) {
                            onUpdateAppearance { it.copy(assistantSurfaceHex = next.trim()) }
                        }
                    },
                    onSelectHex = { hex ->
                        surfaceHexInput = hex
                        onUpdateAppearance { it.copy(assistantSurfaceHex = hex) }
                    },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Typography & Custom Font Upload
                SectionHeader("Typography & Custom Font (.ttf / .otf)")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BUNDLED_FONTS.forEach { (key, label) ->
                            FilterChip(
                                selected = appearance.selectedFontKey == key,
                                onClick = { onUpdateAppearance { it.copy(selectedFontKey = key) } },
                                label = { Text(label) },
                            )
                        }
                        if (appearance.customFontPath.isNotBlank()) {
                            FilterChip(
                                selected = appearance.selectedFontKey == "custom",
                                onClick = { onUpdateAppearance { it.copy(selectedFontKey = "custom") } },
                                label = { Text(appearance.customFontName.ifBlank { "Custom Font" }) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.FontDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onUploadCustomFontClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_custom_font_button"),
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (appearance.customFontName.isNotBlank()) {
                                "Replace Custom Font (${appearance.customFontName})"
                            } else {
                                "Upload Custom Font (.ttf / .otf)"
                            },
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Screen Zoom, Line Spacing, Paragraph Spacing, Bubble Radius
                SectionHeader("Screen Zoom & Readability")

                StudioSliderRow(
                    label = "Screen Zoom",
                    valueLabel = "${(appearance.zoomScale * 100).roundToInt()}%",
                    value = appearance.zoomScale,
                    range = 0.8f..1.6f,
                    onChange = { v -> onUpdateAppearance { it.copy(zoomScale = v) } },
                    tag = "zoom_scale_slider",
                )

                StudioSliderRow(
                    label = "Line Spacing",
                    valueLabel = String.format("%.2fx", appearance.lineSpacingMultiplier),
                    value = appearance.lineSpacingMultiplier,
                    range = 1.0f..2.2f,
                    onChange = { v -> onUpdateAppearance { it.copy(lineSpacingMultiplier = v) } },
                    tag = "line_spacing_slider",
                )

                StudioSliderRow(
                    label = "Paragraph Spacing",
                    valueLabel = "${appearance.paragraphSpacingDp.roundToInt()} dp",
                    value = appearance.paragraphSpacingDp,
                    range = 4f..20f,
                    onChange = { v -> onUpdateAppearance { it.copy(paragraphSpacingDp = v) } },
                    tag = "paragraph_spacing_slider",
                )

                StudioSliderRow(
                    label = "Bubble Corner Radius",
                    valueLabel = "${appearance.bubbleCornerRadiusDp.roundToInt()} dp",
                    value = appearance.bubbleCornerRadiusDp,
                    range = 8f..28f,
                    onChange = { v -> onUpdateAppearance { it.copy(bubbleCornerRadiusDp = v) } },
                    tag = "bubble_radius_slider",
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Compact Message Density", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Tighter padding to fit more conversation on screen",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    Switch(
                        checked = appearance.compactDensity,
                        onCheckedChange = { checked -> onUpdateAppearance { it.copy(compactDensity = checked) } },
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun LiveChatPreviewCard(
    cornerRadiusDp: androidx.compose.ui.unit.Dp,
    userBubbleOverride: Color?,
    assistantSurfaceOverride: Color?,
    paragraphSpacingDp: androidx.compose.ui.unit.Dp,
) {
    val userBg = userBubbleOverride ?: MaterialTheme.colorScheme.primaryContainer
    val userFg = if (userBubbleOverride != null) contrastOnColor(userBubbleOverride) else MaterialTheme.colorScheme.onPrimaryContainer
    val assistantBg = assistantSurfaceOverride ?: MaterialTheme.colorScheme.surfaceContainerLow
    val assistantFg = if (assistantSurfaceOverride != null) contrastOnColor(assistantSurfaceOverride) else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Live Preview",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = cornerRadiusDp,
                        topEnd = cornerRadiusDp,
                        bottomStart = cornerRadiusDp,
                        bottomEnd = (cornerRadiusDp.value * 0.35f).coerceAtLeast(4f).dp,
                    ),
                    color = userBg,
                    modifier = Modifier.widthIn(max = 260.dp),
                ) {
                    Text(
                        text = "How does the custom font and zoom scale look right now?",
                        style = MaterialTheme.typography.bodyLarge.copy(color = userFg),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(cornerRadiusDp),
                color = assistantBg,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(paragraphSpacingDp),
                ) {
                    Text(
                        text = "Crisp, spacious, and tuned to your exact readability preferences.",
                        style = MaterialTheme.typography.bodyLarge.copy(color = assistantFg),
                    )
                    Text(
                        text = "Paragraph spacing, line height, and colors update in real time across every chat.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = assistantFg.copy(alpha = 0.82f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchPickerRow(
    title: String,
    swatches: List<Pair<String, String>>,
    selectedHex: String,
    hexInput: String,
    onHexInputChange: (String) -> Unit,
    onSelectHex: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            swatches.forEach { (hex, label) ->
                val swatchColor = parseHexColorOrNull(hex) ?: MaterialTheme.colorScheme.surfaceContainerHighest
                val isSelected = selectedHex.equals(hex, ignoreCase = true)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable { onSelectHex(hex) },
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = label,
                                tint = if (hex.isBlank()) MaterialTheme.colorScheme.primary else contrastOnColor(swatchColor),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
        OutlinedTextField(
            value = hexInput,
            onValueChange = onHexInputChange,
            label = { Text("Custom Hex (e.g. #5B7CFA, blank for Auto)") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun StudioSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    tag: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.testTag(tag),
        )
    }
}
