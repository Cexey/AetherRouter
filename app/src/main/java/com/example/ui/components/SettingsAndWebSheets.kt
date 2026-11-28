package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.core.BiometricVaultHelper
import com.example.data.model.AppPreferences
import com.example.data.model.Conversation
import com.example.data.model.CreatorProfile
import com.example.data.model.ExportFormat
import com.example.data.model.LinkOpenBehavior
import com.example.data.model.ModelInfo
import com.example.data.model.OpenRouterCreditBalance
import com.example.data.model.OpenRouterUsageSnapshot
import com.example.data.model.ReasoningEffort
import com.example.data.model.SamplerPreset
import com.example.data.model.ThemeMode
import com.example.data.model.WebSearchEngine
import com.example.data.model.WebSearchMode
import com.example.ui.theme.LiveWebActiveColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultAndPreferencesSheet(
    preferences: AppPreferences,
    hasActiveConversation: Boolean,
    creditBalance: OpenRouterCreditBalance? = null,
    usageSnapshot: OpenRouterUsageSnapshot? = null,
    isLoadingCredits: Boolean = false,
    onRefreshCredits: () -> Unit = {},
    onOpenUsageStudio: () -> Unit = {},
    onSaveManagementApiKey: (String) -> Unit = {},
    onSaveGitHubPat: (String) -> Unit = {},
    onOpenGitHubStudio: () -> Unit = {},
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
    var mgmtKeyInput by remember { mutableStateOf("") }
    var showMgmtKey by remember { mutableStateOf(false) }
    var githubPatInput by remember { mutableStateOf("") }
    var showGithubPat by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
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
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Vault & Core Settings",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
            }

            // Compact Live Spend & Remaining Credits Pill in Settings Header
            CompactSpendCreditsPill(
                usageSnapshot = usageSnapshot,
                hasApiKey = preferences.hasApiKey,
                isLoading = isLoadingCredits,
                onClick = {
                    onDismiss()
                    onOpenUsageStudio()
                },
                testTagName = "settings_credits_pill",
            )

            // Encrypted OpenRouter API Key Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = aetherCardBorder(),
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
                        colors = aetherTextFieldColors(),
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
                                colors = aetherSlateButtonColors(),
                                border = aetherCardBorder(),
                                modifier = Modifier.testTag("clear_api_key_button"),
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    // Live OpenRouter Credit Balance Strip
                    if (preferences.hasApiKey) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = "OpenRouter credit balance",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                val balanceHeadline = when {
                                    creditBalance == null -> "Tap refresh to check OpenRouter credits"
                                    creditBalance.remainingUsd != null ->
                                        "Remaining: ${String.format("$%.2f", creditBalance.remainingUsd)}"
                                    creditBalance.isFreeTier -> "OpenRouter Free Tier"
                                    else -> "Active Key (No credit cap)"
                                }
                                Text(
                                    text = balanceHeadline,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    modifier = Modifier.testTag("openrouter_credit_balance_text"),
                                )
                                if (creditBalance != null) {
                                    Text(
                                        text = "Total used: ${String.format("$%.4f", creditBalance.totalUsageUsd)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                            }
                            IconButton(
                                onClick = onRefreshCredits,
                                enabled = !isLoadingCredits,
                                modifier = Modifier.testTag("refresh_credits_button"),
                            ) {
                                if (isLoadingCredits) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = "Refresh credit balance",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Optional OpenRouter Management API Key (30-Day Activity Breakdown)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = aetherCardBorder(),
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
                            imageVector = Icons.Outlined.BarChart,
                            contentDescription = "OpenRouter Management API Key",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (preferences.hasManagementApiKey) {
                                "Management API Key Saved (AES-256-GCM)"
                            } else {
                                "OpenRouter Management Key (Optional)"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                    Text(
                        text = "Unlocks 30-day model & date spend breakdown via /api/v1/activity in Usage & Credits Studio.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    OutlinedTextField(
                        value = mgmtKeyInput,
                        onValueChange = { mgmtKeyInput = it },
                        label = {
                            Text(
                                if (preferences.hasManagementApiKey) "Replace Management API key"
                                else "Enter OpenRouter Management API key",
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (showMgmtKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showMgmtKey = !showMgmtKey }) {
                                Icon(
                                    imageVector = if (showMgmtKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showMgmtKey) "Hide management key" else "Show management key",
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = aetherTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("management_api_key_input"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (mgmtKeyInput.isNotBlank()) {
                                    onSaveManagementApiKey(mgmtKeyInput)
                                    mgmtKeyInput = ""
                                }
                            },
                            enabled = mgmtKeyInput.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_management_api_key_button"),
                        ) {
                            Text("Encrypt & Load 30d")
                        }
                        if (preferences.hasManagementApiKey) {
                            OutlinedButton(
                                onClick = { onSaveManagementApiKey("") },
                                colors = aetherSlateButtonColors(),
                                border = aetherCardBorder(),
                                modifier = Modifier.testTag("clear_management_api_key_button"),
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            // Optional GitHub Personal Access Token (PAT) for GitHub Repo Studio
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = aetherCardBorder(),
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
                            imageVector = Icons.Outlined.Code,
                            contentDescription = "GitHub Personal Access Token",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (preferences.hasGitHubPat) {
                                "GitHub PAT Saved (AES-256-GCM)"
                            } else {
                                "GitHub Personal Access Token (Optional)"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                    Text(
                        text = "Browse repos, attach source files to chat, and push AI code commits to any branch via api.github.com.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    OutlinedTextField(
                        value = githubPatInput,
                        onValueChange = { githubPatInput = it },
                        label = {
                            Text(
                                if (preferences.hasGitHubPat) "Replace GitHub PAT (ghp_... / github_pat_...)"
                                else "Enter GitHub Personal Access Token",
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (showGithubPat) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showGithubPat = !showGithubPat }) {
                                Icon(
                                    imageVector = if (showGithubPat) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showGithubPat) "Hide GitHub token" else "Show GitHub token",
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = aetherTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_pat_input"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (githubPatInput.isNotBlank()) {
                                    onSaveGitHubPat(githubPatInput)
                                    githubPatInput = ""
                                }
                            },
                            enabled = githubPatInput.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_github_pat_button"),
                        ) {
                            Text("Encrypt & Verify")
                        }
                        if (preferences.hasGitHubPat) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenGitHubStudio()
                                },
                                colors = aetherSlateButtonColors(),
                                border = aetherCardBorder(),
                                modifier = Modifier.testTag("settings_open_github_studio_button"),
                            ) {
                                Text("Browse")
                            }
                            OutlinedButton(
                                onClick = { onSaveGitHubPat("") },
                                colors = aetherSlateButtonColors(),
                                border = aetherCardBorder(),
                                modifier = Modifier.testTag("clear_github_pat_button"),
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
                    colors = aetherSlateButtonColors(),
                    border = aetherCardBorder(),
                    modifier = Modifier.testTag("open_appearance_studio_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Full Studio")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val isSelected = preferences.themeMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { onThemeModeChange(mode) },
                        colors = aetherFilterChipColors(),
                        border = aetherFilterChipBorder(selected = isSelected),
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
                    val isSelected = preferences.linkOpenBehavior == behavior
                    FilterChip(
                        selected = isSelected,
                        onClick = { onLinkBehaviorChange(behavior) },
                        colors = aetherFilterChipColors(),
                        border = aetherFilterChipBorder(selected = isSelected),
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // About, Author Contact & Version Footer
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = aetherCardBorder(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_about_footer_card"),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AetherCrystalEmblem(
                            size = 40.dp,
                            cornerRadius = 11.dp,
                            showHalo = false,
                            contentDescription = "AetherRouter",
                            modifier = Modifier.testTag("settings_about_logo"),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AetherRouter",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Built by Cexey • Cexey's Creations",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = aetherCardBorder(selected = true),
                            modifier = Modifier.testTag("settings_version_badge"),
                        ) {
                            val displayVersion = if (BuildConfig.VERSION_NAME.startsWith("v", ignoreCase = true)) {
                                BuildConfig.VERSION_NAME
                            } else {
                                "v${BuildConfig.VERSION_NAME}"
                            }
                            Text(
                                text = "$displayVersion (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            onDismiss()
                            onProfileLinkClick("https://github.com/Cexey/AetherRouter")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = aetherCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("settings_footer_github_row"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = "GitHub repository",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GitHub",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                                Text(
                                    text = "github.com/Cexey/AetherRouter",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { onProfileEmailClick("kustard.cloudz@gmail.com") },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = aetherCardBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("settings_footer_email_row"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = "Developer email",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Email",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                                Text(
                                    text = "kustard.cloudz@gmail.com",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
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
    allModels: List<ModelInfo> = emptyList(),
    samplerPresets: List<SamplerPreset> = emptyList(),
    onSaveSamplerPreset: (
        name: String,
        temperature: Float,
        topP: Float,
        maxTokens: Int,
        reasoningEffort: ReasoningEffort,
        systemPromptSnippet: String,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteSamplerPreset: (String) -> Unit = {},
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
        fallbackModelIds: List<String>,
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
    var fallbackModelIds by remember(conversation) {
        mutableStateOf(conversation?.fallbackModelIds ?: emptyList())
    }
    var showFallbackPickerMenu by remember { mutableStateOf(false) }
    var showSavePresetRow by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
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
                    tint = LiveWebActiveColor,
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
                    val isAgentic = webMode == WebSearchMode.AGENTIC_TOOL
                    val isAlwaysOn = webMode == WebSearchMode.ALWAYS_ON_PLUGIN
                    FilterChip(
                        selected = isAgentic,
                        onClick = { webMode = WebSearchMode.AGENTIC_TOOL },
                        colors = aetherFilterChipColors(),
                        border = aetherFilterChipBorder(selected = isAgentic),
                        label = { Text("Agentic Tools (Billing-Safe)") },
                    )
                    FilterChip(
                        selected = isAlwaysOn,
                        onClick = { webMode = WebSearchMode.ALWAYS_ON_PLUGIN },
                        colors = aetherFilterChipColors(),
                        border = aetherFilterChipBorder(selected = isAlwaysOn),
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
                        val isEngineSelected = webEngine == engine
                        FilterChip(
                            selected = isEngineSelected,
                            onClick = { webEngine = engine },
                            colors = aetherFilterChipColors(),
                            border = aetherFilterChipBorder(selected = isEngineSelected),
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

            // Sampler Presets (Built-in & Custom)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Sampler Presets",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                OutlinedButton(
                    onClick = { showSavePresetRow = !showSavePresetRow },
                    colors = aetherSlateButtonColors(),
                    border = aetherCardBorder(),
                    modifier = Modifier.testTag("toggle_save_preset_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Current")
                }
            }

            if (samplerPresets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    samplerPresets.forEach { preset ->
                        InputChip(
                            selected = false,
                            onClick = {
                                temperature = preset.temperature
                                topP = preset.topP
                                maxTokens = preset.maxTokens
                                reasoningEffort = preset.reasoningEffort
                                if (preset.systemPromptSnippet.isNotBlank()) {
                                    systemPrompt = preset.systemPromptSnippet
                                }
                            },
                            label = { Text(preset.name) },
                            trailingIcon = if (!preset.isBuiltIn) {
                                {
                                    IconButton(
                                        onClick = { onDeleteSamplerPreset(preset.id) },
                                        modifier = Modifier.size(18.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "Delete preset",
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            } else null,
                        )
                    }
                }
            }

            if (showSavePresetRow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        label = { Text("New Preset Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = newPresetName.isNotBlank(),
                        onClick = {
                            onSaveSamplerPreset(
                                newPresetName.trim(),
                                temperature,
                                topP,
                                maxTokens,
                                reasoningEffort,
                                systemPrompt,
                            )
                            newPresetName = ""
                            showSavePresetRow = false
                        },
                    ) {
                        Text("Save")
                    }
                }
            }

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
                    val isEffortSelected = reasoningEffort == effort
                    FilterChip(
                        selected = isEffortSelected,
                        onClick = { reasoningEffort = effort },
                        colors = aetherFilterChipColors(),
                        border = aetherFilterChipBorder(selected = isEffortSelected),
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // OpenRouter Automatic Fallback Routing
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Fallback Model Routing",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                Box {
                    OutlinedButton(
                        enabled = fallbackModelIds.size < 3 && allModels.isNotEmpty(),
                        onClick = { showFallbackPickerMenu = true },
                        colors = aetherSlateButtonColors(),
                        border = aetherCardBorder(),
                        modifier = Modifier.testTag("add_fallback_model_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                    val primaryId = conversation?.modelId ?: preferences.defaultModelId
                    val candidates = remember(allModels, primaryId, fallbackModelIds) {
                        allModels.filter { it.id != primaryId && it.id !in fallbackModelIds }.take(25)
                    }
                    DropdownMenu(
                        expanded = showFallbackPickerMenu,
                        onDismissRequest = { showFallbackPickerMenu = false },
                    ) {
                        candidates.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${m.name} (${m.company})",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = {
                                    fallbackModelIds = (fallbackModelIds + m.id).distinct().take(3)
                                    showFallbackPickerMenu = false
                                },
                            )
                        }
                    }
                }
            }
            Text(
                text = "If the primary model hits a rate limit or provider outage, OpenRouter automatically fails over down this ordered list.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            if (fallbackModelIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    fallbackModelIds.forEachIndexed { idx, fid ->
                        val label = allModels.firstOrNull { it.id == fid }?.name ?: fid.substringAfter('/')
                        InputChip(
                            selected = true,
                            onClick = { fallbackModelIds = fallbackModelIds.filterNot { it == fid } },
                            label = { Text("#${idx + 1} $label") },
                            trailingIcon = {
                                Icon(Icons.Outlined.Close, contentDescription = "Remove fallback", modifier = Modifier.size(14.dp))
                            },
                        )
                    }
                }
            }

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
                        fallbackModelIds,
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
