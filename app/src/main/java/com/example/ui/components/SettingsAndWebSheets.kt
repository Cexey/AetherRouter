package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.BiometricVaultHelper
import com.example.data.model.AppPreferences
import com.example.data.model.Conversation
import com.example.data.model.CreatorProfile
import com.example.data.model.ExportFormat
import com.example.data.model.LinkOpenBehavior
import com.example.data.model.ReasoningEffort
import com.example.data.model.ThemeMode
import com.example.data.model.WebSearchEngine
import com.example.data.model.WebSearchMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultAndPreferencesSheet(
    preferences: AppPreferences,
    hasActiveConversation: Boolean,
    onDismiss: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onOpenAppearanceStudio: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    on24HourClockChange: (Boolean) -> Unit,
    onShowTimestampsChange: (Boolean) -> Unit,
    onLinkBehaviorChange: (LinkOpenBehavior) -> Unit,
    onBiometricLockChange: (Boolean) -> Unit,
    onSmartRepliesChange: (Boolean) -> Unit,
    onSaveCreatorProfile: (CreatorProfile) -> Unit,
    onProfileLinkClick: (String) -> Unit,
    onProfileEmailClick: (String) -> Unit,
    onExportChatSaf: (ExportFormat) -> Unit,
    onShareChatFile: (ExportFormat) -> Unit,
    onExportVaultSaf: (String) -> Unit,
    onShareVaultFile: (String) -> Unit,
    onImportBackupSaf: (String) -> Unit,
) {
    val context = LocalContext.current
    val biometricAvailable = remember(context) { BiometricVaultHelper.canAuthenticate(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var apiKeyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Vault & Core Settings",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }

            // Encrypted OpenRouter API Key Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = "AES-256-GCM encrypted",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (preferences.hasApiKey) {
                                "OpenRouter API Key Saved (AES-256-GCM)"
                            } else {
                                "Configure OpenRouter API Key"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = {
                            Text(if (preferences.hasApiKey) "Replace API key (sk-or-v1-...)" else "Enter OpenRouter API key")
                        },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showKey) "Hide key" else "Show key",
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (apiKeyInput.isNotBlank()) {
                                    onSaveApiKey(apiKeyInput)
                                    apiKeyInput = ""
                                }
                            },
                            enabled = apiKeyInput.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_api_key_button"),
                        ) {
                            Text("Encrypt & Sync")
                        }
                        if (preferences.hasApiKey) {
                            OutlinedButton(
                                onClick = { onSaveApiKey("") },
                                modifier = Modifier.testTag("clear_api_key_button"),
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Vault Biometric Security & Smart Replies
            Text(
                text = "Vault Security & Assistant UX",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            ToggleSettingRow(
                title = "Biometric Vault Lock",
                subtitle = if (biometricAvailable) {
                    "Require fingerprint, face unlock, or device PIN when opening AetherRouter"
                } else {
                    "Set up a device screen lock or biometric in Android Settings to enable"
                },
                checked = preferences.biometricLockEnabled && biometricAvailable,
                enabled = biometricAvailable,
                onCheckedChange = onBiometricLockChange,
            )
            ToggleSettingRow(
                title = "Contextual Smart Reply Chips",
                subtitle = "Show 3 instant follow-up suggestions above the message composer",
                checked = preferences.smartRepliesEnabled,
                onCheckedChange = onSmartRepliesChange,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Appearance, Typography & Zoom Studio Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Appearance & Typography",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenAppearanceStudio()
                    },
                    modifier = Modifier.testTag("open_appearance_studio_button"),
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Full Studio")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = preferences.themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(if (mode == ThemeMode.AMOLED) "AMOLED" else mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            ToggleSettingRow(
                title = "Material You Dynamic Color",
                subtitle = "Zoom ${(preferences.appearance.zoomScale * 100).roundToInt()}% • Line ${String.format("%.2fx", preferences.appearance.lineSpacingMultiplier)}",
                checked = preferences.useDynamicColor,
                onCheckedChange = onDynamicColorChange,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Creator Profile & Project Links Card
            CreatorProfileCard(
                profile = preferences.creatorProfile,
                onSaveProfile = onSaveCreatorProfile,
                onLinkClick = { url ->
                    onDismiss()
                    onProfileLinkClick(url)
                },
                onEmailClick = onProfileEmailClick,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Export, Google Drive (SAF) & Share Sheet Card
            ExportAndBackupCard(
                hasActiveConversation = hasActiveConversation,
                onExportChatSaf = onExportChatSaf,
                onShareChatFile = onShareChatFile,
                onExportVaultSaf = onExportVaultSaf,
                onShareVaultFile = onShareVaultFile,
                onImportBackupSaf = onImportBackupSaf,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Real-Time Clock & Timestamps
            Text(
                text = "Clock & Timestamps",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            ToggleSettingRow(
                title = "24-Hour Clock Format",
                subtitle = "Applies to live header clock, chat bubbles, and model grounding",
                checked = preferences.use24HourClock,
                onCheckedChange = on24HourClockChange,
            )
            ToggleSettingRow(
                title = "Show Per-Message Timestamps",
                subtitle = "Display exact send/receive times beneath every bubble",
                checked = preferences.showMessageTimestamps,
                onCheckedChange = onShowTimestampsChange,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Link Opening Behavior
            Text(
                text = "Chat Link Behavior",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinkOpenBehavior.entries.forEach { behavior ->
                    FilterChip(
                        selected = preferences.linkOpenBehavior == behavior,
                        onClick = { onLinkBehaviorChange(behavior) },
                        label = {
                            Text(
                                when (behavior) {
                                    LinkOpenBehavior.IN_APP -> "In-App"
                                    LinkOpenBehavior.ASK -> "Ask"
                                    LinkOpenBehavior.EXTERNAL -> "External"
                                },
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAndSamplerSheet(
    conversation: Conversation?,
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    onSave: (
        systemPrompt: String,
        temperature: Float,
        topP: Float,
        maxTokens: Int,
        reasoningEffort: ReasoningEffort,
        webSearchEnabled: Boolean,
        webSearchMode: WebSearchMode,
        webSearchEngine: WebSearchEngine,
        webMaxResults: Int,
        webMaxTotalResults: Int,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var systemPrompt by remember(conversation) { mutableStateOf(conversation?.systemPromptOverride.orEmpty()) }
    var temperature by remember(conversation) { mutableFloatStateOf(conversation?.temperature ?: 0.7f) }
    var topP by remember(conversation) { mutableFloatStateOf(conversation?.topP ?: 1.0f) }
    var maxTokens by remember(conversation) { mutableIntStateOf(conversation?.maxTokens ?: 4096) }
    var reasoningEffort by remember(conversation) {
        mutableStateOf(conversation?.reasoningEffort ?: ReasoningEffort.MEDIUM)
    }
    var webEnabled by remember(conversation) {
        mutableStateOf(conversation?.webSearchEnabled ?: preferences.defaultWebSearchEnabled)
    }
    var webMode by remember(conversation) {
        mutableStateOf(conversation?.webSearchMode ?: preferences.defaultWebSearchMode)
    }
    var webEngine by remember(conversation) {
        mutableStateOf(conversation?.webSearchEngine ?: preferences.defaultWebSearchEngine)
    }
    var webMaxResults by remember(conversation) {
        mutableIntStateOf(conversation?.webMaxResults ?: preferences.defaultWebMaxResults)
    }
    var webMaxTotalResults by remember(conversation) {
        mutableIntStateOf(conversation?.webMaxTotalResults ?: preferences.defaultWebMaxTotalResults)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Live Web & Sampler Studio",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }

            // Live Web Search Controls
            ToggleSettingRow(
                title = "Live Web Search & Fetch",
                subtitle = "Give models live internet access & clickable source citations",
                checked = webEnabled,
                onCheckedChange = { webEnabled = it },
            )

            if (webEnabled) {
                Text(
                    text = "Search Mode",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = webMode == WebSearchMode.AGENTIC_TOOL,
                        onClick = { webMode = WebSearchMode.AGENTIC_TOOL },
                        label = { Text("Agentic Tools (Billing-Safe)") },
                    )
                    FilterChip(
                        selected = webMode == WebSearchMode.ALWAYS_ON_PLUGIN,
                        onClick = { webMode = WebSearchMode.ALWAYS_ON_PLUGIN },
                        label = { Text("Always-On Plugin") },
                    )
                }
                Text(
                    text = if (webMode == WebSearchMode.AGENTIC_TOOL) {
                        "Sends openrouter:web_search + openrouter:web_fetch server tools so OpenRouter only bills search credits when the model actually needs live web data."
                    } else {
                        "Forces OpenRouter's web plugin on every turn (ideal for non-tool models or guaranteed grounding)."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )

                Text(
                    text = "Search Engine",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WebSearchEngine.entries.forEach { engine ->
                        FilterChip(
                            selected = webEngine == engine,
                            onClick = { webEngine = engine },
                            label = { Text(engine.label) },
                        )
                    }
                }

                SliderSettingRow(
                    label = "Max Results per Query",
                    valueLabel = "$webMaxResults",
                    value = webMaxResults.toFloat(),
                    valueRange = 1f..10f,
                    onValueChange = { webMaxResults = it.roundToInt() },
                )
                if (webMode == WebSearchMode.AGENTIC_TOOL) {
                    SliderSettingRow(
                        label = "Max Total Results (Agentic Loop)",
                        valueLabel = "$webMaxTotalResults",
                        value = webMaxTotalResults.toFloat(),
                        valueRange = 5f..30f,
                        onValueChange = { webMaxTotalResults = it.roundToInt() },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Reasoning Effort
            Text(
                text = "Reasoning Effort",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    ReasoningEffort.NONE,
                    ReasoningEffort.LOW,
                    ReasoningEffort.MEDIUM,
                    ReasoningEffort.HIGH,
                ).forEach { effort ->
                    FilterChip(
                        selected = reasoningEffort == effort,
                        onClick = { reasoningEffort = effort },
                        label = { Text(effort.label) },
                    )
                }
            }

            // Samplers
            SliderSettingRow(
                label = "Temperature",
                valueLabel = String.format("%.2f", temperature),
                value = temperature,
                valueRange = 0f..2f,
                onValueChange = { temperature = it },
            )
            SliderSettingRow(
                label = "Top-P",
                valueLabel = String.format("%.2f", topP),
                value = topP,
                valueRange = 0.05f..1f,
                onValueChange = { topP = it },
            )
            SliderSettingRow(
                label = "Max Output Tokens",
                valueLabel = "$maxTokens",
                value = maxTokens.toFloat(),
                valueRange = 512f..16384f,
                onValueChange = { maxTokens = ((it / 256f).roundToInt() * 256).coerceIn(512, 16384) },
            )

            // Custom System Prompt Override
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("Conversation System Prompt") },
                placeholder = { Text("Custom instructions for this conversation…") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_prompt_input"),
            )

            Button(
                onClick = {
                    onSave(
                        systemPrompt,
                        temperature,
                        topP,
                        maxTokens,
                        reasoningEffort,
                        webEnabled,
                        webMode,
                        webEngine,
                        webMaxResults,
                        webMaxTotalResults,
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_sampler_web_button"),
            ) {
                Text("Apply to Conversation")
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderSettingRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
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
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}
